package com.example.webapp;

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

import java.net.URI;
import java.net.http.*;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CatalogApiTests {
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    final HttpClient http = HttpClient.newHttpClient();
    final JsonMapper json = JsonMapper.builder().build();

    @BeforeEach
    void emptyDatabase() {
        for (String table : List.of("employee_trainings", "employee_scores", "job_requirements", "employees", "courses"))
            jdbc.update("DELETE FROM " + table);
        jdbc.update("UPDATE evaluation_factors SET parent_id = NULL");
        jdbc.update("UPDATE jobs SET parent_id = NULL");
        jdbc.update("DELETE FROM evaluation_factors");
        jdbc.update("DELETE FROM jobs");
    }

    @Test
    void completeEmployeeRecordCanBeCreatedReadEditedAndDeleted() throws Exception {
        long factor = create("factors", Map.of("code", "F1", "title", "کار تیمی"));
        long job = create("jobs", Map.of("code", "J1", "title", "کارشناس"));
        long employee = create("employees", Map.of("personnelCode", "E1", "firstName", "آزمایش", "lastName", "سامانه", "jobId", job));
        long course = create("courses", Map.of("code", "C1", "title", "مهارت ارتباط", "durationHours", 12.5));
        long training = create("trainings", Map.of("employeeId", employee, "courseId", course, "completionDate", "2025-01-01", "certificateNumber", "CERT-1"));
        long score = create("scores", Map.of("employeeId", employee, "factorId", factor, "score", 75.25, "assessedOn", "2025-01-01"));
        long requirement = create("requirements", Map.of("jobId", job, "factorId", factor, "minimumScore", 80));

        JsonNode employeeRow = read("employees").get(0);
        assertThat(employeeRow.get("firstName").asText()).isEqualTo("آزمایش");
        assertThat(employeeRow.get("jobId").asLong()).isEqualTo(job);
        assertThat(read("trainings").get(0).get("completionDate").asText()).isEqualTo("2025-01-01");
        assertThat(read("trainings").get(0).get("score").isNull()).isTrue();
        assertThat(read("scores").get(0).get("score").asDouble()).isEqualTo(75.25);
        assertThat(read("summary").get("employees").asInt()).isEqualTo(1);

        assertStatus("PUT", "factors/" + factor, Map.of("code", "F1", "title", "کار گروهی"), 200);
        assertStatus("PUT", "jobs/" + job, Map.of("code", "J1", "title", "کارشناس ارشد"), 200);
        assertStatus("PUT", "courses/" + course, Map.of("code", "C1", "title", "ارتباط مؤثر", "durationHours", 16), 200);
        assertStatus("PUT", "employees/" + employee, Map.of("personnelCode", "E1", "firstName", "آزمایش", "lastName", "جدید"), 200);
        assertThat(read("employees").get(0).get("jobId").isNull()).isTrue();
        assertStatus("PUT", "scores/" + score, Map.of("employeeId", employee, "factorId", factor, "score", 100, "assessedOn", "2025-02-01"), 200);
        assertStatus("PUT", "requirements/" + requirement, Map.of("jobId", job, "factorId", factor, "minimumScore", 0), 200);
        assertStatus("PUT", "trainings/" + training, Map.of("employeeId", employee, "courseId", course, "completionDate", "2025-02-01", "score", 99), 200);
        assertThat(read("scores").get(0).get("score").asDouble()).isEqualTo(100);
        assertThat(read("requirements").get(0).get("minimumScore").asDouble()).isZero();
        assertThat(read("trainings").get(0).get("score").asDouble()).isEqualTo(99);

        for (String path : List.of("scores/"+score, "requirements/"+requirement, "trainings/"+training,
                "employees/"+employee, "courses/"+course, "jobs/"+job, "factors/"+factor)) assertStatus("DELETE", path, null, 204);
        assertThat(read("summary").get("employees").asInt()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"factors", "jobs"})
    void hierarchiesRejectSelfParentAndDescendantCycles(String resource) throws Exception {
        long root = create(resource, Map.of("code", "ROOT", "title", "ریشه"));
        long child = create(resource, Map.of("code", "CHILD", "title", "فرزند", "parentId", root));
        long leaf = create(resource, Map.of("code", "LEAF", "title", "برگ", "parentId", child));
        assertStatus("PUT", resource+"/"+root, Map.of("code", "ROOT", "title", "ریشه", "parentId", root), 400);
        assertStatus("PUT", resource+"/"+root, Map.of("code", "ROOT", "title", "ریشه", "parentId", leaf), 400);
        assertStatus("DELETE", resource+"/"+root, null, 409);
        assertStatus("PUT", resource+"/"+leaf, Map.of("code", "LEAF", "title", "برگ مستقل"), 200);
        assertStatus("DELETE", resource+"/"+child, null, 204);
        assertStatus("DELETE", resource+"/"+root, null, 204);
    }

    @Test
    void duplicateCodesAndRelationshipsAreRejectedWithoutChangingExistingData() throws Exception {
        long factor = create("factors", Map.of("code", "F1", "title", "دانش"));
        assertStatus("POST", "factors", Map.of("code", " F1 ", "title", "تکراری"), 409);
        long job = create("jobs", Map.of("code", "J1", "title", "شغل"));
        long employee = create("employees", Map.of("personnelCode", "E1", "firstName", "نام", "lastName", "خانوادگی"));
        assertStatus("POST", "employees", Map.of("personnelCode", "E1", "firstName", "نام", "lastName", "خانوادگی"), 409);
        long course = create("courses", Map.of("code", "C1", "title", "دوره", "durationHours", 8));
        var score = Map.of("employeeId", employee, "factorId", factor, "score", 0, "assessedOn", "2025-01-01");
        var requirement = Map.of("jobId", job, "factorId", factor, "minimumScore", 100);
        var training = Map.of("employeeId", employee, "courseId", course, "completionDate", "2025-01-01");
        create("scores", score); create("requirements", requirement); create("trainings", training);
        assertStatus("POST", "scores", score, 409);
        assertStatus("POST", "requirements", requirement, 409);
        assertStatus("POST", "trainings", training, 409);
        create("trainings", Map.of("employeeId", employee, "courseId", course, "completionDate", "2025-02-01"));
        assertThat(read("scores").size()).isEqualTo(1);
        assertThat(read("trainings").size()).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.01, 100.01, 50.123})
    void rejectsScoresOutsideRangeOrWithExcessPrecision(double value) throws Exception {
        var response = request("POST", "scores", Map.of("employeeId", 1, "factorId", 1, "score", value, "assessedOn", "2025-01-01"));
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(json.readTree(response.body()).get("fields").has("score")).isTrue();
        response = request("POST", "requirements", Map.of("jobId", 1, "factorId", 1, "minimumScore", value));
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(json.readTree(response.body()).get("fields").has("minimumScore")).isTrue();
        response = request("POST", "trainings", Map.of("employeeId", 1, "courseId", 1, "score", value, "completionDate", "2025-01-01"));
        assertThat(response.statusCode()).isEqualTo(400);
    }

    @Test
    void validatesRequiredFieldsDatesAndReferences() throws Exception {
        assertStatus("POST", "factors", Map.of("code", " ", "title", ""), 400);
        assertStatus("POST", "factors", Map.of("code", "F", "title", "عامل", "parentId", 99999), 400);
        assertStatus("POST", "employees", Map.of("personnelCode", "E", "firstName", "نام", "lastName", "نام", "jobId", 99999), 400);
        assertStatus("POST", "courses", Map.of("code", "C", "title", "دوره", "durationHours", 0), 400);
        assertStatus("POST", "scores", Map.of("employeeId", 1, "factorId", 1, "score", 50, "assessedOn", LocalDate.now().plusDays(1).toString()), 400);
        assertStatus("PUT", "jobs/99999", Map.of("code", "J", "title", "شغل"), 404);
        assertStatus("DELETE", "employees/99999", null, 404);
        assertStatus("GET", "not-a-table", null, 404);
    }

    @Test
    void preventsDeletionOfReferencedEmployeeFactorJobAndCourse() throws Exception {
        long factor = create("factors", Map.of("code", "F", "title", "عامل"));
        long job = create("jobs", Map.of("code", "J", "title", "شغل"));
        long employee = create("employees", Map.of("personnelCode", "E", "firstName", "نام", "lastName", "نام", "jobId", job));
        long course = create("courses", Map.of("code", "C", "title", "دوره", "durationHours", 8));
        create("scores", Map.of("employeeId", employee, "factorId", factor, "score", 50, "assessedOn", "2025-01-01"));
        create("trainings", Map.of("employeeId", employee, "courseId", course, "completionDate", "2025-01-01"));
        for (String path : List.of("employees/"+employee, "factors/"+factor, "jobs/"+job, "courses/"+course))
            assertStatus("DELETE", path, null, 409);
        assertThat(read("employees").size()).isEqualTo(1);
    }

    @Test
    void servesPersianApplicationAndAssets() throws Exception {
        for (String path : List.of("/", "/app.js", "/styles.css")) {
            var response = http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).isNotBlank();
            if (path.equals("/")) assertThat(response.body()).contains("lang=\"fa\"", "dir=\"rtl\"", "شایستگی");
        }
    }

    long create(String resource, Object body) throws Exception {
        var response = request("POST", resource, body);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
        return json.readTree(response.body()).get("id").asLong();
    }
    JsonNode read(String resource) throws Exception {
        var response = request("GET", resource, null);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return json.readTree(response.body());
    }
    void assertStatus(String method, String path, Object body, int expected) throws Exception {
        var response = request(method, path, body);
        assertThat(response.statusCode()).as(method+" "+path+": "+response.body()).isEqualTo(expected);
    }
    HttpResponse<String> request(String method, String path, Object body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/"+path)).header("Content-Type", "application/json");
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
