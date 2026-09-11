package com.tayyar.operations;

import static org.assertj.core.api.Assertions.assertThat;

import com.tayyar.support.PostgresIntegrationTest;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ObservabilityIT extends PostgresIntegrationTest {
    @Value("${local.server.port}") int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    @Autowired ObjectMapper json;
    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void probesArePublicSafeAndManagementEndpointsAreRestricted() throws Exception {
        var liveness = get("/actuator/health/liveness", null);
        var readiness = get("/actuator/health/readiness", null);
        assertThat(liveness.statusCode()).isEqualTo(200);
        assertThat(readiness.statusCode()).isEqualTo(200);
        assertThat(liveness.body()).isEqualTo("{\"status\":\"UP\"}");
        assertThat(readiness.body()).isEqualTo("{\"status\":\"UP\"}");
        assertThat(get("/actuator/health", null).statusCode()).isEqualTo(401);
        assertThat(get("/actuator/metrics", null).statusCode()).isEqualTo(401);
        assertThat(get("/actuator/prometheus", null).statusCode()).isEqualTo(401);
        assertThat(get("/actuator/env", null).statusCode()).isIn(401, 404);

        assertThat(liveness.headers().firstValue("X-Content-Type-Options")).contains("nosniff");
        assertThat(liveness.headers().firstValue("X-Frame-Options")).contains("DENY");
        assertThat(liveness.headers().firstValue("Referrer-Policy")).contains("no-referrer");
        assertThat(liveness.headers().firstValue("X-Correlation-ID")).isPresent();
        assertThat(liveness.headers().firstValue("Strict-Transport-Security")).isEmpty();
    }

    @Test
    void arbitraryCredentialedCorsOriginIsRejected() throws Exception {
        var request = HttpRequest.newBuilder(uri("/api/v1/health"))
                .header("Origin", "https://attacker.example")
                .header("Access-Control-Request-Method", "GET")
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .build();
        var response = http.send(request, HttpResponse.BodyHandlers.ofString());
        // A browser may receive an empty preflight response, but cannot grant credentialed CORS
        // access without these response headers.
        assertThat(response.headers().firstValue("Access-Control-Allow-Origin")).isEmpty();
        assertThat(response.headers().firstValue("Access-Control-Allow-Credentials")).isEmpty();
    }

    @Test
    void administratorCanReadLowCardinalityRuntimeMetrics() throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        jdbc.update("INSERT INTO users(id,full_name,email,password_hash,status,created_at,updated_at)"
                        + " VALUES (?,'Operations Admin',?,?,'ACTIVE',now(),now())",
                UUID.randomUUID(), email, passwords.encode("Operations password 42!"));
        jdbc.update("INSERT INTO user_roles(user_id,role_name) SELECT id,'ADMIN' FROM users WHERE email=?",
                email);

        var csrf = get("/api/v1/auth/csrf", null);
        String cookie = sessionCookie(csrf);
        String token = json.readTree(csrf.body()).get("token").asText();
        String credentials = json.writeValueAsString(
                Map.of("email", email, "password", "Operations password 42!"));
        var login = http.send(HttpRequest.newBuilder(uri("/api/v1/auth/session"))
                .header("Cookie", cookie)
                .header("X-CSRF-TOKEN", token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(credentials)).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(login.statusCode()).isEqualTo(204);
        cookie = sessionCookie(login);

        var metrics = get("/actuator/metrics", cookie);
        assertThat(metrics.statusCode()).isEqualTo(200);
        assertThat(metrics.body()).contains("jvm.memory.used", "http.server.requests", "hikaricp.connections");
        var prometheus = get("/actuator/prometheus", cookie);
        assertThat(prometheus.statusCode()).isEqualTo(200);
        assertThat(prometheus.body()).contains("jvm_memory_used_bytes", "hikaricp_connections");
    }

    private HttpResponse<String> get(String path, String cookie) throws Exception {
        var builder = HttpRequest.newBuilder(uri(path)).GET();
        if (cookie != null) builder.header("Cookie", cookie);
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) { return URI.create("http://localhost:" + port + path); }

    private String sessionCookie(HttpResponse<?> response) {
        String header = response.headers().allValues("Set-Cookie").stream()
                .filter(value -> value.startsWith("SESSION="))
                .findFirst().orElseThrow();
        return header.substring(0, header.indexOf(';'));
    }
}
