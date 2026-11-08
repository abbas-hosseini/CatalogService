package com.example.webapp.catalog;

import com.example.webapp.security.AccessControl;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.util.*;

/** Minimal reference labels for permitted forms; never returns full employee records. */
@RestController
public class LookupController {
    private final JdbcTemplate jdbc;
    private final AccessControl access;
    public LookupController(JdbcTemplate jdbc, AccessControl access) { this.jdbc = jdbc; this.access = access; }
    private static final Map<String, List<String>> DEPENDENCIES = Map.of(
        "jobs", List.of("companies"), "positions", List.of("employees", "companies", "jobs"),
        "requirements", List.of("jobs", "companies", "factors"), "trainings", List.of("employees", "courses"),
        "scores", List.of("employees", "factors"));
    private static final Map<String, String> QUERIES = Map.of(
        "companies", "SELECT id, code, title FROM companies ORDER BY id",
        "jobs", "SELECT id, code, title, company_id, parent_id FROM jobs ORDER BY id",
        "employees", "SELECT id, personnel_code, first_name, last_name FROM employees ORDER BY id",
        "factors", "SELECT id, code, title, parent_id FROM evaluation_factors ORDER BY id",
        "courses", "SELECT id, code, title FROM courses ORDER BY id");

    @GetMapping("/api/lookups")
    public Map<String, List<Map<String, Object>>> lookups() {
        var permissions = access.currentPermissions();
        var needed = new TreeSet<String>();
        DEPENDENCIES.forEach((resource, refs) -> { if (permissions.contains(resource + ".view")) needed.addAll(refs); });
        var result = new LinkedHashMap<String, List<Map<String, Object>>>();
        for (String resource : needed) result.put(resource, jdbc.query(QUERIES.get(resource), (rs, n) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            var meta = rs.getMetaData();
            for (int i = 1; i <= meta.getColumnCount(); i++) {
                String[] words = meta.getColumnName(i).toLowerCase(Locale.ROOT).split("_");
                StringBuilder key = new StringBuilder(words[0]);
                for (int j = 1; j < words.length; j++) key.append(Character.toUpperCase(words[j].charAt(0))).append(words[j].substring(1));
                row.put(key.toString(), rs.getObject(i));
            }
            return row;
        }));
        return result;
    }
}
