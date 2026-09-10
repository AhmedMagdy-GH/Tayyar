package com.tayyar.branch;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.tayyar.support.PostgresIntegrationTest;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;

import tools.jackson.databind.*;

import java.net.URI;
import java.net.http.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "tayyar.identity.login-limit=1000",
            "tayyar.identity.registration-limit=1000",
            "tayyar.identity.csrf-limit=1000"
        })
class BranchesIT extends PostgresIntegrationTest {
    @Value("${local.server.port}")
    int port;

    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    @Autowired PlatformTransactionManager transactions;
    @MockitoSpyBean BranchJournal journal;
    private final HttpClient http = HttpClient.newHttpClient();
    private static final String PASSWORD = "Restaurant test password!";
    private String hash;

    @BeforeEach
    void passwordHash() {
        hash = passwords.encode(PASSWORD);
    }

    record Account(UUID id, String email, Browser browser) {}

    class Browser {
        String cookie, token;

        HttpResponse<String> send(String method, String path, Object input) throws Exception {
            var request =
                    HttpRequest.newBuilder(
                            URI.create("http://localhost:" + port + "/api/v1" + path));
            if (cookie != null) request.header("Cookie", cookie);
            if (token != null) request.header("X-CSRF-TOKEN", token);
            if (input != null) request.header("Content-Type", "application/json");
            var response =
                    http.send(
                            request.method(
                                            method,
                                            input == null
                                                    ? HttpRequest.BodyPublishers.noBody()
                                                    : HttpRequest.BodyPublishers.ofString(
                                                            json.writeValueAsString(input)))
                                    .build(),
                            HttpResponse.BodyHandlers.ofString());
            for (String value : response.headers().allValues("Set-Cookie"))
                if (value.startsWith("SESSION=")) cookie = value.substring(0, value.indexOf(';'));
            return response;
        }

        void csrf() throws Exception {
            var response = send("GET", "/auth/csrf", null);
            assertThat(response.statusCode()).isEqualTo(200);
            token = json.readTree(response.body()).get("token").asText();
        }
    }

    Account account(String... roles) throws Exception {
        UUID id = UUID.randomUUID();
        String email = id + "@example.com";
        new TransactionTemplate(transactions)
                .executeWithoutResult(
                        status -> {
                            jdbc.update(
                                    "INSERT INTO"
                                        + " users(id,full_name,email,password_hash,status,created_at,updated_at)"
                                        + " VALUES (?, 'Restaurant Tester', ?, ?, 'ACTIVE', ?, ?)",
                                    id,
                                    email,
                                    hash,
                                    Timestamp.from(Instant.now()),
                                    Timestamp.from(Instant.now()));
                            for (String role : roles)
                                jdbc.update(
                                        "INSERT INTO user_roles(user_id,role_name) VALUES (?,?)",
                                        id,
                                        role);
                        });
        return new Account(id, email, login(email));
    }

    Browser login(String email) throws Exception {
        var browser = new Browser();
        browser.csrf();
        assertThat(
                        browser.send(
                                        "POST",
                                        "/auth/session",
                                        Map.of("email", email, "password", PASSWORD))
                                .statusCode())
                .isEqualTo(204);
        browser.csrf();
        return browser;
    }

    JsonNode body(HttpResponse<String> response, int status) throws Exception {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
        return json.readTree(response.body());
    }

    JsonNode apply(Account customer) throws Exception {
        return body(
                customer.browser()
                        .send(
                                "POST",
                                "/restaurant-applications",
                                Map.of(
                                        "name",
                                        "Tayyar Kitchen",
                                        "description",
                                        "Brand description")),
                201);
    }

    JsonNode review(Account admin, JsonNode application, String outcome) throws Exception {
        return body(
                admin.browser()
                        .send(
                                "POST",
                                "/admin/restaurant-applications/"
                                        + application.get("id").asText()
                                        + "/decisions",
                                Map.of(
                                        "outcome",
                                        outcome,
                                        "reason",
                                        "Reviewed documents",
                                        "version",
                                        application.get("version").asLong())),
                201);
    }

    UUID id(JsonNode node) {
        return UUID.fromString(node.get("id").asText());
    }

    UUID approvedRestaurant(Account owner, Account admin) throws Exception {
        return UUID.fromString(
                review(admin, apply(owner), "APPROVED").get("restaurantId").asText());
    }

    record Fixture(Account owner, Account admin, UUID restaurant, Browser browser) {}

