package com.example.webapp;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class EmployeeExcelImportTests {
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    final HttpClient http = HttpClient.newHttpClient();
    final JsonMapper json = JsonMapper.builder().build();
    static final String[] HEADERS = {"کد پرسنلی", "نام", "نام خانوادگی", "مدرک تحصیلی", "تاریخ شروع به کار (شمسی)", "توضیحات"};

    @BeforeEach
    void emptyEmployees() {
        for (String table : List.of("employee_positions", "employee_trainings", "employee_scores", "employees")) jdbc.update("DELETE FROM " + table);
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
            assertThat(workbook.getNumberOfSheets()).isEqualTo(2);
        }
        assertThat(upload(response.body(), "template.xlsx", false).statusCode()).isEqualTo(400);
        assertThat(count()).isZero();
    }

    private int count() { return jdbc.queryForObject("SELECT COUNT(*) FROM employees", Integer.class); }
    private URI uri(String path) { return URI.create("http://127.0.0.1:" + port + path); }
    private HttpResponse<String> upload(byte[] data, String filename, boolean preview) throws Exception {
        String boundary = "EmployeeImport" + UUID.randomUUID();
        byte[] header = ("--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\""+filename+"\"\r\nContent-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] footer = ("\r\n--"+boundary+"--\r\n").getBytes(StandardCharsets.UTF_8);
        return http.send(HttpRequest.newBuilder(uri("/api/employees/import"+(preview?"/preview":"")))
            .header("Content-Type", "multipart/form-data; boundary="+boundary)
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
