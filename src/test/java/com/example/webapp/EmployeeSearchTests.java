package com.example.webapp;

import com.example.webapp.security.SecurityTestClient;
import com.example.webapp.security.SecurityTestConfiguration;
import com.github.mfathi91.time.PersianDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "spring.datasource.url=jdbc:h2:mem:employee-search-tests;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
@Import(SecurityTestConfiguration.class)
class EmployeeSearchTests {
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    SecurityTestClient admin;
    long speciality;
    LocalDate today;
    @BeforeEach void prepare() throws Exception {
        admin = SecurityTestClient.shared(port);
        today = LocalDate.now(ZoneId.of("Asia/Tehran"));
        speciality = create("specialities", Map.of("code", UUID.randomUUID().toString(), "title", UUID.randomUUID().toString()));
    }
    long create(String resource, Map<String, Object> body) throws Exception {
        var response = admin.write("POST", "/api/" + resource, body, true);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
        return admin.json.readTree(response.body()).get("id").asLong();
    }
    long level(Integer number) throws Exception {
        var body = new HashMap<String, Object>(Map.of("code", UUID.randomUUID().toString(), "title", UUID.randomUUID().toString()));
        body.put("level", number);return create("organizational-levels", body);
    }
    Map<String, Object> person() {
        return new HashMap<>(Map.of("personnelCode", UUID.randomUUID().toString(), "firstName", "آزمایش", "lastName", "جستجو", "specialityId", speciality));
    }
    String yearsAgo(int years) {
        var current = PersianDate.fromGregorian(today);
        return PersianDate.of(current.getYear() - years, current.getMonthValue(), Math.min(current.getDayOfMonth(), 28)).toString();
    }
    String query(Map<String, Object> filters) {
        var values = new LinkedHashMap<String, Object>(Map.of("specialityId", speciality));values.putAll(filters);
        return "/api/employees/search?" + values.entrySet().stream().map(entry -> entry.getKey() + "=" + URLEncoder.encode(entry.getValue().toString(), StandardCharsets.UTF_8)).collect(java.util.stream.Collectors.joining("&"));
    }
    JsonNode search(Map<String, Object> filters) throws Exception {
        var response = admin.get(query(filters));
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return admin.json.readTree(response.body());
    }
    List<Long> ids(JsonNode response) {
        var ids = new ArrayList<Long>();for (var row : response.get("rows")) ids.add(row.get("id").asLong());return ids;
    }
    long assessment(long employee, int score) throws Exception {
        long factor = create("factors", Map.of("code", UUID.randomUUID().toString(), "title", "آزمون"));
        return create("scores", Map.of("employeeId", employee, "factorId", factor, "score", score, "assessedOn", today.toString()));
    }

    @Test void combinesEveryFilterAndUsesWeightedExperienceAndCurrentAssessmentScore() throws Exception {
        long selectedLevel = level(3);
        var body = person();body.putAll(Map.of("grade", selectedLevel, "birthDate", yearsAgo(30), "startDate", yearsAgo(10),
            "hireDate", yearsAgo(2), "gender", "FEMALE", "marineExperience", new BigDecimal("2.5"), "educationLevel", "BACHELOR", "bachelorMajor", "مهندسی دریا"));
        long matched = create("employees", body);assessment(matched, 80);
        long course = create("courses", Map.of("code", UUID.randomUUID().toString(), "title", "دوره", "durationHours", 5));
        create("trainings", Map.of("employeeId", matched, "courseId", course, "completionDate", today.toString(), "score", 80, "impactCoefficient", 20));
        body.put("personnelCode", UUID.randomUUID().toString());body.put("gender", "MALE");
        long wrongGender = create("employees", body);assessment(wrongGender, 80);
        var filters = new LinkedHashMap<String, Object>();
        filters.putAll(Map.of("organizationalLevelId", selectedLevel, "ageMin", 30, "ageMax", 30, "experienceMin", "13.75", "experienceMax", "13.75",
            "gender", "FEMALE", "marineExperienceMin", "2.5", "educationLevel", "BACHELOR", "degree", "دريا"));
        filters.put("assessmentScoreMin", "92.80");filters.put("assessmentScoreMax", "92.80");
        var result = search(filters);assertThat(ids(result)).containsExactly(matched);
        assertThat(result.get("rows").get(0).get("experienceYears").decimalValue()).isEqualByComparingTo("13.75");
        assertThat(result.get("rows").get(0).get("workExperienceYears").asInt()).isEqualTo(10);
        assertThat(result.get("asOfDate").asText()).isEqualTo(PersianDate.fromGregorian(today).toString());
        filters.put("marineExperienceMin", "2.51");assertThat(ids(search(filters))).isEmpty();
        filters.put("marineExperienceMin", "2.5");filters.put("assessmentScoreMin", "92.81");filters.remove("assessmentScoreMax");
        assertThat(ids(search(filters))).isEmpty();
    }

