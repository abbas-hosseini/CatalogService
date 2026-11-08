package com.example.webapp;

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

import java.net.URI;
import java.net.http.*;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(SecurityTestConfiguration.class)
class CatalogApiTests {
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    HttpClient http;
    String csrf;
    final JsonMapper json = JsonMapper.builder().build();

    @BeforeEach
    void emptyDatabase() throws Exception {
        var session = SecurityTestClient.shared(port);http = session.http;csrf = session.csrf;
        for (String table : List.of("employee_positions", "employee_trainings", "employee_scores", "job_requirements", "employees", "courses"))
            jdbc.update("DELETE FROM " + table);
        jdbc.update("UPDATE evaluation_factors SET parent_id = NULL");
        jdbc.update("UPDATE jobs SET parent_id = NULL");
        jdbc.update("DELETE FROM evaluation_factors");
        jdbc.update("DELETE FROM jobs");
        jdbc.update("DELETE FROM companies");
    }

    @Test
    void completeEmployeeRecordCanBeCreatedReadEditedAndDeleted() throws Exception {
        long factor = create("factors", Map.of("code", "F1", "title", "کار تیمی"));
        long company = create("companies", Map.of("code", "CO1", "title", "شرکت اول"));
        long job = create("jobs", Map.of("code", "J1", "title", "کارشناس", "companyId", company));
        long employee = create("employees", Map.of("personnelCode", "E1", "firstName", "آزمایش", "lastName", "سامانه"));
        long position = create("positions", Map.of("employeeId", employee, "companyId", company, "jobId", job));
        long course = create("courses", Map.of("code", "C1", "title", "مهارت ارتباط", "durationHours", 12.5));
        long training = create("trainings", Map.of("employeeId", employee, "courseId", course, "completionDate", "2025-01-01", "certificateNumber", "CERT-1"));
        long score = create("scores", Map.of("employeeId", employee, "factorId", factor, "score", 75.25, "assessedOn", "2025-01-01"));
        long requirement = create("requirements", Map.of("jobId", job, "factorId", factor, "minimumScore", 80));

        JsonNode employeeRow = read("employees").get(0);
        assertThat(employeeRow.get("firstName").asText()).isEqualTo("آزمایش");
        assertThat(employeeRow.has("jobId")).isFalse();
        assertThat(read("positions").get(0).get("jobId").asLong()).isEqualTo(job);
        assertThat(read("trainings").get(0).get("completionDate").asText()).isEqualTo("2025-01-01");
        assertThat(read("trainings").get(0).get("score").isNull()).isTrue();
        assertThat(read("scores").get(0).get("score").asDouble()).isEqualTo(75.25);
        assertThat(read("summary").get("employees").asInt()).isEqualTo(1);

        assertStatus("PUT", "factors/" + factor, Map.of("code", "F1", "title", "کار گروهی"), 200);
        assertStatus("PUT", "jobs/" + job, Map.of("code", "J1", "title", "کارشناس ارشد", "companyId", company), 200);
        assertStatus("PUT", "courses/" + course, Map.of("code", "C1", "title", "ارتباط مؤثر", "durationHours", 16), 200);
        assertStatus("PUT", "employees/" + employee, Map.of("personnelCode", "E1", "firstName", "آزمایش", "lastName", "جدید"), 200);
        assertThat(read("positions").get(0).get("id").asLong()).isEqualTo(position);
        assertStatus("PUT", "scores/" + score, Map.of("employeeId", employee, "factorId", factor, "score", 100, "assessedOn", "2025-02-01"), 200);
        assertStatus("PUT", "requirements/" + requirement, Map.of("jobId", job, "factorId", factor, "minimumScore", 0), 200);
        assertStatus("PUT", "trainings/" + training, Map.of("employeeId", employee, "courseId", course, "completionDate", "2025-02-01", "score", 99), 200);
        assertThat(read("scores").get(0).get("score").asDouble()).isEqualTo(100);
        assertThat(read("requirements").get(0).get("minimumScore").asDouble()).isZero();
        assertThat(read("trainings").get(0).get("score").asDouble()).isEqualTo(99);

        for (String path : List.of("scores/"+score, "requirements/"+requirement, "trainings/"+training,
                "positions/"+position, "employees/"+employee, "courses/"+course, "jobs/"+job, "companies/"+company, "factors/"+factor)) assertStatus("DELETE", path, null, 204);
        assertThat(read("summary").get("employees").asInt()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"factors", "jobs"})
    void hierarchiesRejectSelfParentAndDescendantCycles(String resource) throws Exception {
        Long company = resource.equals("jobs") ? create("companies", Map.of("code", "CO", "title", "شرکت")) : null;
        long root = create(resource, treePayload("ROOT", null, company));
        long child = create(resource, treePayload("CHILD", root, company));
        long leaf = create(resource, treePayload("LEAF", child, company));
        assertStatus("PUT", resource+"/"+root, treePayload("ROOT", root, company), 400);
        assertStatus("PUT", resource+"/"+root, treePayload("ROOT", leaf, company), 400);
        assertStatus("DELETE", resource+"/"+root, null, 409);
        assertStatus("PUT", resource+"/"+leaf, treePayload("LEAF", null, company), 200);
        assertStatus("DELETE", resource+"/"+child, null, 204);
        assertStatus("DELETE", resource+"/"+root, null, 204);
    }

