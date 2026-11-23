package com.example.webapp;

import com.example.webapp.security.SecurityTestClient;
import com.example.webapp.security.SecurityTestConfiguration;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import java.io.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "spring.datasource.url=jdbc:h2:mem:job-import-tests;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
@Import(SecurityTestConfiguration.class)
class JobExcelImportTests {
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    SecurityTestClient client;
    long companyId;
    static final String[] HEADERS = {"کد شغل", "عنوان شغل", "شرکت", "کد شغل بالادست", "توضیحات"};

    @BeforeEach void prepare() throws Exception {
        client = SecurityTestClient.shared(port);
        jdbc.update("UPDATE jobs SET parent_id = NULL");jdbc.update("DELETE FROM jobs");jdbc.update("DELETE FROM companies");
        jdbc.update("INSERT INTO companies(code,title,description) VALUES ('C1','شرکت دریایی','PRIVATE-COMPANY')");
        jdbc.update("INSERT INTO companies(code,title) VALUES ('C2','شرکت دوم')");
        companyId = jdbc.queryForObject("SELECT id FROM companies WHERE code='C1'", Long.class);
    }

    @Test void previewsWithoutWritingAndImportsChildrenBeforeTheirParentsInFileOrder() throws Exception {
        jdbc.update("INSERT INTO jobs(code,title,company_id) VALUES ('EXIST','بالادست موجود',?)", companyId);
        byte[] file = file(new String[]{"003","فرزند دوم","C1","002"},
            new String[]{"002","فرزند اول","C1 — شرکت دریایی","001"},
            new String[]{"001","ریشه جدید","شركت دريايي","exist"}, new String[]{"OTHER","شغل شرکت دوم","C2"});
        var preview = upload(file,"jobs.xlsx",true);
        assertThat(preview.statusCode()).as(preview.body()).isEqualTo(200);
        var report = client.json.readTree(preview.body());
        assertThat(report.get("validCount").asInt()).isEqualTo(4);
        assertThat(report.get("errorCount").asInt()).isZero();assertThat(count()).isEqualTo(1);
        assertThat(report.get("rows").get(0).get("parentCode").asText()).isEqualTo("002");
        assertThat(report.get("rows").get(0).get("rowNumber").asInt()).isEqualTo(2);
        var imported = upload(file,"jobs.xlsx",false);
        assertThat(imported.statusCode()).as(imported.body()).isEqualTo(201);
        assertThat(count()).isEqualTo(5);
        assertThat(parentCode("003")).isEqualTo("002");assertThat(parentCode("002")).isEqualTo("001");
        assertThat(parentCode("001")).isEqualTo("EXIST");
        assertThat(upload(file,"jobs.xlsx",false).statusCode()).isEqualTo(422);assertThat(count()).isEqualTo(5);
    }

