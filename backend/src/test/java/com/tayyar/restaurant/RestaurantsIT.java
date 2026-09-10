package com.tayyar.restaurant;

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
class RestaurantsIT extends PostgresIntegrationTest {
    @Value("${local.server.port}")
    int port;

    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    @Autowired PlatformTransactionManager transactions;
    @MockitoSpyBean RestaurantJournal journal;
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

    @Test
    void customerSubmitsAndReadsOnlyTheirOwnApplication() throws Exception {
        var customer = account("CUSTOMER");
        var other = account("CUSTOMER");
        var application = apply(customer);
        assertThat(application.get("status").asText()).isEqualTo("PENDING");
        assertThat(application.get("applicantId").asText()).isEqualTo(customer.id().toString());
        assertThat(
                        body(
                                        customer.browser()
                                                .send(
                                                        "GET",
                                                        "/restaurant-applications?size=1",
                                                        null),
                                        200)
                                .get("items")
                                .size())
                .isEqualTo(1);
        for (String suffix : List.of("", "/submissions", "/decisions")) {
            assertThat(
                            other.browser()
                                    .send(
                                            "GET",
                                            "/restaurant-applications/" + id(application) + suffix,
                                            null)
                                    .statusCode())
                    .isEqualTo(404);
        }
    }

    @Test
    void anonymousAndPrivilegedFieldInjectionAreRejected() throws Exception {
        var anonymous = new Browser();
        anonymous.csrf();
        assertThat(
                        anonymous
                                .send(
                                        "POST",
                                        "/restaurant-applications",
                                        Map.of("name", "Name", "description", ""))
                                .statusCode())
                .isEqualTo(401);
        var customer = account("CUSTOMER");
        for (String field : List.of("applicantId", "userId", "status", "roles", "ownerId")) {
            var input = new HashMap<String, Object>();
            input.put("name", "Name");
            input.put("description", "");
            input.put(field, "ADMIN");
            assertThat(
                            customer.browser()
                                    .send("POST", "/restaurant-applications", input)
                                    .statusCode())
                    .isEqualTo(400);
        }
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM restaurant_applications WHERE applicant_id=?",
                                Integer.class,
                                customer.id()))
                .isZero();
        customer.browser().token = null;
        assertThat(
                        customer.browser()
                                .send(
                                        "POST",
                                        "/restaurant-applications",
                                        Map.of("name", "Name", "description", ""))
                                .statusCode())
                .isEqualTo(403);
    }

    @Test
    void approvalCreatesRestaurantRoleMembershipAndAuditAtomically() throws Exception {
        var customer = account("CUSTOMER");
        var admin = account("ADMIN");
        var application = apply(customer);
        var result = review(admin, application, "APPROVED");
        UUID restaurant = UUID.fromString(result.get("restaurantId").asText());
        assertThat(result.get("status").asText()).isEqualTo("APPROVED");
        assertThat(
                        jdbc.queryForList(
                                "SELECT role_name FROM user_roles WHERE user_id=?",
                                String.class,
                                customer.id()))
                .containsExactlyInAnyOrder("CUSTOMER", "RESTAURANT_OWNER");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT membership_type FROM restaurant_memberships WHERE"
                                        + " restaurant_id=? AND user_id=?",
                                String.class,
                                restaurant,
                                customer.id()))
                .isEqualTo("OWNER");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM application_decisions WHERE application_id=?",
                                Integer.class,
                                id(application)))
                .isEqualTo(1);
        assertThat(customer.browser().send("GET", "/users/me", null).statusCode()).isEqualTo(401);
        var owner = login(customer.email());
        assertThat(
                        body(owner.send("GET", "/restaurants/" + restaurant, null), 200)
                                .get("status")
                                .asText())
                .isEqualTo("ACTIVE");
        assertThat(
                        body(
                                        owner.send(
                                                "GET",
                                                "/restaurants/" + restaurant + "/memberships",
                                                null),
                                        200)
                                .get("total")
                                .asLong())
                .isEqualTo(1);
    }

    @Test
    void customersCannotReviewAndAdminsCannotReviewThemselves() throws Exception {
        var customer = account("CUSTOMER");
        var application = apply(customer);
        var input = Map.of("outcome", "APPROVED", "version", 0);
        assertThat(
                        customer.browser()
                                .send(
                                        "POST",
                                        "/admin/restaurant-applications/"
                                                + id(application)
                                                + "/decisions",
                                        input)
                                .statusCode())
                .isEqualTo(403);
        var selfAdmin = account("ADMIN", "CUSTOMER");
        var own = apply(selfAdmin);
        assertThat(
                        selfAdmin
                                .browser()
                                .send(
                                        "POST",
                                        "/admin/restaurant-applications/" + id(own) + "/decisions",
                                        input)
                                .statusCode())
                .isEqualTo(403);
    }

    @Test
    void rejectedApplicationResubmitsWithoutLosingHistory() throws Exception {
        var customer = account("CUSTOMER");
        var admin = account("ADMIN");
        var original = apply(customer);
        var rejected = review(admin, original, "REJECTED");
        var revised =
                body(
                        customer.browser()
                                .send(
                                        "POST",
                                        "/restaurant-applications/" + id(original) + "/submissions",
                                        Map.of(
                                                "name",
                                                "Revised Brand",
                                                "description",
                                                "Updated description",
                                                "version",
                                                rejected.get("version").asLong())),
                        201);
        assertThat(revised.get("revision").asInt()).isEqualTo(2);
        assertThat(revised.get("status").asText()).isEqualTo("PENDING");
        var history =
                body(
                        customer.browser()
                                .send(
                                        "GET",
                                        "/restaurant-applications/" + id(original) + "/decisions",
                                        null),
                        200);
        assertThat(history.get("items").get(0).get("reason").asText())
                .isEqualTo("Reviewed documents");
        assertThat(
                        body(
                                        customer.browser()
                                                .send(
                                                        "GET",
                                                        "/restaurant-applications/"
                                                                + id(original)
                                                                + "/submissions",
                                                        null),
                                        200)
                                .get("total")
                                .asLong())
                .isEqualTo(2);
        review(admin, revised, "APPROVED");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM application_decisions WHERE application_id=?",
                                Integer.class,
                                id(original)))
                .isEqualTo(2);
    }

    @Test
    void rejectsMissingReasonInvalidInputAndInvalidTransitions() throws Exception {
        var customer = account("CUSTOMER");
        var admin = account("ADMIN");
        var application = apply(customer);
        assertThat(
                        admin.browser()
                                .send(
                                        "POST",
                                        "/admin/restaurant-applications/"
                                                + id(application)
                                                + "/decisions",
                                        Map.of("outcome", "REJECTED", "version", 0))
                                .statusCode())
                .isEqualTo(422);
        assertThat(
                        customer.browser()
                                .send(
                                        "POST",
                                        "/restaurant-applications/"
                                                + id(application)
                                                + "/submissions",
                                        Map.of("name", "New", "description", "", "version", 0))
                                .statusCode())
                .isEqualTo(409);
        assertThat(
                        customer.browser()
                                .send(
                                        "POST",
                                        "/restaurant-applications",
                                        Map.of("name", " ", "description", ""))
                                .statusCode())
                .isEqualTo(400);
        assertThat(
                        customer.browser()
                                .send("GET", "/restaurant-applications?size=101", null)
                                .statusCode())
                .isEqualTo(400);
        assertThat(
                        admin.browser()
                                .send("GET", "/admin/restaurant-applications?page=-1", null)
                                .statusCode())
                .isEqualTo(400);
    }

    @Test
    void ownerCanEditOnlyOwnedRestaurantsAndNotTheirStatus() throws Exception {
        var first = account("CUSTOMER");
        var second = account("CUSTOMER");
        var admin = account("ADMIN");
        UUID own = approvedRestaurant(first, admin);
        UUID other = approvedRestaurant(second, admin);
        var owner = login(first.email());
        for (String suffix : List.of("", "/memberships"))
            assertThat(owner.send("GET", "/restaurants/" + other + suffix, null).statusCode())
                    .isEqualTo(404);
        var update = Map.of("name", "Changed Brand", "description", "Updated", "version", 0);
        assertThat(owner.send("PUT", "/restaurants/" + other, update).statusCode()).isEqualTo(404);
        var changed = body(owner.send("PUT", "/restaurants/" + own, update), 200);
        assertThat(changed.get("version").asLong()).isEqualTo(1);
        assertThat(owner.send("PUT", "/restaurants/" + own, update).statusCode()).isEqualTo(409);
        assertThat(
                        owner.send(
                                        "PUT",
                                        "/admin/restaurants/" + own + "/status",
                                        Map.of(
                                                "status",
                                                "SUSPENDED",
                                                "reason",
                                                "test",
                                                "version",
                                                1))
                                .statusCode())
                .isEqualTo(403);
        assertThat(
                        owner.send(
                                        "POST",
                                        "/restaurants/" + own + "/memberships",
                                        Map.of("userId", second.id()))
                                .statusCode())
                .isEqualTo(405);
        var inject = new HashMap<String, Object>(update);
        inject.put("status", "SUSPENDED");
        assertThat(owner.send("PUT", "/restaurants/" + own, inject).statusCode()).isEqualTo(400);
    }

    @Test
    void adminSuspensionAndReactivationAreVersionedAndAudited() throws Exception {
        var customer = account("CUSTOMER");
        var admin = account("ADMIN");
        UUID restaurant = approvedRestaurant(customer, admin);
        body(
                admin.browser()
                        .send(
                                "PUT",
                                "/admin/restaurants/" + restaurant + "/status",
                                Map.of(
                                        "status",
                                        "SUSPENDED",
                                        "reason",
                                        "Operations review",
                                        "version",
                                        0)),
                200);
        body(
                admin.browser()
                        .send(
                                "PUT",
                                "/admin/restaurants/" + restaurant + "/status",
                                Map.of(
                                        "status",
                                        "ACTIVE",
                                        "reason",
                                        "Review complete",
                                        "version",
                                        1)),
                200);
        assertThat(
                        body(
                                        admin.browser()
                                                .send(
                                                        "GET",
                                                        "/admin/restaurants/"
                                                                + restaurant
                                                                + "/status-history",
                                                        null),
                                        200)
                                .get("total")
                                .asLong())
                .isEqualTo(2);
    }

    @Test
    void doubleApprovalHasOneWinnerAndOneConflict() throws Exception {
        var customer = account("CUSTOMER");
        var first = account("ADMIN");
        var second = account("ADMIN");
        var application = apply(customer);
        var input = Map.of("outcome", "APPROVED", "reason", "Approved", "version", 0);
        String path = "/admin/restaurant-applications/" + id(application) + "/decisions";
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var one = executor.submit(() -> first.browser().send("POST", path, input).statusCode());
            var two =
                    executor.submit(() -> second.browser().send("POST", path, input).statusCode());
            assertThat(List.of(one.get(), two.get())).containsExactlyInAnyOrder(201, 409);
        }
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM restaurants WHERE application_id=?",
                                Integer.class,
                                id(application)))
                .isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM application_decisions WHERE application_id=?",
                                Integer.class,
                                id(application)))
                .isEqualTo(1);
    }

    @Test
    void auditFailureRollsBackEveryApprovalSideEffect() throws Exception {
        var customer = account("CUSTOMER");
        var admin = account("ADMIN");
        var application = apply(customer);
        doThrow(new IllegalStateException("Simulated audit persistence failure"))
                .when(journal)
                .recordDecision(eq(id(application)), anyInt(), any(), any(), any(), any(), any());
        try {
            assertThat(
                            admin.browser()
                                    .send(
                                            "POST",
                                            "/admin/restaurant-applications/"
                                                    + id(application)
                                                    + "/decisions",
                                            Map.of("outcome", "APPROVED", "version", 0))
                                    .statusCode())
                    .isEqualTo(500);
        } finally {
            reset(journal);
        }
        assertThat(
                        jdbc.queryForObject(
                                "SELECT status FROM restaurant_applications WHERE id=?",
                                String.class,
                                id(application)))
                .isEqualTo("PENDING");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM restaurants WHERE application_id=?",
                                Integer.class,
                                id(application)))
                .isZero();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM restaurant_memberships WHERE user_id=?",
                                Integer.class,
                                customer.id()))
                .isZero();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM application_decisions WHERE application_id=?",
                                Integer.class,
                                id(application)))
                .isZero();
        assertThat(
                        jdbc.queryForList(
                                "SELECT role_name FROM user_roles WHERE user_id=?",
                                String.class,
                                customer.id()))
                .containsExactly("CUSTOMER");
        assertThat(customer.browser().send("GET", "/users/me", null).statusCode()).isEqualTo(200);
    }

    @Test
    void multipleOwnersWorkAndDatabasePreventsDuplicatesAndLastOwnerRemoval() throws Exception {
        var first = account("CUSTOMER");
        var second = account("CUSTOMER", "RESTAURANT_OWNER");
        var admin = account("ADMIN");
        UUID restaurant = approvedRestaurant(first, admin);
        jdbc.update(
                "INSERT INTO"
                    + " restaurant_memberships(restaurant_id,user_id,membership_type,created_at)"
                    + " VALUES (?,?,'OWNER',?)",
                restaurant,
                second.id(),
                Timestamp.from(Instant.now()));
        body(second.browser().send("GET", "/restaurants/" + restaurant, null), 200);
        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "INSERT INTO"
                                            + " restaurant_memberships(restaurant_id,user_id,membership_type,created_at)"
                                            + " VALUES (?,?,'OWNER',?)",
                                        restaurant,
                                        second.id(),
                                        Timestamp.from(Instant.now())))
                .rootCause()
                .isInstanceOfSatisfying(
                        SQLException.class, e -> assertThat(e.getSQLState()).isEqualTo("23505"));
        jdbc.update(
                "DELETE FROM restaurant_memberships WHERE restaurant_id=? AND user_id=?",
                restaurant,
                second.id());
        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "DELETE FROM restaurant_memberships WHERE restaurant_id=?",
                                        restaurant))
                .rootCause()
                .isInstanceOfSatisfying(
                        SQLException.class, e -> assertThat(e.getSQLState()).isEqualTo("23514"));
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM restaurant_memberships WHERE restaurant_id=?",
                                Integer.class,
                                restaurant))
                .isEqualTo(1);
    }

    @Test
    void concurrentOwnerRemovalsCannotLeaveRestaurantOwnerless() throws Exception {
        var first = account("CUSTOMER");
        var second = account("CUSTOMER", "RESTAURANT_OWNER");
        var admin = account("ADMIN");
        UUID restaurant = approvedRestaurant(first, admin);
        jdbc.update(
                "INSERT INTO"
                    + " restaurant_memberships(restaurant_id,user_id,membership_type,created_at)"
                    + " VALUES (?,?,'OWNER',?)",
                restaurant,
                second.id(),
                Timestamp.from(Instant.now()));
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var removals = new ArrayList<Future<String>>();
            for (UUID owner : List.of(first.id(), second.id())) {
                removals.add(
                        executor.submit(
                                () -> {
                                    ready.countDown();
                                    if (!start.await(10, TimeUnit.SECONDS))
                                        throw new AssertionError("Start timeout");
                                    try {
                                        new TransactionTemplate(transactions)
                                                .executeWithoutResult(
                                                        status ->
                                                                jdbc.update(
                                                                        "DELETE FROM"
                                                                            + " restaurant_memberships"
                                                                            + " WHERE"
                                                                            + " restaurant_id=? AND"
                                                                            + " user_id=?",
                                                                        restaurant,
                                                                        owner));
                                        return "committed";
                                    } catch (RuntimeException failure) {
                                        Throwable cause = failure;
                                        while (cause.getCause() != null) cause = cause.getCause();
                                        if (cause instanceof SQLException sql)
                                            return sql.getSQLState();
                                        throw failure;
                                    }
                                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(
                            List.of(
                                    removals.get(0).get(15, TimeUnit.SECONDS),
                                    removals.get(1).get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("committed", "23514");
        }
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM restaurant_memberships WHERE restaurant_id=?",
                                Integer.class,
                                restaurant))
                .isEqualTo(1);
    }

    @Test
    void historyIsImmutableAndSelfReviewConstraintWorks() throws Exception {
        var customer = account("CUSTOMER");
        var admin = account("ADMIN");
        var application = apply(customer);
        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "INSERT INTO"
                                            + " application_decisions(application_id,revision,reviewer_id,outcome,reason,decided_at)"
                                            + " VALUES (?,1,?,'REJECTED','Reason',?)",
                                        id(application),
                                        customer.id(),
                                        Timestamp.from(Instant.now())))
                .rootCause()
                .isInstanceOfSatisfying(
                        SQLException.class, e -> assertThat(e.getSQLState()).isEqualTo("23514"));
        review(admin, application, "REJECTED");
        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "DELETE FROM application_decisions WHERE application_id=?",
                                        id(application)))
                .rootCause()
                .isInstanceOfSatisfying(
                        SQLException.class, e -> assertThat(e.getSQLState()).isEqualTo("23514"));
        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "UPDATE application_submissions SET name='Changed' WHERE"
                                                + " application_id=?",
                                        id(application)))
                .rootCause()
                .isInstanceOfSatisfying(
                        SQLException.class, e -> assertThat(e.getSQLState()).isEqualTo("23514"));
    }

    @Test
    void queueIsFilteredPaginatedAndRejectionDoesNotGrantOwnership() throws Exception {
        var customer = account("CUSTOMER");
        var admin = account("ADMIN");
        var rejected = apply(customer);
        review(admin, rejected, "REJECTED");
        apply(customer);
        var queue =
                body(
                        admin.browser()
                                .send(
                                        "GET",
                                        "/admin/restaurant-applications?status=PENDING&size=1",
                                        null),
                        200);
        assertThat(queue.get("items").size()).isLessThanOrEqualTo(1);
        assertThat(queue.get("items").get(0).get("status").asText()).isEqualTo("PENDING");
        assertThat(
                        jdbc.queryForList(
                                "SELECT role_name FROM user_roles WHERE user_id=?",
                                String.class,
                                customer.id()))
                .containsExactly("CUSTOMER");
    }
}