    @Test
    void duplicateCodesAndRelationshipsAreRejectedWithoutChangingExistingData() throws Exception {
        long factor = create("factors", Map.of("code", "F1", "title", "دانش"));
        assertStatus("POST", "factors", Map.of("code", " F1 ", "title", "تکراری"), 409);
        long company = create("companies", Map.of("code", "CO", "title", "شرکت"));
        long job = create("jobs", Map.of("code", "J1", "title", "شغل", "companyId", company));
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
        assertStatus("POST", "employees", Map.of("personnelCode", "E", "firstName", "نام", "lastName", ""), 400);
        assertStatus("POST", "courses", Map.of("code", "C", "title", "دوره", "durationHours", 0), 400);
        assertStatus("POST", "scores", Map.of("employeeId", 1, "factorId", 1, "score", 50, "assessedOn", LocalDate.now().plusDays(1).toString()), 400);
        assertStatus("PUT", "jobs/99999", Map.of("code", "J", "title", "شغل", "companyId", 99999), 404);
        assertStatus("DELETE", "employees/99999", null, 404);
        assertStatus("GET", "not-a-table", null, 404);
    }

    @Test
    void preventsDeletionOfReferencedEmployeeFactorJobAndCourse() throws Exception {
        long factor = create("factors", Map.of("code", "F", "title", "عامل"));
        long employee = create("employees", Map.of("personnelCode", "E", "firstName", "نام", "lastName", "نام"));
        long company = create("companies", Map.of("code", "CO", "title", "شرکت"));
        long job = create("jobs", Map.of("code", "J", "title", "شغل", "companyId", company));
        create("positions", Map.of("employeeId", employee, "companyId", company, "jobId", job));
        long course = create("courses", Map.of("code", "C", "title", "دوره", "durationHours", 8));
        create("scores", Map.of("employeeId", employee, "factorId", factor, "score", 50, "assessedOn", "2025-01-01"));
        create("trainings", Map.of("employeeId", employee, "courseId", course, "completionDate", "2025-01-01"));
        for (String path : List.of("employees/"+employee, "companies/"+company, "factors/"+factor, "jobs/"+job, "courses/"+course))
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
    @ValueSource(strings = {"BELOW_DIPLOMA", "DIPLOMA", "ASSOCIATE", "BACHELOR", "MASTER", "DOCTORATE"})
    void employeeEducationAndStartDateRoundTrip(String educationLevel) throws Exception {
        long id = create("employees", Map.of("personnelCode", "EDU", "firstName", "کارمند", "lastName", "آزمایشی",
            "educationLevel", educationLevel, "startDate", "1399-01-01"));
        JsonNode row = read("employees").get(0);
        assertThat(row.get("educationLevel").asText()).isEqualTo(educationLevel);
        assertThat(row.get("startDate").asText()).isEqualTo("1399-01-01");
        assertThat(jdbc.queryForObject("SELECT start_date FROM employees WHERE id = ?", String.class, id)).isEqualTo("1399-01-01");

        assertStatus("PUT", "employees/" + id, Map.of("personnelCode", "EDU", "firstName", "کارمند", "lastName", "آزمایشی",
            "educationLevel", "DOCTORATE", "startDate", "1400-01-21"), 200);
        row = read("employees").get(0);
        assertThat(row.get("educationLevel").asText()).isEqualTo("DOCTORATE");
        assertThat(row.get("startDate").asText()).isEqualTo("1400-01-21");
    }

    @Test
    void employeeNewFieldsCanBeOmittedOrCleared() throws Exception {
        var payload = new HashMap<String, Object>(Map.of("personnelCode", "OPTIONAL", "firstName", "کارمند", "lastName", "آزمایشی"));
        long id = create("employees", payload);
        assertThat(read("employees").get(0).get("educationLevel").isNull()).isTrue();
        assertThat(read("employees").get(0).get("startDate").isNull()).isTrue();
        payload.put("educationLevel", "BACHELOR");
        payload.put("startDate", "1401-06-10");
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

    @Test
    void companiesAndMultiplePositionsCanBeManagedIndependentlyOfEmployeeRecords() throws Exception {
        long first = create("companies", Map.of("code", "CO1", "title", "شرکت اول"));
        long second = create("companies", Map.of("code", "CO2", "title", "شرکت دوم"));
        assertStatus("POST", "companies", Map.of("code", " CO1 ", "title", "تکراری"), 409);
        assertStatus("POST", "companies", Map.of("code", "CO3", "title", " "), 400);
        assertStatus("PUT", "companies/" + first, Map.of("code", "CO1", "title", "شرکت ویرایش‌شده", "description", "توضیحات"), 200);
        assertThat(jdbc.queryForObject("SELECT title FROM companies WHERE id = ?", String.class, first)).isEqualTo("شرکت ویرایش‌شده");
        long job = create("jobs", Map.of("code", "J1", "title", "کارشناس", "companyId", first));
        long otherJob = create("jobs", Map.of("code", "J2", "title", "مدیر", "companyId", first));
        long secondCompanyJob = create("jobs", Map.of("code", "J3", "title", "کارشناس شرکت دوم", "companyId", second));
        long employee = create("employees", Map.of("personnelCode", "E1", "firstName", "نام", "lastName", "اول"));
        long other = create("employees", Map.of("personnelCode", "E2", "firstName", "نام", "lastName", "دوم"));
        var firstAssignment = Map.of("employeeId", employee, "companyId", first, "jobId", job);
        long factor = create("factors", Map.of("code", "F", "title", "عامل"));
        long course = create("courses", Map.of("code", "C", "title", "دوره", "durationHours", 8));
        create("scores", Map.of("employeeId", employee, "factorId", factor, "score", 75, "assessedOn", "2025-01-01"));
        create("trainings", Map.of("employeeId", employee, "courseId", course, "completionDate", "2025-01-01"));
        JsonNode scoresBefore = read("scores"), trainingsBefore = read("trainings");
        long position = create("positions", firstAssignment);
        long secondPosition = create("positions", Map.of("employeeId", employee, "companyId", second, "jobId", secondCompanyJob));
        long thirdPosition = create("positions", Map.of("employeeId", employee, "companyId", first, "jobId", otherJob));
        long otherPosition = create("positions", Map.of("employeeId", other, "companyId", first, "jobId", job));
        assertStatus("POST", "positions", firstAssignment, 409);
        assertStatus("PUT", "positions/" + thirdPosition, firstAssignment, 409);
        assertThat(read("summary").get("positions").asInt()).isEqualTo(4);
        assertStatus("PUT", "positions/" + position, Map.of("employeeId", employee, "companyId", first, "jobId", job, "notes", "سمت اصلی"), 200);
        assertThat(jdbc.queryForObject("SELECT notes FROM employee_positions WHERE id = ?", String.class, position)).isEqualTo("سمت اصلی");
        assertStatus("DELETE", "companies/" + second, null, 409);
        assertStatus("DELETE", "employees/" + other, null, 409);
        assertStatus("DELETE", "positions/" + secondPosition, null, 204);
        assertStatus("DELETE", "companies/" + second, null, 409);
        assertStatus("DELETE", "jobs/" + secondCompanyJob, null, 204);
        assertStatus("DELETE", "companies/" + second, null, 204);
        assertThat(read("employees").size()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT employee_id FROM employee_positions WHERE id = ?", Long.class, otherPosition)).isEqualTo(other);
        // Replacing a job/company assignment does not alter personal scores or training history.
        assertStatus("PUT", "positions/" + thirdPosition, Map.of("employeeId", employee, "companyId", first, "jobId", otherJob), 200);
        assertThat(read("positions").size()).isEqualTo(3);
        assertThat(read("scores")).isEqualTo(scoresBefore);
        assertThat(read("trainings")).isEqualTo(trainingsBefore);
    }

    @Test
    void positionsRequireExistingEmployeeCompanyAndJobAndAllowCompletingLegacyAssignment() throws Exception {
        long employee = create("employees", Map.of("personnelCode", "E", "firstName", "نام", "lastName", "نام"));
        long company = create("companies", Map.of("code", "CO", "title", "شرکت"));
        long job = create("jobs", Map.of("code", "J", "title", "شغل", "companyId", company));
        var valid = Map.of("employeeId", employee, "companyId", company, "jobId", job);
        for (String field : valid.keySet()) {
            var invalid = new HashMap<String, Object>(valid);
            for (Long value : Arrays.asList(null, -1L, 999999L)) {
                invalid.put(field, value);
                assertStatus("POST", "positions", invalid, 400);
            }
        }
        assertThat(read("positions").size()).isZero();
        assertStatus("PUT", "positions/999999", valid, 404);
        assertStatus("PUT", "companies/999999", Map.of("code", "CO", "title", "شرکت"), 404);
        jdbc.update("INSERT INTO employee_positions (employee_id, job_id) VALUES (?, ?)", employee, job);
        var legacy = read("positions").get(0);
        assertThat(legacy.get("companyId").isNull()).isTrue();
        assertStatus("PUT", "positions/" + legacy.get("id").asLong(), valid, 200);
        assertThat(read("positions").get(0).get("companyId").asLong()).isEqualTo(company);
    }

    @ParameterizedTest
    @CsvSource({"۱۴۰۳/۱۲/۳۰,1403-12-30", "١٤٠٤/١/١,1404-01-01", "1399-12-30,1399-12-30", "1404/6/31,1404-06-31", "1404-07-30,1404-07-30"})
    void startDateAcceptsPersianDigitsAndRealJalaliDates(String input, String expected) throws Exception {
        long id = create("employees", Map.of("personnelCode", "JALALI", "firstName", "نام", "lastName", "نام", "startDate", input));
        assertThat(read("employees").get(0).get("startDate").asText()).isEqualTo(expected);
        assertThat(jdbc.queryForObject("SELECT start_date FROM employees WHERE id = ?", String.class, id)).isEqualTo(expected);
        assertStatus("PUT", "employees/" + id, Map.of("personnelCode", "JALALI", "firstName", "نام", "lastName", "نام", "startDate", "  "), 200);
        assertThat(read("employees").get(0).get("startDate").isNull()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"1404-12-30", "1403-12-31", "1404-07-31", "1404-13-01", "1404-00-10", "1404-01-00", "0000-01-01", "2020-03-20", "1404/1", "not-a-date"})
    void invalidJalaliDateReportsFieldErrorWithoutChangingEmployee(String input) throws Exception {
        var payload = new HashMap<String, Object>(Map.of("personnelCode", "JALALI", "firstName", "نام", "lastName", "نام", "startDate", "1403-12-30"));
        long id = create("employees", payload);
        payload.put("startDate", input);
        var response = request("PUT", "employees/" + id, payload);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(400);
        assertThat(json.readTree(response.body()).get("fields").has("startDate")).isTrue();
        assertThat(read("employees").get(0).get("startDate").asText()).isEqualTo("1403-12-30");
    }

    @Test
    void rejectedEmployeeValueIsNotReportedAsAForeignKeyDependency() throws Exception {
        // Reproduce a database whose education constraint still predates the MASTER option.
        jdbc.execute("ALTER TABLE employees ADD CONSTRAINT test_legacy_education CHECK (education_level IS NULL OR education_level <> 'MASTER')");
        try {
            var response = request("POST", "employees", Map.of("personnelCode", "CONSTRAINT-TEST",
                "firstName", "PrivateFirstName", "lastName", "PrivateLastName", "educationLevel", "MASTER",
                "startDate", "1403-01-01"));
            assertThat(response.statusCode()).as(response.body()).isEqualTo(400);
            assertThat(response.body()).contains("مقادیر مجاز بانک").doesNotContain("وابسته", "PrivateFirstName", "PrivateLastName", "test_legacy_education");
            assertThat(read("employees").size()).isZero();
        } finally {
            jdbc.execute("ALTER TABLE employees DROP CONSTRAINT test_legacy_education");
        }
        create("employees", Map.of("personnelCode", "CONSTRAINT-TEST", "firstName", "نام", "lastName", "نام",
            "educationLevel", "MASTER", "startDate", "1403-01-01"));
    }

    @Test
    void jobsRequireCompanyAndRejectParentsAndPositionsFromAnotherCompany() throws Exception {
        long first = create("companies", Map.of("code", "CO1", "title", "اول"));
        long second = create("companies", Map.of("code", "CO2", "title", "دوم"));
        for (Long company : Arrays.asList(null, -1L, 999999L)) {
            var invalid = treePayload("INVALID", null, null);
            invalid.put("companyId", company);
            assertStatus("POST", "jobs", invalid, 400);
        }
        long root = create("jobs", treePayload("ROOT", null, first));
        long child = create("jobs", treePayload("CHILD", root, first));
        long otherJob = create("jobs", treePayload("OTHER", null, second));
        assertStatus("POST", "jobs", treePayload("CROSS", root, second), 400);
        assertStatus("PUT", "jobs/" + child, treePayload("CHILD", otherJob, first), 400);
        assertStatus("PUT", "jobs/" + root, treePayload("ROOT", null, second), 409);
        long employee = create("employees", Map.of("personnelCode", "E", "firstName", "نام", "lastName", "نام"));
        long position = create("positions", Map.of("employeeId", employee, "companyId", first, "jobId", child));
        assertStatus("POST", "positions", Map.of("employeeId", employee, "companyId", second, "jobId", child), 400);
        assertStatus("PUT", "positions/" + position, Map.of("employeeId", employee, "companyId", first, "jobId", otherJob), 400);
        assertStatus("PUT", "jobs/" + child, treePayload("CHILD", null, second), 409);
        assertThat(jdbc.queryForObject("SELECT company_id FROM jobs WHERE id = ?", Long.class, child)).isEqualTo(first);
        assertThat(jdbc.queryForObject("SELECT company_id FROM employee_positions WHERE id = ?", Long.class, position)).isEqualTo(first);
        assertStatus("DELETE", "positions/" + position, null, 204);
        assertStatus("PUT", "jobs/" + child, treePayload("CHILD", otherJob, second), 200);
        assertThat(jdbc.queryForObject("SELECT company_id FROM jobs WHERE id = ?", Long.class, child)).isEqualTo(second);
        assertThat(read("jobs").get(0).has("companyId")).isTrue();
    }

    @Test
    void legacyJobsCanBeAssignedTopDownWithoutLosingRequirementsOrPositions() throws Exception {
        long first = create("companies", Map.of("code", "CO1", "title", "اول"));
        long second = create("companies", Map.of("code", "CO2", "title", "دوم"));
        jdbc.update("INSERT INTO jobs (code, title) VALUES ('OLD-ROOT', 'Previous root')");
        long root = jdbc.queryForObject("SELECT id FROM jobs WHERE code = 'OLD-ROOT'", Long.class);
        jdbc.update("INSERT INTO jobs (code, title, parent_id) VALUES ('OLD-CHILD', 'Previous child', ?)", root);
        long child = jdbc.queryForObject("SELECT id FROM jobs WHERE code = 'OLD-CHILD'", Long.class);
        long factor = create("factors", treePayload("FACTOR", null, null));
        long requirement = create("requirements", Map.of("jobId", child, "factorId", factor, "minimumScore", 75));
        long employee = create("employees", Map.of("personnelCode", "E", "firstName", "نام", "lastName", "نام"));
        jdbc.update("INSERT INTO employee_positions (employee_id, company_id, job_id, notes) VALUES (?, ?, ?, 'Previous note')", employee, first, child);
        long position = read("positions").get(0).get("id").asLong();
        assertStatus("POST", "positions", Map.of("employeeId", employee, "companyId", first, "jobId", root), 400);
        assertStatus("PUT", "jobs/" + child, treePayload("OLD-CHILD", root, first), 400);
        assertStatus("PUT", "jobs/" + root, treePayload("OLD-ROOT", null, first), 200);
        assertStatus("PUT", "jobs/" + child, treePayload("OLD-CHILD", null, second), 409);
        assertStatus("PUT", "jobs/" + child, treePayload("OLD-CHILD", root, first), 200);
        assertThat(jdbc.queryForObject("SELECT parent_id FROM jobs WHERE id = ?", Long.class, child)).isEqualTo(root);
        assertThat(read("requirements").get(0).get("id").asLong()).isEqualTo(requirement);
        assertThat(read("positions").get(0).get("id").asLong()).isEqualTo(position);
        assertThat(read("positions").get(0).get("notes").asText()).isEqualTo("Previous note");
        assertStatus("PUT", "positions/" + position, Map.of("employeeId", employee, "companyId", first, "jobId", child, "notes", "Previous note"), 200);
    }

    Map<String, Object> treePayload(String code, Long parentId, Long companyId) {
        var payload = new HashMap<String, Object>(Map.of("code", code, "title", code));
        if (parentId != null) payload.put("parentId", parentId);
        if (companyId != null) payload.put("companyId", companyId);
        return payload;
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
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/"+path)).header("Content-Type", "application/json").header("X-CSRF-TOKEN", csrf);
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
