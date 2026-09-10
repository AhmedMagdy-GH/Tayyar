package com.tayyar.address;

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
class AddressesIT extends PostgresIntegrationTest {
    @Value("${local.server.port}")
    int port;

    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    @Autowired PlatformTransactionManager transactions;
    @MockitoSpyBean AddressJournal journal;
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

    private static final String BASE = "/users/me/addresses";

    Map<String, Object> profile() {
        return new HashMap<>(
                Map.of(
                        "label",
                        "Home / custom",
                        "street",
                        "Test Street",
                        "building",
                        "12A",
                        "city",
                        "Cairo",
                        "countryCode",
                        "EG",
                        "instructions",
                        "Ring once",
                        "latitude",
                        new java.math.BigDecimal("30.044400"),
                        "longitude",
                        new java.math.BigDecimal("31.235700")));
    }

    JsonNode create(Account customer) throws Exception {
        return body(customer.browser().send("POST", BASE, profile()), 201);
    }

    UUID id(JsonNode node) {
        return UUID.fromString(node.get("id").asText());
    }

    void sqlState(String expected, org.assertj.core.api.ThrowableAssert.ThrowingCallable work) {
        assertThatThrownBy(work)
                .rootCause()
                .isInstanceOfSatisfying(
                        SQLException.class, e -> assertThat(e.getSQLState()).isEqualTo(expected));
    }

    @Test
    void customerCreatesMultipleAddressesAndListsOnlyOwn() throws Exception {
        var customer = account("CUSTOMER");
        var other = account("CUSTOMER");
        var first = create(customer);
        var second = create(customer);
        assertThat(first.get("isDefault").asBoolean()).isTrue();
        assertThat(second.get("isDefault").asBoolean()).isFalse();
        assertThat(first.get("profile").get("latitude").decimalValue())
                .isEqualByComparingTo("30.044400");
        assertThat(
                        body(customer.browser().send("GET", BASE + "/" + id(first), null), 200)
                                .get("profile")
                                .get("label")
                                .asText())
                .isEqualTo("Home / custom");
        var listed = body(customer.browser().send("GET", BASE + "?size=1", null), 200);
        assertThat(listed.get("items").size()).isEqualTo(1);
        assertThat(listed.get("total").asLong()).isEqualTo(2);
        assertThat(body(other.browser().send("GET", BASE, null), 200).get("total").asLong())
                .isZero();
    }

    @Test
    void anonymousAndNoncustomerRolesCannotUseAddresses() throws Exception {
        var anonymous = new Browser();
        anonymous.csrf();
        assertThat(anonymous.send("POST", BASE, profile()).statusCode()).isEqualTo(401);
        for (String role : List.of("ADMIN", "DRIVER", "RESTAURANT_OWNER", "RESTAURANT_STAFF")) {
            var actor = account(role);
            assertThat(actor.browser().send("GET", BASE, null).statusCode()).isEqualTo(403);
            assertThat(actor.browser().send("POST", BASE, profile()).statusCode()).isEqualTo(403);
        }
    }

    @Test
    void everyResourceOperationIsScopedToOwnerEvenForAdminCustomers() throws Exception {
        var customer = account("CUSTOMER");
        var other = account("CUSTOMER", "ADMIN");
        var address = create(customer);
        String url = BASE + "/" + id(address);
        assertThat(other.browser().send("GET", url, null).statusCode()).isEqualTo(404);
        assertThat(
                        other.browser()
                                .send("PUT", url, Map.of("profile", profile(), "version", 0))
                                .statusCode())
                .isEqualTo(404);
        assertThat(other.browser().send("DELETE", url, Map.of("version", 0)).statusCode())
                .isEqualTo(404);
        assertThat(other.browser().send("PUT", url + "/default", Map.of("version", 0)).statusCode())
                .isEqualTo(404);
        assertThat(
                        customer.browser()
                                .send("GET", "/users/" + customer.id() + "/addresses", null)
                                .statusCode())
                .isEqualTo(403);
        assertThat(body(customer.browser().send("GET", url, null), 200).get("version").asLong())
                .isZero();
    }

