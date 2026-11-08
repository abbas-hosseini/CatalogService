package com.example.webapp.catalog;

import java.util.Arrays;

/** The only permitted table names for catalog queries. */
public enum Catalog {
    FACTORS("factors", "evaluation_factors"),
    JOBS("jobs", "jobs"),
    COMPANIES("companies", "companies"),
    EMPLOYEES("employees", "employees"),
    POSITIONS("positions", "employee_positions"),
    COURSES("courses", "courses"),
    TRAININGS("trainings", "employee_trainings"),
    SCORES("scores", "employee_scores"),
    REQUIREMENTS("requirements", "job_requirements");

    final String path;
    final String table;

    Catalog(String path, String table) {
        this.path = path;
        this.table = table;
    }

    public static Catalog fromPath(String path) {
        return Arrays.stream(values()).filter(c -> c.path.equals(path)).findFirst()
            .orElseThrow(() -> new CatalogException(404, "بخش موردنظر پیدا نشد."));
    }
}
