package com.example.webapp.security;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.net.http.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "spring.datasource.url=jdbc:h2:mem:security-tests;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
@Import(SecurityTestConfiguration.class)
class SecurityApiTests {
    @LocalServerPort int port;
    @Autowired UserAccounts accounts;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    String username;
    SecurityTestClient client;
    static final String NEW_PASSWORD = "River!Cobalt2026Moon";

    @BeforeEach void prepare() {
        username = "user" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        accounts.createInitialUser(username, SecurityTestClient.PASSWORD);
        client = new SecurityTestClient(port);
    }
    void login() throws Exception { assertThat(client.login(username, SecurityTestClient.PASSWORD).statusCode()).isEqualTo(204); }

    @Test void protectsPagesEveryCatalogAndImportEndpoints() throws Exception {
        for (String path : List.of("/", "/index.html", "/account.html", "/templates/employees-import.xlsx")) {
            var response=client.get(path);assertThat(response.statusCode()).isEqualTo(302);assertThat(response.headers().firstValue("Location").orElse("")).endsWith("/login.html");
        }
        for (String path : List.of("/api/employees", "/api/jobs", "/api/companies", "/api/summary", "/api/account"))
            assertThat(client.get(path).statusCode()).isEqualTo(401);
        for (String path : List.of("/api/employees", "/api/employees/import", "/api/employees/import/preview", "/api/account/password"))
            assertThat(client.write("POST",path,Map.of(),true).statusCode()).isEqualTo(401);
        assertThat(client.get("/login.html").statusCode()).isEqualTo(200);
        assertThat(client.get("/api/auth/captcha").headers().firstValue("Cache-Control").orElse("")).contains("no-store");
    }

    @Test void captchaIsRequiredConsumedOnFailureAndBoundToSession() throws Exception {
        client.refreshCsrf();
        assertThat(client.loginForm(username,SecurityTestClient.PASSWORD,"AAAAA").statusCode()).isEqualTo(400);
        client.get("/api/auth/captcha");
        assertThat(client.loginForm(username,SecurityTestClient.PASSWORD,"WRONG").statusCode()).isEqualTo(400);
        assertThat(client.loginForm(username,SecurityTestClient.PASSWORD,"AAAAA").statusCode()).isEqualTo(400);
        var other = new SecurityTestClient(port);other.refreshCsrf();
        client.get("/api/auth/captcha");
        assertThat(other.loginForm(username,SecurityTestClient.PASSWORD,"AAAAA").statusCode()).isEqualTo(400);
        assertThat(client.loginForm(username,SecurityTestClient.PASSWORD,"aaaaa").statusCode()).isEqualTo(204);
    }