    @Test void rejectsDuplicatesMissingReferencesCrossCompanyParentsAndPropagatesParentErrors() throws Exception {
        jdbc.update("INSERT INTO jobs(code,title,company_id) VALUES ('EXIST','قبلی',?)", companyId);
        var response = upload(file(new String[]{"GOOD","معتبر","C1"},
            new String[]{"DUP","تکراری","C1"}, new String[]{"dup","تکراری","C2"},
            new String[]{"EXIST","تغییر نباید اعمال شود","C1"}, new String[]{"MISSING","عنوان","ناشناخته"},
            new String[]{"ORPHAN","عنوان","C1","NONE"}, new String[]{"CROSS","عنوان","C2","EXIST"},
            new String[]{"PARENT","","C1"}, new String[]{"CHILD","عنوان","C1","PARENT"},
            new String[]{"BATCHROOT","عنوان","C2"}, new String[]{"BATCHCROSS","عنوان","C1","BATCHROOT"}), "jobs.xlsx", false);
        assertThat(response.statusCode()).isEqualTo(422);
        var report=client.json.readTree(response.body());
        assertThat(report.get("validCount").asInt()).isEqualTo(2);assertThat(report.get("errorCount").asInt()).isEqualTo(9);
        assertThat(response.body()).contains("تکرار", "همین شرکت", "ردیف شغل بالادست دارای خطا", "پیدا نشد");
        assertThat(count()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT title FROM jobs WHERE code='EXIST'",String.class)).isEqualTo("قبلی");
    }

    @Test void detectsSelfReferencesCyclesAndDescendantsOfCycles() throws Exception {
        var response=upload(file(new String[]{"SELF","عنوان","C1","SELF"},
            new String[]{"A","عنوان","C1","B"},new String[]{"B","عنوان","C1","A"},
            new String[]{"CHILD","عنوان","C1","A"},new String[]{"ROOT","عنوان","C1"}),"jobs.xlsx",false);
        assertThat(response.statusCode()).isEqualTo(422);
        assertThat(client.json.readTree(response.body()).get("errorCount").asInt()).isEqualTo(4);
        assertThat(response.body()).contains("چرخه");assertThat(count()).isZero();
    }

    @Test void rejectsAmbiguousCompaniesAndParentCodesAndUnassignedLegacyParents() throws Exception {
        jdbc.update("INSERT INTO companies(code,title) VALUES ('AMB','C1')");
        jdbc.update("INSERT INTO jobs(code,title,company_id) VALUES ('DUP','عنوان',?),('dup','عنوان',?)",companyId,companyId);
        jdbc.update("INSERT INTO jobs(code,title) VALUES ('LEGACY','بدون شرکت')");
        var response=upload(file(new String[]{"A","عنوان","C1"},
            new String[]{"B","عنوان","C1 — شرکت دریایی","DUP"},
            new String[]{"C","عنوان","C1 — شرکت دریایی","LEGACY"},
            new String[]{"D","عنوان","C1 — شرکت دریایی"}),"jobs.xlsx",true);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(client.json.readTree(response.body()).get("errorCount").asInt()).isEqualTo(3);
        assertThat(response.body()).contains("مبهم", "شرکت نامشخص");
    }

    @ParameterizedTest @ValueSource(booleans={true,false})
    void acceptsBothExcelFormatsReorderedHeadersAndFormattedNumericCodes(boolean xlsx) throws Exception {
        try(Workbook book=xlsx?new XSSFWorkbook():new HSSFWorkbook()) {
            var sheet=book.createSheet();headers(sheet,"title","companyCode","code","parentCode");
            var root=sheet.createRow(4);root.createCell(0).setCellValue("عنوان");root.createCell(1).setCellValue("C1");root.createCell(2).setCellValue(42);
            var style=book.createCellStyle();style.setDataFormat(book.createDataFormat().getFormat("00000"));root.getCell(2).setCellStyle(style);
            var child=sheet.createRow(1);child.createCell(0).setCellValue("فرزند");child.createCell(1).setCellValue("C1");child.createCell(2).setCellValue("CHILD");
            child.createCell(3).setCellValue(42);child.getCell(3).setCellStyle(style);
            book.createSheet("راهنما").createRow(0).createCell(0).setCellValue("این برگه وارد نمی‌شود");
            var response=upload(bytes(book),"jobs."+(xlsx?"xlsx":"xls"),false);
            assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
            assertThat(parentCode("CHILD")).isEqualTo("00042");assertThat(count()).isEqualTo(2);
            assertThat(client.json.readTree(response.body()).get("rows").get(1).get("rowNumber").asInt()).isEqualTo(5);
        }
    }

    @ParameterizedTest @ValueSource(strings={"missing","duplicate","unknown","formula","untitled"})
    void rejectsInvalidHeadersAndUnlabelledData(String kind) throws Exception {
        try(Workbook book=new XSSFWorkbook()) {
            var sheet=book.createSheet();headers(sheet,HEADERS);
            var row=sheet.createRow(1);row.createCell(0).setCellValue("A");row.createCell(1).setCellValue("عنوان");row.createCell(2).setCellValue("C1");
            switch(kind) {
                case "missing" -> sheet.getRow(0).getCell(2).setBlank();
                case "duplicate" -> sheet.getRow(0).getCell(4).setCellValue("code");
                case "unknown" -> sheet.getRow(0).getCell(4).setCellValue("unknown");
                case "formula" -> sheet.getRow(0).getCell(4).setCellFormula("1+1");
                case "untitled" -> row.createCell(7).setCellValue("اطلاعات بدون عنوان");
            }
            assertThat(upload(bytes(book),"jobs.xlsx",false).statusCode()).isEqualTo(kind.equals("untitled")?422:400);
            assertThat(count()).isZero();
        }
    }

    @Test void rejectsFormulasErrorBooleanDateFractionalCodesAndOversizedFields() throws Exception {
        try(Workbook book=new XSSFWorkbook()) {
            var sheet=book.createSheet();headers(sheet,HEADERS);
            for(int i=1;i<=6;i++) {
                var row=sheet.createRow(i);row.createCell(0).setCellValue("J"+i);row.createCell(1).setCellValue("عنوان");row.createCell(2).setCellValue("C1");
            }
            sheet.getRow(1).getCell(1).setCellFormula("1+1");
            sheet.getRow(2).getCell(0).setCellValue(1.25);
            sheet.getRow(3).getCell(0).setCellValue(45000);var date=book.createCellStyle();date.setDataFormat((short)14);sheet.getRow(3).getCell(0).setCellStyle(date);
            sheet.getRow(4).getCell(1).setCellValue(true);
            sheet.getRow(5).getCell(1).setCellErrorValue(FormulaError.VALUE.getCode());
            sheet.getRow(6).getCell(0).setCellValue("X".repeat(41));sheet.getRow(6).getCell(1).setCellValue("T".repeat(161));
            sheet.getRow(6).createCell(3).setCellValue("P".repeat(41));sheet.getRow(6).createCell(4).setCellValue("D".repeat(2001));
            var response=upload(bytes(book),"jobs.xlsx",false);
            assertThat(response.statusCode()).isEqualTo(422);
            assertThat(client.json.readTree(response.body()).get("errorCount").asInt()).isEqualTo(6);
            assertThat(response.body()).contains("فرمول", "Text", "طول متن", "۴۰");assertThat(count()).isZero();
        }
    }

    @Test void revalidatesAtCommitAndRollsBackEveryRowIfDatabaseRejectsOne() throws Exception {
        byte[] file=file(new String[]{"LATER","عنوان","C1"},new String[]{"NEW","عنوان","C1"});
        assertThat(upload(file,"jobs.xlsx",true).statusCode()).isEqualTo(200);
        jdbc.update("INSERT INTO jobs(code,title,company_id) VALUES ('LATER','قبلی',?)",companyId);
        assertThat(upload(file,"jobs.xlsx",false).statusCode()).isEqualTo(422);assertThat(count()).isEqualTo(1);
        jdbc.execute("ALTER TABLE jobs ADD CONSTRAINT test_job_import CHECK (title <> 'BLOCK')");
        try {
            var response=upload(file(new String[]{"OK","عنوان","C1"},new String[]{"FAIL","BLOCK","C1","OK"}),"jobs.xlsx",false);
            assertThat(response.statusCode()).isEqualTo(400);assertThat(count()).isEqualTo(1);
        } finally {jdbc.execute("ALTER TABLE jobs DROP CONSTRAINT test_job_import");}
        byte[] orphan=file(new String[]{"ORPHAN","عنوان","C1","LATER"});
        assertThat(client.json.readTree(upload(orphan,"jobs.xlsx",true).body()).get("errorCount").asInt()).isZero();
        jdbc.update("DELETE FROM jobs WHERE code='LATER'");
        assertThat(upload(orphan,"jobs.xlsx",false).statusCode()).isEqualTo(422);assertThat(count()).isZero();
    }

    @Test void rejectsUnreadableEmptyOversizedAndOverlongWorkbooks() throws Exception {
        assertThat(upload(new byte[0],"jobs.xlsx",true).statusCode()).isEqualTo(400);
        assertThat(upload("not excel".getBytes(StandardCharsets.UTF_8),"jobs.xlsx",true).statusCode()).isEqualTo(400);
        assertThat(upload(file(new String[]{"A","عنوان","C1"}),"jobs.csv",true).statusCode()).isEqualTo(400);
        assertThat(upload(new byte[5*1024*1024+1],"jobs.xlsx",true).statusCode()).isEqualTo(413);
        try(Workbook book=new XSSFWorkbook()) {
            var sheet=book.createSheet();headers(sheet,HEADERS);
            assertThat(upload(bytes(book),"jobs.xlsx",true).statusCode()).isEqualTo(400);
            sheet.createRow(1001).createCell(0).setCellValue("LIMIT");
            assertThat(upload(bytes(book),"jobs.xlsx",true).statusCode()).isEqualTo(400);
        }
    }

    @Test void downloadedTemplateContainsCurrentReferencesAndCanBeFilledAndImported() throws Exception {
        jdbc.update("INSERT INTO jobs(code,title,company_id,description) VALUES ('OLD','شغل موجود',?,'PRIVATE-JOB')",companyId);
        var response=client.http.send(HttpRequest.newBuilder(client.uri("/templates/jobs-import.xlsx")).GET().build(),HttpResponse.BodyHandlers.ofByteArray());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
        Path qa=Path.of("target/job-import-qa");Files.createDirectories(qa);Files.write(qa.resolve("live-template.xlsx"),response.body());
        try(Workbook book=WorkbookFactory.create(new ByteArrayInputStream(response.body()))) {
            assertThat(book.getNumberOfSheets()).isEqualTo(4);
            var data=book.getSheetAt(0);assertThat(data.isRightToLeft()).isTrue();
            assertThat(data.getRow(0).getCell(3).getStringCellValue()).isEqualTo("کد شغل بالادست");
            assertThat(data.getDataValidations()).hasSize(1);
            assertThat(book.getName("JobCompanies").getRefersToFormula()).isEqualTo("'شرکت‌ها'!$C$2:$C$3");
            assertThat(book.getSheet("شرکت‌ها").getRow(1).getCell(2).getStringCellValue()).isEqualTo("C1 — شرکت دریایی");
            assertThat(book.getSheet("مشاغل موجود").getRow(1).getCell(0).getStringCellValue()).isEqualTo("OLD");
            assertThat(book.getSheet("مشاغل موجود").getRow(0).getLastCellNum()).isEqualTo((short)4);
            assertThat(upload(response.body(),"jobs.xlsx",true).statusCode()).isEqualTo(400);
            var row=data.getRow(1);row.getCell(0).setCellValue("NEW");row.getCell(1).setCellValue("شغل جدید");row.getCell(2).setCellValue("C1 — شرکت دریایی");row.getCell(3).setCellValue("OLD");
            assertThat(upload(bytes(book),"jobs.xlsx",false).statusCode()).isEqualTo(201);assertThat(parentCode("NEW")).isEqualTo("OLD");
        }
        jdbc.update("UPDATE jobs SET parent_id=NULL");jdbc.update("DELETE FROM jobs");jdbc.update("DELETE FROM companies");
        var empty=client.http.send(HttpRequest.newBuilder(client.uri("/templates/jobs-import.xlsx")).GET().build(),HttpResponse.BodyHandlers.ofByteArray());
        assertThat(empty.statusCode()).isEqualTo(200);
        try(Workbook book=WorkbookFactory.create(new ByteArrayInputStream(empty.body()))) {
            assertThat(book.getName("JobCompanies").getRefersToFormula()).isEqualTo("'شرکت‌ها'!$C$2:$C$2");
        }
    }

    @Test void importRequiresItsOwnPermissionAndCsrfButDoesNotGrantManualEdits() throws Exception {
        byte[] file=file(new String[]{"SEC","عنوان","C1"});
        var anonymous=new SecurityTestClient(port);
        var anonymousDownload=anonymous.get("/templates/jobs-import.xlsx");
        assertThat(anonymousDownload.statusCode()).isEqualTo(302);
        assertThat(anonymousDownload.headers().firstValue("Location").orElse("")).endsWith("/login.html");
        var viewer=account("jobs.view");
        assertThat(viewer.get("/templates/jobs-import.xlsx").statusCode()).isEqualTo(403);
        assertThat(upload(viewer,file,true,true).statusCode()).isEqualTo(403);
        assertThat(upload(viewer,file,false,true).statusCode()).isEqualTo(403);
        var importer=account("jobs.import");
        assertThat(importer.get("/api/access/me").body()).contains("jobs.import","jobs.view");
        assertThat(importer.get("/api/companies").statusCode()).isEqualTo(403);
        assertThat(importer.get("/api/lookups").body()).contains("C1").doesNotContain("PRIVATE-COMPANY");
        assertThat(importer.get("/templates/jobs-import.xlsx").statusCode()).isEqualTo(200);
        assertThat(upload(importer,file,true,false).statusCode()).isEqualTo(403);
        assertThat(upload(importer,file,false,false).statusCode()).isEqualTo(403);
        assertThat(upload(importer,file,true,true).statusCode()).isEqualTo(200);
        assertThat(upload(importer,file,false,true).statusCode()).isEqualTo(201);
        assertThat(importer.write("POST","/api/jobs",Map.of("code","NO","title","عنوان","companyId",companyId),true).statusCode()).isEqualTo(403);
        assertThat(importer.get("/api/jobs/import").statusCode()).isEqualTo(403);
    }

    private SecurityTestClient account(String permission) throws Exception {
        var role=client.write("POST","/api/admin/roles",Map.of("name","role-"+UUID.randomUUID(),"permissions",List.of(permission)),true);
        assertThat(role.statusCode()).isEqualTo(201);
        long id=client.json.readTree(role.body()).get("id").asLong();String username="u"+UUID.randomUUID().toString().replace("-","");
        var user=client.write("POST","/api/admin/users",Map.of("username",username,"displayName","آزمایشی","password",SecurityTestClient.PASSWORD,
            "confirmation",SecurityTestClient.PASSWORD,"enabled",true,"roleIds",List.of(id)),true);
        assertThat(user.statusCode()).isEqualTo(201);var result=new SecurityTestClient(port);
        assertThat(result.login(username,SecurityTestClient.PASSWORD).statusCode()).isEqualTo(204);return result;
    }
    private int count(){return jdbc.queryForObject("SELECT COUNT(*) FROM jobs",Integer.class);}
    private String parentCode(String code){return jdbc.queryForObject("SELECT p.code FROM jobs j JOIN jobs p ON p.id=j.parent_id WHERE j.code=?",String.class,code);}
    private HttpResponse<String> upload(byte[] bytes,String filename,boolean preview) throws Exception {return upload(client,bytes,filename,preview,true);}
    private HttpResponse<String> upload(SecurityTestClient session,byte[] bytes,boolean preview,boolean csrf) throws Exception {return upload(session,bytes,"jobs.xlsx",preview,csrf);}
    private HttpResponse<String> upload(SecurityTestClient session,byte[] bytes,String filename,boolean preview,boolean csrf) throws Exception {
        String boundary="JobImport"+UUID.randomUUID();
        byte[] header=("--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\""+filename+"\"\r\nContent-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] footer=("\r\n--"+boundary+"--\r\n").getBytes(StandardCharsets.UTF_8);
        var request=HttpRequest.newBuilder(session.uri("/api/jobs/import"+(preview?"/preview":""))).header("Content-Type","multipart/form-data; boundary="+boundary);
        if(csrf){session.refreshCsrf();request.header("X-CSRF-TOKEN",session.csrf);}
        return session.http.send(request.POST(HttpRequest.BodyPublishers.concat(HttpRequest.BodyPublishers.ofByteArray(header),HttpRequest.BodyPublishers.ofByteArray(bytes),HttpRequest.BodyPublishers.ofByteArray(footer))).build(),HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }
    private static void headers(Sheet sheet,String... values){var row=sheet.createRow(0);for(int i=0;i<values.length;i++)row.createCell(i).setCellValue(values[i]);}
    private static byte[] bytes(Workbook book) throws Exception {var out=new ByteArrayOutputStream();book.write(out);return out.toByteArray();}
    private static byte[] file(String[]... rows) throws Exception {
        try(Workbook book=new XSSFWorkbook()) {var sheet=book.createSheet();headers(sheet,HEADERS);
            for(int i=0;i<rows.length;i++){var row=sheet.createRow(i+1);for(int j=0;j<rows[i].length;j++)row.createCell(j).setCellValue(rows[i][j]);}
            return bytes(book);
        }
    }
}
