package com.example.webapp;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.annotation.Import;
import com.example.webapp.security.SecurityTestConfiguration;
import com.example.webapp.security.SecurityTestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(SecurityTestConfiguration.class)
class EmployeeExcelImportTests {
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    HttpClient http;
    String csrf;
    final JsonMapper json = JsonMapper.builder().build();
    static final String[] HEADERS = {"کد پرسنلی", "نام", "نام خانوادگی", "مدرک تحصیلی", "تاریخ شروع به کار (شمسی)", "توضیحات"};
    static final String[] PROFILE_HEADERS = {"personnelCode", "firstName", "lastName", "educationLevel", "startDate",
        "birthDate", "marineExperience", "gender", "bachelorMajor", "bachelorUniversity", "masterMajor", "masterUniversity",
        "doctoralMajor", "doctoralUniversity", "speciality", "grade", "hireDate", "lastMarineRank", "description"};
    static final String[] PROFILE_PERSIAN_HEADERS = {"کد پرسنلی", "نام", "نام خانوادگی", "مدرک تحصیلی", "تاریخ شروع به کار (شمسی)",
        "تاریخ تولد (شمسی)", "سابقه دریایی (سال)", "جنسیت", "رشته کارشناسی", "دانشگاه کارشناسی", "رشته کارشناسی ارشد", "دانشگاه کارشناسی ارشد",
        "رشته دکتری", "دانشگاه دکتری", "تخصص", "سطح سازمانی", "تاریخ استخدام (شمسی)", "آخرین درجه دریایی", "توضیحات"};
    static final Object[] PROFILE_VALUES = {"00042", "علی", "رضایی", "doctorate", "۱۴۰۳/۱/۱", "١٣٧٠/١/٢", 12.5, "زن",
        "مهندسی دریا", "دانشگاه اول", "مدیریت", "دانشگاه دوم", "حمل و نقل", "دانشگاه سوم", "  مدیریت دریایی  ", "LEVEL-01", "۱۳۹۹/۱۲/۳۰", "master", "توضیحات کامل"};

    long specialityId, gradeId;

    @BeforeEach
    void emptyEmployees() throws Exception {
        var session = SecurityTestClient.shared(port);http = session.http;csrf = session.csrf;
        for (String table : List.of("employee_positions", "employee_trainings", "employee_scores", "employees")) jdbc.update("DELETE FROM " + table);
        jdbc.update("DELETE FROM specialities");jdbc.update("DELETE FROM organizational_levels");
        jdbc.update("INSERT INTO specialities(code,title) VALUES ('MARINE','مدیریت دریایی')");
        jdbc.update("INSERT INTO organizational_levels(code,title) VALUES ('LEVEL-01','کارشناس')");
        specialityId = jdbc.queryForObject("SELECT id FROM specialities", Long.class);
        gradeId = jdbc.queryForObject("SELECT id FROM organizational_levels", Long.class);
    }

    @Test
    void previewsWithoutWritingThenImportsPersianDatesAndPreservesLeadingZeros() throws Exception {
        byte[] file = file(new String[]{"00123", "علی", "رضایی", "کارشناسی ارشد", "۱۴۰۳/۱/۲", "آزمایشی"},
            new String[]{"E2", "سارا", "احمدی", "", "", ""});
        var preview = upload(file, "employees.xlsx", true);
        assertThat(preview.statusCode()).isEqualTo(200);
        var report = json.readTree(preview.body());
        assertThat(report.get("validCount").asInt()).isEqualTo(2);
        assertThat(report.get("errorCount").asInt()).isZero();
        assertThat(count()).isZero();
        assertThat(upload(file, "employees.xlsx", false).statusCode()).isEqualTo(201);
        assertThat(count()).isEqualTo(2);
        var employee = jdbc.queryForMap("SELECT * FROM employees WHERE personnel_code = '00123'");
        assertThat(employee.get("education_level")).isEqualTo("MASTER");
        assertThat(employee.get("start_date")).isEqualTo("1403-01-02");
        assertThat(employee.get("description")).isEqualTo("آزمایشی");
        assertThat(upload(file, "employees.xlsx", false).statusCode()).isEqualTo(422);
        assertThat(count()).isEqualTo(2);
    }