    Fixture fixture() throws Exception {
        var owner = account("CUSTOMER");
        var admin = account("ADMIN");
        UUID restaurant = approvedRestaurant(owner, admin);
        return new Fixture(owner, admin, restaurant, login(owner.email()));
    }

    String path(UUID restaurant) {
        return "/restaurants/" + restaurant + "/branches";
    }

    String path(Fixture f) {
        return path(f.restaurant());
    }

    Map<String, Object> profile() {
        return new HashMap<>(
                Map.of(
                        "name",
                        "Downtown",
                        "addressLine1",
                        "12 Test Street",
                        "city",
                        "Cairo",
                        "countryCode",
                        "EG",
                        "timezone",
                        "Africa/Cairo",
                        "deliveryModel",
                        "RESTAURANT_DELIVERY",
                        "latitude",
                        30.0444,
                        "longitude",
                        31.2357));
    }

    JsonNode create(Fixture f) throws Exception {
        return body(f.browser().send("POST", path(f), profile()), 201);
    }

    Map<String, Object> weekly(int day, String opens, String closes) {
        return Map.of("weekday", day, "opensAt", opens, "closesAt", closes);
    }

    Map<String, Object> hours(Object weekly, Object special, long version) {
        return Map.of("weekly", weekly, "special", special, "version", version);
    }

    void sqlState(String expected, org.assertj.core.api.ThrowableAssert.ThrowingCallable work) {
        assertThatThrownBy(work)
                .rootCause()
                .isInstanceOfSatisfying(
                        SQLException.class, e -> assertThat(e.getSQLState()).isEqualTo(expected));
    }

    @Test
    void ownerCreatesMultipleBranchesAndAdminCanManage() throws Exception {
        var f = fixture();
        var one = create(f);
        create(f);
        assertThat(one.get("restaurantId").asText()).isEqualTo(f.restaurant().toString());
        assertThat(one.get("profile").get("timezone").asText()).isEqualTo("Africa/Cairo");
        assertThat(
                        body(f.browser().send("GET", path(f) + "?size=1", null), 200)
                                .get("total")
                                .asLong())
                .isEqualTo(2);
        String branch = path(f) + "/" + id(one);
        var changed = profile();
        changed.put("deliveryModel", "TAYYAR_DELIVERY");
        body(
                f.admin().browser().send("PUT", branch, Map.of("profile", changed, "version", 0)),
                200);
        assertThat(
                        body(f.browser().send("GET", branch, null), 200)
                                .get("profile")
                                .get("deliveryModel")
                                .asText())
                .isEqualTo("TAYYAR_DELIVERY");
        body(f.admin().browser().send("POST", path(f), profile()), 201);
    }

    @Test
    void blocksCustomerAnonymousCrossOwnerAndWrongParentPaths() throws Exception {
        var f = fixture();
        var other = fixture();
        var branch = create(f);
        var customer = account("CUSTOMER");
        assertThat(customer.browser().send("POST", path(f), profile()).statusCode()).isEqualTo(403);
        var anonymous = new Browser();
        anonymous.csrf();
        assertThat(anonymous.send("POST", path(f), profile()).statusCode()).isEqualTo(401);
        assertThat(other.browser().send("POST", path(f), profile()).statusCode()).isEqualTo(404);
        String own = path(f) + "/" + id(branch);
        for (String suffix : List.of("", "/hours", "/staff", "/availability"))
            assertThat(other.browser().send("GET", own + suffix, null).statusCode()).isEqualTo(404);
        assertThat(
                        other.browser()
                                .send("PUT", own, Map.of("profile", profile(), "version", 0))
                                .statusCode())
                .isEqualTo(404);
        assertThat(
                        f.admin()
                                .browser()
                                .send("GET", path(other) + "/" + id(branch), null)
                                .statusCode())
                .isEqualTo(404);
        assertThat(
                        f.admin()
                                .browser()
                                .send("POST", path(UUID.randomUUID()), profile())
                                .statusCode())
                .isEqualTo(404);
        f.browser().token = null;
        assertThat(f.browser().send("POST", path(f), profile()).statusCode()).isEqualTo(403);
    }

    @Test
    void rejectsProfilePrivilegeAndRestaurantInjection() throws Exception {
        var f = fixture();
        var branch = create(f);
        for (String field : List.of("restaurantId", "status", "paused", "role", "userId")) {
            var input = profile();
            input.put(field, "ADMIN");
            assertThat(f.browser().send("POST", path(f), input).statusCode()).isEqualTo(400);
            assertThat(
                            f.browser()
                                    .send(
                                            "PUT",
                                            path(f) + "/" + id(branch),
                                            Map.of("profile", input, "version", 0))
                                    .statusCode())
                    .isEqualTo(400);
        }
    }

