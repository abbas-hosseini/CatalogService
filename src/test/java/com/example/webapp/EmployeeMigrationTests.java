package com.example.webapp;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmployeeMigrationTests {
    @Test
    void upgradesExistingEmployeesWithoutLosingDataAndRestrictsEducationValues() throws Exception {
        String url = "jdbc:h2:mem:migration-" + UUID.randomUUID();
        try (var connection = DriverManager.getConnection(url, "sa", "");
             var statement = connection.createStatement()) {
            Flyway.configure().dataSource(url, "sa", "").target("1").load().migrate();
            statement.executeUpdate("INSERT INTO jobs (id, code, title) VALUES (1, 'J-OLD', 'Existing job')");
            statement.executeUpdate("INSERT INTO employees (id, personnel_code, first_name, last_name, job_id, description) "
                + "VALUES (1, 'E-OLD', 'Existing', 'Employee', 1, 'Preserve this note')");

            Flyway.configure().dataSource(url, "sa", "").target("2").load().migrate();
            try (var row = statement.executeQuery("SELECT * FROM employees WHERE id = 1")) {
                assertThat(row.next()).isTrue();
                assertThat(row.getString("personnel_code")).isEqualTo("E-OLD");
                assertThat(row.getString("first_name")).isEqualTo("Existing");
                assertThat(row.getString("last_name")).isEqualTo("Employee");
                assertThat(row.getLong("job_id")).isEqualTo(1);
                assertThat(row.getString("description")).isEqualTo("Preserve this note");
                assertThat(row.getString("education_level")).isNull();
                assertThat(row.getDate("start_date")).isNull();
            }
            assertThat(statement.executeUpdate("UPDATE employees SET education_level = 'BACHELOR', "
                + "start_date = DATE '2020-03-20' WHERE id = 1")).isEqualTo(1);
            assertThatThrownBy(() -> statement.executeUpdate("UPDATE employees SET education_level = 'UNKNOWN' WHERE id = 1"))
                .isInstanceOfSatisfying(SQLException.class, error -> assertThat(error.getSQLState()).isEqualTo("23513"));
        }
    }
}
