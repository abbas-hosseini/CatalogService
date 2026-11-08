package com.example.webapp.security;

import com.example.webapp.catalog.CatalogException;
import java.util.*;

/** Stable permission identifiers shared by authorization and the role editor. */
public final class Permissions {
    private Permissions() {}
    public record Group(String resource, String title, List<String> actions) {}
    public static final List<Group> GROUPS = List.of(
        new Group("dashboard", "نمای کلی", List.of("view")),
        new Group("companies", "شرکت‌ها", List.of("view", "create", "edit", "delete")),
        new Group("factors", "عوامل ارزشیابی", List.of("view", "create", "edit", "delete")),
        new Group("jobs", "ساختار مشاغل", List.of("view", "create", "edit", "delete")),
        new Group("requirements", "شرایط احراز شغل", List.of("view", "create", "edit", "delete")),
        new Group("employees", "کارمندان", List.of("view", "create", "edit", "delete", "import")),
        new Group("positions", "سمت‌های کارمندان", List.of("view", "create", "edit", "delete")),
        new Group("courses", "دوره‌های آموزشی", List.of("view", "create", "edit", "delete")),
        new Group("trainings", "سوابق آموزشی", List.of("view", "create", "edit", "delete")),
        new Group("scores", "نمرات شایستگی", List.of("view", "create", "edit", "delete")),
        new Group("security", "کاربران و نقش‌ها", List.of("manage"))
    );
    public static final Set<String> ALL;
    public static final Set<String> CATALOGS = Set.of("companies", "factors", "jobs", "requirements", "employees", "positions", "courses", "trainings", "scores");
    static {
        var keys = new LinkedHashSet<String>();
        GROUPS.forEach(group -> group.actions().forEach(action -> keys.add(group.resource() + "." + action)));
        ALL = Collections.unmodifiableSet(keys);
    }

    public static Set<String> normalize(Set<String> selected) {
        if (selected == null || !ALL.containsAll(selected)) throw new CatalogException(400, "یکی از مجوزهای انتخاب‌شده معتبر نیست.");
        var result = new LinkedHashSet<>(selected);
        for (String resource : CATALOGS) {
            if (result.stream().anyMatch(key -> key.startsWith(resource + "."))) result.add(resource + ".view");
        }
        // These forms are accessed through their parent records, not separate menus.
        if (result.contains("requirements.view")) result.add("jobs.view");
        if (result.contains("positions.view")) result.add("employees.view");
        return result;
    }
}
