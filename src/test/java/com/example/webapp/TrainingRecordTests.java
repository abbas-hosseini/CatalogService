package com.example.webapp;

import com.example.webapp.catalog.*;
import com.example.webapp.security.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import tools.jackson.databind.JsonNode;

import java.io.*;
import java.math.BigDecimal;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "spring.datasource.url=jdbc:h2:mem:training-record-tests;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
@Import(SecurityTestConfiguration.class)
class TrainingRecordTests {
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired TrainingRecordService service;
    SecurityTestClient admin;
    long employeeId, courseId;
    record FilePart(String name, byte[] bytes) {}
    FilePart file(String name, String text) { return new FilePart(name, text.getBytes(StandardCharsets.UTF_8)); }

    @BeforeEach void prepare() throws Exception {
        admin = SecurityTestClient.shared(port);
        employeeId = id(admin.write("POST", "/api/employees", Map.of("personnelCode", UUID.randomUUID().toString(), "firstName", "کارمند", "lastName", "آزمایشی"), true));
        courseId = id(admin.write("POST", "/api/courses", Map.of("code", UUID.randomUUID().toString(), "title", "دوره آزمایشی", "durationHours", 20), true));
    }
    long id(HttpResponse<String> response) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
        return admin.json.readTree(response.body()).get("id").asLong();
    }
    Map<String, Object> training() {
        return new HashMap<>(Map.of("employeeId", employeeId, "courseId", courseId, "completionDate", "2026-01-15", "startDate", "2026-01-01",
            "impactCoefficient", new BigDecimal("75.50"), "score", 80, "certificateNumber", "CERT-1", "notes", "یادداشت آموزشی"));
    }
    Requests.Training request(String notes) {
        return new Requests.Training(employeeId, courseId, LocalDate.of(2026, 1, 15), BigDecimal.valueOf(80), "CERT-1", notes,
            LocalDate.of(2026, 1, 1), new BigDecimal("75.50"));
    }
    String path(long trainingId) { return "/api/trainings/" + trainingId + "/attachments"; }
    JsonNode attachments(long trainingId) throws Exception {
        var response = admin.get(path(trainingId));
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return admin.json.readTree(response.body());
    }
    JsonNode row(long trainingId) throws Exception {
        for (var row : admin.json.readTree(admin.get("/api/trainings").body())) if (row.get("id").asLong() == trainingId) return row;
        throw new AssertionError("Training not found");
    }
    HttpResponse<String> save(SecurityTestClient client, Long trainingId, Map<String, Object> training, List<Long> removed, boolean csrf, FilePart... files) throws Exception {
        String boundary = "training" + UUID.randomUUID();
        var body = new ByteArrayOutputStream();
        var metadata = Map.of("training", training, "removedAttachmentIds", removed);
        body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"details\"\r\nContent-Type: application/json\r\n\r\n" + client.json.writeValueAsString(metadata) + "\r\n").getBytes(StandardCharsets.UTF_8));
        for (var file : files) {
            body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"files\"; filename=\"" + file.name + "\"\r\nContent-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            body.write(file.bytes);body.write("\r\n".getBytes(StandardCharsets.UTF_8));
        }
        body.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        var request = HttpRequest.newBuilder(client.uri("/api/trainings" + (trainingId == null ? "" : "/" + trainingId)))
            .header("Content-Type", "multipart/form-data; boundary=" + boundary);
        if (csrf) { client.refreshCsrf();request.header("X-CSRF-TOKEN", client.csrf); }
        return client.http.send(request.method(trainingId == null ? "POST" : "PUT", HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build(),
            HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    @Test void createsAllFieldsWithMultipleFilesAndDownloadsOriginalBytesWithSafeHeaders() throws Exception {
        byte[] binary = {0, 1, 2, (byte)255};
        long record = id(save(admin, null, training(), List.of(), true, file("گواهی.html", "<script>example</script>"), new FilePart("evidence.bin", binary)));
        assertThat(row(record).get("startDate").asText()).isEqualTo("2026-01-01");
        assertThat(row(record).get("impactCoefficient").decimalValue()).isEqualByComparingTo("75.50");
        var files = attachments(record);assertThat(files.size()).isEqualTo(2);
        assertThat(files.toString()).doesNotContain("content", "<script>");
        assertThat(files.get(0).get("fileName").asText()).isEqualTo("گواهی.html");
        for (int i = 0; i < 2; i++) {
            var download = admin.http.send(HttpRequest.newBuilder(admin.uri(path(record) + "/" + files.get(i).get("id").asLong())).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
            assertThat(download.statusCode()).isEqualTo(200);
            assertThat(download.headers().firstValue("Content-Type")).contains("application/octet-stream");
            assertThat(download.headers().firstValue("Content-Disposition").orElseThrow()).startsWith("attachment;");
            assertThat(download.headers().firstValue("X-Content-Type-Options")).contains("nosniff");
            assertThat(download.headers().firstValue("Cache-Control").orElseThrow()).contains("no-store");
            assertThat(download.body()).isEqualTo(i == 0 ? "<script>example</script>".getBytes(StandardCharsets.UTF_8) : binary);
        }
    }

    @Test void keepsJsonApiCompatibleAllowsOptionalFieldsAndBothPercentageBoundaries() throws Exception {
        var body = training();body.remove("startDate");body.remove("impactCoefficient");
        long record = id(admin.write("POST", "/api/trainings", body, true));
        assertThat(row(record).get("startDate").isNull()).isTrue();
        assertThat(row(record).get("impactCoefficient").isNull()).isTrue();
        assertThat(attachments(record).size()).isZero();
        for (int percentage : List.of(0, 100)) {
            body.put("impactCoefficient", percentage);body.put("startDate", "2026-01-15");
            assertThat(admin.write("PUT", "/api/trainings/" + record, body, true).statusCode()).isEqualTo(200);
            assertThat(row(record).get("impactCoefficient").asInt()).isEqualTo(percentage);
        }
    }

    @Test void validatesPercentagePrecisionAndDatesInJsonAndMultipartWithoutSavingAnyFiles() throws Exception {
        for (Object invalid : List.of(-1, 101, new BigDecimal("75.555"))) {
            var body = training();body.put("impactCoefficient", invalid);
            assertThat(admin.write("POST", "/api/trainings", body, true).statusCode()).isEqualTo(400);
            var response = save(admin, null, body, List.of(), true, file("valid.txt", "data"));
            assertThat(response.statusCode()).isEqualTo(400);assertThat(response.body()).contains("impactCoefficient");
        }
        for (String invalid : List.of("2026-01-16", LocalDate.now().plusDays(1).toString(), "not-a-date")) {
            var body = training();body.put("startDate", invalid);
            assertThat(admin.write("POST", "/api/trainings", body, true).statusCode()).isEqualTo(400);
            assertThat(save(admin, null, body, List.of(), true, file("valid.txt", "data")).statusCode()).isEqualTo(400);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM employee_trainings WHERE employee_id=?", Integer.class, employeeId)).isZero();
    }

    @Test void editsFieldsAndAddsRemovesOrRetainsFilesTogetherAndJsonEditsPreserveFiles() throws Exception {
        long record = id(save(admin, null, training(), List.of(), true, file("one.txt", "one"), file("two.txt", "two")));
        var initial = attachments(record);long first = initial.get(0).get("id").asLong(), second = initial.get(1).get("id").asLong();
        var body = training();body.put("impactCoefficient", 50);body.put("startDate", "2026-01-02");
        assertThat(save(admin, record, body, List.of(first), true, file("three.txt", "three")).statusCode()).isEqualTo(200);
        assertThat(row(record).get("impactCoefficient").asInt()).isEqualTo(50);
        assertThat(row(record).get("startDate").asText()).isEqualTo("2026-01-02");
        assertThat(attachments(record).size()).isEqualTo(2);
        assertThat(attachments(record).get(0).get("id").asLong()).isEqualTo(second);
        assertThat(admin.get(path(record) + "/" + first).statusCode()).isEqualTo(404);
        body.put("startDate", null);body.put("impactCoefficient", null);
        assertThat(admin.write("PUT", "/api/trainings/" + record, body, true).statusCode()).isEqualTo(200);
        assertThat(row(record).get("startDate").isNull()).isTrue();assertThat(row(record).get("impactCoefficient").isNull()).isTrue();
        assertThat(attachments(record).size()).isEqualTo(2);
        assertThat(save(admin, record, training(), List.of(), true).statusCode()).isEqualTo(200);
        assertThat(attachments(record).size()).isEqualTo(2);
    }

    @Test void rejectsEmptyOversizedAndExcessFilesAndEnforcesCountAcrossEdits() throws Exception {
        assertThat(save(admin, null, training(), List.of(), true, file("ok.txt", "valid"), file("empty.txt", "")).statusCode()).isEqualTo(400);
        assertThat(save(admin, null, training(), List.of(), true, new FilePart("large.bin", new byte[5*1024*1024+1])).statusCode()).isEqualTo(413);
        assertThat(save(admin, null, training(), List.of(), true, file("x".repeat(256), "text")).statusCode()).isEqualTo(400);
        FilePart[] files = new FilePart[11];Arrays.fill(files, file("file.txt", "text"));
        assertThat(save(admin, null, training(), List.of(), true, files).statusCode()).isEqualTo(400);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM employee_trainings WHERE employee_id=?", Integer.class, employeeId)).isZero();
        long record = id(save(admin, null, training(), List.of(), true, Arrays.copyOf(files, 10)));
        assertThat(save(admin, record, training(), List.of(), true, files[0]).statusCode()).isEqualTo(400);
        long removed = attachments(record).get(0).get("id").asLong();
        assertThat(save(admin, record, training(), List.of(removed), true, files[0]).statusCode()).isEqualTo(200);
        assertThat(attachments(record).size()).isEqualTo(10);
    }

    @Test void preventsUsingFilesFromAnotherRecordAndHandlesMissingRecords() throws Exception {
        long record = id(save(admin, null, training(), List.of(), true, file("one.txt", "one")));
        long attachment = attachments(record).get(0).get("id").asLong();
        var other = training();other.put("completionDate", "2026-01-16");
        long otherRecord = id(admin.write("POST", "/api/trainings", other, true));
        assertThat(admin.get(path(otherRecord) + "/" + attachment).statusCode()).isEqualTo(404);
        assertThat(save(admin, otherRecord, other, List.of(attachment), true).statusCode()).isEqualTo(409);
        assertThat(save(admin, null, other, List.of(attachment), true).statusCode()).isEqualTo(409);
        assertThat(admin.get(path(Long.MAX_VALUE)).statusCode()).isEqualTo(404);
        assertThat(save(admin, Long.MAX_VALUE, training(), List.of(), true).statusCode()).isEqualTo(404);
        assertThat(attachments(record).size()).isEqualTo(1);
    }

    @Test void deletingTrainingDeletesAllItsAttachments() throws Exception {
        long record = id(save(admin, null, training(), List.of(), true, file("one.txt", "one")));
        long attachment = attachments(record).get(0).get("id").asLong();
        assertThat(admin.write("DELETE", "/api/trainings/" + record, null, true).statusCode()).isEqualTo(204);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM training_attachments WHERE training_id=?", Integer.class, record)).isZero();
        assertThat(admin.get(path(record) + "/" + attachment).statusCode()).isEqualTo(404);
    }

    @Test void fileReadFailureRollsBackNewTrainingAndAllEditsRemovalsAndUploads() throws Exception {
        var broken = new MockMultipartFile("files", "broken.txt", "text/plain", new byte[]{1}) {
            @Override public InputStream getInputStream() throws IOException { throw new IOException("test read failure"); }
        };
        var files = List.of(new MockMultipartFile("files", "new.txt", "text/plain", new byte[]{2}), broken);
        assertThatThrownBy(() -> service.save(null, new TrainingRecordService.Submission(request("not saved"), List.of()), new ArrayList<>(files)))
            .isInstanceOf(CatalogException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM employee_trainings WHERE employee_id=?", Integer.class, employeeId)).isZero();
        long record = id(save(admin, null, training(), List.of(), true, file("old.txt", "old")));
        long attachment = attachments(record).get(0).get("id").asLong();
        assertThatThrownBy(() -> service.save(record, new TrainingRecordService.Submission(request("not saved"), List.of(attachment)), new ArrayList<>(files)))
            .isInstanceOf(CatalogException.class);
        assertThat(row(record).get("notes").asText()).isEqualTo("یادداشت آموزشی");
        assertThat(attachments(record).size()).isEqualTo(1);assertThat(attachments(record).get(0).get("id").asLong()).isEqualTo(attachment);
    }

    @Test void concurrentUploadsCannotExceedTheLimit() throws Exception {
        FilePart[] files = new FilePart[9];Arrays.fill(files, file("one.txt", "one"));
        long record = id(save(admin, null, training(), List.of(), true, files));
        var start = new CountDownLatch(1);
        Callable<Boolean> upload = () -> {
            start.await();
            try { service.save(record, new TrainingRecordService.Submission(request("updated"), List.of()), List.of(new MockMultipartFile("files", "new.txt", "text/plain", new byte[]{1})));return true; }
            catch (CatalogException e) { return false; }
        };
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(upload);var b = executor.submit(upload);start.countDown();
            assertThat(List.of(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS))).containsExactlyInAnyOrder(true, false);
        }
        assertThat(attachments(record).size()).isEqualTo(10);
    }

    @Test void permissionsAndCsrfApplyToCreateEditReadAndDownload() throws Exception {
        var viewer = user("trainings.view");var creator = user("trainings.create");var editor = user("trainings.edit");var stranger = user("employees.view");
        assertThat(save(viewer, null, training(), List.of(), true).statusCode()).isEqualTo(403);
        assertThat(save(editor, null, training(), List.of(), true).statusCode()).isEqualTo(403);
        assertThat(save(creator, null, training(), List.of(), false).statusCode()).isEqualTo(403);
        long record = id(save(creator, null, training(), List.of(), true, file("private.txt", "private bytes")));
        long attachment = attachments(record).get(0).get("id").asLong();String download = path(record) + "/" + attachment;
        assertThat(viewer.get(path(record)).statusCode()).isEqualTo(200);assertThat(viewer.get(download).body()).isEqualTo("private bytes");
        assertThat(save(viewer, record, training(), List.of(attachment), true).statusCode()).isEqualTo(403);
        assertThat(save(creator, record, training(), List.of(), true).statusCode()).isEqualTo(403);
        assertThat(stranger.get(path(record)).statusCode()).isEqualTo(403);assertThat(stranger.get(download).statusCode()).isEqualTo(403);
        assertThat(new SecurityTestClient(port).get(download).statusCode()).isEqualTo(401);
        assertThat(save(editor, record, training(), List.of(), false).statusCode()).isEqualTo(403);
        assertThat(save(editor, record, training(), List.of(attachment), true, file("new.txt", "new")).statusCode()).isEqualTo(200);
        assertThat(editor.write("DELETE", "/api/trainings/" + record, null, true).statusCode()).isEqualTo(403);
    }
    SecurityTestClient user(String permission) throws Exception {
        long role = id(admin.write("POST", "/api/admin/roles", Map.of("name", "training-" + UUID.randomUUID(), "permissions", List.of(permission)), true));
        String username = "u" + UUID.randomUUID().toString().replace("-", "");
        id(admin.write("POST", "/api/admin/users", Map.of("username", username, "displayName", "آزمایش", "password", SecurityTestClient.PASSWORD,
            "confirmation", SecurityTestClient.PASSWORD, "enabled", true, "roleIds", List.of(role)), true));
        var client = new SecurityTestClient(port);assertThat(client.login(username, SecurityTestClient.PASSWORD).statusCode()).isEqualTo(204);
        return client;
    }
}
