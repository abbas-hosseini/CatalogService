package com.example.webapp.catalog;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

@Repository
public class CatalogRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public CatalogRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<Map<String, Object>> list(Catalog catalog) {
        return jdbc.query("SELECT * FROM " + catalog.table + " ORDER BY id DESC", this::mapRow);
    }

    public List<Map<String, Object>> employeeScores(long employeeId) {
        return jdbc.query("SELECT * FROM employee_scores WHERE employee_id = :employeeId ORDER BY id DESC",
            Map.of("employeeId", employeeId), this::mapRow);
    }

    private Map<String, Object> mapRow(ResultSet rs, int rowNum) throws SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        var meta = rs.getMetaData();
        for (int i = 1; i <= meta.getColumnCount(); i++) {
            String column = meta.getColumnName(i).toLowerCase(Locale.ROOT);
            StringBuilder key = new StringBuilder();
            boolean uppercase = false;
            for (char c : column.toCharArray()) {
                if (c == '_') { uppercase = true; }
                else { key.append(uppercase ? Character.toUpperCase(c) : c); uppercase = false; }
            }
            Object value = rs.getObject(i);
            row.put(key.toString(), value instanceof Date date ? date.toLocalDate().toString() : value);
        }
        return row;
    }

    public long count(Catalog catalog) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + catalog.table, Map.of(), Long.class);
    }

    public boolean exists(Catalog catalog, Long id) {
        return id != null && jdbc.queryForObject("SELECT COUNT(*) FROM " + catalog.table + " WHERE id = :id",
            Map.of("id", id), Integer.class) > 0;
    }

    public Set<String> existingPersonnelCodes(Collection<String> codes) {
        if (codes.isEmpty()) return Set.of();
        return new HashSet<>(jdbc.queryForList("SELECT personnel_code FROM employees WHERE personnel_code IN (:codes)",
            Map.of("codes", codes), String.class));
    }

    public Long parent(Catalog catalog, long id) {
        return jdbc.queryForObject("SELECT parent_id FROM " + catalog.table + " WHERE id = :id", Map.of("id", id), Long.class);
    }

    public Long jobCompany(long id) {
        return jdbc.queryForObject("SELECT company_id FROM jobs WHERE id = :id", Map.of("id", id), Long.class);
    }

    public boolean hasJobChildrenInOtherCompanies(long jobId, long companyId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM jobs WHERE parent_id = :jobId AND company_id <> :companyId",
            Map.of("jobId", jobId, "companyId", companyId), Integer.class) > 0;
    }

    public boolean hasJobPositionsInOtherCompanies(long jobId, long companyId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM employee_positions WHERE job_id = :jobId AND company_id <> :companyId",
            Map.of("jobId", jobId, "companyId", companyId), Integer.class) > 0;
    }

    // Column names are supplied only by the service, never by request parameters.
    public long save(Catalog catalog, Long id, Map<String, Object> fields) {
        var params = new MapSqlParameterSource(fields);
        if (id != null) {
            String assignments = fields.keySet().stream().map(k -> k + " = :" + k).collect(Collectors.joining(", "));
            int updated = jdbc.update("UPDATE " + catalog.table + " SET " + assignments + " WHERE id = :id", params.addValue("id", id));
            if (updated == 0) throw new CatalogException(404, "رکورد موردنظر پیدا نشد.");
            return id;
        }
        String columns = String.join(", ", fields.keySet());
        String values = fields.keySet().stream().map(k -> ":" + k).collect(Collectors.joining(", "));
        var keys = new GeneratedKeyHolder();
        jdbc.update("INSERT INTO " + catalog.table + " (" + columns + ") VALUES (" + values + ")", params, keys, new String[]{"id"});
        return Objects.requireNonNull(keys.getKey()).longValue();
    }

    public void delete(Catalog catalog, long id) {
        if (jdbc.update("DELETE FROM " + catalog.table + " WHERE id = :id", Map.of("id", id)) == 0)
            throw new CatalogException(404, "رکورد موردنظر پیدا نشد.");
    }

    public void lockTree(Catalog catalog) {
        // Serialize tree mutations, including simultaneous moves of two existing nodes.
        jdbc.query("SELECT id FROM " + catalog.table + " ORDER BY id FOR UPDATE", (rs, n) -> rs.getLong(1));
    }
}
