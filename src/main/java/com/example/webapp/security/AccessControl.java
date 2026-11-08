package com.example.webapp.security;

import com.example.webapp.catalog.CatalogException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class AccessControl {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    public AccessControl(JdbcTemplate jdbc, PasswordEncoder encoder) { this.jdbc = jdbc; this.encoder = encoder; }

    public record Role(long id, String name, String description, boolean builtIn, Set<String> permissions, long userCount) {}
    public record User(long id, String username, String displayName, String email, boolean enabled, List<Long> roleIds) {}
    public record MyAccess(long userId, List<RoleName> roles, Set<String> permissions) {}
    public record RoleName(long id, String name) {}

    public UserPrincipal currentUser() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal user))
            throw new AccessDeniedException("Authentication required");
        return user;
    }
    public Set<String> permissions(UserPrincipal user) {
        var result = new LinkedHashSet<String>();
        jdbc.query("""
            SELECT r.system_code, p.permission FROM app_users u
            JOIN app_user_roles ur ON ur.user_id = u.id
            JOIN app_roles r ON r.id = ur.role_id
            LEFT JOIN app_role_permissions p ON p.role_id = r.id
            WHERE u.id = ? AND u.enabled = TRUE AND u.credential_version = ?
            """, (org.springframework.jdbc.core.RowCallbackHandler) rs -> {
                if ("ADMIN".equals(rs.getString("system_code"))) result.addAll(Permissions.ALL);
                String permission = rs.getString("permission");
                if (Permissions.ALL.contains(permission)) result.add(permission);
            }, user.id(), user.credentialVersion());
        return result;
    }
    public Set<String> currentPermissions() { return permissions(currentUser()); }
    public void require(String permission) {
        if (!currentPermissions().contains(permission)) throw new AccessDeniedException("Permission required: " + permission);
    }
    public MyAccess mine() {
        var user = currentUser();
        var roles = jdbc.query("SELECT r.id, r.name FROM app_roles r JOIN app_user_roles ur ON r.id = ur.role_id WHERE ur.user_id = ? ORDER BY r.id",
            (rs, n) -> new RoleName(rs.getLong("id"), rs.getString("name")), user.id());
        return new MyAccess(user.id(), roles, permissions(user));
    }
    public List<Role> roles() {
        return jdbc.query("SELECT r.*, (SELECT COUNT(*) FROM app_user_roles ur WHERE ur.role_id = r.id) AS user_count FROM app_roles r ORDER BY r.id",
            (rs, n) -> new Role(rs.getLong("id"), rs.getString("name"), rs.getString("description"),
                "ADMIN".equals(rs.getString("system_code")), "ADMIN".equals(rs.getString("system_code")) ? Permissions.ALL :
                    new LinkedHashSet<>(jdbc.queryForList("SELECT permission FROM app_role_permissions WHERE role_id = ? ORDER BY permission", String.class, rs.getLong("id"))),
                rs.getLong("user_count")));
    }
    public List<User> users() {
        return jdbc.query("SELECT id, username, display_name, email, enabled FROM app_users ORDER BY id",
            (rs, n) -> new User(rs.getLong("id"), rs.getString("username"), rs.getString("display_name"), rs.getString("email"), rs.getBoolean("enabled"),
                jdbc.queryForList("SELECT role_id FROM app_user_roles WHERE user_id = ? ORDER BY role_id", Long.class, rs.getLong("id"))));
    }

    @Transactional
    public long saveRole(Long id, String name, String description, Set<String> selected) {
        lockManagement();
        var permissions = Permissions.normalize(selected);
        name = name.strip();
        if (name.isEmpty()) throw new CatalogException(400, "نام نقش را وارد کنید.");
        if (id != null) requireEditableRole(id);
        if (jdbc.queryForObject("SELECT COUNT(*) FROM app_roles WHERE name = ? AND id <> ?", Integer.class, name, id == null ? -1 : id) > 0)
            throw new CatalogException(409, "نقشی با این نام قبلاً ثبت شده است.");
        if (id == null) {
            jdbc.update("INSERT INTO app_roles(name, description) VALUES (?, ?)", name, clean(description));
            id = jdbc.queryForObject("SELECT id FROM app_roles WHERE name = ?", Long.class, name);
        } else {
            jdbc.update("UPDATE app_roles SET name = ?, description = ? WHERE id = ?", name, clean(description), id);
            jdbc.update("DELETE FROM app_role_permissions WHERE role_id = ?", id);
        }
        for (String permission : permissions) jdbc.update("INSERT INTO app_role_permissions(role_id, permission) VALUES (?, ?)", id, permission);
        return id;
    }
    @Transactional
    public void deleteRole(long id) {
        lockManagement();requireEditableRole(id);
        if (jdbc.queryForObject("SELECT COUNT(*) FROM app_user_roles WHERE role_id = ?", Integer.class, id) > 0)
            throw new CatalogException(409, "این نقش به کاربر اختصاص دارد؛ ابتدا نقش کاربران مرتبط را تغییر دهید.");
        jdbc.update("DELETE FROM app_roles WHERE id = ?", id);
    }
    @Transactional
    public long createUser(String username, String displayName, String email, String password, String confirmation, boolean enabled, Set<Long> roles) {
        lockManagement();validateRoles(roles);
        username = UserAccounts.normalizeUsername(username);
        if (!username.matches("[a-z0-9][a-z0-9._-]{2,63}")) throw new CatalogException(400, "نام کاربری باید ۳ تا ۶۴ حرف انگلیسی، عدد، نقطه، خط تیره یا زیرخط باشد.");
        if (!Objects.equals(password, confirmation)) throw new CatalogException(400, "رمز عبور و تکرار آن یکسان نیستند.");
        PasswordPolicy.validate(password, username);
        if (jdbc.queryForObject("SELECT COUNT(*) FROM app_users WHERE username = ?", Integer.class, username) > 0)
            throw new CatalogException(409, "این نام کاربری قبلاً ثبت شده است.");
        jdbc.update("INSERT INTO app_users(username, display_name, email, password_hash, enabled) VALUES (?, ?, ?, ?, ?)",
            username, displayName.strip(), clean(email), encoder.encode(password), enabled);
        long id = jdbc.queryForObject("SELECT id FROM app_users WHERE username = ?", Long.class, username);
        assignRoles(id, roles);
        return id;
    }
    @Transactional
    public void updateUser(long id, String displayName, String email, boolean enabled, Set<Long> roles) {
        lockManagement();validateRoles(roles);
        // Incrementing the version on a status change keeps old sessions invalid even after re-enabling.
        int updated = jdbc.update("""
            UPDATE app_users SET display_name = ?, email = ?,
            credential_version = credential_version + CASE WHEN enabled <> ? THEN 1 ELSE 0 END,
            enabled = ? WHERE id = ?
            """, displayName.strip(), clean(email), enabled, enabled, id);
        if (updated == 0) throw new CatalogException(404, "کاربر موردنظر پیدا نشد.");
        assignRoles(id, roles);
        if (jdbc.queryForObject("""
            SELECT COUNT(*) FROM app_users u JOIN app_user_roles ur ON ur.user_id = u.id
            JOIN app_roles r ON r.id = ur.role_id WHERE u.enabled = TRUE AND r.system_code = 'ADMIN'
            """, Integer.class) == 0)
            throw new CatalogException(409, "حداقل یک مدیر سامانهٔ فعال باید باقی بماند. ابتدا این نقش را به کاربر دیگری بدهید.");
    }
    private void lockManagement() {
        jdbc.queryForObject("SELECT id FROM app_access_lock WHERE id = 1 FOR UPDATE", Integer.class);
        // Recheck after the lock so concurrent revocation cannot grant stale administrative rights.
        require("security.manage");
    }
    private void requireEditableRole(long id) {
        var values = jdbc.query("SELECT system_code FROM app_roles WHERE id = ?", (rs, n) -> Objects.toString(rs.getString(1), ""), id);
        if (values.isEmpty()) throw new CatalogException(404, "نقش موردنظر پیدا نشد.");
        if (!values.getFirst().isEmpty()) throw new CatalogException(409, "نقش مدیر سامانه محافظت‌شده است و قابل تغییر یا حذف نیست.");
    }
    private void validateRoles(Set<Long> roles) {
        for (Long role : roles) if (role == null || jdbc.queryForObject("SELECT COUNT(*) FROM app_roles WHERE id = ?", Integer.class, role) != 1)
            throw new CatalogException(400, "یکی از نقش‌های انتخاب‌شده وجود ندارد؛ فهرست را تازه‌سازی کنید.");
    }
    private void assignRoles(long id, Set<Long> roles) {
        jdbc.update("DELETE FROM app_user_roles WHERE user_id = ?", id);
        for (Long role : roles) jdbc.update("INSERT INTO app_user_roles(user_id, role_id) VALUES (?, ?)", id, role);
    }
    private static String clean(String value) { return value == null || value.isBlank() ? null : value.strip(); }
}
