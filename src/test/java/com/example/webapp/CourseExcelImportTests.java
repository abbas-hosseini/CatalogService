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
import java.math.BigDecimal;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "spring.datasource.url=jdbc:h2:mem:course-import-tests;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
@Import(SecurityTestConfiguration.class)
class CourseExcelImportTests {
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    SecurityTestClient client;
    static final String[] HEADERS = {"کد دوره", "عنوان دوره", "برگزارکننده", "مدت دوره (ساعت)", "توضیحات"};

    @BeforeEach void prepare() throws Exception {
        client = SecurityTestClient.shared(port);
        jdbc.update("DELETE FROM courses");
    }

    @Test void previewsWithoutWritingThenImportsPersianArabicAndEnglishDurations() throws Exception {
        byte[] file = file(new String[]{"001","ایمنی دریایی","مرکز آموزش","۱۲٫۵","یادداشت"},
            new String[]{"002","ناوبری","","٠٫٠١"}, new String[]{"003","مدیریت","","10000"});
        var preview = upload(file,"courses.xlsx",true);
        assertThat(preview.statusCode()).as(preview.body()).isEqualTo(200);
        assertThat(client.json.readTree(preview.body()).get("validCount").asInt()).isEqualTo(3);
        assertThat(count()).isZero();
        var result = upload(file,"courses.xlsx",false);
        assertThat(result.statusCode()).as(result.body()).isEqualTo(201);
        assertThat(client.json.readTree(result.body()).get("importedCount").asInt()).isEqualTo(3);
        assertThat(count()).isEqualTo(3);
        assertThat(duration("001")).isEqualByComparingTo("12.5");
        assertThat(duration("002")).isEqualByComparingTo("0.01");
        assertThat(duration("003")).isEqualByComparingTo("10000");
        assertThat(jdbc.queryForObject("SELECT provider FROM courses WHERE code='001'",String.class)).isEqualTo("مرکز آموزش");
        assertThat(jdbc.queryForObject("SELECT provider FROM courses WHERE code='002'",String.class)).isNull();
        assertThat(upload(file,"courses.xlsx",false).statusCode()).isEqualTo(422);
        assertThat(count()).isEqualTo(3);
    }

    @ParameterizedTest @ValueSource(booleans={true,false})
    void acceptsBothFormatsReorderedMinimalHeadersAndPreservesFormattedCodes(boolean xlsx) throws Exception {
        try (Workbook book=xlsx?new XSSFWorkbook():new HSSFWorkbook()) {
            var sheet=book.createSheet();headers(sheet,"durationHours","code","title");
            var row=sheet.createRow(4);row.createCell(0).setCellValue(12.5);row.createCell(1).setCellValue(42);row.createCell(2).setCellValue("دوره");
            var code=book.createCellStyle();code.setDataFormat(book.createDataFormat().getFormat("00000"));row.getCell(1).setCellStyle(code);
            var rounded=book.createCellStyle();rounded.setDataFormat(book.createDataFormat().getFormat("0"));row.getCell(0).setCellStyle(rounded);
            book.createSheet("راهنما").createRow(0).createCell(0).setCellValue("وارد نمی‌شود");
            var response=upload(bytes(book),"courses."+(xlsx?"xlsx":"xls"),false);
            assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
            assertThat(duration("00042")).isEqualByComparingTo("12.5");assertThat(count()).isEqualTo(1);
            assertThat(client.json.readTree(response.body()).get("rows").get(0).get("rowNumber").asInt()).isEqualTo(5);
        }
    }

    @Test void invalidDurationsAreReportedWithoutPartiallySavingValidRows() throws Exception {
        String[] invalid={"0","-2","10000.01","1.234","text","12,5","1,000","12%","12:30","1e2",""};
        var rows=new ArrayList<String[]>();
        for(int i=0;i<invalid.length;i++)rows.add(new String[]{"C"+i,"دوره","",invalid[i]});
        rows.add(new String[]{"VALID","معتبر","","10"});
        var response=upload(file(rows.toArray(String[][]::new)),"courses.xlsx",false);
        assertThat(response.statusCode()).isEqualTo(422);
        var report=client.json.readTree(response.body());
        assertThat(report.get("errorCount").asInt()).isEqualTo(invalid.length);
        assertThat(report.get("validCount").asInt()).isEqualTo(1);
        assertThat(report.get("rows").get(4).get("durationHours").asText()).isEqualTo("text");
        assertThat(count()).isZero();
    }

