package com.tayyar.notification;

import static org.assertj.core.api.Assertions.*;

import com.tayyar.support.PostgresIntegrationTest;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import tools.jackson.databind.*;

import java.net.*;
import java.net.http.*;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"tayyar.identity.login-limit=1000", "tayyar.identity.csrf-limit=1000"})
class NotificationsIT extends PostgresIntegrationTest {
    private static final String PASSWORD = "Notification test password!";

    @Value("${local.server.port}") int port;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    @Autowired NotificationStore notifications;
    final HttpClient http = HttpClient.newHttpClient();

    class Browser {
        String cookie;
        String token;

        HttpResponse<String> send(String method, String path) throws Exception {
            var request = HttpRequest.newBuilder(
                    URI.create("http://localhost:" + port + "/api/v1" + path));
            if (cookie != null) request.header("Cookie", cookie);
            if (token != null) request.header("X-CSRF-TOKEN", token);
            var response = http.send(request.method(method, HttpRequest.BodyPublishers.noBody()).build(),
                    HttpResponse.BodyHandlers.ofString());
            for (String value : response.headers().allValues("Set-Cookie"))
                if (value.startsWith("SESSION=")) cookie = value.substring(0, value.indexOf(';'));
            return response;
        }

        HttpResponse<String> login(UUID user) throws Exception {
            csrf();
            var request = HttpRequest.newBuilder(
                            URI.create("http://localhost:" + port + "/api/v1/auth/session"))
                    .header("Cookie", cookie)
                    .header("X-CSRF-TOKEN", token)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of(
                            "email", user + "@example.com", "password", PASSWORD))))
                    .build();
            var response = http.send(request, HttpResponse.BodyHandlers.ofString());
            for (String value : response.headers().allValues("Set-Cookie"))
                if (value.startsWith("SESSION=")) cookie = value.substring(0, value.indexOf(';'));
            csrf();
            return response;
        }

        void csrf() throws Exception {
            var response = send("GET", "/auth/csrf");
            assertThat(response.statusCode()).isEqualTo(200);
            token = json.readTree(response.body()).get("token").asText();
        }
    }

    UUID account(String role) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id,full_name,email,password_hash,status,created_at,updated_at)"
                        + " VALUES (?,'Notification User',?,?,'ACTIVE',now(),now())",
                id, id + "@example.com", passwords.encode(PASSWORD));
        jdbc.update("INSERT INTO user_roles(user_id,role_name) VALUES (?,?)", id, role);
        return id;
    }

    Browser login(UUID user) throws Exception {
        Browser browser = new Browser();
        assertThat(browser.login(user).statusCode()).isEqualTo(204);
        return browser;
    }

    UUID notification(UUID recipient, Instant created, String key) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO notifications(id,recipient_user_id,type,title,body,"
                        + "related_entity_type,related_entity_id,created_at,deduplication_key)"
                        + " VALUES (?,?,'ORDER_ACCEPTED','Order accepted','Your order was accepted.',"
                        + "'ORDER',?,?,?)",
                id, recipient, UUID.randomUUID(), java.sql.Timestamp.from(created), key);
        return id;
    }

    JsonNode body(HttpResponse<String> response, int status) throws Exception {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
        return response.body().isBlank() ? null : json.readTree(response.body());
    }

    @Test
    void inboxIsOwnedBoundedFilteredDeterministicAndPrivate() throws Exception {
        UUID owner = account("CUSTOMER"), foreign = account("ADMIN");
        UUID older = notification(owner, Instant.parse("2026-01-01T00:00:00Z"), UUID.randomUUID().toString());
        UUID newest = notification(owner, Instant.parse("2026-01-02T00:00:00Z"), UUID.randomUUID().toString());
        UUID foreignId = notification(foreign, Instant.parse("2026-01-03T00:00:00Z"), UUID.randomUUID().toString());
        jdbc.update("UPDATE notifications SET read_at=created_at WHERE id=?", older);
        Browser browser = login(owner);

        JsonNode page = body(browser.send("GET", "/notifications?page=0&size=1"), 200);
        assertThat(page.get("total").asLong()).isEqualTo(2);
        assertThat(page.get("items")).hasSize(1);
        assertThat(page.get("items").get(0).get("id").asText()).isEqualTo(newest.toString());
        assertThat(page.toString()).doesNotContain(
                "recipientUserId", "deduplicationKey", foreignId.toString(), "password");

        JsonNode unread = body(browser.send("GET", "/notifications?read=false"), 200);
        assertThat(unread.get("total").asLong()).isEqualTo(1);
        assertThat(unread.get("items").get(0).get("read").asBoolean()).isFalse();
        body(browser.send("GET", "/notifications/" + foreignId), 404);
        body(browser.send("GET", "/notifications?size=101"), 400);
        body(browser.send("GET", "/notifications?read=yes"), 400);
        body(browser.send("GET", "/notifications?sort=createdAt"), 400);
        body(browser.send("GET", "/notifications?page=0&page=1"), 400);
    }

    @Test
    void readOperationsAreOwnedIdempotentCsrfProtectedAndMarkAllIsScoped() throws Exception {
        UUID owner = account("DRIVER"), foreign = account("CUSTOMER");
        UUID first = notification(owner, Instant.parse("2026-02-01T00:00:00Z"), UUID.randomUUID().toString());
        notification(owner, Instant.parse("2026-02-02T00:00:00Z"), UUID.randomUUID().toString());
        UUID foreignId = notification(foreign, Instant.parse("2026-02-03T00:00:00Z"), UUID.randomUUID().toString());
        Browser browser = login(owner);

        JsonNode read = body(browser.send("POST", "/notifications/" + first + "/read"), 200);
        String readAt = read.get("readAt").asText();
        assertThat(read.get("read").asBoolean()).isTrue();
        assertThat(body(browser.send("POST", "/notifications/" + first + "/read"), 200)
                .get("readAt").asText()).isEqualTo(readAt);
        body(browser.send("POST", "/notifications/" + foreignId + "/read"), 404);

        Browser withoutCsrf = login(owner);
        withoutCsrf.token = null;
        body(withoutCsrf.send("POST", "/notifications/read-all"), 403);
        JsonNode result = body(browser.send("POST", "/notifications/read-all"), 200);
        assertThat(result.get("markedRead").asInt()).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM notifications WHERE recipient_user_id=? AND read_at IS NULL",
                Integer.class, owner)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM notifications WHERE recipient_user_id=? AND read_at IS NULL",
                Integer.class, foreign)).isEqualTo(1);
    }

    @Test
    void anonymousMalformedSuspendedAndArbitrarySendRequestsAreRejected() throws Exception {
        assertThat(new Browser().send("GET", "/notifications").statusCode()).isEqualTo(401);
        UUID user = account("RESTAURANT_OWNER");
        Browser browser = login(user);
        body(browser.send("GET", "/notifications/not-a-uuid"), 400);
        assertThat(browser.send("POST", "/notifications").statusCode()).isEqualTo(405);
        jdbc.update("UPDATE users SET status='SUSPENDED' WHERE id=?", user);
        body(browser.send("GET", "/notifications"), 401);
    }

    @Test
    void databaseEnforcesDeduplicationImmutableOwnershipAndRetention() {
        UUID owner = account("CUSTOMER"), other = account("CUSTOMER");
        Instant now = Instant.parse("2026-03-01T00:00:00Z");
        notifications.insert(owner, NotificationType.ORDER_ACCEPTED, "Accepted", "Order accepted.",
                RelatedEntityType.ORDER, UUID.randomUUID(), "stable-event-key", now);
        notifications.insert(owner, NotificationType.ORDER_ACCEPTED, "Accepted", "Order accepted.",
                RelatedEntityType.ORDER, UUID.randomUUID(), "stable-event-key", now);
        UUID id = jdbc.queryForObject(
                "SELECT id FROM notifications WHERE deduplication_key='stable-event-key'", UUID.class);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM notifications WHERE deduplication_key='stable-event-key'",
                Integer.class)).isEqualTo(1);
        assertThatThrownBy(() -> jdbc.update(
                "UPDATE notifications SET recipient_user_id=? WHERE id=?", other, id))
                .rootCause().isInstanceOfSatisfying(
                        SQLException.class, error -> assertThat(error.getSQLState()).isEqualTo("23514"));
        assertThatThrownBy(() -> jdbc.update("DELETE FROM notifications WHERE id=?", id))
                .rootCause().isInstanceOfSatisfying(
                        SQLException.class, error -> assertThat(error.getSQLState()).isEqualTo("23514"));
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO notifications(id,recipient_user_id,type,title,body,created_at,deduplication_key)"
                        + " VALUES (?,?,?,'x','x',now(),?)",
                UUID.randomUUID(), owner, "SPECULATIVE_TYPE", UUID.randomUUID().toString()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
