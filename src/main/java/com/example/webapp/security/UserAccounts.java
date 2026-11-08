package com.example.webapp.security;

import com.example.webapp.catalog.CatalogException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class UserAccounts {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final String dummyHash;
    public UserAccounts(JdbcTemplate jdbc, PasswordEncoder encoder) {
        this.jdbc = jdbc; this.encoder = encoder;
        this.dummyHash = encoder.encode(UUID.randomUUID().toString());
    }
    public record Account(long id, String username, String passwordHash, String displayName, String email, boolean enabled, long version) {}
    public record Profile(String username, String displayName, String email) {}

    public static String normalizeUsername(String username) {
        return username == null ? "" : username.strip().toLowerCase(Locale.ROOT);
    }
    public Optional<Account> find(String username) {
        return jdbc.query("SELECT * FROM app_users WHERE username = ?", (rs, n) -> new Account(rs.getLong("id"),
            rs.getString("username"), rs.getString("password_hash"), rs.getString("display_name"), rs.getString("email"),
            rs.getBoolean("enabled"), rs.getLong("credential_version")), normalizeUsername(username)).stream().findFirst();
    }
    public boolean hasUsers() { return jdbc.queryForObject("SELECT COUNT(*) FROM app_users", Long.class) > 0; }

    @Transactional
    public void createInitialUser(String username, String password) {
        username = normalizeUsername(username);
        if (!username.matches("[a-z0-9][a-z0-9._-]{2,63}")) throw new CatalogException(400, "نام کاربری اولیه باید ۳ تا ۶۴ حرف انگلیسی، عدد، نقطه، خط تیره یا زیرخط باشد.");
        PasswordPolicy.validate(password, username);
        jdbc.update("INSERT INTO app_users(username, password_hash, display_name) VALUES (?, ?, ?)", username, encoder.encode(password), "مدیر سامانه");
        jdbc.update("INSERT INTO app_user_roles(user_id, role_id) SELECT u.id, r.id FROM app_users u CROSS JOIN app_roles r WHERE u.username = ? AND r.system_code = 'ADMIN'", username);
    }

    public UserPrincipal authenticate(String username, String password) {
        var account = find(username);
        boolean matches = encoder.matches(password, account.map(Account::passwordHash).orElse(dummyHash));
        if (!matches || account.isEmpty() || !account.get().enabled()) return null;
        var user = account.get();
        return new UserPrincipal(user.id(), user.username(), user.version());
    }

    public boolean isCurrent(UserPrincipal principal) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM app_users WHERE id = ? AND enabled = TRUE AND credential_version = ?",
            Integer.class, principal.id(), principal.credentialVersion()) == 1;
    }
    public Profile profile(UserPrincipal principal) {
        var user = find(principal.username()).orElseThrow(() -> new CatalogException(401, "دوباره وارد شوید."));
        return new Profile(user.username(), user.displayName(), user.email());
    }
    @Transactional
    public Profile updateProfile(UserPrincipal principal, String displayName, String email) {
        int updated = jdbc.update("UPDATE app_users SET display_name = ?, email = ? WHERE id = ? AND credential_version = ? AND enabled = TRUE",
            displayName.strip(), email == null || email.isBlank() ? null : email.strip(), principal.id(), principal.credentialVersion());
        if (updated != 1) throw new CatalogException(401, "دوباره وارد شوید.");
        return profile(principal);
    }
    @Transactional
    public void changePassword(UserPrincipal principal, String currentPassword, String newPassword, String confirmation) {
        var account = find(principal.username()).orElseThrow(() -> new CatalogException(401, "دوباره وارد شوید."));
        if (!encoder.matches(currentPassword, account.passwordHash())) throw new CatalogException(400, "رمز فعلی صحیح نیست.");
        if (!Objects.equals(newPassword, confirmation)) throw new CatalogException(400, "رمز جدید و تکرار آن یکسان نیستند.");
        PasswordPolicy.validate(newPassword, principal.username());
        if (encoder.matches(newPassword, account.passwordHash())) throw new CatalogException(400, "رمز جدید باید با رمز فعلی متفاوت باشد.");
        int updated = jdbc.update("UPDATE app_users SET password_hash = ?, credential_version = credential_version + 1, password_changed_at = CURRENT_TIMESTAMP WHERE id = ? AND credential_version = ? AND enabled = TRUE",
            encoder.encode(newPassword), principal.id(), principal.credentialVersion());
        if (updated != 1) throw new CatalogException(409, "اطلاعات ورود تغییر کرده است؛ دوباره وارد شوید.");
    }
}
