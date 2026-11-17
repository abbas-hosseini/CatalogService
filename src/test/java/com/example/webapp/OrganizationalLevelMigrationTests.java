package com.example.webapp;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class OrganizationalLevelMigrationTests {
    @Test void addsNullableLevelPreservingExistingRowsAndEmployeeLinksAndEnforcesNonnegativeValues() throws Exception {
        String url = "jdbc:h2:mem:organizational-level-migration-" + UUID.randomUUID() + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE";
        try (var connection = DriverManager.getConnection(url, "sa", ""); var statement = connection.createStatement()) {
            Flyway.configure().dataSource(url, "sa", "").target("12").load().migrate();
            statement.executeUpdate("INSERT INTO organizational_levels(id,code,title,description) VALUES (100,'L-1','کارشناس','keep')");
            statement.executeUpdate("INSERT INTO employees(personnel_code,first_name,last_name,grade) VALUES ('E-1','نام','آزمایش',100)");
            Flyway.configure().dataSource(url, "sa", "").load().migrate();
            try (var rows = statement.executeQuery("SELECT l.level,l.code,l.title,l.description,e.grade FROM organizational_levels l JOIN employees e ON e.grade=l.id")) {
                assertThat(rows.next()).isTrue();assertThat(rows.getObject("level")).isNull();
                assertThat(rows.getString("code")).isEqualTo("L-1");assertThat(rows.getString("title")).isEqualTo("کارشناس");
                assertThat(rows.getString("description")).isEqualTo("keep");assertThat(rows.getLong("grade")).isEqualTo(100);
                assertThat(rows.next()).isFalse();
            }
            assertThat(statement.executeUpdate("UPDATE organizational_levels SET level=0 WHERE id=100")).isEqualTo(1);
            assertThatThrownBy(() -> statement.executeUpdate("UPDATE organizational_levels SET level=-1 WHERE id=100")).isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> statement.executeUpdate("DELETE FROM organizational_levels WHERE id=100")).isInstanceOf(SQLException.class);
            assertThat(Flyway.configure().dataSource(url, "sa", "").load().migrate().migrationsExecuted).isZero();
        }
    }
}