    @Test
    void reportsAllInvalidRowsAndDoesNotInsertValidRowsOrOverwriteExistingEmployees() throws Exception {
        jdbc.update("INSERT INTO employees(personnel_code,first_name,last_name) VALUES ('EXIST','قبلی','کارمند')");
        var response = upload(file(
            new String[]{"VALID", "نام", "معتبر", "دیپلم", "1403/01/01"},
            new String[]{"DUP", "نام", "تکراری"}, new String[]{"DUP", "نام", "تکراری"},
            new String[]{"EXIST", "جدید", "نام"}, new String[]{"BAD", "", "نام", "نامعتبر", "1402/12/30"}), "employees.xlsx", false);
        assertThat(response.statusCode()).isEqualTo(422);
        var report = json.readTree(response.body());
        assertThat(report.get("validCount").asInt()).isEqualTo(1);
        assertThat(report.get("errorCount").asInt()).isEqualTo(4);
        assertThat(report.get("rows").get(4).get("rowNumber").asInt()).isEqualTo(6);
        assertThat(report.get("rows").get(4).get("errors").size()).isEqualTo(3);
        assertThat(count()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT first_name FROM employees WHERE personnel_code='EXIST'", String.class)).isEqualTo("قبلی");
    }

    @Test
    void rechecksDatabaseAtImportTime() throws Exception {
        byte[] file = file(new String[]{"LATER", "نام", "خانوادگی"}, new String[]{"NEW", "نام", "خانوادگی"});
        assertThat(upload(file, "employees.xlsx", true).statusCode()).isEqualTo(200);
        jdbc.update("INSERT INTO employees(personnel_code,first_name,last_name) VALUES ('LATER','قبلی','کارمند')");
        assertThat(upload(file, "employees.xlsx", false).statusCode()).isEqualTo(422);
        assertThat(count()).isEqualTo(1);
    }

    @Test
    void rollsBackWholeImportWhenDatabaseRejectsALaterRow() throws Exception {
        jdbc.execute("ALTER TABLE employees ADD CONSTRAINT test_import_rejection CHECK (last_name <> 'BLOCK')");
        try {
            var response = upload(file(new String[]{"OK", "نام", "معتبر"}, new String[]{"FAIL", "نام", "BLOCK"}), "employees.xlsx", false);
            assertThat(response.statusCode()).isEqualTo(400);
            assertThat(count()).isZero();
        } finally { jdbc.execute("ALTER TABLE employees DROP CONSTRAINT test_import_rejection"); }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void acceptsXlsAndXlsxAndFormattedNumericCodes(boolean xlsx) throws Exception {
        try (Workbook workbook = xlsx ? new XSSFWorkbook() : new HSSFWorkbook()) {
            Sheet sheet = workbook.createSheet();headers(sheet, "lastName", "personnelCode", "firstName");
            Row row = sheet.createRow(1);row.createCell(0).setCellValue("خانوادگی");row.createCell(1).setCellValue(42);
            var style = workbook.createCellStyle();style.setDataFormat(workbook.createDataFormat().getFormat("00000"));row.getCell(1).setCellStyle(style);
            row.createCell(2).setCellValue("نام");
            assertThat(upload(bytes(workbook), "employees." + (xlsx ? "xlsx" : "xls"), false).statusCode()).isEqualTo(201);
            assertThat(jdbc.queryForObject("SELECT personnel_code FROM employees", String.class)).isEqualTo("00042");
        }
    }

    @Test
    void rejectsFormulasNumericDatesAndLongText() throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet();headers(sheet, HEADERS);
            Row row = sheet.createRow(1);
            row.createCell(0).setCellValue("X".repeat(41));row.createCell(1).setCellFormula("1+1");
            row.createCell(2).setCellValue("نام".repeat(50));row.createCell(4).setCellValue(45000);
            var response = upload(bytes(workbook), "employees.xlsx", false);
            assertThat(response.statusCode()).isEqualTo(422);
            assertThat(response.body()).contains("Text", "فرمول", "طول متن");
            assertThat(count()).isZero();
        }
    }