    @Test void genericCredentialErrorsDoNotRevealUserExistenceAndBasicAuthCannotBypassCaptcha() throws Exception {
        var invalid=client.login(username,"Wrong!Credentials999");
        var absent=client.login("absent-user","Wrong!Credentials999");
        assertThat(invalid.statusCode()).isEqualTo(401);assertThat(absent.body()).isEqualTo(invalid.body());
        String basic=Base64.getEncoder().encodeToString((username+":"+SecurityTestClient.PASSWORD).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var response=client.http.send(HttpRequest.newBuilder(client.uri("/api/employees")).header("Authorization","Basic "+basic).GET().build(),HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(401);
    }

    @Test void loginRotatesSessionAndCsrfAndProvidesSecurityHeaders() throws Exception {
        client.refreshCsrf();String oldCsrf=client.csrf;
        var cookies=(java.net.CookieManager)client.http.cookieHandler().orElseThrow();
        String oldSession=cookies.getCookieStore().getCookies().stream().filter(c->c.getName().equals("JSESSIONID")).findFirst().orElseThrow().getValue();
        var response=client.login(username,SecurityTestClient.PASSWORD);
        assertThat(response.statusCode()).isEqualTo(204);
        String newSession=cookies.getCookieStore().getCookies().stream().filter(c->c.getName().equals("JSESSIONID")).findFirst().orElseThrow().getValue();
        assertThat(newSession).isNotEqualTo(oldSession);
        assertThat(response.headers().allValues("Set-Cookie").toString()).contains("HttpOnly","SameSite=Lax");
        var rejected=client.http.send(HttpRequest.newBuilder(client.uri("/api/companies")).header("X-CSRF-TOKEN",oldCsrf).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString("{\"code\":\"CSRF\",\"title\":\"test\"}")).build(),HttpResponse.BodyHandlers.ofString());
        assertThat(rejected.statusCode()).isEqualTo(403);
        var page=client.get("/account.html");assertThat(page.headers().firstValue("X-Frame-Options")).contains("DENY");
        assertThat(page.headers().firstValue("X-Content-Type-Options")).contains("nosniff");
        assertThat(page.headers().firstValue("Content-Security-Policy").orElse("")).contains("frame-ancestors 'none'");
        assertThat(page.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
    }

    @Test void profileUpdatesOnlyCurrentAccountAndNeverExposesPasswordHash() throws Exception {
        login();
        var response=client.write("PUT","/api/account",Map.of("displayName","کاربر آزمایشی","email","test@example.com","username","hijacked","id",1),true);
        assertThat(response.statusCode()).isEqualTo(200);
        var profile=client.json.readTree(response.body());assertThat(profile.get("username").asText()).isEqualTo(username);
        assertThat(profile.get("displayName").asText()).isEqualTo("کاربر آزمایشی");
        assertThat(response.body()).doesNotContain("password","credential","hash");
        assertThat(client.write("PUT","/api/account",Map.of("displayName","x","email","invalid"),true).statusCode()).isEqualTo(400);
        assertThat(client.write("PUT","/api/account",Map.of("displayName","x"),false).statusCode()).isEqualTo(403);
    }

    @Test void passwordChangeRequiresCurrentStrongMatchingNewPasswordAndRevokesAllSessions() throws Exception {
        login();var second=new SecurityTestClient(port);assertThat(second.login(username,SecurityTestClient.PASSWORD).statusCode()).isEqualTo(204);
        String oldHash=accounts.find(username).orElseThrow().passwordHash();
        assertThat(change("wrong",NEW_PASSWORD,NEW_PASSWORD).statusCode()).isEqualTo(400);
        assertThat(change(SecurityTestClient.PASSWORD,"weak","weak").statusCode()).isEqualTo(400);
        assertThat(change(SecurityTestClient.PASSWORD,NEW_PASSWORD,"different").statusCode()).isEqualTo(400);
        assertThat(accounts.find(username).orElseThrow().passwordHash()).isEqualTo(oldHash);
        assertThat(change(SecurityTestClient.PASSWORD,NEW_PASSWORD,NEW_PASSWORD).statusCode()).isEqualTo(200);
        String newHash=accounts.find(username).orElseThrow().passwordHash();assertThat(newHash).isNotEqualTo(oldHash).doesNotContain(NEW_PASSWORD);
        assertThat(encoder.matches(NEW_PASSWORD,newHash)).isTrue();
        assertThat(client.get("/api/account").statusCode()).isEqualTo(401);assertThat(second.get("/api/employees").statusCode()).isEqualTo(401);
        assertThat(client.login(username,SecurityTestClient.PASSWORD).statusCode()).isEqualTo(401);
        assertThat(client.login(username,NEW_PASSWORD).statusCode()).isEqualTo(204);
    }

    @Test void passwordReuseAndMissingCsrfAreRejected() throws Exception {
        login();String hash=accounts.find(username).orElseThrow().passwordHash();
        var body=Map.of("currentPassword",SecurityTestClient.PASSWORD,"newPassword",SecurityTestClient.PASSWORD,"confirmation",SecurityTestClient.PASSWORD);
        assertThat(client.write("POST","/api/account/password",body,false).statusCode()).isEqualTo(403);
        assertThat(client.write("POST","/api/account/password",body,true).statusCode()).isEqualTo(400);
        assertThat(accounts.find(username).orElseThrow().passwordHash()).isEqualTo(hash);
    }

    @Test void logoutIsCsrfProtectedAndDisablesOldSession() throws Exception {
        login();assertThat(client.write("POST","/api/auth/logout",null,false).statusCode()).isEqualTo(403);
        assertThat(client.get("/api/account").statusCode()).isEqualTo(200);
        assertThat(client.write("POST","/api/auth/logout",null,true).statusCode()).isEqualTo(204);
        assertThat(client.get("/api/account").statusCode()).isEqualTo(401);
    }

    @Test void disablingAccountRevokesSession() throws Exception {
        login();jdbc.update("UPDATE app_users SET enabled=FALSE WHERE username=?",username);
        assertThat(client.get("/api/account").statusCode()).isEqualTo(401);
        assertThat(client.login(username,SecurityTestClient.PASSWORD).statusCode()).isEqualTo(401);
    }

    @Test void hashingUsesDifferentSaltForSamePassword() {
        String first=accounts.find(username).orElseThrow().passwordHash();
        String second=encoder.encode(SecurityTestClient.PASSWORD);
        assertThat(first).startsWith("{pbkdf2-sha256-600k}").isNotEqualTo(second);
        assertThat(encoder.matches(SecurityTestClient.PASSWORD,first)).isTrue();
    }
    HttpResponse<String> change(String current,String next,String confirmation) throws Exception {
        return client.write("POST","/api/account/password",Map.of("currentPassword",current,"newPassword",next,"confirmation",confirmation),true);
    }
}
