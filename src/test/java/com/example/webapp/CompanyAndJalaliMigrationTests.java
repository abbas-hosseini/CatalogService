package com.example.webapp;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.sql.DriverManager;
import java.sql.Types;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class CompanyAndJalaliMigrationTests {
    @ParameterizedTest
    @ValueSource(strings = {"", ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE"})
    void migratesOldJobsAndGregorianDatesWithoutLosingEmployeeData(String mode) throws Exception {
        String url = "jdbc:h2:mem:company-date-migration-" + UUID.randomUUID() + mode;
        try (var connection = DriverManager.getConnection(url, "sa", "");
             var statement = connection.createStatement()) {
            Flyway.configure().dataSource(url, "sa", "").target("2").load().migrate();
            statement.executeUpdate("INSERT INTO jobs (id, code, title) VALUES (1, 'J-OLD', 'Existing job')");
            String[][] dates = {
                {"2020-03-20", "1399-01-01"}, {"2021-03-20", "1399-12-30"},
                {"2024-03-20", "1403-01-01"}, {"2025-03-20", "1403-12-30"},
                {"2025-03-21", "1404-01-01"}, {"2026-03-21", "1405-01-01"},
                {"2024-09-21", "1403-06-31"}, {"2024-09-22", "1403-07-01"}
            };
            for (int i = 0; i < dates.length; i++) {
                try (var insert = connection.prepareStatement("INSERT INTO employees (personnel_code, first_name, last_name, job_id, education_level, start_date, description) VALUES (?, 'Existing', 'Employee', 1, 'BACHELOR', ?, 'Preserve note')")) {
                    insert.setString(1, "E-" + i);
                    insert.setDate(2, java.sql.Date.valueOf(dates[i][0]));
                    insert.executeUpdate();
                }
            }
            statement.executeUpdate("INSERT INTO employees (personnel_code, first_name, last_name) VALUES ('NO-JOB', 'No', 'Job')");
            Flyway.configure().dataSource(url, "sa", "").load().migrate();
            try (var rows = statement.executeQuery("SELECT personnel_code, first_name, education_level, start_date, description FROM employees WHERE personnel_code <> 'NO-JOB' ORDER BY personnel_code")) {
                assertThat(rows.getMetaData().getColumnType(4)).isEqualTo(Types.VARCHAR);
                for (String[] date : dates) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getString("first_name")).isEqualTo("Existing");
                    assertThat(rows.getString("education_level")).isEqualTo("BACHELOR");
                    assertThat(rows.getString("description")).isEqualTo("Preserve note");
                    assertThat(rows.getString("start_date")).isEqualTo(date[1]);
                }
                assertThat(rows.next()).isFalse();
            }
            try (var rows = statement.executeQuery("SELECT * FROM employees WHERE personnel_code = 'NO-JOB'")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("start_date")).isNull();
            }
            try (var rows = statement.executeQuery("SELECT employee_id, company_id, job_id FROM employee_positions ORDER BY employee_id")) {
                for (int i = 0; i < dates.length; i++) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getLong("employee_id")).isEqualTo(i + 1);
                    assertThat(rows.getObject("company_id")).isNull();
                    assertThat(rows.getLong("job_id")).isEqualTo(1);
                }
                assertThat(rows.next()).isFalse();
            }
            // A second startup does not convert an already-Jalali date a second time.
            assertThat(Flyway.configure().dataSource(url, "sa", "").load().migrate().migrationsExecuted).isZero();
            assertThat(statement.executeUpdate("UPDATE employees SET education_level = 'MASTER' WHERE personnel_code = 'E-0'")).isEqualTo(1);
        }
    }
}
