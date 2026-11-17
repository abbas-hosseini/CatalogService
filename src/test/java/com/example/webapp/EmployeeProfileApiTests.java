package com.example.webapp;

import com.example.webapp.security.SecurityTestClient;
import com.example.webapp.security.SecurityTestConfiguration;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "spring.datasource.url=jdbc:h2:mem:employee-profile-tests;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
@Import(SecurityTestConfiguration.class)
class EmployeeProfileApiTests {
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    SecurityTestClient client;
    @BeforeEach void prepare() throws Exception { client = SecurityTestClient.shared(port); }

    Map<String, Object> employee() {
        var body = new LinkedHashMap<String, Object>();
        body.put("personnelCode", UUID.randomUUID().toString());body.put("firstName", "آزمایش");body.put("lastName", "کارمند");
        return body;
    }
    long create(Map<String, Object> body) throws Exception {
        var response = client.write("POST", "/api/employees", body, true);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
        return client.json.readTree(response.body()).get("id").asLong();
    }
    JsonNode read(long id) throws Exception {
        var response = client.get("/api/employees");assertThat(response.statusCode()).isEqualTo(200);
        for (var row : client.json.readTree(response.body())) if (row.get("id").asLong() == id) return row;
        throw new AssertionError("Employee not returned");
    }
    @Test void allProfileFieldsRoundTripNormalizeDatesAndCanBeEditedAndCleared() throws Exception {
        var body = employee();
        body.putAll(Map.of("educationLevel","DOCTORATE","startDate","۱۴۰۳/۰۱/۰۱","birthDate","١٣٧٠/١/٢","hireDate","۱۳۹۹/۱۲/۳۰",
            "marineExperience",12.5,"gender","FEMALE","speciality","  مدیریت دریایی  ","grade",0,"lastMarineRank","NAKHDA_BANI"));
        body.putAll(Map.of("bachelorMajor","مهندسی","bachelorUniversity","دانشگاه اول","masterMajor","مدیریت","masterUniversity","دانشگاه دوم",
            "doctoralMajor","حمل و نقل","doctoralUniversity","دانشگاه سوم","description","توضیحات کامل"));
        long id = create(body);
        var row = read(id);
        assertThat(row.get("birthDate").asText()).isEqualTo("1370-01-02");
        assertThat(row.get("hireDate").asText()).isEqualTo("1399-12-30");
        assertThat(row.get("startDate").asText()).isEqualTo("1403-01-01");
        assertThat(jdbc.queryForObject("SELECT hire_date FROM employees WHERE id = ?", java.sql.Date.class, id).toLocalDate().toString()).isEqualTo("2021-03-20");
        assertThat(row.get("marineExperience").asDouble()).isEqualTo(12.5);
        assertThat(row.get("grade").asDouble()).isZero();
        assertThat(row.get("speciality").asText()).isEqualTo("مدیریت دریایی");
        for (String key : List.of("gender","lastMarineRank","bachelorMajor","bachelorUniversity","masterMajor","masterUniversity","doctoralMajor","doctoralUniversity","description"))
            assertThat(row.get(key).asText()).isEqualTo(body.get(key));
        body.put("birthDate","۱۳۷۱/۰۶/۳۱");body.put("hireDate","۱۴۰۰/۱/۱");body.put("marineExperience",0);body.put("grade",99.75);
        assertThat(client.write("PUT","/api/employees/"+id,body,true).statusCode()).isEqualTo(200);
        row = read(id);assertThat(row.get("birthDate").asText()).isEqualTo("1371-06-31");
        assertThat(row.get("hireDate").asText()).isEqualTo("1400-01-01");assertThat(row.get("marineExperience").asDouble()).isZero();
        assertThat(row.get("grade").asDouble()).isEqualTo(99.75);
        for (String key : new ArrayList<>(body.keySet())) if (!Set.of("personnelCode","firstName","lastName").contains(key)) body.put(key,null);
        assertThat(client.write("PUT","/api/employees/"+id,body,true).statusCode()).isEqualTo(200);
        row = read(id);
        for (String key : body.keySet()) if (body.get(key) == null) assertThat(row.get(key).isNull()).as(key).isTrue();
    }
    @ParameterizedTest
    @CsvSource({"birthDate,1404-12-30", "hireDate,1404-07-31", "marineExperience,-0.01", "marineExperience,100.01", "grade,1.234", "grade,101", "gender,OTHER", "lastMarineRank,UNKNOWN"})
    void rejectsInvalidNewFieldsWithoutInserting(String field, String value) throws Exception {
        var body = employee();body.put(field,value);
        var response = client.write("POST","/api/employees",body,true);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(400);
        assertThat(client.json.readTree(response.body()).get("fields").has(field)).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM employees WHERE personnel_code = ?",Integer.class,body.get("personnelCode"))).isZero();
    }
    @Test void optionalFieldsAllowLegacyFormsAndTextLengthIsEnforced() throws Exception {
        var body = employee();long id = create(body);
        assertThat(read(id).get("birthDate").isNull()).isTrue();
        body.put("masterUniversity","x".repeat(201));
        var response = client.write("PUT","/api/employees/"+id,body,true);
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(client.json.readTree(response.body()).get("fields").has("masterUniversity")).isTrue();
        assertThat(read(id).get("masterUniversity").isNull()).isTrue();
    }
}
