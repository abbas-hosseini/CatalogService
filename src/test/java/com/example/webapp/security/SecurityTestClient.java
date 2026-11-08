package com.example.webapp.security;

import tools.jackson.databind.json.JsonMapper;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class SecurityTestClient {
    public static final String PASSWORD = "Forest!Quartz2026Bird";
    private static final Map<Integer, SecurityTestClient> CLIENTS = new HashMap<>();
    public final HttpClient http = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
    public final JsonMapper json = JsonMapper.builder().build();
    public final int port;
    public String csrf;
    public SecurityTestClient(int port) { this.port = port; }
    public static synchronized SecurityTestClient shared(int port) throws Exception {
        var client = CLIENTS.get(port);
        if (client == null) {
            client = new SecurityTestClient(port);
            if (client.login("testuser", PASSWORD).statusCode() != 204) throw new IllegalStateException("Test login failed");
            client.refreshCsrf();CLIENTS.put(port, client);
        }
        return client;
    }
    public URI uri(String path) { return URI.create("http://127.0.0.1:" + port + path); }
    public void refreshCsrf() throws Exception { csrf = json.readTree(get("/api/auth/csrf").body()).get("token").asText(); }
    public HttpResponse<String> get(String path) throws Exception {
        return http.send(HttpRequest.newBuilder(uri(path)).GET().build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }
    public HttpResponse<String> login(String username, String password) throws Exception {
        refreshCsrf();get("/api/auth/captcha");return loginForm(username, password, "AAAAA");
    }
    public HttpResponse<String> loginForm(String username, String password, String captcha) throws Exception {
        var form = "username="+encode(username)+"&password="+encode(password)+"&captcha="+encode(captcha);
        return http.send(HttpRequest.newBuilder(uri("/api/auth/login")).header("X-CSRF-TOKEN",csrf)
            .header("Content-Type","application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(form)).build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }
    public HttpResponse<String> write(String method, String path, Object body, boolean withCsrf) throws Exception {
        var builder = HttpRequest.newBuilder(uri(path)).header("Content-Type", "application/json");
        if (withCsrf) { refreshCsrf();builder.header("X-CSRF-TOKEN", csrf); }
        return http.send(builder.method(method,body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }
    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
}