    @Test
    void ownershipDefaultAndRoleInjectionAreRejected() throws Exception {
        var customer = account("CUSTOMER");
        var address = create(customer);
        for (String field :
                List.of("userId", "customerId", "ownerId", "isDefault", "roles", "status")) {
            var input = profile();
            input.put(field, "ADMIN");
            assertThat(customer.browser().send("POST", BASE, input).statusCode()).isEqualTo(400);
            assertThat(
                            customer.browser()
                                    .send(
                                            "PUT",
                                            BASE + "/" + id(address),
                                            Map.of("profile", input, "version", 0))
                                    .statusCode())
                    .isEqualTo(400);
        }
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM customer_addresses WHERE user_id=?",
                                Integer.class,
                                customer.id()))
                .isEqualTo(1);
    }

    @Test
    void coordinatesValidateRangePrecisionAndPairing() throws Exception {
        var customer = account("CUSTOMER");
        for (var entry :
                Map.of(
                                "latitude",
                                new java.math.BigDecimal("90.000001"),
                                "longitude",
                                new java.math.BigDecimal("-180.000001"))
                        .entrySet()) {
            var input = profile();
            input.put(entry.getKey(), entry.getValue());
            assertThat(customer.browser().send("POST", BASE, input).statusCode()).isEqualTo(400);
        }
        var precise = profile();
        precise.put("latitude", new java.math.BigDecimal("30.0000001"));
        assertThat(customer.browser().send("POST", BASE, precise).statusCode()).isEqualTo(400);
        var half = profile();
        half.remove("longitude");
        assertThat(customer.browser().send("POST", BASE, half).statusCode()).isEqualTo(400);
        var absent = profile();
        absent.remove("latitude");
        absent.remove("longitude");
        body(customer.browser().send("POST", BASE, absent), 201);
        var edges = profile();
        edges.put("latitude", -90);
        edges.put("longitude", 180);
        body(customer.browser().send("POST", BASE, edges), 201);
    }

    @Test
    void requiredAndOversizedFieldsAreRejectedWithoutEchoingInput() throws Exception {
        var customer = account("CUSTOMER");
        var limits =
                Map.of(
                        "label",
                        81,
                        "street",
                        201,
                        "building",
                        81,
                        "floor",
                        41,
                        "apartment",
                        41,
                        "landmark",
                        201,
                        "instructions",
                        1001,
                        "city",
                        101,
                        "region",
                        101,
                        "postalCode",
                        21);
        for (var field : limits.entrySet()) {
            var input = profile();
            String secret = "x".repeat(field.getValue());
            input.put(field.getKey(), secret);
            var response = customer.browser().send("POST", BASE, input);
            assertThat(response.statusCode()).isEqualTo(400);
            assertThat(response.body()).doesNotContain(secret);
        }
        var blank = profile();
        blank.put("street", " ");
        assertThat(customer.browser().send("POST", BASE, blank).statusCode()).isEqualTo(400);
        assertThat(customer.browser().send("GET", BASE + "?size=101", null).statusCode())
                .isEqualTo(400);
    }

    @Test
    void defaultReplacementAdvancesBothVersionsAndRejectsStaleEdits() throws Exception {
        var customer = account("CUSTOMER");
        var first = create(customer);
        var second = create(customer);
        body(
                customer.browser()
                        .send("PUT", BASE + "/" + id(second) + "/default", Map.of("version", 0)),
                200);
        var old = body(customer.browser().send("GET", BASE + "/" + id(first), null), 200);
        assertThat(old.get("isDefault").asBoolean()).isFalse();
        assertThat(old.get("version").asLong()).isEqualTo(1);
        assertThat(
                        customer.browser()
                                .send(
                                        "PUT",
                                        BASE + "/" + id(first),
                                        Map.of("profile", profile(), "version", 0))
                                .statusCode())
                .isEqualTo(409);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM customer_addresses WHERE user_id=? AND"
                                    + " is_default",
                                Integer.class,
                                customer.id()))
                .isEqualTo(1);
    }

    @Test
    void concurrentDefaultSelectionsSerializeAndKeepExactlyOneDefault() throws Exception {
        var customer = account("CUSTOMER");
        create(customer);
        var second = create(customer);
        var third = create(customer);
        var otherSession = login(customer.email());
        var start = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var one =
                    executor.submit(
                            () -> {
                                start.await();
                                return customer.browser()
                                        .send(
                                                "PUT",
                                                BASE + "/" + id(second) + "/default",
                                                Map.of("version", 0))
                                        .statusCode();
                            });
            var two =
                    executor.submit(
                            () -> {
                                start.await();
                                return otherSession
                                        .send(
                                                "PUT",
                                                BASE + "/" + id(third) + "/default",
                                                Map.of("version", 0))
                                        .statusCode();
                            });
            start.countDown();
            assertThat(List.of(one.get(15, TimeUnit.SECONDS), two.get(15, TimeUnit.SECONDS)))
                    .containsOnly(200);
        }
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM customer_addresses WHERE user_id=? AND"
                                    + " is_default",
                                Integer.class,
                                customer.id()))
                .isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT id FROM customer_addresses WHERE user_id=? AND is_default",
                                UUID.class,
                                customer.id()))
                .isIn(id(second), id(third));
    }

    @Test
    void concurrentProfileUpdatesHaveOneWinner() throws Exception {
        var customer = account("CUSTOMER");
        var address = create(customer);
        var second = login(customer.email());
        var start = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var one =
                    executor.submit(
                            () -> {
                                start.await();
                                return customer.browser()
                                        .send(
                                                "PUT",
                                                BASE + "/" + id(address),
                                                Map.of("profile", profile(), "version", 0))
                                        .statusCode();
                            });
            var two =
                    executor.submit(
                            () -> {
                                start.await();
                                return second.send(
                                                "PUT",
                                                BASE + "/" + id(address),
                                                Map.of("profile", profile(), "version", 0))
                                        .statusCode();
                            });
            start.countDown();
            assertThat(List.of(one.get(15, TimeUnit.SECONDS), two.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 409);
        }
        assertThat(
                        customer.browser()
                                .send("DELETE", BASE + "/" + id(address), Map.of("version", 0))
                                .statusCode())
                .isEqualTo(409);
    }

    @Test
    void deletingDefaultLeavesNoneAndDeletingAllResetsFirstAddressBehavior() throws Exception {
        var customer = account("CUSTOMER");
        var first = create(customer);
        var second = create(customer);
        assertThat(
                        customer.browser()
                                .send("DELETE", BASE + "/" + id(first), Map.of("version", 0))
                                .statusCode())
                .isEqualTo(204);
        assertThat(customer.browser().send("GET", BASE + "/" + id(first), null).statusCode())
                .isEqualTo(404);
        assertThat(
                        body(customer.browser().send("GET", BASE + "/" + id(second), null), 200)
                                .get("isDefault")
                                .asBoolean())
                .isFalse();
        assertThat(
                        customer.browser()
                                .send("DELETE", BASE + "/" + id(second), Map.of("version", 0))
                                .statusCode())
                .isEqualTo(204);
        assertThat(create(customer).get("isDefault").asBoolean()).isTrue();
    }

    @Test
    void failureAfterClearingDefaultRestoresFlagAndVersions() throws Exception {
        var customer = account("CUSTOMER");
        var first = create(customer);
        var second = create(customer);
        doAnswer(
                        invocation -> {
                            invocation.callRealMethod();
                            throw new IllegalStateException("Injected default replacement failure");
                        })
                .when(journal)
                .clearDefault(eq(customer.id()), any(), any());
        try {
            assertThat(
                            customer.browser()
                                    .send(
                                            "PUT",
                                            BASE + "/" + id(second) + "/default",
                                            Map.of("version", 0))
                                    .statusCode())
                    .isEqualTo(500);
        } finally {
            reset(journal);
        }
        var original = body(customer.browser().send("GET", BASE + "/" + id(first), null), 200);
        var target = body(customer.browser().send("GET", BASE + "/" + id(second), null), 200);
        assertThat(original.get("isDefault").asBoolean()).isTrue();
        assertThat(original.get("version").asLong()).isZero();
        assertThat(target.get("isDefault").asBoolean()).isFalse();
        assertThat(target.get("version").asLong()).isZero();
    }

    @Test
    void csrfAndAccountStatusRemainEnforced() throws Exception {
        var customer = account("CUSTOMER");
        var first = create(customer);
        customer.browser().token = null;
        assertThat(customer.browser().send("POST", BASE, profile()).statusCode()).isEqualTo(403);
        assertThat(
                        customer.browser()
                                .send("DELETE", BASE + "/" + id(first), Map.of("version", 0))
                                .statusCode())
                .isEqualTo(403);
        assertThat(
                        customer.browser()
                                .send(
                                        "PUT",
                                        BASE + "/" + id(first) + "/default",
                                        Map.of("version", 0))
                                .statusCode())
                .isEqualTo(403);
        jdbc.update("UPDATE users SET status='SUSPENDED' WHERE id=?", customer.id());
        assertThat(customer.browser().send("GET", BASE, null).statusCode()).isEqualTo(401);
    }

    @Test
    void databaseProtectsOwnerDefaultCoordinatesAndReferences() throws Exception {
        var customer = account("CUSTOMER");
        var other = account("CUSTOMER");
        var first = create(customer);
        var second = create(customer);
        sqlState(
                "23505",
                () ->
                        jdbc.update(
                                "UPDATE customer_addresses SET is_default=TRUE WHERE id=?",
                                id(second)));
        sqlState(
                "23514",
                () ->
                        jdbc.update(
                                "UPDATE customer_addresses SET user_id=? WHERE id=?",
                                other.id(),
                                id(first)));
        sqlState(
                "23514",
                () ->
                        jdbc.update(
                                "UPDATE customer_addresses SET latitude=91 WHERE id=?", id(first)));
        sqlState(
                "23514",
                () ->
                        jdbc.update(
                                "UPDATE customer_addresses SET longitude=-181 WHERE id=?",
                                id(first)));
        sqlState(
                "23514",
                () ->
                        jdbc.update(
                                "UPDATE customer_addresses SET longitude=NULL WHERE id=?",
                                id(first)));
        sqlState(
                "23503",
                () ->
                        jdbc.update(
                                "INSERT INTO"
                                    + " customer_addresses(id,user_id,label,street,building,city,country_code,created_at,updated_at)"
                                    + " VALUES (?,?,'Home','Street','12','Cairo','EG',now(),now())",
                                UUID.randomUUID(),
                                UUID.randomUUID()));
    }

    @Test
    void addressLimitIsEnforcedAndReadsRemainBounded() throws Exception {
        var customer = account("CUSTOMER");
        create(customer);
        jdbc.update(
                "INSERT INTO"
                    + " customer_addresses(id,user_id,label,street,building,city,country_code,created_at,updated_at)"
                    + " SELECT gen_random_uuid(),?,'Other','Street','12','Cairo','EG',now(),now()"
                    + " FROM generate_series(1,99)",
                customer.id());
        assertThat(customer.browser().send("POST", BASE, profile()).statusCode()).isEqualTo(400);
        assertThat(
                        body(customer.browser().send("GET", BASE + "?size=20", null), 200)
                                .get("items")
                                .size())
                .isEqualTo(20);
    }
}
