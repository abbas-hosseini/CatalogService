package com.example.webapp.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

final class LoginGuardFilter extends OncePerRequestFilter {
    private final CaptchaService captcha;
    private final AttemptLimiter limiter;
    LoginGuardFilter(CaptchaService captcha, AttemptLimiter limiter) { this.captcha = captcha;this.limiter = limiter; }
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getServletPath().equals("/api/auth/login") || !request.getMethod().equals("POST");
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        boolean validCaptcha = captcha.consume(request.getSession(false), request.getParameter("captcha"));
        String username = UserAccounts.normalizeUsername(request.getParameter("username"));
        if (!limiter.allow("login-ip:" + request.getRemoteAddr(), 30, 300_000)) {
            response.setHeader("Retry-After", "300");SecurityResponses.error(response, 429, "تلاش‌های ورود زیاد است؛ پنج دقیقه بعد دوباره تلاش کنید.");return;
        }
        if (!validCaptcha) { SecurityResponses.error(response, 400, "کد امنیتی اشتباه یا منقضی است؛ تصویر جدید را وارد کنید.");return; }
        if (username.length() > 64 || !limiter.allow("login-user:" + username, 10, 600_000)) {
            response.setHeader("Retry-After", "600");SecurityResponses.error(response, 429, "تلاش‌های ورود زیاد است؛ ده دقیقه بعد دوباره تلاش کنید.");return;
        }
        String password = request.getParameter("password");
        if (password == null || password.length() > 128) { SecurityResponses.error(response, 401, "نام کاربری یا رمز عبور صحیح نیست.");return; }
        chain.doFilter(request, response);
    }
}
