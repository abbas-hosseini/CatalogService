package com.example.webapp;

import com.example.webapp.catalog.Catalog;
import com.example.webapp.catalog.CatalogRepository;
import com.example.webapp.security.SecurityTestClient;
import com.example.webapp.security.SecurityTestConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "spring.datasource.url=jdbc:h2:mem:employee-assessment-score-tests;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
@Import(SecurityTestConfiguration.class)
class EmployeeAssessmentScoreTests {
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired CatalogRepository repository;
    SecurityTestClient admin;
    long employee;

    @BeforeEach void prepare() throws Exception {
        admin = SecurityTestClient.shared(port);
        employee = employee();
    }

    long create(String resource, Map<String, Object> body) throws Exception {
        var response = admin.write("POST", "/api/" + resource, body, true);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
        return admin.json.readTree(response.body()).get("id").asLong();
    }
    long employee() throws Exception {
        return create("employees", Map.of("personnelCode", UUID.randomUUID().toString(), "firstName", "آزمایش", "lastName", "محاسبه"));
    }
    long factor() throws Exception {
        return create("factors", Map.of("code", UUID.randomUUID().toString(), "title", "شایستگی آزمایشی"));
    }
    Map<String, Object> score(long employeeId, long factorId, Number value, String date) {
        return new HashMap<>(Map.of("employeeId", employeeId, "factorId", factorId, "score", value, "assessedOn", date));
    }
    long score(long employeeId, Number value, String date) throws Exception {
        return create("scores", score(employeeId, factor(), value, date));
    }
    Map<String, Object> training(long employeeId, Number value, Number impact) throws Exception {
        long course = create("courses", Map.of("code", UUID.randomUUID().toString(), "title", "دوره آزمایشی", "durationHours", 10));
        var body = new HashMap<String, Object>(Map.of("employeeId", employeeId, "courseId", course, "completionDate", "2026-01-15"));
        body.put("score", value);body.put("impactCoefficient", impact);
        return body;
    }
    void update(String resource, long id, Map<String, Object> body) throws Exception {
        var response = admin.write("PUT", "/api/" + resource + "/" + id, body, true);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
    }
    void delete(String resource, long id) throws Exception {
        var response = admin.write("DELETE", "/api/" + resource + "/" + id, null, true);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(204);
    }
    JsonNode result(SecurityTestClient client, long id) throws Exception {
        var response = client.get("/api/employees");
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        for (var row : client.json.readTree(response.body())) if (row.get("id").asLong() == id) return row.get("assessmentScore");
        throw new AssertionError("Employee missing");
    }
    void expect(long id, String value) throws Exception {
        assertThat(result(admin, id).decimalValue()).isEqualByComparingTo(value);
        assertThat((BigDecimal) repository.find(Catalog.EMPLOYEES, id).get("assessmentScore")).isEqualByComparingTo(value);
    }

    @Test void noAssessmentIsNullZeroIsPreservedAndNoTrainingsReturnsBaseScore() throws Exception {
        assertThat(result(admin, employee).isNull()).isTrue();
        create("trainings", training(employee, 100, 100));
        assertThat(result(admin, employee).isNull()).isTrue();
        score(employee, 0, "2026-01-01");expect(employee, "0");
        long other = employee();score(other, 72, "2026-01-01");expect(other, "72");
    }

    @Test void multipliesAllCoursesAndRecalculatesOnScoreImpactAndDeletionChanges() throws Exception {
        score(employee, 80, "2026-02-01");
        var first = training(employee, 80, 20);long firstId = create("trainings", first);
        long secondId = create("trainings", training(employee, 50, 10));
        expect(employee, "97.44");
        first.put("impactCoefficient", 50);update("trainings", firstId, first);expect(employee, "117.60");
        first.put("score", 40);update("trainings", firstId, first);expect(employee, "100.80");
        delete("trainings", secondId);expect(employee, "96");
        first.put("impactCoefficient", null);update("trainings", firstId, first);expect(employee, "80");
        first.put("impactCoefficient", 100);first.put("score", null);update("trainings", firstId, first);expect(employee, "80");
        first.put("score", 100);update("trainings", firstId, first);expect(employee, "160");
        delete("trainings", firstId);expect(employee, "80");
    }

    @Test void latestUsesAssessmentDateThenIdAndReevaluatesWhenDatesChangeOrLatestIsDeleted() throws Exception {
        long factor = factor();
        var body = score(employee, factor, 70, "2026-02-01");
        long latest = create("scores", body);
        long older = score(employee, 40, "2026-01-01");expect(employee, "70");
        long tied = score(employee, 85, "2026-02-01");expect(employee, "85");
        body.put("assessedOn", "2026-03-01");body.put("score", 90);
        update("scores", latest, body);expect(employee, "90");
        delete("scores", latest);expect(employee, "85");
        delete("scores", tied);expect(employee, "40");
        delete("scores", older);assertThat(result(admin, employee).isNull()).isTrue();
    }

