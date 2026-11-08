package com.example.webapp;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class JobCompanyMigrationTests {
    @ParameterizedTest
    @ValueSource(strings = {"", ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE"})
    void preservesLegacyHierarchyRequirementsAndMultiCompanyPositions(String mode) throws Exception {
        String url = "jdbc:h2:mem:job-company-" + UUID.randomUUID() + mode;
        try (var connection = DriverManager.getConnection(url, "sa", "");
             var statement = connection.createStatement()) {
            Flyway.configure().dataSource(url, "sa", "").target("5").load().migrate();
            statement.executeUpdate("INSERT INTO companies (code, title) VALUES ('CO1', 'First'), ('CO2', 'Second')");
            statement.executeUpdate("INSERT INTO jobs (code, title, description) VALUES ('ROOT', 'Root', 'Keep root')");
            statement.executeUpdate("INSERT INTO jobs (code, title, parent_id) VALUES ('CHILD', 'Child', 1)");
            statement.executeUpdate("INSERT INTO employees (personnel_code, first_name, last_name) VALUES ('E', 'Test', 'Employee')");
            statement.executeUpdate("INSERT INTO evaluation_factors (code, title) VALUES ('F', 'Factor')");
            statement.executeUpdate("INSERT INTO job_requirements (job_id, factor_id, minimum_score) VALUES (2, 1, 70)");
            statement.executeUpdate("INSERT INTO employee_positions (employee_id, company_id, job_id, notes) VALUES (1, 1, 2, 'Keep first'), (1, 2, 2, 'Keep second')");
            var flyway = Flyway.configure().dataSource(url, "sa", "").load();
            assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
            try (var rows = statement.executeQuery("SELECT id, parent_id, company_id FROM jobs ORDER BY id")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getObject("company_id")).isNull();
                assertThat(rows.next()).isTrue();
                assertThat(rows.getLong("parent_id")).isEqualTo(1);
                assertThat(rows.getObject("company_id")).isNull();
            }
            try (var rows = statement.executeQuery("SELECT notes FROM employee_positions ORDER BY id")) {
                assertThat(rows.next()).isTrue();assertThat(rows.getString(1)).isEqualTo("Keep first");
                assertThat(rows.next()).isTrue();assertThat(rows.getString(1)).isEqualTo("Keep second");
                assertThat(rows.next()).isFalse();
            }
            try (var rows = statement.executeQuery("SELECT minimum_score FROM job_requirements WHERE job_id = 2")) {
                assertThat(rows.next()).isTrue();assertThat(rows.getInt(1)).isEqualTo(70);
            }
            statement.executeUpdate("UPDATE jobs SET company_id = 1 WHERE id = 1");
            assertThatThrownBy(() -> statement.executeUpdate("UPDATE jobs SET company_id = 2 WHERE id = 2"))
                .isInstanceOf(SQLException.class);
            statement.executeUpdate("UPDATE jobs SET company_id = 1 WHERE id = 2");
            assertThatThrownBy(() -> statement.executeUpdate("DELETE FROM companies WHERE id = 1"))
                .isInstanceOf(SQLException.class);
            assertThat(flyway.migrate().migrationsExecuted).isZero();
        }
    }
}