    @Test
    void skipsBlankRowsButKeepsExcelRowNumbersAndReadsOnlyFirstSheet() throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet();headers(sheet, HEADERS);
            Row row = sheet.createRow(4);row.createCell(0).setCellValue("  CODE  ");row.createCell(1).setCellValue("نام");row.createCell(2).setCellValue("خانوادگی");
            workbook.createSheet("راهنما").createRow(0).createCell(0).setCellValue("راهنما");
            var response = upload(bytes(workbook), "employees.xlsx", true);
            assertThat(response.statusCode()).isEqualTo(200);
            var rows = json.readTree(response.body()).get("rows");
            assertThat(rows.size()).isEqualTo(1);assertThat(rows.get(0).get("rowNumber").asInt()).isEqualTo(5);
            assertThat(rows.get(0).get("employee").get("personnelCode").asText()).isEqualTo("CODE");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "duplicate", "unknown", "no-title"})
    void rejectsInvalidHeadersOrUnlabelledData(String kind) throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet();headers(sheet, HEADERS);
            switch (kind) {
                case "missing" -> sheet.getRow(0).getCell(0).setBlank();
                case "duplicate" -> sheet.getRow(0).getCell(1).setCellValue("کد پرسنلی");
                case "unknown" -> sheet.getRow(0).getCell(3).setCellValue("شرکت");
                default -> { Row row = sheet.createRow(1);row.createCell(8).setCellValue("نباید نادیده بماند"); }
            }
            assertThat(upload(bytes(workbook), "employees.xlsx", false).statusCode()).isEqualTo(kind.equals("no-title") ? 422 : 400);
            assertThat(count()).isZero();
        }
    }

    @Test
    void rejectsEmptyCorruptWrongTypeAndOversizedFiles() throws Exception {
        assertThat(upload(new byte[0], "empty.xlsx", false).statusCode()).isEqualTo(400);
        assertThat(upload("broken".getBytes(StandardCharsets.UTF_8), "broken.xlsx", false).statusCode()).isEqualTo(400);
        assertThat(upload(file(new String[]{"A","B","C"}), "wrong.csv", false).statusCode()).isEqualTo(400);
        assertThat(upload(new byte[5 * 1024 * 1024 + 1], "large.xlsx", false).statusCode()).isEqualTo(413);
        assertThat(count()).isZero();
    }

    @Test
    void rejectsMoreThanThousandRows() throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet();headers(sheet, HEADERS);sheet.createRow(1001).createCell(0).setCellValue("LAST");
            assertThat(upload(bytes(workbook), "employees.xlsx", false).statusCode()).isEqualTo(400);
        }
    }

    @Test
    void servesBlankRtlTemplateAndDoesNotImportGuideExamples() throws Exception {
        var response = http.send(HttpRequest.newBuilder(uri("/templates/employees-import.xlsx")).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        assertThat(response.statusCode()).isEqualTo(200);
        try (var workbook = WorkbookFactory.create(new java.io.ByteArrayInputStream(response.body()))) {
            assertThat(workbook.getSheetAt(0).isRightToLeft()).isTrue();
            assertThat(workbook.getSheetAt(0).getRow(0).getCell(0).getStringCellValue()).isEqualTo(HEADERS[0]);
            Sheet sheet = workbook.getSheetAt(0);
            assertThat(sheet.getRow(0).getLastCellNum()).isEqualTo((short) 19);
            assertThat(sheet.getDataValidations()).hasSize(6);
            assertThat(workbook.getNumberOfSheets()).isEqualTo(3);
            assertThat(workbook.isSheetHidden(2)).isTrue();
            assertThat(workbook.getName("MarineRanks").getRefersToFormula()).isEqualTo("'گزینه‌ها'!$A$2:$A$11");
            assertThat(workbook.getName("Specialities").getRefersToFormula()).isEqualTo("'گزینه‌ها'!$B$2:$B$2");
            assertThat(workbook.getName("OrganizationalLevels").getRefersToFormula()).isEqualTo("'گزینه‌ها'!$C$2:$C$2");
            assertThat(workbook.getSheet("گزینه‌ها").getRow(1).getCell(1).getStringCellValue()).isEqualTo("MARINE — مدیریت دریایی");
            assertThat(workbook.getSheet("گزینه‌ها").getRow(1).getCell(2).getStringCellValue()).isEqualTo("LEVEL-01 — کارشناس");
        }
        assertThat(upload(response.body(), "template.xlsx", false).statusCode()).isEqualTo(400);
        assertThat(count()).isZero();
    }

    @Test
    void importsEveryProfileFieldFromTheActualDownloadableTemplate() throws Exception {
        var template = http.send(HttpRequest.newBuilder(uri("/templates/employees-import.xlsx")).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        assertThat(template.statusCode()).isEqualTo(200);
        try (var workbook = WorkbookFactory.create(new java.io.ByteArrayInputStream(template.body()))) {
            Sheet sheet = workbook.getSheetAt(0);
            Row row = sheet.getRow(1);
            for (Cell header : sheet.getRow(0)) {
                int index = Arrays.asList(PROFILE_PERSIAN_HEADERS).indexOf(header.getStringCellValue());
                assertThat(index).as(header.getStringCellValue()).isGreaterThanOrEqualTo(0);
                setValue(row.getCell(header.getColumnIndex()), PROFILE_VALUES[index]);
            }
            byte[] file = bytes(workbook);
            var preview = upload(file, "template.xlsx", true);
            assertThat(preview.statusCode()).as(preview.body()).isEqualTo(200);
            var report = json.readTree(preview.body());
            assertThat(report.get("rows").size()).isEqualTo(1);
            assertProfile(report.get("rows").get(0).get("employee"));
            assertThat(count()).isZero();
            var imported = upload(file, "template.xlsx", false);
            assertThat(imported.statusCode()).as(imported.body()).isEqualTo(201);
            assertStoredProfile();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void importsAllProfileFieldsWithReorderedEnglishHeadersInBothFormats(boolean xlsx) throws Exception {
        try (Workbook workbook = xlsx ? new XSSFWorkbook() : new HSSFWorkbook()) {
            Sheet sheet = workbook.createSheet();Row header = sheet.createRow(0);Row row = sheet.createRow(1);
            for (int i = 0; i < PROFILE_HEADERS.length; i++) {
                int column = PROFILE_HEADERS.length - i - 1;
                header.createCell(column).setCellValue(PROFILE_HEADERS[i]);
                setValue(row.createCell(column), PROFILE_VALUES[i]);
            }
            var response = upload(bytes(workbook), "profile." + (xlsx ? "xlsx" : "xls"), false);
            assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
            assertStoredProfile();
        }
    }

    @ParameterizedTest
    @CsvSource({"birthDate,1404/12/30,تاریخ تولد", "hireDate,1404/07/31,تاریخ استخدام",
        "marineExperience,-0.01,سابقه دریایی", "marineExperience,100.01,سابقه دریایی", "marineExperience,۱۲٫۵۶۷,سابقه دریایی",
        "grade,not-defined,سطح سازمانی", "grade,۱۰۱,سطح سازمانی", "grade,1.234,سطح سازمانی",
        "gender,OTHER,جنسیت", "lastMarineRank,UNKNOWN,آخرین درجه دریایی"})
    void invalidProfileFieldReportsItsRowAndPreventsTheEntireImport(String field, String value, String label) throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet();headers(sheet, "personnelCode", "firstName", "lastName", field);
            Row valid = sheet.createRow(1);valid.createCell(0).setCellValue("VALID");valid.createCell(1).setCellValue("نام");valid.createCell(2).setCellValue("خانوادگی");
            Row invalid = sheet.createRow(2);invalid.createCell(0).setCellValue("INVALID");invalid.createCell(1).setCellValue("نام");invalid.createCell(2).setCellValue("خانوادگی");invalid.createCell(3).setCellValue(value);
            var response = upload(bytes(workbook), "profile.xlsx", false);
            assertThat(response.statusCode()).as(response.body()).isEqualTo(422);
            var report = json.readTree(response.body());
            assertThat(report.get("validCount").asInt()).isEqualTo(1);
            assertThat(report.get("errorCount").asInt()).isEqualTo(1);
            assertThat(report.get("rows").get(1).get("rowNumber").asInt()).isEqualTo(3);
            assertThat(report.get("rows").get(1).get("errors").toString()).contains(label).doesNotContain("null:");
            assertThat(count()).isZero();
        }
    }

    @Test
    void validatesStoredNumericPrecisionAndRejectsNumericDatesFormulasAndOversizedProfileText() throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet();headers(sheet, PROFILE_HEADERS);
            Row row = sheet.createRow(1);
            for (int i = 0; i < PROFILE_VALUES.length; i++) setValue(row.createCell(i), PROFILE_VALUES[i]);
            row.getCell(5).setCellValue(45000); // birthDate: Excel date serials are not Jalali text
            row.getCell(16).setCellValue(45000); // hireDate
            row.getCell(6).setCellValue(1.234);
            var style = workbook.createCellStyle();style.setDataFormat(workbook.createDataFormat().getFormat("0.00"));
            row.getCell(6).setCellStyle(style); // displayed as 1.23; must not silently round
            row.getCell(15).setCellFormula("1+1");
            for (int i = 8; i <= 14; i++) row.getCell(i).setCellValue("x".repeat(201));
            var response = upload(bytes(workbook), "profile.xlsx", false);
            assertThat(response.statusCode()).as(response.body()).isEqualTo(422);
            var errors = json.readTree(response.body()).get("rows").get(0).get("errors");
            assertThat(errors.size()).isEqualTo(11);
            for (int i : new int[]{5, 6, 8, 9, 10, 11, 12, 13, 14, 15, 16}) assertThat(errors.toString()).contains(PROFILE_PERSIAN_HEADERS[i]);
            assertThat(errors.toString()).contains("Text", "فرمول", "دو رقم اعشار", "طول متن");
            assertThat(count()).isZero();
        }
    }

    private void assertStoredProfile() throws Exception {
        assertThat(count()).isEqualTo(1);
        var response = http.send(HttpRequest.newBuilder(uri("/api/employees")).GET().build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertThat(response.statusCode()).isEqualTo(200);
        assertProfile(json.readTree(response.body()).get(0));
        assertThat(jdbc.queryForObject("SELECT hire_date FROM employees", java.sql.Date.class).toLocalDate().toString()).isEqualTo("2021-03-20");
    }

    @Test void resolvesReferenceCodesTitlesAndChoicesAndRevalidatesDeletedChoicesAtImportTime() throws Exception {
        for (String speciality : List.of("MARINE", "مدیریت دریایی", "MARINE — مدیریت دریایی")) {
            try (Workbook workbook = new XSSFWorkbook()) {
                var sheet = workbook.createSheet();headers(sheet,"personnelCode","firstName","lastName","speciality","grade");
                var row = sheet.createRow(1);
                for (int i=0;i<5;i++) row.createCell(i).setCellValue(new String[]{"REF-1","نام","خانوادگی",speciality,"LEVEL-01 — کارشناس"}[i]);
                var response=upload(bytes(workbook),"refs.xlsx",true);
                assertThat(response.statusCode()).isEqualTo(200);
                var report=json.readTree(response.body());assertThat(report.get("errorCount").asInt()).isZero();
                assertThat(report.get("rows").get(0).get("employee").get("specialityId").asLong()).isEqualTo(specialityId);
                assertThat(report.get("rows").get(0).get("employee").get("grade").asLong()).isEqualTo(gradeId);
            }
        }
        try (Workbook workbook = new XSSFWorkbook()) {
            var sheet=workbook.createSheet();headers(sheet,"personnelCode","firstName","lastName","speciality","grade");
            var row=sheet.createRow(1);
            for(int i=0;i<5;i++) row.createCell(i).setCellValue(new String[]{"REF-2","نام","خانوادگی","MARINE","LEVEL-01"}[i]);
            byte[] file=bytes(workbook);
            assertThat(json.readTree(upload(file,"refs.xlsx",true).body()).get("errorCount").asInt()).isZero();
            jdbc.update("DELETE FROM specialities");
            var response=upload(file,"refs.xlsx",false);assertThat(response.statusCode()).isEqualTo(422);
            assertThat(response.body()).contains("تخصص", "تعریف نشده");assertThat(count()).isZero();
        }
    }

    @Test void ambiguousReferenceNamesRequireTheFullChoiceAndEmptyCatalogTemplateRemainsValid() throws Exception {
        jdbc.update("INSERT INTO specialities(code,title) VALUES ('OTHER','MARINE')");
        try (Workbook workbook = new XSSFWorkbook()) {
            var sheet=workbook.createSheet();headers(sheet,"personnelCode","firstName","lastName","speciality");
            var row=sheet.createRow(1);
            for(int i=0;i<4;i++) row.createCell(i).setCellValue(new String[]{"AMB-1","نام","خانوادگی","MARINE"}[i]);
            var ambiguous=upload(bytes(workbook),"ambiguous.xlsx",true);
            assertThat(json.readTree(ambiguous.body()).get("errorCount").asInt()).isEqualTo(1);
            row.getCell(3).setCellValue("MARINE — مدیریت دریایی");
            assertThat(json.readTree(upload(bytes(workbook),"ambiguous.xlsx",true).body()).get("errorCount").asInt()).isZero();
        }
        jdbc.update("DELETE FROM specialities");jdbc.update("DELETE FROM organizational_levels");
        var response=http.send(HttpRequest.newBuilder(uri("/templates/employees-import.xlsx")).GET().build(),HttpResponse.BodyHandlers.ofByteArray());
        assertThat(response.statusCode()).isEqualTo(200);
        try(var workbook=WorkbookFactory.create(new java.io.ByteArrayInputStream(response.body()))) {
            assertThat(workbook.getName("Specialities").getRefersToFormula()).doesNotContain("$1:");
            assertThat(workbook.getSheetAt(0).getDataValidations()).hasSize(6);
        }
    }

    private void assertProfile(JsonNode employee) {
        for (int i = 0; i < PROFILE_HEADERS.length; i++) {
            String field = PROFILE_HEADERS[i];
            switch (field) {
                case "marineExperience" -> assertThat(employee.get(field).asDouble()).isEqualTo(12.5);
                case "grade" -> assertThat(employee.get(field).asLong()).isEqualTo(gradeId);
                case "speciality" -> assertThat(employee.get("specialityId").asLong()).isEqualTo(specialityId);
                case "startDate" -> assertThat(employee.get(field).asText()).isEqualTo("1403-01-01");
                case "birthDate" -> assertThat(employee.get(field).asText()).isEqualTo("1370-01-02");
                case "hireDate" -> assertThat(employee.get(field).asText()).isEqualTo("1399-12-30");
                case "gender" -> assertThat(employee.get(field).asText()).isEqualTo("FEMALE");
                case "lastMarineRank" -> assertThat(employee.get(field).asText()).isEqualTo("MASTER");
                case "educationLevel" -> assertThat(employee.get(field).asText()).isEqualTo("DOCTORATE");
                default -> assertThat(employee.get(field).asText()).as(field).isEqualTo(PROFILE_VALUES[i].toString().strip());
            }
        }
    }

    private static void setValue(Cell cell, Object value) {
        if (value instanceof Number number) cell.setCellValue(number.doubleValue());
        else cell.setCellValue(value.toString());
    }

    private int count() { return jdbc.queryForObject("SELECT COUNT(*) FROM employees", Integer.class); }
    private URI uri(String path) { return URI.create("http://127.0.0.1:" + port + path); }
    private HttpResponse<String> upload(byte[] data, String filename, boolean preview) throws Exception {
        String boundary = "EmployeeImport" + UUID.randomUUID();
        byte[] header = ("--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\""+filename+"\"\r\nContent-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] footer = ("\r\n--"+boundary+"--\r\n").getBytes(StandardCharsets.UTF_8);
        return http.send(HttpRequest.newBuilder(uri("/api/employees/import"+(preview?"/preview":"")))
            .header("Content-Type", "multipart/form-data; boundary="+boundary)
            .header("X-CSRF-TOKEN", csrf)
            .POST(HttpRequest.BodyPublishers.concat(HttpRequest.BodyPublishers.ofByteArray(header), HttpRequest.BodyPublishers.ofByteArray(data), HttpRequest.BodyPublishers.ofByteArray(footer)))
            .build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }
    private static void headers(Sheet sheet, String... headers) {
        Row row = sheet.createRow(0);for(int i=0;i<headers.length;i++)row.createCell(i).setCellValue(headers[i]);
    }
    private static byte[] bytes(Workbook workbook) throws Exception {
        var output = new ByteArrayOutputStream();workbook.write(output);return output.toByteArray();
    }
    private static byte[] file(String[]... data) throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet();headers(sheet, HEADERS);
            for(int i=0;i<data.length;i++){Row row=sheet.createRow(i+1);for(int j=0;j<data[i].length;j++)row.createCell(j).setCellValue(data[i][j]);}
            return bytes(workbook);
        }
    }
}
