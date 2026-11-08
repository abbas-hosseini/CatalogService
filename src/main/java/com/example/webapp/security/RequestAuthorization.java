package com.example.webapp.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class RequestAuthorization {
    private final AccessControl access;
    public RequestAuthorization(AccessControl access) { this.access = access; }

    public boolean allows(Authentication authentication, HttpServletRequest request) {
        if (authentication == null || !authentication.isAuthenticated() || !(authentication.getPrincipal() instanceof UserPrincipal user)) return false;
        String path = request.getServletPath(), method = request.getMethod();
        boolean read = method.equals("GET") || method.equals("HEAD");
        if ((read && Set.of("/api/account", "/api/access/me", "/api/lookups").contains(path)) ||
            (method.equals("PUT") && path.equals("/api/account")) ||
            (method.equals("POST") && Set.of("/api/account/password", "/api/auth/logout").contains(path))) return true;
        Set<String> permissions = access.permissions(user);
        if (path.equals("/access.html") || path.startsWith("/api/admin/")) return permissions.contains("security.manage");
        if (path.equals("/api/summary")) return read && permissions.contains("dashboard.view");
        if (path.equals("/templates/employees-import.xlsx")) return read && permissions.contains("employees.import");
        if (Set.of("/api/employees/import", "/api/employees/import/preview").contains(path))
            return method.equals("POST") && permissions.contains("employees.import");
        if (path.matches("/api/employees/[0-9]+/scores"))
            return method.equals("PUT") && permissions.contains("scores.view") &&
                Set.of("scores.create", "scores.edit", "scores.delete").stream().anyMatch(permissions::contains);
        String[] parts = path.split("/");
        if (parts.length < 3 || !parts[1].equals("api") || !Permissions.CATALOGS.contains(parts[2])) return false;
        String action = switch (method) {
            case "GET", "HEAD" -> parts.length == 3 ? "view" : null;
            case "POST" -> parts.length == 3 ? "create" : null;
            case "PUT" -> parts.length == 4 && parts[3].matches("[0-9]+") ? "edit" : null;
            case "DELETE" -> parts.length == 4 && parts[3].matches("[0-9]+") ? "delete" : null;
            default -> null;
        };
        return action != null && permissions.contains(parts[2] + "." + action);
    }
}
