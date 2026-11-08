package com.example.webapp.security;

import com.example.webapp.catalog.CatalogException;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;
import java.util.Map;

@RestController
public class AccountController {
    private final CaptchaService captcha;
    private final AttemptLimiter limiter;
    private final UserAccounts accounts;
    public AccountController(CaptchaService captcha, AttemptLimiter limiter, UserAccounts accounts) {
        this.captcha = captcha;this.limiter = limiter;this.accounts = accounts;
    }
    @GetMapping("/api/auth/csrf")
    public Map<String, String> csrf(CsrfToken token) { return Map.of("headerName", token.getHeaderName(), "token", token.getToken()); }

    @GetMapping(value = "/api/auth/captcha", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> captcha(HttpServletRequest request) throws IOException {
        if (!limiter.allow("captcha:" + request.getRemoteAddr(), 60, 300_000)) throw new CatalogException(429, "درخواست‌های تصویر زیاد است؛ پنج دقیقه بعد دوباره تلاش کنید.");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(captcha.issue(request.getSession(true)));
    }
    @GetMapping("/api/account")
    public UserAccounts.Profile profile(@AuthenticationPrincipal UserPrincipal user) { return accounts.profile(user); }
    public record ProfileRequest(@NotBlank @Size(max = 120) String displayName, @Email @Size(max = 254) String email) {}
    @PutMapping("/api/account")
    public UserAccounts.Profile update(@AuthenticationPrincipal UserPrincipal user, @Valid @RequestBody ProfileRequest request) {
        return accounts.updateProfile(user, request.displayName(), request.email());
    }
    public record PasswordRequest(@NotBlank @Size(max = 128) String currentPassword,
                                  @NotBlank @Size(max = 128) String newPassword, @NotBlank @Size(max = 128) String confirmation) {}
    @PostMapping("/api/account/password")
    public Map<String, String> password(@AuthenticationPrincipal UserPrincipal user, @Valid @RequestBody PasswordRequest body, HttpServletRequest request) {
        if (!limiter.allow("password:" + user.id(), 5, 600_000)) throw new CatalogException(429, "تلاش‌های تغییر رمز زیاد است؛ ده دقیقه بعد دوباره تلاش کنید.");
        accounts.changePassword(user, body.currentPassword(), body.newPassword(), body.confirmation());
        request.getSession().invalidate();SecurityContextHolder.clearContext();
        return Map.of("message", "رمز تغییر کرد. برای ادامه با رمز جدید وارد شوید.");
    }
}