    @Test void rejectsDuplicateCodesAndOversizedOrMissingFieldsWithoutOverwriting() throws Exception {
        jdbc.update("INSERT INTO courses(code,title,duration_hours) VALUES ('OLD','قبلی',8)");
        var response=upload(file(new String[]{"old","جدید","","12"},new String[]{"DUP","الف","","12"},
            new String[]{" dup ","ب","","12"},new String[]{"","دوره","","12"},new String[]{"TITLE","","","12"},
            new String[]{"X".repeat(41),"T".repeat(161),"P".repeat(161),"12","D".repeat(2001)},
            new String[]{"VALID","معتبر","","12"}),"courses.xlsx",false);
        assertThat(response.statusCode()).isEqualTo(422);
        assertThat(client.json.readTree(response.body()).get("errorCount").asInt()).isEqualTo(6);
        assertThat(response.body()).contains("تکرار", "طول متن", "الزامی");assertThat(count()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT title FROM courses WHERE code='OLD'",String.class)).isEqualTo("قبلی");
    }

    @ParameterizedTest @ValueSource(strings={"missing","duplicate","unknown","formula","untitled"})
    void rejectsBadHeadersAndUnlabelledData(String kind) throws Exception {
        try(Workbook book=new XSSFWorkbook()) {
            var sheet=book.createSheet();headers(sheet,HEADERS);
            var row=sheet.createRow(1);row.createCell(0).setCellValue("A");row.createCell(1).setCellValue("دوره");row.createCell(3).setCellValue(12);
            switch(kind) {
                case "missing" -> sheet.getRow(0).getCell(3).setBlank();
                case "duplicate" -> sheet.getRow(0).getCell(4).setCellValue("code");
                case "unknown" -> sheet.getRow(0).getCell(4).setCellValue("unknown");
                case "formula" -> sheet.getRow(0).getCell(4).setCellFormula("1+1");
                case "untitled" -> row.createCell(7).setCellValue("اطلاعات بدون عنوان");
            }
            assertThat(upload(bytes(book),"courses.xlsx",false).statusCode()).isEqualTo(kind.equals("untitled")?422:400);
            assertThat(count()).isZero();
        }
    }

    @Test void rejectsFormulasBooleanErrorsDatePercentageAndRoundedInvalidNumbers() throws Exception {
        try(Workbook book=new XSSFWorkbook()) {
            var sheet=book.createSheet();headers(sheet,HEADERS);
            for(int i=1;i<=8;i++) {
                var row=sheet.createRow(i);row.createCell(0).setCellValue("C"+i);row.createCell(1).setCellValue("دوره");row.createCell(3).setCellValue(12);
            }
            sheet.getRow(1).getCell(3).setCellFormula("6+6");
            sheet.getRow(2).getCell(1).setCellValue(true);
            sheet.getRow(3).getCell(1).setCellErrorValue(FormulaError.VALUE.getCode());
            var date=book.createCellStyle();date.setDataFormat((short)14);sheet.getRow(4).getCell(3).setCellStyle(date);
            var percent=book.createCellStyle();percent.setDataFormat((short)9);sheet.getRow(5).getCell(3).setCellStyle(percent);
            sheet.getRow(6).getCell(0).setCellValue(1.25);
            sheet.getRow(7).getCell(3).setCellValue(1.234);var rounded=book.createCellStyle();rounded.setDataFormat((short)2);sheet.getRow(7).getCell(3).setCellStyle(rounded);
            sheet.getRow(8).getCell(0).setCellValue(1000000000000000d);
            var response=upload(bytes(book),"courses.xlsx",false);
            assertThat(response.statusCode()).isEqualTo(422);
            assertThat(client.json.readTree(response.body()).get("errorCount").asInt()).isEqualTo(8);
            assertThat(response.body()).contains("فرمول", "درصد", "اعشار", "Text");assertThat(count()).isZero();
        }
    }

    @Test void revalidatesOnCommitAndRollsBackEarlierInsertsOnDatabaseFailure() throws Exception {
        byte[] file=file(new String[]{"LATER","دوره","","12"},new String[]{"NEW","دوره","","12"});
        assertThat(upload(file,"courses.xlsx",true).statusCode()).isEqualTo(200);
        jdbc.update("INSERT INTO courses(code,title,duration_hours) VALUES ('LATER','قبلی',8)");
        assertThat(upload(file,"courses.xlsx",false).statusCode()).isEqualTo(422);assertThat(count()).isEqualTo(1);
        jdbc.execute("ALTER TABLE courses ADD CONSTRAINT test_course_import CHECK (title <> 'BLOCK')");
        try {
            var response=upload(file(new String[]{"OK","دوره","","12"},new String[]{"FAIL","BLOCK","","12"}),"courses.xlsx",false);
            assertThat(response.statusCode()).isEqualTo(400);assertThat(count()).isEqualTo(1);
        } finally {jdbc.execute("ALTER TABLE courses DROP CONSTRAINT test_course_import");}
    }

    @Test void rejectsEmptyCorruptWrongExtensionOversizedAndOverlongFiles() throws Exception {
        assertThat(upload(new byte[0],"courses.xlsx",true).statusCode()).isEqualTo(400);
        assertThat(upload("not excel".getBytes(StandardCharsets.UTF_8),"courses.xlsx",true).statusCode()).isEqualTo(400);
        assertThat(upload(file(new String[]{"A","دوره","","12"}),"courses.csv",true).statusCode()).isEqualTo(400);
        assertThat(upload(new byte[5*1024*1024+1],"courses.xlsx",true).statusCode()).isEqualTo(413);
        try(Workbook book=new XSSFWorkbook()) {
            var sheet=book.createSheet();headers(sheet,HEADERS);
            assertThat(upload(bytes(book),"courses.xlsx",true).statusCode()).isEqualTo(400);
            sheet.createRow(1001).createCell(0).setCellValue("LIMIT");
            assertThat(upload(bytes(book),"courses.xlsx",true).statusCode()).isEqualTo(400);
        }
    }

    @Test void downloadedTemplateHasNoSampleDataAndCanBeFilledAndImported() throws Exception {
        var response=client.http.send(HttpRequest.newBuilder(client.uri("/templates/courses-import.xlsx")).GET().build(),HttpResponse.BodyHandlers.ofByteArray());
        assertThat(response.statusCode()).isEqualTo(200);
        try(Workbook book=WorkbookFactory.create(new ByteArrayInputStream(response.body()))) {
            assertThat(book.getNumberOfSheets()).isEqualTo(2);
            var data=book.getSheetAt(0);assertThat(data.isRightToLeft()).isTrue();
            assertThat(data.getRow(0).getCell(3).getStringCellValue()).isEqualTo("مدت دوره (ساعت)");
            assertThat(data.getRow(1).getCell(0).getCellStyle().getDataFormatString()).isEqualTo("@");
            assertThat(data.getDataValidations()).hasSize(1);
            assertThat(upload(response.body(),"courses.xlsx",true).statusCode()).isEqualTo(400);
            var row=data.getRow(1);row.getCell(0).setCellValue("001");row.getCell(1).setCellValue("ایمنی دریایی");row.getCell(2).setCellValue("مرکز آموزش");row.getCell(3).setCellValue(12.5);
            byte[] filled=bytes(book);
            var qa=Path.of("target/course-import-qa");Files.createDirectories(qa);Files.write(qa.resolve("valid-courses.xlsx"),filled);
            assertThat(upload(filled,"courses.xlsx",false).statusCode()).isEqualTo(201);
            assertThat(duration("001")).isEqualByComparingTo("12.5");
        }
    }

    @Test void requiresSpecificImportPermissionAndCsrfForBothEndpoints() throws Exception {
        byte[] file=file(new String[]{"SEC","دوره","","12"});
        var anonymous=new SecurityTestClient(port);
        assertThat(anonymous.get("/templates/courses-import.xlsx").statusCode()).isEqualTo(302);
        assertThat(upload(anonymous,file,true,true).statusCode()).isEqualTo(401);
        var viewer=account("courses.view");var creator=account("courses.create");var importer=account("courses.import");
        for(var denied:List.of(viewer,creator)) {
            assertThat(denied.get("/templates/courses-import.xlsx").statusCode()).isEqualTo(403);
            assertThat(upload(denied,file,true,true).statusCode()).isEqualTo(403);
            assertThat(upload(denied,file,false,true).statusCode()).isEqualTo(403);
        }
        assertThat(importer.get("/templates/courses-import.xlsx").statusCode()).isEqualTo(200);
        assertThat(upload(importer,file,true,false).statusCode()).isEqualTo(403);
        assertThat(upload(importer,file,false,false).statusCode()).isEqualTo(403);
        assertThat(upload(importer,file,true,true).statusCode()).isEqualTo(200);
        assertThat(upload(importer,file,false,true).statusCode()).isEqualTo(201);
        assertThat(importer.write("POST","/api/courses",Map.of(),true).statusCode()).isEqualTo(403);
        assertThat(importer.get("/api/courses/import").statusCode()).isEqualTo(403);
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
    private int count(){return jdbc.queryForObject("SELECT COUNT(*) FROM courses",Integer.class);}
    private BigDecimal duration(String code){return jdbc.queryForObject("SELECT duration_hours FROM courses WHERE code=?",BigDecimal.class,code);}
    private HttpResponse<String> upload(byte[] bytes,String filename,boolean preview) throws Exception {return upload(client,bytes,filename,preview,true);}
    private HttpResponse<String> upload(SecurityTestClient session,byte[] bytes,boolean preview,boolean csrf) throws Exception {return upload(session,bytes,"courses.xlsx",preview,csrf);}
    private HttpResponse<String> upload(SecurityTestClient session,byte[] bytes,String filename,boolean preview,boolean csrf) throws Exception {
        String boundary="CourseImport"+UUID.randomUUID();
        byte[] header=("--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\""+filename+"\"\r\nContent-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] footer=("\r\n--"+boundary+"--\r\n").getBytes(StandardCharsets.UTF_8);
        var request=HttpRequest.newBuilder(session.uri("/api/courses/import"+(preview?"/preview":""))).header("Content-Type","multipart/form-data; boundary="+boundary);
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