    @Test void levelUsesNumericValueIncludingOtherTitlesAtSameRankAndOnlyOneLowerRank() throws Exception {
        long selected = level(3), sameRank = level(3), lower = level(2), higher = level(4), tooLow = level(1), unknown = level(null);
        var people = new HashMap<Long, Long>();
        for (long grade : List.of(selected, sameRank, lower, higher, tooLow, unknown)) {
            var body = person();body.put("grade", grade);people.put(grade, create("employees", body));
        }
        assertThat(ids(search(Map.of("organizationalLevelId", selected)))).containsExactlyInAnyOrder(people.get(selected), people.get(sameRank), people.get(lower));
        assertThat(admin.get(query(Map.of("organizationalLevelId", unknown))).statusCode()).isEqualTo(400);
        long zero = level(0);var body = person();body.put("grade", zero);long zeroEmployee = create("employees", body);
        assertThat(ids(search(Map.of("organizationalLevelId", zero)))).containsExactly(zeroEmployee);
    }

    @Test void missingInformationIsNotTreatedAsZeroAndEmptyFiltersIncludeIncompleteEmployees() throws Exception {
        long missing = create("employees", person());
        var body = person();body.put("startDate", PersianDate.fromGregorian(today).toString());body.put("marineExperience", 0);
        long zero = create("employees", body);assessment(zero, 0);
        assertThat(ids(search(Map.of()))).containsExactlyInAnyOrder(missing, zero);
        assertThat(ids(search(Map.of("experienceMin", 0, "experienceMax", 0)))).containsExactly(zero);
        assertThat(ids(search(Map.of("marineExperienceMin", 0)))).containsExactly(zero);
        assertThat(ids(search(Map.of("assessmentScoreMin", 0, "assessmentScoreMax", 0)))).containsExactly(zero);
        assertThat(ids(search(Map.of("ageMin", 0)))).isEmpty();
        assertThat(admin.get("/api/employees/search").statusCode()).isEqualTo(200);
    }

    @Test void ageRangeIncludesWholeYearEndpointsAndExcludesFutureAndMissingBirthDates() throws Exception {
        var body = person();body.put("birthDate", yearsAgo(30));long thirty = create("employees", body);
        body = person();body.put("birthDate", yearsAgo(31));long thirtyOne = create("employees", body);
        body = person();body.put("birthDate", yearsAgo(29));create("employees", body);
        body = person();body.put("birthDate", PersianDate.fromGregorian(today.plusDays(1)).toString());create("employees", body);
        create("employees", person());
        assertThat(ids(search(Map.of("ageMin", 30, "ageMax", 31)))).containsExactlyInAnyOrder(thirty, thirtyOne);
    }