    @Test
    void validatesCoordinatesTimezoneDeliveryAddressAndBounds() throws Exception {
        var f = fixture();
        for (var entry :
                Map.<String, Object>of(
                                "latitude",
                                91,
                                "longitude",
                                -181,
                                "timezone",
                                "Invalid/Zone",
                                "deliveryModel",
                                "DRIVER",
                                "countryCode",
                                "egy",
                                "name",
                                " ",
                                "phone",
                                "0123456789")
                        .entrySet()) {
            var input = profile();
            input.put(entry.getKey(), entry.getValue());
            assertThat(f.browser().send("POST", path(f), input).statusCode())
                    .as(entry.getKey())
                    .isEqualTo(400);
        }
        var half = profile();
        half.remove("longitude");
        assertThat(f.browser().send("POST", path(f), half).statusCode()).isEqualTo(400);
        assertThat(f.browser().send("GET", path(f) + "?size=101", null).statusCode())
                .isEqualTo(400);
    }

    @Test
    void persistsWeeklyAndSpecialHoursWithClosedDays() throws Exception {
        var f = fixture();
        var branch = create(f);
        String url = path(f) + "/" + id(branch);
        assertThat(
                        body(f.browser().send("GET", url + "/availability", null), 200)
                                .get("state")
                                .asText())
                .isEqualTo("CLOSED");
        var closure = new HashMap<String, Object>();
        closure.put("date", "2026-12-25");
        closure.put("opensAt", null);
        closure.put("closesAt", null);
        var result =
                body(
                        f.browser()
                                .send(
                                        "PUT",
                                        url + "/hours",
                                        hours(
                                                List.of(weekly(1, "09:00", "23:00")),
                                                List.of(closure),
                                                0)),
                        200);
        assertThat(result.get("version").asLong()).isEqualTo(1);
        var stored = body(f.browser().send("GET", url + "/hours", null), 200);
        assertThat(stored.get("weekly").size()).isEqualTo(1);
        assertThat(stored.get("special").get(0).get("opensAt").isNull()).isTrue();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM branch_opening_hours WHERE branch_id=? AND"
                                        + " weekday=2",
                                Integer.class,
                                id(branch)))
                .isZero();
    }

    @Test
    void rejectsInvalidAndDuplicateHoursWithoutChangingVersion() throws Exception {
        var f = fixture();
        var branch = create(f);
        String url = path(f) + "/" + id(branch) + "/hours";
        for (var row :
                List.of(
                        weekly(0, "09:00", "23:00"),
                        weekly(8, "09:00", "23:00"),
                        weekly(1, "23:00", "09:00"),
                        weekly(1, "09:00", "09:00"),
                        weekly(1, "09:00:01", "23:00")))
            assertThat(f.browser().send("PUT", url, hours(List.of(row), List.of(), 0)).statusCode())
                    .isEqualTo(400);
        var row = weekly(1, "09:00", "23:00");
        assertThat(
                        f.browser()
                                .send("PUT", url, hours(List.of(row, row), List.of(), 0))
                                .statusCode())
                .isEqualTo(400);
        assertThat(body(f.browser().send("GET", url, null), 200).get("version").asLong()).isZero();
    }

    @Test
    void operationSeparatesPauseInactivityAndParentSuspension() throws Exception {
        var f = fixture();
        var branch = create(f);
        String url = path(f) + "/" + id(branch);
        body(
                f.browser()
                        .send(
                                "PUT",
                                url + "/operation",
                                Map.of("status", "ACTIVE", "paused", true, "version", 0)),
                200);
        assertThat(
                        body(f.browser().send("GET", url + "/availability", null), 200)
                                .get("state")
                                .asText())
                .isEqualTo("PAUSED");
        body(
                f.browser()
                        .send(
                                "PUT",
                                url + "/operation",
                                Map.of("status", "INACTIVE", "paused", false, "version", 1)),
                200);
        assertThat(
                        body(f.browser().send("GET", url + "/availability", null), 200)
                                .get("state")
                                .asText())
                .isEqualTo("INACTIVE");
        body(
                f.admin()
                        .browser()
                        .send(
                                "PUT",
                                "/admin/restaurants/" + f.restaurant() + "/status",
                                Map.of("status", "SUSPENDED", "reason", "Review", "version", 0)),
                200);
        assertThat(
                        body(f.browser().send("GET", url + "/availability", null), 200)
                                .get("state")
                                .asText())
                .isEqualTo("RESTAURANT_SUSPENDED");
    }

    @Test
    void existingMemberAssignmentIsAtomicAndDoesNotGrantRoles() throws Exception {
        var f = fixture();
        var branch = create(f);
        String url = path(f) + "/" + id(branch);
        var assigned =
                body(
                        f.browser()
                                .send(
                                        "PUT",
                                        url + "/staff/" + f.owner().id(),
                                        Map.of("version", 0)),
                        200);
        assertThat(assigned.get("version").asLong()).isEqualTo(1);
        assertThat(body(f.browser().send("GET", url + "/staff", null), 200).get("total").asLong())
                .isEqualTo(1);
        assertThat(
                        f.browser()
                                .send("PUT", url + "/staff/" + f.owner().id(), Map.of("version", 1))
                                .statusCode())
                .isEqualTo(409);
        assertThat(
                        jdbc.queryForList(
                                "SELECT role_name FROM user_roles WHERE user_id=?",
                                String.class,
                                f.owner().id()))
                .containsExactlyInAnyOrder("CUSTOMER", "RESTAURANT_OWNER");
        body(
                f.admin()
                        .browser()
                        .send("DELETE", url + "/staff/" + f.owner().id(), Map.of("version", 1)),
                200);
        assertThat(body(f.browser().send("GET", url + "/staff", null), 200).get("total").asLong())
                .isZero();
    }

    @Test
    void unauthorizedAndNonmemberStaffAssignmentAreRejected() throws Exception {
        var f = fixture();
        var other = fixture();
        var customer = account("CUSTOMER");
        var branch = create(f);
        String url = path(f) + "/" + id(branch) + "/staff/";
        assertThat(
                        customer.browser()
                                .send("PUT", url + customer.id(), Map.of("version", 0))
                                .statusCode())
                .isEqualTo(403);
        assertThat(
                        other.browser()
                                .send("PUT", url + other.owner().id(), Map.of("version", 0))
                                .statusCode())
                .isEqualTo(404);
        assertThat(f.browser().send("PUT", url + customer.id(), Map.of("version", 0)).statusCode())
                .isEqualTo(400);
        assertThat(
                        f.admin()
                                .browser()
                                .send("PUT", url + other.owner().id(), Map.of("version", 0))
                                .statusCode())
                .isEqualTo(400);
        assertThat(
                        f.browser()
                                .send(
                                        "PUT",
                                        url + f.owner().id(),
                                        Map.of("version", 0, "role", "ADMIN"))
                                .statusCode())
                .isEqualTo(400);
        jdbc.update("UPDATE users SET status='SUSPENDED' WHERE id=?", f.owner().id());
        assertThat(
                        f.admin()
                                .browser()
                                .send("PUT", url + f.owner().id(), Map.of("version", 0))
                                .statusCode())
                .isEqualTo(400);
    }

    @Test
    void concurrentEditsHaveOneWinner() throws Exception {
        var f = fixture();
        var branch = create(f);
        String url = path(f) + "/" + id(branch);
        var gate = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var one =
                    executor.submit(
                            () -> {
                                gate.await();
                                return f.browser()
                                        .send(
                                                "PUT",
                                                url,
                                                Map.of("profile", profile(), "version", 0))
                                        .statusCode();
                            });
            var two =
                    executor.submit(
                            () -> {
                                gate.await();
                                return f.admin()
                                        .browser()
                                        .send(
                                                "PUT",
                                                url,
                                                Map.of("profile", profile(), "version", 0))
                                        .statusCode();
                            });
            gate.countDown();
            assertThat(List.of(one.get(15, TimeUnit.SECONDS), two.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 409);
        }
        assertThat(
                        f.browser()
                                .send("PUT", url + "/hours", hours(List.of(), List.of(), 0))
                                .statusCode())
                .isEqualTo(409);
    }

    @Test
    void scheduleFailureRollsBackChildReplacementAndVersion() throws Exception {
        var f = fixture();
        var branch = create(f);
        String url = path(f) + "/" + id(branch) + "/hours";
        body(
                f.browser()
                        .send(
                                "PUT",
                                url,
                                hours(List.of(weekly(1, "09:00", "23:00")), List.of(), 0)),
                200);
        doAnswer(
                        invocation -> {
                            invocation.callRealMethod();
                            throw new IllegalStateException("Injected schedule failure");
                        })
                .when(journal)
                .replaceHours(eq(id(branch)), any());
        try {
            assertThat(f.browser().send("PUT", url, hours(List.of(), List.of(), 1)).statusCode())
                    .isEqualTo(500);
        } finally {
            reset(journal);
        }
        var result = body(f.browser().send("GET", url, null), 200);
        assertThat(result.get("version").asLong()).isEqualTo(1);
        assertThat(result.get("weekly").size()).isEqualTo(1);
    }

    @Test
    void staffFailureRollsBackAssignmentAndAggregateVersion() throws Exception {
        var f = fixture();
        var branch = create(f);
        String url = path(f) + "/" + id(branch);
        doAnswer(
                        invocation -> {
                            invocation.callRealMethod();
                            throw new IllegalStateException("Injected assignment failure");
                        })
                .when(journal)
                .assign(eq(id(branch)), any(), any(), any(), any());
        try {
            assertThat(
                            f.browser()
                                    .send(
                                            "PUT",
                                            url + "/staff/" + f.owner().id(),
                                            Map.of("version", 0))
                                    .statusCode())
                    .isEqualTo(500);
        } finally {
            reset(journal);
        }
        assertThat(body(f.browser().send("GET", url, null), 200).get("version").asLong()).isZero();
        assertThat(body(f.browser().send("GET", url + "/staff", null), 200).get("total").asLong())
                .isZero();
    }

    @Test
    void databaseConstraintsPreventReparentingInvalidHoursAndAssignments() throws Exception {
        var f = fixture();
        var other = fixture();
        var branch = create(f);
        UUID bid = id(branch);
        sqlState(
                "23503",
                () ->
                        jdbc.update(
                                "INSERT INTO"
                                    + " branches(id,restaurant_id,name,address_line1,city,country_code,timezone,delivery_model,status,created_at,updated_at)"
                                    + " VALUES"
                                    + " (?,?,'Test','Street','Cairo','EG','Africa/Cairo','RESTAURANT_DELIVERY','ACTIVE',now(),now())",
                                UUID.randomUUID(),
                                UUID.randomUUID()));
        sqlState("23514", () -> jdbc.update("UPDATE branches SET longitude=NULL WHERE id=?", bid));
        sqlState(
                "23514",
                () ->
                        jdbc.update(
                                "INSERT INTO branch_special_hours VALUES"
                                    + " (?,'2026-12-25',NULL,'23:00')",
                                bid));
        sqlState(
                "23514",
                () ->
                        jdbc.update(
                                "UPDATE branches SET restaurant_id=? WHERE id=?",
                                other.restaurant(),
                                bid));
        sqlState("23514", () -> jdbc.update("UPDATE branches SET latitude=91 WHERE id=?", bid));
        sqlState(
                "23514",
                () -> jdbc.update("UPDATE branches SET timezone='Bad/Zone' WHERE id=?", bid));
        sqlState(
                "23514",
                () ->
                        jdbc.update(
                                "INSERT INTO branch_opening_hours VALUES (?,8,'09:00','23:00')",
                                bid));
        sqlState(
                "23514",
                () ->
                        jdbc.update(
                                "INSERT INTO branch_opening_hours VALUES (?,1,'23:00','09:00')",
                                bid));
        jdbc.update("INSERT INTO branch_opening_hours VALUES (?,1,'09:00','23:00')", bid);
        sqlState(
                "23505",
                () ->
                        jdbc.update(
                                "INSERT INTO branch_opening_hours VALUES (?,1,'10:00','22:00')",
                                bid));
        sqlState(
                "23503",
                () ->
                        jdbc.update(
                                "INSERT INTO branch_staff_assignments VALUES (?,?,?,?,now())",
                                bid,
                                f.restaurant(),
                                other.owner().id(),
                                f.admin().id()));
        jdbc.update(
                "INSERT INTO branch_staff_assignments VALUES (?,?,?,?,now())",
                bid,
                f.restaurant(),
                f.owner().id(),
                f.admin().id());
        sqlState(
                "23505",
                () ->
                        jdbc.update(
                                "INSERT INTO branch_staff_assignments VALUES (?,?,?,?,now())",
                                bid,
                                f.restaurant(),
                                f.owner().id(),
                                f.admin().id()));
        sqlState(
                "23503",
                () ->
                        jdbc.update(
                                "INSERT INTO branch_opening_hours VALUES (?,1,'09:00','23:00')",
                                UUID.randomUUID()));
    }
}
