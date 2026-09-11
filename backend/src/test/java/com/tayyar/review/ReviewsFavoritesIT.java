package com.tayyar.review;

import static org.assertj.core.api.Assertions.*;

import com.tayyar.support.PostgresIntegrationTest;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.*;

import java.net.*;
import java.net.http.*;
import java.util.*;
import java.util.concurrent.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"tayyar.identity.login-limit=1000", "tayyar.identity.csrf-limit=1000"})
class ReviewsFavoritesIT extends PostgresIntegrationTest {
    static final String PASSWORD = "Reviews favorites password!";
    @Value("${local.server.port}") int port;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    @Autowired PlatformTransactionManager transactions;
    final HttpClient http = HttpClient.newHttpClient();
    UUID customer, foreignCustomer, admin, owner, restaurant, branch, delivered, placed;
    Browser customerBrowser, foreignBrowser, adminBrowser, ownerBrowser;

    class Browser {
        String cookie, token;
        HttpResponse<String> send(String method, String path, Object input) throws Exception {
            var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1" + path));
            if (cookie != null) request.header("Cookie", cookie);
            if (token != null) request.header("X-CSRF-TOKEN", token);
            if (input != null) request.header("Content-Type", "application/json");
            var response = http.send(request.method(method, input == null
                            ? HttpRequest.BodyPublishers.noBody()
                            : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(input))).build(),
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

    @BeforeEach void fixture() throws Exception {
        customer = account("CUSTOMER"); foreignCustomer = account("CUSTOMER");
        admin = account("ADMIN"); owner = account("RESTAURANT_OWNER");
        restaurant = restaurant(owner); branch = branch(restaurant);
        delivered = order(customer, "DELIVERED"); placed = order(customer, "PLACED");
        customerBrowser = login(customer); foreignBrowser = login(foreignCustomer);
        adminBrowser = login(admin); ownerBrowser = login(owner);
    }

    UUID account(String role) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id,full_name,email,password_hash,status,created_at,updated_at) " +
                        "VALUES (?,'Private Name',?,?,'ACTIVE',now(),now())",
                id, id + "@example.com", passwords.encode(PASSWORD));
        jdbc.update("INSERT INTO user_roles VALUES (?,?)", id, role);
        return id;
    }

    UUID restaurant(UUID restaurantOwner) {
        UUID app = UUID.randomUUID(), id = UUID.randomUUID();
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            jdbc.update("INSERT INTO restaurant_applications(id,applicant_id,status,current_revision,created_at,updated_at) " +
                    "VALUES (?,?,'APPROVED',1,now(),now())", app, restaurantOwner);
            jdbc.update("INSERT INTO application_submissions VALUES (?,1,'Review Kitchen','Submission',now())", app);
            jdbc.update("INSERT INTO restaurants(id,application_id,name,description,status,created_at,updated_at) " +
                    "VALUES (?,?,'Review Kitchen','Public description','ACTIVE',now(),now())", id, app);
            jdbc.update("INSERT INTO restaurant_memberships VALUES (?,?,'OWNER',now())", id, restaurantOwner);
        });
        return id;
    }

    UUID branch(UUID restaurantId) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO branches(id,restaurant_id,name,address_line1,city,country_code,timezone," +
                        "delivery_model,status,created_at,updated_at) VALUES (?,?,'Main','Street','Cairo','EG'," +
                        "'Africa/Cairo','RESTAURANT_DELIVERY','ACTIVE',now(),now())", id, restaurantId);
        return id;
    }

    UUID order(UUID buyer, String status) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO orders(id,customer_id,restaurant_id,branch_id,status,currency," +
                        "merchandise_subtotal,delivery_fee,discount_total,final_total,created_at,updated_at) " +
                        "VALUES (?,?,?,?,?,'EGP',100,10,0,110,now(),now())",
                id, buyer, restaurant, branch, status);
        return id;
    }

    Browser login(UUID user) throws Exception {
        Browser browser = new Browser(); browser.csrf();
        assertThat(browser.send("POST", "/auth/session", Map.of("email", user + "@example.com", "password", PASSWORD)).statusCode())
                .isEqualTo(204);
        browser.csrf(); return browser;
    }

    JsonNode body(HttpResponse<String> response, int status) throws Exception {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
        return response.body().isBlank() ? null : json.readTree(response.body());
    }

    @Test void verifiedPurchaseBoundariesValidationAndPrivacy() throws Exception {
        body(customerBrowser.send("POST", "/orders/" + placed + "/review", Map.of("rating", 5)), 409);
        body(foreignBrowser.send("POST", "/orders/" + delivered + "/review", Map.of("rating", 5)), 404);
        body(customerBrowser.send("POST", "/orders/" + delivered + "/review", Map.of("rating", 0)), 400);
        body(customerBrowser.send("POST", "/orders/" + delivered + "/review", Map.of("rating", 6)), 400);
        body(customerBrowser.send("POST", "/orders/" + delivered + "/review",
                Map.of("rating", 5, "comment", "x".repeat(2001))), 400);
        var created = body(customerBrowser.send("POST", "/orders/" + delivered + "/review",
                Map.of("rating", 1, "comment", "  Good  ")), 201);
        assertThat(created.get("rating").asInt()).isEqualTo(1);
        assertThat(created.get("comment").asText()).isEqualTo("Good");
        var publicPage = body(new Browser().send("GET", "/discovery/restaurants/" + restaurant + "/reviews", null), 200);
        assertThat(publicPage.get("ratingSummary").get("averageRating").decimalValue()).isEqualByComparingTo("1.00");
        assertThat(publicPage.toString()).doesNotContain(customer.toString(), "email", "customerId", "moderationStatus");
    }

    @Test void duplicateCreationIncludingConcurrentRequestsHasOneWinner() throws Exception {
        var responses = race(
                () -> customerBrowser.send("POST", "/orders/" + delivered + "/review", Map.of("rating", 4)),
                () -> login(customer).send("POST", "/orders/" + delivered + "/review", Map.of("rating", 5)));
        assertThat(responses.stream().map(HttpResponse::statusCode).toList()).containsExactlyInAnyOrder(201, 409);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reviews WHERE order_id=?", Integer.class, delivered)).isEqualTo(1);
    }

    @Test void editUsesVersionAndCannotMassAssignContext() throws Exception {
        var created = body(customerBrowser.send("POST", "/orders/" + delivered + "/review", Map.of("rating", 5)), 201);
        body(customerBrowser.send("PATCH", "/orders/" + delivered + "/review",
                Map.of("rating", 4, "comment", "Changed", "version", 0)), 200);
        body(customerBrowser.send("PATCH", "/orders/" + delivered + "/review",
                Map.of("rating", 3, "version", 0)), 409);
        body(foreignBrowser.send("PATCH", "/orders/" + delivered + "/review",
                Map.of("rating", 3, "version", 1)), 404);
        body(customerBrowser.send("PATCH", "/orders/" + delivered + "/review",
                Map.of("rating", 3, "version", 1, "restaurantId", UUID.randomUUID())), 400);
        assertThat(created.get("orderId").asText()).isEqualTo(delivered.toString());
    }

    @Test void moderationControlsPublicAggregateWithoutChangingContent() throws Exception {
        var created = body(customerBrowser.send("POST", "/orders/" + delivered + "/review",
                Map.of("rating", 5, "comment", "Keep me")), 201);
        UUID review = UUID.fromString(created.get("id").asText());
        body(ownerBrowser.send("PATCH", "/admin/reviews/" + review + "/moderation",
                Map.of("status", "HIDDEN", "version", 0)), 403);
        var hidden = body(adminBrowser.send("PATCH", "/admin/reviews/" + review + "/moderation",
                Map.of("status", "HIDDEN", "version", 0)), 200);
        assertThat(hidden.get("comment").asText()).isEqualTo("Keep me");
        var page = body(new Browser().send("GET", "/discovery/restaurants/" + restaurant + "/reviews", null), 200);
        assertThat(page.get("total").asLong()).isZero();
        assertThat(page.get("ratingSummary").get("averageRating").isNull()).isTrue();
        body(adminBrowser.send("PATCH", "/admin/reviews/" + review + "/moderation",
                Map.of("status", "VISIBLE", "version", 1)), 200);
        assertThat(body(new Browser().send("GET", "/discovery/restaurants/" + restaurant + "/reviews?size=1", null), 200)
                .get("total").asLong()).isEqualTo(1);
    }

    @Test void favoritesAreIdempotentPrivateAndBounded() throws Exception {
        body(customerBrowser.send("PUT", "/favorites/" + restaurant, null), 204);
        body(customerBrowser.send("PUT", "/favorites/" + restaurant, null), 204);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM favorites WHERE customer_id=?", Integer.class, customer)).isEqualTo(1);
        var page = body(customerBrowser.send("GET", "/favorites?page=0&size=1", null), 200);
        assertThat(page.get("total").asLong()).isEqualTo(1);
        assertThat(page.toString()).doesNotContain(customer.toString());
        assertThat(body(foreignBrowser.send("GET", "/favorites", null), 200).get("total").asLong()).isZero();
        assertThat(new Browser().send("GET", "/favorites", null).statusCode()).isEqualTo(401);
        body(ownerBrowser.send("GET", "/favorites", null), 403);
        body(customerBrowser.send("GET", "/favorites?size=101", null), 400);
        body(customerBrowser.send("DELETE", "/favorites/" + restaurant, null), 204);
        body(customerBrowser.send("DELETE", "/favorites/" + restaurant, null), 204);
    }

    @Test void csrfUnknownFieldsMalformedIdsAndSuspensionAreRejected() throws Exception {
        String token = customerBrowser.token; customerBrowser.token = null;
        body(customerBrowser.send("POST", "/orders/" + delivered + "/review", Map.of("rating", 5)), 403);
        customerBrowser.token = token;
        body(customerBrowser.send("POST", "/orders/" + delivered + "/review",
                Map.of("rating", 5, "customerId", customer)), 400);
        body(customerBrowser.send("PUT", "/favorites/" + UUID.randomUUID(), null), 404);
        body(customerBrowser.send("GET", "/orders/not-a-uuid/review", null), 400);
        jdbc.update("UPDATE users SET status='SUSPENDED' WHERE id=?", customer);
        assertThat(customerBrowser.send("GET", "/favorites", null).statusCode()).isIn(401, 403);
    }

    @Test void databaseConstraintsProtectRatingPurchaseContextAndRetention() throws Exception {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO reviews(id,order_id,customer_id,restaurant_id,branch_id,rating,created_at,updated_at) " +
                        "VALUES (?,?,?,?,?,0,now(),now())",
                UUID.randomUUID(), delivered, customer, restaurant, branch))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO reviews(id,order_id,customer_id,restaurant_id,branch_id,rating,created_at,updated_at) " +
                        "VALUES (?,?,?,?,?,5,now(),now())",
                UUID.randomUUID(), delivered, foreignCustomer, restaurant, branch))
                .isInstanceOf(DataIntegrityViolationException.class);
        body(customerBrowser.send("POST", "/orders/" + delivered + "/review", Map.of("rating", 5)), 201);
        assertThatThrownBy(() -> jdbc.update("UPDATE reviews SET branch_id=? WHERE order_id=?",
                UUID.randomUUID(), delivered)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM reviews WHERE order_id=?", delivered))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    List<HttpResponse<String>> race(Callable<HttpResponse<String>> first, Callable<HttpResponse<String>> second)
            throws Exception {
        var start = new CountDownLatch(1); var pool = Executors.newFixedThreadPool(2);
        try {
            var one = pool.submit(() -> { start.await(); return first.call(); });
            var two = pool.submit(() -> { start.await(); return second.call(); });
            start.countDown(); return List.of(one.get(20, TimeUnit.SECONDS), two.get(20, TimeUnit.SECONDS));
        } finally { pool.shutdownNow(); }
    }
}