    @Test void movingTrainingsAndAssessmentsUpdatesBothEmployeesWithoutStaleValues() throws Exception {
        long other = employee(), factor = factor();
        var assessment = score(employee, factor, 80, "2026-02-01");
        long assessmentId = create("scores", assessment);
        score(other, 40, "2026-01-01");
        var course = training(employee, 80, 20);long trainingId = create("trainings", course);
        expect(employee, "92.80");expect(other, "40");
        course.put("employeeId", other);update("trainings", trainingId, course);
        expect(employee, "80");expect(other, "46.40");
        assessment.put("employeeId", other);update("scores", assessmentId, assessment);
        assertThat(result(admin, employee).isNull()).isTrue();expect(other, "92.80");
    }

    @Test void bulkScoreSheetCreationUpdateAndClearAreReflectedImmediately() throws Exception {
        long factor = factor();
        create("trainings", training(employee, 50, 50));
        for (int value : List.of(60, 80)) {
            var response = admin.write("PUT", "/api/employees/" + employee + "/scores",
                Map.of("scores", List.of(Map.of("factorId", factor, "score", value))), true);
            assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
            expect(employee, value == 60 ? "75" : "100");
        }
        var empty = new HashMap<String, Object>();empty.put("factorId", factor);empty.put("score", null);
        assertThat(admin.write("PUT", "/api/employees/" + employee + "/scores", Map.of("scores", List.of(empty)), true).statusCode()).isEqualTo(200);
        assertThat(result(admin, employee).isNull()).isTrue();
    }

    @Test void keepsDecimalPrecisionUntilFinalRoundingAndAcceptsZeroImpact() throws Exception {
        score(employee, new BigDecimal("12.34"), "2026-01-01");
        for (int i = 0; i < 5; i++) create("trainings", training(employee, 1, 1));
        // 12.34 * 1.0001^5 = 12.346171234...; rounding each step would incorrectly return 12.34.
        expect(employee, "12.35");
        create("trainings", training(employee, 100, 0));create("trainings", training(employee, 0, 100));
        expect(employee, "12.35");
    }

    @Test void existingSourceRowsAndDirectDatabaseChangesNeedNoBackfillAndFutureCoursesAreExcluded() throws Exception {
        long assessment = score(employee, 50, "2026-01-01");
        long training = create("trainings", training(employee, 100, 100));
        expect(employee, "100");
        jdbc.update("UPDATE employee_scores SET score=60 WHERE id=?", assessment);
        expect(employee, "120");
        jdbc.update("UPDATE employee_trainings SET completion_date=? WHERE id=?", java.sql.Date.valueOf(java.time.LocalDate.now().plusDays(1)), training);
        expect(employee, "60");
        jdbc.update("UPDATE employee_trainings SET completion_date=CURRENT_DATE, impact_coefficient=50 WHERE id=?", training);
        expect(employee, "90");
    }

    @Test void employeePayloadCannotOverrideDerivedScoreAndRestrictedLookupsDoNotExposeIt() throws Exception {
        score(employee, 80, "2026-01-01");
        var current = repository.find(Catalog.EMPLOYEES, employee);
        var body = new HashMap<String, Object>(Map.of("personnelCode", current.get("personnelCode"), "firstName", "ویرایش", "lastName", "کارمند"));
        body.put("assessmentScore", 99999);body.put("assessment_score", 99999);
        update("employees", employee, body);expect(employee, "80");
        long forged = create("employees", new HashMap<>(Map.of("personnelCode", UUID.randomUUID().toString(), "firstName", "آزمایش", "lastName", "جدید", "assessmentScore", 999)));
        assertThat(result(admin, forged).isNull()).isTrue();

        var reader = user("employees.view");
        assertThat(result(reader, employee).decimalValue()).isEqualByComparingTo("80");
        assertThat(reader.get("/api/scores").statusCode()).isEqualTo(403);
        assertThat(reader.get("/api/trainings").statusCode()).isEqualTo(403);
        assertThat(reader.write("PUT", "/api/employees/" + employee, body, true).statusCode()).isEqualTo(403);
        var trainingReader = user("trainings.view");
        assertThat(trainingReader.get("/api/employees").statusCode()).isEqualTo(403);
        var lookups = trainingReader.get("/api/lookups");
        assertThat(lookups.statusCode()).isEqualTo(200);
        assertThat(lookups.body()).contains("personnelCode").doesNotContain("assessmentScore", "baseScore", "assessment_score");
    }

    SecurityTestClient user(String permission) throws Exception {
        long role = create("admin/roles", Map.of("name", UUID.randomUUID().toString(), "permissions", List.of(permission)));
        String username = "u" + UUID.randomUUID().toString().replace("-", "");
        create("admin/users", Map.of("username", username, "displayName", "کاربر آزمایش", "password", SecurityTestClient.PASSWORD,
            "confirmation", SecurityTestClient.PASSWORD, "enabled", true, "roleIds", List.of(role)));
        var client = new SecurityTestClient(port);
        assertThat(client.login(username, SecurityTestClient.PASSWORD).statusCode()).isEqualTo(204);
        return client;
    }
}
