package com.example.webapp;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import java.sql.*;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class EmployeeReferenceMigrationTests {
    @Test void preservesDeduplicatesAndLinksExistingValuesIncludingZeroFractionalAndEmptyValues() throws Exception {
        String url = "jdbc:h2:mem:employee-reference-migration-" + UUID.randomUUID() + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE";
        try (var connection = DriverManager.getConnection(url, "sa", ""); var statement = connection.createStatement()) {
            Flyway.configure().dataSource(url, "sa", "").target("11").load().migrate();
            statement.executeUpdate("INSERT INTO employees(id,personnel_code,first_name,last_name,speciality,grade,description) VALUES "
                + "(1,'A','Name','One','  مدیریت دریایی  ',0,'keep'),(2,'B','Name','Two','مدیریت دریایی',0,NULL),"
                + "(3,'C','Name','Three','x',75.50,NULL),(4,'D','Name','Four','  ',NULL,NULL),(5,'E','Name','Five',NULL,100,NULL)");
            Flyway.configure().dataSource(url, "sa", "").load().migrate();
            try (var rows = statement.executeQuery("SELECT e.*, s.title AS speciality_title, l.title AS level_title, l.code AS level_code "
                    + "FROM employees e LEFT JOIN specialities s ON s.id=e.speciality_id LEFT JOIN organizational_levels l ON l.id=e.grade ORDER BY e.id")) {
                assertThat(rows.next()).isTrue();long speciality = rows.getLong("speciality_id"), level = rows.getLong("grade");
                assertThat(rows.getString("description")).isEqualTo("keep");
                assertThat(rows.getString("speciality_title")).isEqualTo("مدیریت دریایی");
                assertThat(rows.getString("level_title")).isEqualTo("سطح قبلی 0");
                assertThat(rows.next()).isTrue();assertThat(rows.getLong("speciality_id")).isEqualTo(speciality);assertThat(rows.getLong("grade")).isEqualTo(level);
                assertThat(rows.next()).isTrue();assertThat(rows.getString("level_code")).isEqualTo("LEGACY-75.5");
                assertThat(rows.next()).isTrue();assertThat(rows.getObject("speciality_id")).isNull();assertThat(rows.getObject("grade")).isNull();
                assertThat(rows.next()).isTrue();assertThat(rows.getString("level_code")).isEqualTo("LEGACY-100");
                assertThat(rows.next()).isFalse();
            }
            assertThatThrownBy(() -> statement.executeUpdate("DELETE FROM specialities")).isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> statement.executeUpdate("DELETE FROM organizational_levels")).isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> statement.executeUpdate("UPDATE employees SET grade=99999 WHERE id=1")).isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> statement.executeUpdate("UPDATE employees SET speciality_id=99999 WHERE id=1")).isInstanceOf(SQLException.class);
            assertThat(Flyway.configure().dataSource(url, "sa", "").load().migrate().migrationsExecuted).isZero();
        }
    }
}
