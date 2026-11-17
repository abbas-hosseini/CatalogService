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
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "spring.datasource.url=jdbc:h2:mem:assessment-tests;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
@Import(SecurityTestConfiguration.class)
class AssessmentDetailsTests {
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired AssessmentService service;
    SecurityTestClient admin;
    long scoreId, employeeId, factorId;
    record FilePart(String name, byte[] bytes) {}
    FilePart file(String name, String text) { return new FilePart(name, text.getBytes(StandardCharsets.UTF_8)); }

    @BeforeEach void prepare() throws Exception {
        admin = SecurityTestClient.shared(port);
        employeeId = id(admin.write("POST", "/api/employees", Map.of("personnelCode", UUID.randomUUID().toString(), "firstName", "نام", "lastName", "خانوادگی"), true));
        factorId = id(admin.write("POST", "/api/factors", Map.of("code", UUID.randomUUID().toString(), "title", "عامل"), true));
        scoreId = id(admin.write("POST", "/api/scores", Map.of("employeeId", employeeId, "factorId", factorId, "score", 60, "assessedOn", "2026-01-01", "notes", "قبلی"), true));
    }
    long id(HttpResponse<String> response) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
        return admin.json.readTree(response.body()).get("id").asLong();
    }
    String path(long id) { return "/api/scores/" + id + "/details"; }
    Map<String, Object> update(String strengths, String weaknesses, List<Long> removed) {
        return Map.of("strengths", strengths, "weaknesses", weaknesses, "notes", "یادداشت", "removedAttachmentIds", removed);
    }
    JsonNode read() throws Exception {
        var response = admin.get(path(scoreId));assertThat(response.statusCode()).isEqualTo(200);
        return admin.json.readTree(response.body());
    }
    HttpResponse<String> save(SecurityTestClient client, long score, Map<String, Object> metadata, boolean csrf, FilePart... files) throws Exception {
        String boundary = "assessment" + UUID.randomUUID();
        var body = new ByteArrayOutputStream();
        body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"details\"\r\nContent-Type: application/json\r\n\r\n" + client.json.writeValueAsString(metadata) + "\r\n").getBytes(StandardCharsets.UTF_8));
        for (var file : files) {
            body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"files\"; filename=\"" + file.name + "\"\r\nContent-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            body.write(file.bytes);body.write("\r\n".getBytes(StandardCharsets.UTF_8));
        }
        body.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        var request = HttpRequest.newBuilder(client.uri(path(score))).header("Content-Type", "multipart/form-data; boundary=" + boundary);
        if (csrf) { client.refreshCsrf();request.header("X-CSRF-TOKEN", client.csrf); }
        return client.http.send(request.PUT(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    @Test void savesMultilineTextAndMultipleFilesAndDownloadsOriginalBytesWithSafeHeaders() throws Exception {
        byte[] binary = {0, 1, 2, (byte)255};
        var response = save(admin, scoreId, update("  همکاری تیمی\nدانش فنی  ", "مدیریت زمان", List.of()), true,
            file("گزارش.html", "<script>alert('test')</script>"), new FilePart("evidence.bin", binary));
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        var details = read();
        assertThat(details.get("score").get("strengths").asText()).isEqualTo("همکاری تیمی\nدانش فنی");
        assertThat(details.get("score").get("weaknesses").asText()).isEqualTo("مدیریت زمان");
        assertThat(details.get("score").get("notes").asText()).isEqualTo("یادداشت");
        assertThat(details.get("attachments").size()).isEqualTo(2);
        assertThat(details.toString()).doesNotContain("content", "<script>");
        for (int i = 0; i < 2; i++) {
            var attachment = details.get("attachments").get(i);
            var download = admin.http.send(HttpRequest.newBuilder(admin.uri("/api/scores/" + scoreId + "/attachments/" + attachment.get("id").asLong())).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
            assertThat(download.statusCode()).isEqualTo(200);
            assertThat(download.headers().firstValue("Content-Type")).contains("application/octet-stream");
            assertThat(download.headers().firstValue("Content-Disposition").orElseThrow()).startsWith("attachment;");
            assertThat(download.headers().firstValue("X-Content-Type-Options")).contains("nosniff");
            assertThat(download.headers().firstValue("Cache-Control").orElseThrow()).contains("no-store");
            assertThat(download.body()).isEqualTo(i == 0 ? "<script>alert('test')</script>".getBytes(StandardCharsets.UTF_8) : binary);
        }
        var scores = admin.get("/api/scores");
        assertThat(scores.body()).contains("همکاری تیمی", "مدیریت زمان").doesNotContain("<script>");
    }

    @Test void preservesDetailsWhenEditingScoresAndCanRemoveOneFileWhileAddingOthersAndClearText() throws Exception {
        assertThat(save(admin, scoreId, update("قوت", "ضعف", List.of()), true, file("first.txt", "one"), file("second.txt", "two")).statusCode()).isEqualTo(200);
        var initial = read();long first = initial.get("attachments").get(0).get("id").asLong(), second = initial.get("attachments").get(1).get("id").asLong();
        assertThat(admin.write("PUT", "/api/employees/" + employeeId + "/scores", Map.of("scores", List.of(Map.of("factorId", factorId, "score", 80))), true).statusCode()).isEqualTo(200);
        assertThat(read().get("score").get("strengths").asText()).isEqualTo("قوت");
        assertThat(read().get("attachments").size()).isEqualTo(2);
        assertThat(save(admin, scoreId, update(" ", "", List.of(first)), true, file("third.txt", "three")).statusCode()).isEqualTo(200);
        var after = read();assertThat(after.get("score").get("strengths").isNull()).isTrue();assertThat(after.get("score").get("weaknesses").isNull()).isTrue();
        assertThat(after.get("attachments").size()).isEqualTo(2);
        assertThat(after.get("attachments").get(0).get("id").asLong()).isEqualTo(second);
        assertThat(admin.get("/api/scores/" + scoreId + "/attachments/" + first).statusCode()).isEqualTo(404);
        assertThat(save(admin, scoreId, update("جدید", "", List.of()), true).statusCode()).isEqualTo(200);
        assertThat(read().get("attachments").size()).isEqualTo(2);
    }

    @Test void enforcesTextLengthFileSizeEmptyFilesAndFileCountsWithoutPartialChanges() throws Exception {
        var response = save(admin, scoreId, update("x".repeat(5001), "", List.of()), true, file("valid.txt", "text"));
        assertThat(response.statusCode()).isEqualTo(400);assertThat(response.body()).contains("strengths");
        assertThat(save(admin, scoreId, update("", "x".repeat(5001), List.of()), true).statusCode()).isEqualTo(400);
        assertThat(save(admin, scoreId, Map.of("notes", "x".repeat(2001)), true).statusCode()).isEqualTo(400);
        assertThat(save(admin, scoreId, update("not saved", "", List.of()), true, file("valid.txt", "text"), file("empty.txt", "")).statusCode()).isEqualTo(400);
        assertThat(save(admin, scoreId, update("not saved", "", List.of()), true, new FilePart("big.bin", new byte[5*1024*1024+1])).statusCode()).isEqualTo(413);
        assertThat(save(admin, scoreId, update("not saved", "", List.of()), true, file("x".repeat(256), "text")).statusCode()).isEqualTo(400);
        FilePart[] files = new FilePart[11];Arrays.fill(files, file("small.txt", "test"));
        assertThat(save(admin, scoreId, update("not saved", "", List.of()), true, files).statusCode()).isEqualTo(400);
        assertThat(read().get("attachments").size()).isZero();assertThat(read().get("score").get("strengths").isNull()).isTrue();
        assertThat(save(admin, scoreId, update("saved", "", List.of()), true, Arrays.copyOf(files, 10)).statusCode()).isEqualTo(200);
        assertThat(save(admin, scoreId, update("not saved", "", List.of()), true, file("extra.txt", "text")).statusCode()).isEqualTo(400);
        assertThat(read().get("attachments").size()).isEqualTo(10);assertThat(read().get("score").get("strengths").asText()).isEqualTo("saved");
    }

    @Test void acceptsSeveralFilesWhoseCombinedSizeExceedsTheFormerRequestLimit() throws Exception {
        byte[] bytes = new byte[4*1024*1024];Arrays.fill(bytes, (byte)7);
        var response = save(admin, scoreId, update("", "", List.of()), true, new FilePart("one.bin", bytes), new FilePart("two.bin", bytes));
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThat(read().get("attachments").size()).isEqualTo(2);
    }

    @Test void rejectsCrossAssessmentAttachmentReferencesAndMissingRecords() throws Exception {
        save(admin, scoreId, update("original", "", List.of()), true, file("file.txt", "text"));
        long attachmentId = read().get("attachments").get(0).get("id").asLong();
        long otherFactor = id(admin.write("POST", "/api/factors", Map.of("code", UUID.randomUUID().toString(), "title", "دیگر"), true));
        long otherScore = id(admin.write("POST", "/api/scores", Map.of("employeeId", employeeId, "factorId", otherFactor, "score", 60, "assessedOn", "2026-01-01"), true));
        assertThat(admin.get("/api/scores/" + otherScore + "/attachments/" + attachmentId).statusCode()).isEqualTo(404);
        assertThat(save(admin, otherScore, update("not saved", "", List.of(attachmentId)), true).statusCode()).isEqualTo(409);
        assertThat(read().get("attachments").size()).isEqualTo(1);
        assertThat(admin.get(path(Long.MAX_VALUE)).statusCode()).isEqualTo(404);
        assertThat(save(admin, Long.MAX_VALUE, update("", "", List.of()), true).statusCode()).isEqualTo(404);
    }

    @Test void deletingAnAssessmentDirectlyOrThroughTheScoreSheetDeletesItsAttachments() throws Exception {
        save(admin, scoreId, update("قوت", "ضعف", List.of()), true, file("file.txt", "text"));
        long firstId = scoreId;
        assertThat(admin.write("DELETE", "/api/scores/" + scoreId, null, true).statusCode()).isEqualTo(204);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM assessment_attachments WHERE score_id = ?", Integer.class, firstId)).isZero();
        scoreId = id(admin.write("POST", "/api/scores", Map.of("employeeId", employeeId, "factorId", factorId, "score", 60, "assessedOn", "2026-01-01"), true));
        save(admin, scoreId, update("قوت", "ضعف", List.of()), true, file("file.txt", "text"));
        var entry = new HashMap<String, Object>();entry.put("factorId", factorId);entry.put("score", null);
        assertThat(admin.write("PUT", "/api/employees/" + employeeId + "/scores", Map.of("scores", List.of(entry)), true).statusCode()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM assessment_attachments WHERE score_id = ?", Integer.class, scoreId)).isZero();
    }

    @Test void aFileReadFailureRollsBackTextsRemovalsAndPriorInserts() throws Exception {
        save(admin, scoreId, update("original", "", List.of()), true, file("old.txt", "old"));
        long attachmentId = read().get("attachments").get(0).get("id").asLong();
        var broken = new MockMultipartFile("files", "broken.txt", "text/plain", new byte[]{1}) {
            @Override public InputStream getInputStream() throws IOException { throw new IOException("test read failure"); }
        };
        assertThatThrownBy(() -> service.save(scoreId, new AssessmentService.Update("not saved", "", "", List.of(attachmentId)),
            List.of(new MockMultipartFile("files", "new.txt", "text/plain", new byte[]{2}), broken))).isInstanceOf(CatalogException.class);
        assertThat(read().get("score").get("strengths").asText()).isEqualTo("original");
        assertThat(read().get("attachments").size()).isEqualTo(1);assertThat(read().get("attachments").get(0).get("id").asLong()).isEqualTo(attachmentId);
    }

    @Test void simultaneousUploadsCannotExceedThePerAssessmentLimit() throws Exception {
        FilePart[] initial = new FilePart[9];Arrays.fill(initial, file("initial.txt", "text"));
        assertThat(save(admin, scoreId, update("", "", List.of()), true, initial).statusCode()).isEqualTo(200);
        var start = new CountDownLatch(1);
        Callable<Boolean> upload = () -> {
            start.await();
            try { service.save(scoreId, new AssessmentService.Update("", "", "", List.of()), List.of(new MockMultipartFile("files", "new.txt", "text/plain", new byte[]{1})));return true; }
            catch (CatalogException e) { return false; }
        };
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(upload);var b = executor.submit(upload);start.countDown();
            assertThat(List.of(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS))).containsExactlyInAnyOrder(true, false);
        }
        assertThat(read().get("attachments").size()).isEqualTo(10);
    }

    @Test void viewEditAndCsrfPermissionsApplyToTextAndEveryAttachmentRoute() throws Exception {
        save(admin, scoreId, update("private strength", "", List.of()), true, file("private.txt", "private bytes"));
        long attachment = read().get("attachments").get(0).get("id").asLong();String download = "/api/scores/" + scoreId + "/attachments/" + attachment;
        var viewer = user("scores.view");var editor = user("scores.edit");var creator = user("scores.create");var stranger = user("employees.view");
        assertThat(viewer.get(path(scoreId)).statusCode()).isEqualTo(200);assertThat(viewer.get(download).body()).isEqualTo("private bytes");
        assertThat(save(viewer, scoreId, update("not saved", "", List.of(attachment)), true, file("x.txt", "x")).statusCode()).isEqualTo(403);
        assertThat(save(creator, scoreId, update("not saved", "", List.of()), true).statusCode()).isEqualTo(403);
        assertThat(stranger.get(path(scoreId)).statusCode()).isEqualTo(403);assertThat(stranger.get(download).statusCode()).isEqualTo(403);
        assertThat(new SecurityTestClient(port).get(download).statusCode()).isEqualTo(401);
        assertThat(save(editor, scoreId, update("not saved", "", List.of()), false).statusCode()).isEqualTo(403);
        assertThat(save(editor, scoreId, update("edited", "", List.of(attachment)), true, file("new.txt", "new")).statusCode()).isEqualTo(200);
        assertThat(read().get("score").get("strengths").asText()).isEqualTo("edited");
        assertThat(editor.write("DELETE", "/api/scores/" + scoreId, null, true).statusCode()).isEqualTo(403);
        long roleId = jdbc.queryForObject("SELECT role_id FROM app_user_roles ur JOIN app_users u ON u.id=ur.user_id WHERE u.username=?", Long.class, usernames.get(editor));
        assertThat(admin.write("PUT", "/api/admin/roles/" + roleId, Map.of("name", "revoked-" + UUID.randomUUID(), "permissions", List.of()), true).statusCode()).isEqualTo(200);
        assertThat(editor.get(path(scoreId)).statusCode()).isEqualTo(403);
        long newAttachment = read().get("attachments").get(0).get("id").asLong();
        assertThat(editor.get("/api/scores/" + scoreId + "/attachments/" + newAttachment).statusCode()).isEqualTo(403);
    }
    final Map<SecurityTestClient, String> usernames = new HashMap<>();
    SecurityTestClient user(String permission) throws Exception {
        long role = id(admin.write("POST", "/api/admin/roles", Map.of("name", "assessment-" + UUID.randomUUID(), "permissions", List.of(permission)), true));
        String username = "u" + UUID.randomUUID().toString().replace("-", "");
        id(admin.write("POST", "/api/admin/users", Map.of("username", username, "displayName", "آزمایش", "password", SecurityTestClient.PASSWORD,
            "confirmation", SecurityTestClient.PASSWORD, "enabled", true, "roleIds", List.of(role)), true));
        var client = new SecurityTestClient(port);assertThat(client.login(username, SecurityTestClient.PASSWORD).statusCode()).isEqualTo(204);
        usernames.put(client, username);return client;
    }
}