    @Test void educationAndDegreeMatchTheSelectedStageOrAnyRecordedStageWhenUnspecified() throws Exception {
        var body = person();body.putAll(Map.of("educationLevel", "MASTER", "bachelorMajor", "مهندسی دریا", "masterMajor", "مدیریت"));
        long master = create("employees", body);
        body = person();body.putAll(Map.of("educationLevel", "BACHELOR", "bachelorMajor", "مهندسی دریا"));long bachelor = create("employees", body);
        assertThat(ids(search(Map.of("degree", "دريا")))).containsExactlyInAnyOrder(master, bachelor);
        assertThat(ids(search(Map.of("educationLevel", "MASTER", "degree", "دریا")))).isEmpty();
        assertThat(ids(search(Map.of("educationLevel", "MASTER", "degree", "مديريت")))).containsExactly(master);
        assertThat(ids(search(Map.of("educationLevel", "BACHELOR", "degree", "دریا")))).containsExactly(bachelor);
        assertThat(ids(search(Map.of("degree", "' OR 1=1 --")))).isEmpty();
    }

    @Test void subsequentSearchReflectsSourceChangesAndDoesNotCapAssessmentScoreAt100() throws Exception {
        long employee = create("employees", person()), assessment = assessment(employee, 80);
        long course = create("courses", Map.of("code", UUID.randomUUID().toString(), "title", "دوره", "durationHours", 1));
        long training = create("trainings", Map.of("employeeId", employee, "courseId", course, "completionDate", today.toString(), "score", 100, "impactCoefficient", 100));
        assertThat(ids(search(Map.of("assessmentScoreMin", 160, "assessmentScoreMax", 160)))).containsExactly(employee);
        jdbc.update("UPDATE employee_scores SET score=60 WHERE id=?", assessment);
        assertThat(ids(search(Map.of("assessmentScoreMin", 160)))).isEmpty();
        assertThat(ids(search(Map.of("assessmentScoreMin", 120, "assessmentScoreMax", 120)))).containsExactly(employee);
        assertThat(admin.write("DELETE", "/api/trainings/" + training, null, true).statusCode()).isEqualTo(204);
        assertThat(ids(search(Map.of("assessmentScoreMin", 60, "assessmentScoreMax", 60)))).containsExactly(employee);
    }

    @Test void paginatesInStablePersonnelCodeOrderAndHandlesEmptyResults() throws Exception {
        var expected = new ArrayList<Long>();
        for (int i = 1; i <= 5; i++) {
            var body = person();body.put("personnelCode", speciality + "-" + i);expected.add(create("employees", body));
        }
        var first = search(Map.of("pageSize", 2));assertThat(first.get("total").asLong()).isEqualTo(5);
        assertThat(ids(first)).containsExactlyElementsOf(expected.subList(0, 2));
        assertThat(ids(search(Map.of("page", 2, "pageSize", 2)))).containsExactlyElementsOf(expected.subList(2, 4));
        var last = search(Map.of("page", 99, "pageSize", 2));
        assertThat(last.get("page").asInt()).isEqualTo(3);assertThat(ids(last)).containsExactly(expected.get(4));
        var empty = search(Map.of("degree", "رشته ناموجود"));assertThat(empty.get("total").asLong()).isZero();assertThat(ids(empty)).isEmpty();
    }

    @Test void rejectsInvalidRangesTypesAndReferencesBeforeReturningAnyEmployees() throws Exception {
        for (var invalid : List.<Map<String, Object>>of(
            Map.of("ageMin", -1), Map.of("ageMin", "2.5"), Map.of("ageMax", 151), Map.of("ageMin", 40, "ageMax", 30),
            Map.of("experienceMin", 20, "experienceMax", 10), Map.of("experienceMax", 301), Map.of("experienceMin", "1.234"),
            Map.of("assessmentScoreMin", -1), Map.of("assessmentScoreMin", 90, "assessmentScoreMax", 80),
            Map.of("gender", "OTHER"), Map.of("educationLevel", "UNKNOWN"), Map.of("marineExperienceMin", 101),
            Map.of("degree", "x".repeat(201)), Map.of("page", 0), Map.of("pageSize", 101),
            Map.of("specialityId", Long.MAX_VALUE), Map.of("organizationalLevelId", Long.MAX_VALUE))) {
            var response = admin.get(query(invalid));assertThat(response.statusCode()).as(invalid + " " + response.body()).isEqualTo(400);
            assertThat(response.body()).doesNotContain("personnelCode");
        }
    }
}
