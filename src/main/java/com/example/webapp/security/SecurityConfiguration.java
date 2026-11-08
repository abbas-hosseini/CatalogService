package com.example.webapp.security;

import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.*;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.*;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import java.util.*;

@Configuration
public class SecurityConfiguration {
    @Bean PasswordEncoder passwordEncoder() {
        var pbkdf2 = new Pbkdf2PasswordEncoder("", 16, 600_000, Pbkdf2PasswordEncoder.SecretKeyFactoryAlgorithm.PBKDF2WithHmacSHA256);
        return new DelegatingPasswordEncoder("pbkdf2-sha256-600k", Map.of("pbkdf2-sha256-600k", pbkdf2));
    }
    @Bean AuthenticationProvider authenticationProvider(UserAccounts accounts) {
        return new AuthenticationProvider() {
            public Authentication authenticate(Authentication authentication) {
                String password = Objects.toString(authentication.getCredentials(), "");
                if (password.length() > 128) throw new BadCredentialsException("Invalid credentials");
                var principal = accounts.authenticate(authentication.getName(), password);
                if (principal == null) throw new BadCredentialsException("Invalid credentials");
                return UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
            }
            public boolean supports(Class<?> type) { return UsernamePasswordAuthenticationToken.class.isAssignableFrom(type); }
        };
    }
    @Bean SecurityFilterChain securityFilterChain(HttpSecurity http, AuthenticationProvider provider, UserAccounts accounts,
                                                  CaptchaService captcha, AttemptLimiter limiter, RequestAuthorization authorization) throws Exception {
        http.authenticationProvider(provider)
            .authorizeHttpRequests(auth -> auth
                .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                .requestMatchers("/login.html", "/login.js", "/auth.js", "/auth.css", "/styles.css", "/fonts/vazirmatn/**", "/favicon.ico",
                    "/api/auth/csrf", "/api/auth/captcha", "/api/auth/login").permitAll()
                .requestMatchers("/api/**", "/access.html", "/templates/employees-import.xlsx")
                    .access((authentication, context) -> new AuthorizationDecision(authorization.allows(authentication.get(), context.getRequest())))
                .anyRequest().authenticated())
            .requestCache(cache -> cache.disable())
            .formLogin(form -> form.loginPage("/login.html").loginProcessingUrl("/api/auth/login")
                .successHandler((request, response, authentication) -> response.setStatus(204))
                .failureHandler((request, response, exception) -> SecurityResponses.error(response, 401, "نام کاربری یا رمز عبور صحیح نیست.")))
            .logout(logout -> logout.logoutUrl("/api/auth/logout").deleteCookies("JSESSIONID")
                .logoutSuccessHandler((request, response, authentication) -> response.setStatus(204)))
            .exceptionHandling(errors -> errors.authenticationEntryPoint((request, response, exception) -> {
                if (request.getServletPath().startsWith("/api/")) SecurityResponses.error(response, 401, "برای ادامه وارد سامانه شوید.");
                else response.sendRedirect("/login.html");
            }).accessDeniedHandler((request, response, exception) -> SecurityResponses.error(response, 403, "مجوز انجام این عملیات را ندارید یا درخواست منقضی شده است. صفحه را تازه‌سازی کنید؛ در صورت تداوم، با مدیر سامانه تماس بگیرید.")))
            .headers(headers -> headers
                .frameOptions(frame -> frame.deny())
                .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' blob:; connect-src 'self'; font-src 'self'; object-src 'none'; base-uri 'self'; frame-ancestors 'none'; form-action 'self'"))
                .referrerPolicy(referrer -> referrer.policy(org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.SAME_ORIGIN)))
            .addFilterAfter(new SessionVersionFilter(accounts), SecurityContextHolderFilter.class)
            .addFilterBefore(new LoginGuardFilter(captcha, limiter), UsernamePasswordAuthenticationFilter.class);
        // Keep Spring Security's session-backed CSRF tokens and session-fixation protection enabled.
        return http.build();
    }
}
