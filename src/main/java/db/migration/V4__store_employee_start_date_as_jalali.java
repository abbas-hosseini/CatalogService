package db.migration;

import com.github.mfathi91.time.PersianDate;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import java.util.ArrayList;
import java.util.Locale;

public class V4__store_employee_start_date_as_jalali extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        record ConvertedDate(long id, String date) {}
        var converted = new ArrayList<ConvertedDate>();
        var connection = context.getConnection();
        // Convert and validate everything before changing the schema, also on H2 (non-transactional DDL).
        try (var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT id, start_date FROM employees WHERE start_date IS NOT NULL")) {
            while (rows.next()) {
                var date = PersianDate.fromGregorian(rows.getDate("start_date").toLocalDate());
                converted.add(new ConvertedDate(rows.getLong("id"), String.format(Locale.ROOT,
                    "%04d-%02d-%02d", date.getYear(), date.getMonthValue(), date.getDayOfMonth())));
            }
        }
        try (var statement = connection.createStatement()) {
            statement.execute("ALTER TABLE employees ADD COLUMN start_date_jalali VARCHAR(10)");
        }
        try (var statement = connection.prepareStatement("UPDATE employees SET start_date_jalali = ? WHERE id = ?")) {
            for (var row : converted) {
                statement.setString(1, row.date());
                statement.setLong(2, row.id());
                statement.addBatch();
            }
            statement.executeBatch();
        }
        try (var statement = connection.createStatement()) {
            statement.execute("ALTER TABLE employees DROP COLUMN start_date");
            statement.execute("ALTER TABLE employees RENAME COLUMN start_date_jalali TO start_date");
        }
    }
}
