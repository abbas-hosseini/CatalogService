package com.example.webapp.security;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties="spring.datasource.url=jdbc:h2:mem:theme-preference-tests;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
@Import(SecurityTestConfiguration.class)
class ThemePreferenceTests {
    @LocalServerPort int port;
    @Autowired UserAccounts accounts;
    @Autowired JdbcTemplate jdbc;
    SecurityTestClient client;
    String username;

    @BeforeEach void prepare() throws Exception {
        username="theme"+UUID.randomUUID().toString().replace("-","");
        accounts.createInitialUser(username,SecurityTestClient.PASSWORD);
        client=new SecurityTestClient(port);
        assertThat(client.login(username,SecurityTestClient.PASSWORD).statusCode()).isEqualTo(204);
    }

    @Test void persistsEveryThemeAcrossSessionsAndPreservesProfileAndCredentialVersion() throws Exception {
        assertThat(theme(client)).isEqualTo("classic");
        long version=accounts.find(username).orElseThrow().version();
        assertThat(client.write("PUT","/api/account",Map.of("displayName","نام نمایشی","email","test@example.com"),true).statusCode()).isEqualTo(200);
        for(String theme:List.of("ocean","violet","dark","classic")) {
            var response=client.write("PUT","/api/account/preferences",Map.of("theme",theme),true);
            assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
            var profile=client.json.readTree(response.body());
            assertThat(profile.get("theme").asText()).isEqualTo(theme);
            assertThat(profile.get("displayName").asText()).isEqualTo("نام نمایشی");
            assertThat(profile.get("email").asText()).isEqualTo("test@example.com");
            assertThat(response.body()).doesNotContain("password","hash","credential");
            assertThat(jdbc.queryForObject("SELECT ui_theme FROM app_users WHERE username=?",String.class,username)).isEqualTo(theme);
        }
        client.write("PUT","/api/account/preferences",Map.of("theme","dark"),true);
        client.write("PUT","/api/account",Map.of("displayName","نام جدید"),true);
        assertThat(theme(client)).isEqualTo("dark");
        assertThat(accounts.find(username).orElseThrow().version()).isEqualTo(version);
        var second=new SecurityTestClient(port);
        assertThat(second.login(username,SecurityTestClient.PASSWORD).statusCode()).isEqualTo(204);
        assertThat(theme(second)).isEqualTo("dark");
        assertThat(client.write("POST","/api/auth/logout",null,true).statusCode()).isEqualTo(204);
        assertThat(client.login(username,SecurityTestClient.PASSWORD).statusCode()).isEqualTo(204);
        assertThat(theme(client)).isEqualTo("dark");
    }

    @Test void usersWithoutRolesCanChangeOnlyTheirOwnTheme() throws Exception {
        String otherName="other"+UUID.randomUUID().toString().replace("-","");
        accounts.createInitialUser(otherName,SecurityTestClient.PASSWORD);
        long otherId=accounts.find(otherName).orElseThrow().id();
        jdbc.update("DELETE FROM app_user_roles WHERE user_id=?",accounts.find(username).orElseThrow().id());
        assertThat(client.get("/api/employees").statusCode()).isEqualTo(403);
        var response=client.write("PUT","/api/account/preferences",Map.of("theme","violet","id",otherId,"username",otherName,"permissions",List.of("security.manage")),true);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(theme(client)).isEqualTo("violet");
        assertThat(jdbc.queryForObject("SELECT ui_theme FROM app_users WHERE id=?",String.class,otherId)).isEqualTo("classic");
        assertThat(client.get("/api/admin/users").statusCode()).isEqualTo(403);
    }

    @ParameterizedTest @NullAndEmptySource @ValueSource(strings={"invalid","DARK"," dark ","<script>alert(1)</script>","https://example.com/theme.css"})
    void rejectsUnrecognizedThemesWithoutChangingTheSavedValue(String value) throws Exception {
        client.write("PUT","/api/account/preferences",Map.of("theme","ocean"),true);
        var response=client.write("PUT","/api/account/preferences",Collections.singletonMap("theme",value),true);
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(theme(client)).isEqualTo("ocean");
    }

    @Test void preferenceWritesRequireAuthenticationAndCsrf() throws Exception {
        var anonymous=new SecurityTestClient(port);
        assertThat(anonymous.write("PUT","/api/account/preferences",Map.of("theme","dark"),true).statusCode()).isEqualTo(401);
        assertThat(client.write("PUT","/api/account/preferences",Map.of("theme","dark"),false).statusCode()).isEqualTo(403);
        assertThat(client.write("POST","/api/account/preferences",Map.of("theme","dark"),true).statusCode()).isEqualTo(403);
        assertThat(theme(client)).isEqualTo("classic");
    }

    @Test void authenticatedPagesLoadSharedThemesAndLoginRemainsIndependent() throws Exception {
        for(String path:List.of("/","/account.html","/access.html")) {
            var page=client.get(path);
            assertThat(page.statusCode()).isEqualTo(200);
            assertThat(page.body()).contains("/themes.css", "/themes.js", "data-theme=\"classic\"");
            assertThat(page.headers().firstValue("Content-Security-Policy").orElse("")).contains("script-src 'self'");
        }
        assertThat(client.get("/login.html").body()).doesNotContain("/themes.js");
        assertThat(client.get("/themes.css").statusCode()).isEqualTo(200);
    }

    private String theme(SecurityTestClient session) throws Exception {
        var response=session.get("/api/account");assertThat(response.statusCode()).isEqualTo(200);
        return session.json.readTree(response.body()).get("theme").asText();
    }
}
