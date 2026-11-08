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

    @ParameterizedTest
    @ValueSource(strings = {"BELOW_DIPLOMA", "DIPLOMA", "ASSOCIATE", "BACHELOR", "DOCTORATE"})
    void employeeEducationAndStartDateRoundTrip(String educationLevel) throws Exception {
        long id = create("employees", Map.of("personnelCode", "EDU", "firstName", "کارمند", "lastName", "آزمایشی",
            "educationLevel", educationLevel, "startDate", "2020-03-20"));
        JsonNode row = read("employees").get(0);
        assertThat(row.get("educationLevel").asText()).isEqualTo(educationLevel);
        assertThat(row.get("startDate").asText()).isEqualTo("2020-03-20");

        assertStatus("PUT", "employees/" + id, Map.of("personnelCode", "EDU", "firstName", "کارمند", "lastName", "آزمایشی",
            "educationLevel", "DOCTORATE", "startDate", "2021-04-10"), 200);
        row = read("employees").get(0);
        assertThat(row.get("educationLevel").asText()).isEqualTo("DOCTORATE");
        assertThat(row.get("startDate").asText()).isEqualTo("2021-04-10");
    }

    @Test
    void employeeNewFieldsCanBeOmittedOrCleared() throws Exception {
        var payload = new HashMap<String, Object>(Map.of("personnelCode", "OPTIONAL", "firstName", "کارمند", "lastName", "آزمایشی"));
        long id = create("employees", payload);
        assertThat(read("employees").get(0).get("educationLevel").isNull()).isTrue();
        assertThat(read("employees").get(0).get("startDate").isNull()).isTrue();
        payload.put("educationLevel", "BACHELOR");
        payload.put("startDate", "2022-09-01");
        assertStatus("PUT", "employees/" + id, payload, 200);
        payload.put("educationLevel", null);
        payload.put("startDate", null);
        assertStatus("PUT", "employees/" + id, payload, 200);
        assertThat(read("employees").get(0).get("educationLevel").isNull()).isTrue();
        assertThat(read("employees").get(0).get("startDate").isNull()).isTrue();
    }

    @Test
    void employeeRejectsUnknownEducationAndMalformedDate() throws Exception {
        var payload = new HashMap<String, Object>(Map.of("personnelCode", "INVALID", "firstName", "کارمند", "lastName", "آزمایشی",
            "educationLevel", "UNKNOWN"));
        var response = request("POST", "employees", payload);
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(json.readTree(response.body()).get("fields").has("educationLevel")).isTrue();
        payload.put("educationLevel", "DIPLOMA");
        payload.put("startDate", "not-a-date");
        assertStatus("POST", "employees", payload, 400);
        assertThat(read("employees").size()).isZero();
    }

    @Test
    void scoreSheetSavesAllChangesAndPreservesUnchangedScoresNotesAndOtherEmployees() throws Exception {
        long employee = create("employees", Map.of("personnelCode", "E1", "firstName", "نام", "lastName", "اول"));
        long other = create("employees", Map.of("personnelCode", "E2", "firstName", "نام", "lastName", "دوم"));
        var factors = new ArrayList<Long>();
        for (int i = 0; i < 6; i++)
            factors.add(create("factors", Map.of("code", "F" + i, "title", "عامل " + i)));
        for (int i = 0; i < 4; i++)
            create("scores", Map.of("employeeId", employee, "factorId", factors.get(i), "score", 60,
                "assessedOn", "2025-01-01", "notes", "یادداشت قبلی"));
        create("scores", Map.of("employeeId", other, "factorId", factors.get(0), "score", 90, "assessedOn", "2025-01-01"));
        long originalId = jdbc.queryForObject("SELECT id FROM employee_scores WHERE employee_id = ? AND factor_id = ?",
            Long.class, employee, factors.get(0));
        var cleared = new HashMap<String, Object>();
        cleared.put("factorId", factors.get(1));cleared.put("score", null);
        var payload = Map.of("scores", List.of(
            Map.of("factorId", factors.get(0), "score", 75.25), cleared,
            Map.of("factorId", factors.get(2), "score", 60),
            Map.of("factorId", factors.get(4), "score", 0),
            Map.of("factorId", factors.get(5), "score", 100)));
        var response = request("PUT", "employees/" + employee + "/scores", payload);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        JsonNode saved = json.readTree(response.body());
        assertThat(saved.size()).isEqualTo(5);
        var byFactor = new HashMap<Long, JsonNode>();
        saved.forEach(row -> {
            assertThat(row.get("employeeId").asLong()).isEqualTo(employee);
            byFactor.put(row.get("factorId").asLong(), row);
        });
        assertThat(byFactor.get(factors.get(0)).get("id").asLong()).isEqualTo(originalId);
        assertThat(byFactor.get(factors.get(0)).get("score").asDouble()).isEqualTo(75.25);
        assertThat(byFactor.get(factors.get(0)).get("notes").asText()).isEqualTo("یادداشت قبلی");
        assertThat(byFactor.get(factors.get(0)).get("assessedOn").asText()).isEqualTo(LocalDate.now().toString());
        assertThat(byFactor).doesNotContainKey(factors.get(1));
        for (int i : List.of(2, 3)) {
            assertThat(byFactor.get(factors.get(i)).get("score").asDouble()).isEqualTo(60);
            assertThat(byFactor.get(factors.get(i)).get("assessedOn").asText()).isEqualTo("2025-01-01");
            assertThat(byFactor.get(factors.get(i)).get("notes").asText()).isEqualTo("یادداشت قبلی");
        }
        assertThat(byFactor.get(factors.get(4)).get("score").asDouble()).isZero();
        assertThat(byFactor.get(factors.get(5)).get("score").asDouble()).isEqualTo(100);
        assertThat(byFactor.get(factors.get(4)).get("assessedOn").asText()).isEqualTo(LocalDate.now().toString());
        assertThat(jdbc.queryForObject("SELECT score FROM employee_scores WHERE employee_id = ?", Double.class, other)).isEqualTo(90);
        response = request("PUT", "employees/" + employee + "/scores", payload);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(json.readTree(response.body())).isEqualTo(saved);
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.01, 100.01, 50.123})
    void invalidScoreSheetDoesNotPartiallySave(double invalidScore) throws Exception {
        long employee = create("employees", Map.of("personnelCode", "E", "firstName", "نام", "lastName", "نام"));
        long first = create("factors", Map.of("code", "F1", "title", "عامل اول"));
        long second = create("factors", Map.of("code", "F2", "title", "عامل دوم", "parentId", first));
        create("scores", Map.of("employeeId", employee, "factorId", first, "score", 80, "assessedOn", "2025-01-01"));
        JsonNode before = read("scores");
        var response = request("PUT", "employees/" + employee + "/scores", Map.of("scores", List.of(
            Map.of("factorId", first, "score", 20), Map.of("factorId", second, "score", invalidScore))));
        assertThat(response.statusCode()).as(response.body()).isEqualTo(400);
        assertThat(json.readTree(response.body()).get("fields").has("scores[1].score")).isTrue();
        assertThat(read("scores")).isEqualTo(before);
    }

    @Test
    void scoreSheetValidatesEmployeesFactorsDuplicateRowsAndRequestShape() throws Exception {
        long employee = create("employees", Map.of("personnelCode", "E", "firstName", "نام", "lastName", "نام"));
        long factor = create("factors", Map.of("code", "F", "title", "عامل"));
        create("scores", Map.of("employeeId", employee, "factorId", factor, "score", 80, "assessedOn", "2025-01-01"));
        JsonNode before = read("scores");
        String path = "employees/" + employee + "/scores";
        var validRow = Map.of("factorId", factor, "score", 20);
        assertStatus("PUT", path, Map.of("scores", List.of(validRow, Map.of("factorId", 999999, "score", 30))), 400);
        assertStatus("PUT", path, Map.of("scores", List.of(validRow, validRow)), 400);
        assertStatus("PUT", path, Map.of(), 400);
        assertStatus("PUT", path, Map.of("scores", Arrays.asList((Object) null)), 400);
        assertStatus("PUT", path, Map.of("scores", List.of(Map.of("score", 30))), 400);
        assertStatus("PUT", path, Map.of("scores", List.of(Map.of("factorId", -1, "score", 30))), 400);
        assertStatus("PUT", "employees/999999/scores", Map.of("scores", List.of(validRow)), 404);
        assertStatus("PUT", path, Map.of("scores", List.of()), 200);
        assertThat(read("scores")).isEqualTo(before);
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
