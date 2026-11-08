package com.example.webapp.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

final class SessionVersionFilter extends OncePerRequestFilter {
    private final UserAccounts accounts;
    SessionVersionFilter(UserAccounts accounts) { this.accounts = accounts; }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal principal && !accounts.isCurrent(principal)) {
            var session = request.getSession(false);if (session != null) session.invalidate();
            SecurityContextHolder.clearContext();
            if (request.getServletPath().startsWith("/api/")) SecurityResponses.error(response, 401, "نشست منقضی شده است؛ دوباره وارد شوید.");
            else response.sendRedirect("/login.html");
            return;
        }
        chain.doFilter(request, response);
    }
}
