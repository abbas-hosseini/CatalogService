package com.example.webapp.security;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

final class SecurityResponses {
    private SecurityResponses() {}
    static void error(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        // Only fixed application messages are passed here.
        response.getWriter().write("{\"message\":\"" + message + "\"}");
    }
}
