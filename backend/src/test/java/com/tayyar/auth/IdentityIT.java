package com.tayyar.auth;

import static org.assertj.core.api.Assertions.*;

import com.tayyar.support.PostgresIntegrationTest;
import com.tayyar.user.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.web.bind.annotation.*;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "tayyar.identity.login-limit=1000",
            "tayyar.identity.registration-limit=1000",
            "tayyar.identity.csrf-limit=1000"
        })
@Import(IdentityIT.AuthorizationConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class IdentityIT extends PostgresIntegrationTest {
    @Value("${local.server.port}")
    int port;

    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    @Autowired SessionRepository<?> sessions;
    @Autowired RestrictedOperation restricted;
    private static final String PASSWORD = "Correct horse battery 42!";
    private final HttpClient http =
            HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

    class Browser {
        String cookie;
        String token;

        HttpResponse<String> send(String method, String path, String body, boolean csrf)
                throws Exception {
            var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
            if (cookie != null) request.header("Cookie", cookie);
            if (csrf && token != null) request.header("X-CSRF-TOKEN", token);
            if (body != null) request.header("Content-Type", "application/json");
            var response =
                    http.send(
                            request.method(
                                            method,
                                            body == null
                                                    ? HttpRequest.BodyPublishers.noBody()
                                                    : HttpRequest.BodyPublishers.ofString(body))
                                    .build(),
                            HttpResponse.BodyHandlers.ofString());
            for (String setCookie : response.headers().allValues("Set-Cookie")) {
                if (setCookie.startsWith("SESSION="))
                    cookie = setCookie.substring(0, setCookie.indexOf(';'));
            }
            return response;
        }

        HttpResponse<String> csrf() throws Exception {
            var response = send("GET", "/api/v1/auth/csrf", null, false);
            assertThat(response.statusCode()).isEqualTo(200);
            token = json.readTree(response.body()).get("token").asText();
            return response;
        }

        String sessionId() {
            return new String(
                    Base64.getDecoder().decode(cookie.substring("SESSION=".length())),
                    StandardCharsets.UTF_8);
        }
    }

    String email() {
        return UUID.randomUUID() + "@example.com";
    }

    String registration(String email) {
        return json.writeValueAsString(
                Map.of("fullName", "Identity Customer", "email", email, "password", PASSWORD));
    }

    String credentials(String email, String password) {
        return json.writeValueAsString(Map.of("email", email, "password", password));
    }

    Browser registered(String email) throws Exception {
        var browser = new Browser();
        browser.csrf();
        assertThat(
                        browser.send(
                                        "POST",
                                        "/api/v1/auth/registrations",
                                        registration(email),
                                        true)
                                .statusCode())
                .isEqualTo(201);
        return browser;
    }

    Browser loggedIn(String email) throws Exception {
        var browser = registered(email);
        var result =
                browser.send("POST", "/api/v1/auth/session", credentials(email, PASSWORD), true);
        assertThat(result.statusCode()).as(result.body()).isEqualTo(204);
        browser.csrf();
        return browser;
    }

    @Test
    void registrationHashesPasswordAndAssignsOnlyCustomer() throws Exception {
        String email = email();
        var browser = new Browser();
        browser.csrf();
        var response =
                browser.send(
                        "POST",
                        "/api/v1/auth/registrations",
                        registration("  " + email.toUpperCase(Locale.ROOT) + "  "),
                        true);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
        JsonNode body = json.readTree(response.body());
        assertThat(body.get("email").asText()).isEqualTo(email);
        assertThat(body.get("roles").toString()).isEqualTo("[\"CUSTOMER\"]");
        assertThat(response.body()).doesNotContain(PASSWORD, "passwordHash", "authVersion");
        String stored =
                jdbc.queryForObject(
                        "SELECT password_hash FROM users WHERE email=?", String.class, email);
        assertThat(stored).startsWith("{bcrypt}").doesNotContain(PASSWORD);
        assertThat(passwords.matches(PASSWORD, stored)).isTrue();
        assertThat(browser.send("GET", "/api/v1/users/me", null, false).statusCode())
                .isEqualTo(401);
    }

    @Test
    void privilegedRegistrationFieldsAreRejected() throws Exception {
        var browser = new Browser();
        browser.csrf();
        for (String role : List.of("ADMIN", "DRIVER", "RESTAURANT_OWNER", "RESTAURANT_STAFF")) {
            String email = email();
            String body =
                    json.writeValueAsString(
                            Map.of(
                                    "fullName",
                                    "Name",
                                    "email",
                                    email,
                                    "password",
                                    PASSWORD,
                                    "roles",
                                    List.of(role)));
            assertThat(browser.send("POST", "/api/v1/auth/registrations", body, true).statusCode())
                    .isEqualTo(400);
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT count(*) FROM users WHERE email=?",
                                    Integer.class,
                                    email))
                    .isZero();
        }
    }

    @Test
    void loginRotatesSessionAndCsrfLogoutRevokesOldCookie() throws Exception {
        String email = email();
        var browser = registered(email);
        String anonymousCookie = browser.cookie;
        String oldCsrf = browser.token;
        var login =
                browser.send("POST", "/api/v1/auth/session", credentials(email, PASSWORD), true);
        assertThat(login.statusCode()).as(login.body()).isEqualTo(204);
        assertThat(browser.cookie).isNotEqualTo(anonymousCookie);
        String authenticatedCookie = browser.cookie;
        assertThat(login.headers().allValues("Set-Cookie").toString())
                .contains("HttpOnly", "Secure", "SameSite=Lax", "Path=/");
        var me = browser.send("GET", "/api/v1/users/me", null, false);
        assertThat(me.statusCode()).as(me.body()).isEqualTo(200);
        assertThat(json.readTree(me.body()).get("email").asText()).isEqualTo(email);
        assertThat(me.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
        assertThat(browser.send("DELETE", "/api/v1/auth/session", null, true).statusCode())
                .isEqualTo(403);
        browser.csrf();
        assertThat(browser.token).isNotEqualTo(oldCsrf);
        String sessionId = browser.sessionId();
        assertThat(sessions.findById(sessionId)).isNotNull();
        assertThat(browser.send("DELETE", "/api/v1/auth/session", null, true).statusCode())
                .isEqualTo(204);
        assertThat(sessions.findById(sessionId)).isNull();
        browser.cookie = authenticatedCookie;
        assertThat(browser.send("GET", "/api/v1/users/me", null, false).statusCode())
                .isEqualTo(401);
        browser.cookie = anonymousCookie;
        assertThat(browser.send("GET", "/api/v1/users/me", null, false).statusCode())
                .isEqualTo(401);
    }

    @Test
    void missingCsrfAndAnonymousAccessAreDeniedWithJson() throws Exception {
        var browser = new Browser();
        assertThat(
                        browser.send(
                                        "POST",
                                        "/api/v1/auth/registrations",
                                        registration(email()),
                                        false)
                                .statusCode())
                .isEqualTo(403);
        assertThat(
                        browser.send(
                                        "POST",
                                        "/api/v1/auth/session",
                                        credentials(email(), PASSWORD),
                                        false)
                                .statusCode())
                .isEqualTo(403);
        var me = browser.send("GET", "/api/v1/users/me", null, false);
        assertThat(me.statusCode()).isEqualTo(401);
        assertThat(json.readTree(me.body()).get("code").asText())
                .isEqualTo("AUTHENTICATION_REQUIRED");
        assertThat(browser.send("GET", "/api/v1/health", null, false).statusCode()).isEqualTo(200);
    }

    @Test
    void loginErrorsDoNotDiscloseAccountExistenceOrStatus() throws Exception {
        String email = email();
        var browser = registered(email);
        var missing =
                browser.send("POST", "/api/v1/auth/session", credentials(email(), PASSWORD), true);
        var wrong =
                browser.send(
                        "POST",
                        "/api/v1/auth/session",
                        credentials(email, "wrong password value"),
                        true);
        jdbc.update("UPDATE users SET status='SUSPENDED' WHERE email=?", email);
        var suspended =
                browser.send("POST", "/api/v1/auth/session", credentials(email, PASSWORD), true);
        for (var response : List.of(missing, wrong, suspended)) {
            assertThat(response.statusCode()).as(response.body()).isEqualTo(401);
            assertThat(json.readTree(response.body()).get("message").asText())
                    .isEqualTo("Invalid email or password");
            assertThat(response.body()).doesNotContain(PASSWORD, "SUSPENDED", "password_hash");
        }
    }

    @Test
    void accountPasswordAndRoleChangesInvalidateSessions() throws Exception {
        for (String change : List.of("status", "password", "role")) {
            String email = email();
            var browser = loggedIn(email);
            String sessionId = browser.sessionId();
            if (change.equals("status"))
                jdbc.update("UPDATE users SET status='DISABLED' WHERE email=?", email);
            if (change.equals("password"))
                jdbc.update(
                        "UPDATE users SET password_hash=? WHERE email=?",
                        passwords.encode("New password value!"),
                        email);
            if (change.equals("role"))
                jdbc.update(
                        "INSERT INTO user_roles(user_id,role_name) SELECT id,'DRIVER' FROM users"
                            + " WHERE email=?",
                        email);
            var response = browser.send("GET", "/api/v1/users/me", null, false);
            assertThat(response.statusCode()).as(change + ": " + response.body()).isEqualTo(401);
            assertThat(sessions.findById(sessionId)).isNull();
        }
    }

    @Test
    void absoluteAndIdleExpiryDenyAccess() throws Exception {
        String email = email();
        var browser = loggedIn(email);
        expireAuthentication(sessions, browser.sessionId());
        assertThat(browser.send("GET", "/api/v1/users/me", null, false).statusCode())
                .isEqualTo(401);
        var idle = loggedIn(email());
        jdbc.update(
                "UPDATE spring_session SET last_access_time=?, expiry_time=? WHERE session_id=?",
                Instant.now().minusSeconds(3600).toEpochMilli(),
                Instant.now().minusSeconds(1800).toEpochMilli(),
                idle.sessionId());
        assertThat(idle.send("GET", "/api/v1/users/me", null, false).statusCode()).isEqualTo(401);
    }

    private <S extends Session> void expireAuthentication(
            SessionRepository<S> repository, String id) {
        S session = repository.findById(id);
        var context = (SecurityContext) session.getAttribute("SPRING_SECURITY_CONTEXT");
        var original = (SessionPrincipal) context.getAuthentication().getPrincipal();
        var expired =
                new SessionPrincipal(
                        original.id(),
                        original.roles(),
                        original.authVersion(),
                        Instant.now().minus(Duration.ofHours(13)));
        var changed = SecurityContextHolder.createEmptyContext();
        changed.setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        expired, null, context.getAuthentication().getAuthorities()));
        session.setAttribute("SPRING_SECURITY_CONTEXT", changed);
        repository.save(session);
    }

    @Test
    void currentUserIgnoresOtherUserIdentifiersAndUnknownRoutesAreDenied() throws Exception {
        String first = email();
        String second = email();
        var own = loggedIn(first);
        registered(second);
        var response = own.send("GET", "/api/v1/users/me?userId=" + UUID.randomUUID(), null, false);
        assertThat(json.readTree(response.body()).get("email").asText()).isEqualTo(first);
        assertThat(own.send("GET", "/api/v1/admin/users", null, false).statusCode()).isEqualTo(403);
    }

    @Test
    void duplicateNormalizedEmailsAreAtomicUnderConcurrentRegistration(CapturedOutput output)
            throws Exception {
        String email = email();
        var first = new Browser();
        var second = new Browser();
        first.csrf();
        second.csrf();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var one =
                    executor.submit(
                            () ->
                                    first.send(
                                                    "POST",
                                                    "/api/v1/auth/registrations",
                                                    registration(email),
                                                    true)
                                            .statusCode());
            var two =
                    executor.submit(
                            () ->
                                    second.send(
                                                    "POST",
                                                    "/api/v1/auth/registrations",
                                                    registration(email.toUpperCase(Locale.ROOT)),
                                                    true)
                                            .statusCode());
            assertThat(List.of(one.get(), two.get())).containsExactlyInAnyOrder(201, 409);
        }
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM users WHERE email=?", Integer.class, email))
                .isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM user_roles r JOIN users u ON u.id=r.user_id"
                                    + " WHERE u.email=?",
                                Integer.class,
                                email))
                .isEqualTo(1);
        assertThat(output.getAll()).doesNotContain(email, PASSWORD);
    }

    @Test
    void persistedPrincipalHasNoCredentialsAndMethodRolesAreEnforced() throws Exception {
        String email = email();
        var browser = loggedIn(email);
        SecurityContext context =
                sessions.findById(browser.sessionId()).getAttribute("SPRING_SECURITY_CONTEXT");
        assertThat(context.getAuthentication().getCredentials()).isNull();
        try {
            SecurityContextHolder.setContext(context);
            assertThatThrownBy(restricted::adminOnly)
                    .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        } finally {
            SecurityContextHolder.clearContext();
        }
        jdbc.update(
                "INSERT INTO user_roles(user_id,role_name) SELECT id,'ADMIN' FROM users WHERE"
                    + " email=?",
                email);
        var administrator = new Browser();
        administrator.csrf();
        assertThat(
                        administrator
                                .send(
                                        "POST",
                                        "/api/v1/auth/session",
                                        credentials(email, PASSWORD),
                                        true)
                                .statusCode())
                .isEqualTo(204);
        SecurityContext adminContext =
                sessions.findById(administrator.sessionId())
                        .getAttribute("SPRING_SECURITY_CONTEXT");
        try {
            SecurityContextHolder.setContext(adminContext);
            assertThat(restricted.adminOnly()).isEqualTo("allowed");
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class AuthorizationConfiguration {
        @Bean
        RestrictedOperation restrictedOperation() {
            return new RestrictedOperation();
        }
    }

    static class RestrictedOperation {
        @PreAuthorize("hasRole('ADMIN')")
        public String adminOnly() {
            return "allowed";
        }
    }

    @Test
    void invalidInputDoesNotCreateAccounts() throws Exception {
        var browser = new Browser();
        browser.csrf();
        for (var values :
                List.of(
                        Map.of("fullName", "", "email", email(), "password", PASSWORD),
                        Map.of("fullName", "Name", "email", email(), "password", "short"),
                        Map.of("fullName", "Name", "email", "invalid", "password", PASSWORD),
                        Map.of("fullName", "Name", "email", email(), "password", "é".repeat(37)))) {
            assertThat(
                            browser.send(
                                            "POST",
                                            "/api/v1/auth/registrations",
                                            json.writeValueAsString(values),
                                            true)
                                    .statusCode())
                    .isEqualTo(400);
        }
    }

    @Test
    void rateLimitIsAtomicAndHasRetryHeader() throws Exception {
        var browser = new Browser();
        browser.csrf();
        String limited = email();
        var digest = java.security.MessageDigest.getInstance("SHA-256");
        String key =
                HexFormat.of()
                        .formatHex(
                                digest.digest(
                                        ("login-email:" + limited)
                                                .getBytes(StandardCharsets.UTF_8)));
        jdbc.update(
                "INSERT INTO auth_rate_limits(key_hash,window_started,attempts) VALUES (?,?,?)",
                key,
                Timestamp.from(Instant.now()),
                1000);
        var response =
                browser.send("POST", "/api/v1/auth/session", credentials(limited, PASSWORD), true);
        assertThat(response.statusCode()).isEqualTo(429);
        assertThat(response.headers().firstValue("Retry-After")).contains("900");
    }
}
