package com.tayyar.cart;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.tayyar.support.PostgresIntegrationTest;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import tools.jackson.databind.*;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"tayyar.identity.login-limit=1000", "tayyar.identity.csrf-limit=1000"})
class CartIT extends PostgresIntegrationTest {
    static final String PASSWORD = "Cart integration password!";

    @Value("${local.server.port}")
    int port;

    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    @Autowired PlatformTransactionManager transactions;
    @MockitoSpyBean CartStore store;

    UUID customer, restaurant, branch, menu, category, item;
    Browser browser;
    final HttpClient http = HttpClient.newHttpClient();

    record Account(UUID id, Browser browser) {}

    class Browser {
        String cookie;
        String token;

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

    @BeforeEach
    void fixture() throws Exception {
        customer = accountRow("CUSTOMER");
        restaurant = restaurant(customer, "Cart Kitchen");
        branch = branch(restaurant, "Primary");
        menu = UUID.randomUUID();
        category = UUID.randomUUID();
        item = UUID.randomUUID();
        new TransactionTemplate(transactions)
                .executeWithoutResult(
                        status -> {
                            jdbc.update(
                                    "INSERT INTO"
                                        + " restaurant_menus(id,restaurant_id,name,created_at,updated_at)"
                                        + " VALUES (?,?,'Main',now(),now())",
                                    menu,
                                    restaurant);
                            jdbc.update(
                                    "INSERT INTO"
                                        + " menu_categories(id,menu_id,restaurant_id,name,position,created_at,updated_at)"
                                        + " VALUES (?,?,?,'Meals',0,now(),now())",
                                    category,
                                    menu,
                                    restaurant);
                            addItem(item, category, restaurant, "Chicken", "80.00", 0);
                        });
        browser = login(customer);
    }

    UUID accountRow(String... roles) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO"
                        + " users(id,full_name,email,password_hash,status,created_at,updated_at)"
                        + " VALUES (?,'Cart User',?,?,'ACTIVE',now(),now())",
                id,
                id + "@example.com",
                passwords.encode(PASSWORD));
        for (String role : roles)
            jdbc.update("INSERT INTO user_roles(user_id,role_name) VALUES (?,?)", id, role);
        return id;
    }

    UUID restaurant(UUID owner, String name) {
        UUID application = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        new TransactionTemplate(transactions)
                .executeWithoutResult(
                        status -> {
                            jdbc.update(
                                    "INSERT INTO"
                                        + " restaurant_applications(id,applicant_id,status,current_revision,created_at,updated_at)"
                                        + " VALUES (?,?,'APPROVED',1,now(),now())",
                                    application,
                                    owner);
                            jdbc.update(
                                    "INSERT INTO application_submissions VALUES (?,1,?,'Cart"
                                            + " test',now())",
                                    application,
                                    name);
                            jdbc.update(
                                    "INSERT INTO"
                                        + " restaurants(id,application_id,name,description,status,created_at,updated_at)"
                                        + " VALUES (?,?,?,'Public','ACTIVE',now(),now())",
                                    id,
                                    application,
                                    name);
                            jdbc.update(
                                    "INSERT INTO restaurant_memberships VALUES (?,?,'OWNER',now())",
                                    id,
                                    owner);
                        });
        return id;
    }

    UUID branch(UUID restaurantId, String name) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
INSERT INTO branches(id,restaurant_id,name,address_line1,city,country_code,timezone,delivery_model,status,created_at,updated_at)
VALUES (?,?,?,'Street','Cairo','EG','Africa/Cairo','RESTAURANT_DELIVERY','ACTIVE',now(),now())
""",
                id,
                restaurantId,
                name);
        return id;
    }

    void addItem(
            UUID id, UUID categoryId, UUID restaurantId, String name, String price, int position) {
        jdbc.update(
                "INSERT INTO"
                    + " menu_items(id,category_id,restaurant_id,name,base_price,position,created_at,updated_at)"
                    + " VALUES (?,?,?,?,?,?,now(),now())",
                id,
                categoryId,
                restaurantId,
                name,
                new BigDecimal(price),
                position);
    }

    Browser login(UUID account) throws Exception {
        var result = new Browser();
        result.csrf();
        var response =
                result.send(
                        "POST",
                        "/auth/session",
                        Map.of("email", account + "@example.com", "password", PASSWORD));
        assertThat(response.statusCode()).as(response.body()).isEqualTo(204);
        result.csrf();
        return result;
    }

    JsonNode body(HttpResponse<String> response, int status) throws Exception {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
        return response.body().isBlank() ? null : json.readTree(response.body());
    }

    Map<String, Object> add(UUID branchId, UUID itemId, int quantity, Long version) {
        var result = new HashMap<String, Object>();
        result.put("branchId", branchId);
        result.put("menuItemId", itemId);
        result.put("quantity", quantity);
        if (version != null) {
            result.put(
                    "cartId",
                    jdbc.queryForObject(
                            "SELECT id FROM carts WHERE customer_id=? AND status='ACTIVE'",
                            UUID.class,
                            customer));
            result.put("cartVersion", version);
        }
        return result;
    }

    JsonNode first() throws Exception {
        return body(browser.send("POST", "/cart/items", add(branch, item, 1, null)), 201);
    }

    @Test
    void lazyCreationBasePricingTotalsAndDeterministicIncrement() throws Exception {
        assertThat(body(browser.send("GET", "/cart", null), 204)).isNull();
        JsonNode cart = first();
        assertThat(cart.get("branch").get("id").asText()).isEqualTo(branch.toString());
        assertThat(cart.get("currency").asText()).isEqualTo("EGP");
        assertThat(cart.get("merchandiseSubtotal").decimalValue()).isEqualByComparingTo("80.00");
        JsonNode line = cart.get("items").get(0);
        assertThat(line.get("acknowledgedUnitPrice").decimalValue()).isEqualByComparingTo("80");
        assertThat(line.get("currentUnitPrice").decimalValue()).isEqualByComparingTo("80");
        assertThat(line.get("lineSubtotal").decimalValue()).isEqualByComparingTo("80");
        cart =
                body(
                        browser.send(
                                "POST",
                                "/cart/items",
                                add(branch, item, 2, cart.get("version").asLong())),
                        200);
        assertThat(cart.get("items")).hasSize(1);
        assertThat(cart.get("items").get(0).get("quantity").asInt()).isEqualTo(3);
        assertThat(cart.get("merchandiseSubtotal").decimalValue()).isEqualByComparingTo("240");
        UUID second = UUID.randomUUID();
        addItem(second, category, restaurant, "Rice", "20.00", 1);
        cart =
                body(
                        browser.send(
                                "POST",
                                "/cart/items",
                                add(branch, second, 2, cart.get("version").asLong())),
                        200);
        assertThat(cart.get("items")).hasSize(2);
        assertThat(cart.get("merchandiseSubtotal").decimalValue()).isEqualByComparingTo("280");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM carts WHERE customer_id=?",
                                Integer.class,
                                customer))
                .isEqualTo(1);
    }

    @Test
    void priceIsServerDerivedAndChangesAreNeverSilentlyAcknowledged() throws Exception {
        jdbc.update(
                "INSERT INTO branch_menu_item_overrides VALUES (?,?,?,true,95,now(),now())",
                branch,
                item,
                restaurant);
        JsonNode cart = first();
        JsonNode line = cart.get("items").get(0);
        assertThat(line.get("acknowledgedUnitPrice").decimalValue()).isEqualByComparingTo("95");
        jdbc.update("UPDATE branch_menu_item_overrides SET price=110 WHERE branch_id=?", branch);
        line = body(browser.send("GET", "/cart", null), 200).get("items").get(0);
        assertThat(line.get("acknowledgedUnitPrice").decimalValue()).isEqualByComparingTo("95");
        assertThat(line.get("currentUnitPrice").decimalValue()).isEqualByComparingTo("110");
        assertThat(line.get("priceChanged").asBoolean()).isTrue();
        assertThat(line.get("lineSubtotal").decimalValue()).isEqualByComparingTo("110");
        cart = body(browser.send("GET", "/cart", null), 200);
        body(
                browser.send(
                        "POST", "/cart/items", add(branch, item, 1, cart.get("version").asLong())),
                200);
        line = body(browser.send("GET", "/cart", null), 200).get("items").get(0);
        assertThat(line.get("acknowledgedUnitPrice").decimalValue()).isEqualByComparingTo("95");
        assertThat(line.get("quantity").asInt()).isEqualTo(2);
    }

    @Test
    void priceAndOwnershipFieldsCannotBeInjected() throws Exception {
        for (String field :
                List.of(
                        "unitPrice",
                        "subtotal",
                        "total",
                        "customerId",
                        "restaurantId",
                        "available")) {
            var input = add(branch, item, 1, null);
            input.put(field, field.equals("customerId") ? customer : 1);
            body(browser.send("POST", "/cart/items", input), 400);
        }
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM carts WHERE customer_id=?",
                                Integer.class,
                                customer))
                .isZero();
    }

    @Test
    void unavailableItemsAreRejectedWhileExistingLinesReportLaterDrift() throws Exception {
        JsonNode cart = first();
        jdbc.update("UPDATE menu_items SET available=false WHERE id=?", item);
        JsonNode read = body(browser.send("GET", "/cart", null), 200);
        assertThat(read.get("id")).isEqualTo(cart.get("id"));
        assertThat(read.get("items").get(0).get("currentlyAvailable").asBoolean()).isFalse();
        UUID second = UUID.randomUUID();
        addItem(second, category, restaurant, "Unavailable", "30", 1);
        jdbc.update("UPDATE menu_items SET available=false WHERE id=?", second);
        body(
                browser.send(
                        "POST",
                        "/cart/items",
                        add(branch, second, 1, cart.get("version").asLong())),
                409);
        jdbc.update("UPDATE menu_items SET available=true WHERE id=?", second);
        jdbc.update("UPDATE menu_items SET active=false WHERE id=?", second);
        body(
                browser.send(
                        "POST",
                        "/cart/items",
                        add(branch, second, 1, cart.get("version").asLong())),
                409);
        jdbc.update("UPDATE menu_items SET active=true WHERE id=?", second);
        jdbc.update("UPDATE menu_categories SET active=false WHERE id=?", category);
        body(
                browser.send(
                        "POST",
                        "/cart/items",
                        add(branch, second, 1, cart.get("version").asLong())),
                409);
        jdbc.update("UPDATE menu_categories SET active=true WHERE id=?", category);
        jdbc.update("UPDATE restaurant_menus SET active=false WHERE id=?", menu);
        body(
                browser.send(
                        "POST",
                        "/cart/items",
                        add(branch, second, 1, cart.get("version").asLong())),
                409);
        assertThat(
                        body(browser.send("GET", "/cart", null), 200)
                                .get("items")
                                .get(0)
                                .get("currentlyAvailable")
                                .asBoolean())
                .isFalse();
    }

    @Test
    void branchStateIsSeparateAndExistingCartSurvivesOperationalChanges() throws Exception {
        first();
        jdbc.update("UPDATE branches SET paused=true WHERE id=?", branch);
        JsonNode cart = body(browser.send("GET", "/cart", null), 200);
        assertThat(cart.get("branch").get("state").asText()).isEqualTo("PAUSED");
        assertThat(cart.get("items").get(0).get("currentlyAvailable").asBoolean()).isTrue();
        jdbc.update("UPDATE branches SET paused=false,status='INACTIVE' WHERE id=?", branch);
        cart = body(browser.send("GET", "/cart", null), 200);
        assertThat(cart.get("branch").get("state").asText()).isEqualTo("INACTIVE");
        UUID second = UUID.randomUUID();
        addItem(second, category, restaurant, "Bread", "10", 1);
        body(
                browser.send(
                        "POST",
                        "/cart/items",
                        add(branch, second, 1, cart.get("version").asLong())),
                409);
        jdbc.update("UPDATE restaurants SET status='SUSPENDED' WHERE id=?", restaurant);
        assertThat(
                        body(browser.send("GET", "/cart", null), 200)
                                .get("branch")
                                .get("state")
                                .asText())
                .isEqualTo("RESTAURANT_SUSPENDED");
    }

    @Test
    void crossRestaurantInjectionAndCrossBranchSwitchNeedExplicitReplacement() throws Exception {
        JsonNode old = first();
        UUID otherBranch = branch(restaurant, "Secondary");
        body(
                browser.send(
                        "POST",
                        "/cart/items",
                        add(otherBranch, item, 1, old.get("version").asLong())),
                409);
        JsonNode replacement =
                body(
                        browser.send(
                                "POST",
                                "/cart/replace",
                                Map.of(
                                        "branchId",
                                        otherBranch,
                                        "menuItemId",
                                        item,
                                        "quantity",
                                        1,
                                        "cartId",
                                        UUID.fromString(old.get("id").asText()),
                                        "cartVersion",
                                        old.get("version").asLong())),
                        201);
        assertThat(replacement.get("id")).isNotEqualTo(old.get("id"));
        assertThat(replacement.get("branch").get("id").asText()).isEqualTo(otherBranch.toString());
        assertThat(
                        jdbc.queryForObject(
                                "SELECT status FROM carts WHERE id=?",
                                String.class,
                                UUID.fromString(old.get("id").asText())))
                .isEqualTo("REPLACED");
        UUID otherRestaurant = restaurant(customer, "Other Kitchen");
        UUID foreignBranch = branch(otherRestaurant, "Foreign");
        UUID foreignMenu = UUID.randomUUID(),
                foreignCategory = UUID.randomUUID(),
                foreignItem = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO restaurant_menus(id,restaurant_id,name,created_at,updated_at) VALUES"
                        + " (?,?,'Other',now(),now())",
                foreignMenu,
                otherRestaurant);
        jdbc.update(
                "INSERT INTO"
                    + " menu_categories(id,menu_id,restaurant_id,name,position,created_at,updated_at)"
                    + " VALUES (?,?,?,'Other',0,now(),now())",
                foreignCategory,
                foreignMenu,
                otherRestaurant);
        addItem(foreignItem, foreignCategory, otherRestaurant, "Foreign meal", "25", 0);
        body(
                browser.send(
                        "POST",
                        "/cart/items",
                        add(otherBranch, foreignItem, 1, replacement.get("version").asLong())),
                404);
        body(
                browser.send(
                        "POST",
                        "/cart/items",
                        add(foreignBranch, item, 1, replacement.get("version").asLong())),
                409);
    }

    @Test
    void replacementRollsBackAfterOldCartRetirement() throws Exception {
        JsonNode old = first();
        UUID otherBranch = branch(restaurant, "Rollback target");
        doThrow(new IllegalStateException("injected replacement failure"))
                .when(store)
                .createCart(eq(customer), eq(otherBranch), eq(restaurant), any(Instant.class));
        body(
                browser.send(
                        "POST",
                        "/cart/replace",
                        Map.of(
                                "branchId",
                                otherBranch,
                                "menuItemId",
                                item,
                                "quantity",
                                1,
                                "cartId",
                                UUID.fromString(old.get("id").asText()),
                                "cartVersion",
                                old.get("version").asLong())),
                500);
        reset(store);
        JsonNode current = body(browser.send("GET", "/cart", null), 200);
        assertThat(current.get("id")).isEqualTo(old.get("id"));
        assertThat(
                        jdbc.queryForObject(
                                "SELECT status FROM carts WHERE id=?",
                                String.class,
                                UUID.fromString(old.get("id").asText())))
                .isEqualTo("ACTIVE");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM carts WHERE customer_id=? AND"
                                        + " status='ACTIVE'",
                                Integer.class,
                                customer))
                .isEqualTo(1);
    }

    @Test
    void updateRemoveClearAndEmptyCartBehavior() throws Exception {
        JsonNode cart = first();
        UUID secondItem = UUID.randomUUID();
        addItem(secondItem, category, restaurant, "Rice", "20", 1);
        cart =
                body(
                        browser.send(
                                "POST",
                                "/cart/items",
                                add(branch, secondItem, 1, cart.get("version").asLong())),
                        200);
        JsonNode line = cart.get("items").get(0);
        cart =
                body(
                        browser.send(
                                "PATCH",
                                "/cart/items/" + line.get("id").asText(),
                                Map.of(
                                        "quantity",
                                        4,
                                        "cartId",
                                        UUID.fromString(cart.get("id").asText()),
                                        "cartVersion",
                                        cart.get("version").asLong(),
                                        "itemVersion",
                                        line.get("version").asLong())),
                        200);
        assertThat(cart.get("merchandiseSubtotal").decimalValue()).isEqualByComparingTo("340");
        line = cart.get("items").get(0);
        cart =
                body(
                        browser.send(
                                "DELETE",
                                "/cart/items/"
                                        + line.get("id").asText()
                                        + "?cartId="
                                        + cart.get("id").asText()
                                        + "&cartVersion="
                                        + cart.get("version").asLong()
                                        + "&itemVersion="
                                        + line.get("version").asLong(),
                                null),
                        200);
        assertThat(cart.get("items")).hasSize(1);
        line = cart.get("items").get(0);
        body(
                browser.send(
                        "DELETE",
                        "/cart/items/"
                                + line.get("id").asText()
                                + "?cartId="
                                + cart.get("id").asText()
                                + "&cartVersion="
                                + cart.get("version").asLong()
                                + "&itemVersion="
                                + line.get("version").asLong(),
                        null),
                204);
        body(browser.send("GET", "/cart", null), 204);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT status FROM carts WHERE id=?",
                                String.class,
                                UUID.fromString(cart.get("id").asText())))
                .isEqualTo("ABANDONED");
        cart = first();
        body(
                browser.send(
                        "DELETE",
                        "/cart?cartId="
                                + cart.get("id").asText()
                                + "&version="
                                + cart.get("version").asLong(),
                        null),
                204);
        body(browser.send("GET", "/cart", null), 204);
    }

    @Test
    void invalidQuantitiesVersionsAndForgedLineIdsAreRejected() throws Exception {
        for (int quantity : new int[] {-1, 0, 100, Integer.MAX_VALUE})
            body(browser.send("POST", "/cart/items", add(branch, item, quantity, null)), 400);
        JsonNode cart = first();
        JsonNode line = cart.get("items").get(0);
        body(
                browser.send(
                        "POST", "/cart/items", add(branch, item, 99, cart.get("version").asLong())),
                400);
        body(
                browser.send(
                        "PATCH",
                        "/cart/items/" + UUID.randomUUID(),
                        Map.of(
                                "quantity",
                                2,
                                "cartId",
                                UUID.fromString(cart.get("id").asText()),
                                "cartVersion",
                                cart.get("version").asLong(),
                                "itemVersion",
                                0)),
                404);
        body(
                browser.send(
                        "DELETE", "/cart?cartId=" + cart.get("id").asText() + "&version=-1", null),
                400);
        body(
                browser.send(
                        "DELETE",
                        "/cart?cartId="
                                + cart.get("id").asText()
                                + "&version=0&customerId="
                                + customer,
                        null),
                400);
        body(
                browser.send(
                        "DELETE",
                        "/cart/items/"
                                + line.get("id").asText()
                                + "?cartId="
                                + cart.get("id").asText()
                                + "&cartVersion=0",
                        null),
                400);
        body(browser.send("GET", "/cart?customerId=" + customer, null), 400);
    }

    @Test
    void customersAreIsolatedAndNoUserAddressedCartRouteExists() throws Exception {
        JsonNode first = first();
        UUID other = accountRow("CUSTOMER");
        Browser otherBrowser = login(other);
        assertThat(body(otherBrowser.send("GET", "/cart", null), 204)).isNull();
        JsonNode theirs =
                body(otherBrowser.send("POST", "/cart/items", add(branch, item, 2, null)), 201);
        assertThat(theirs.get("id")).isNotEqualTo(first.get("id"));
        JsonNode line = first.get("items").get(0);
        body(
                otherBrowser.send(
                        "PATCH",
                        "/cart/items/" + line.get("id").asText(),
                        Map.of(
                                "quantity",
                                3,
                                "cartId",
                                UUID.fromString(theirs.get("id").asText()),
                                "cartVersion",
                                0,
                                "itemVersion",
                                0)),
                404);
        assertThat(otherBrowser.send("GET", "/users/" + customer + "/cart", null).statusCode())
                .isEqualTo(403);
        assertThat(body(browser.send("GET", "/cart", null), 200).get("id"))
                .isEqualTo(first.get("id"));
    }

    @Test
    void authenticationRolesCsrfAndAccountStatusAreEnforced() throws Exception {
        var anonymous = new Browser();
        assertThat(anonymous.send("GET", "/cart", null).statusCode()).isEqualTo(401);
        for (String role : List.of("RESTAURANT_OWNER", "RESTAURANT_STAFF", "DRIVER", "ADMIN")) {
            Browser denied = login(accountRow(role));
            assertThat(denied.send("GET", "/cart", null).statusCode()).isEqualTo(403);
        }
        String token = browser.token;
        browser.token = null;
        assertThat(browser.send("POST", "/cart/items", add(branch, item, 1, null)).statusCode())
                .isEqualTo(403);
        browser.token = token;
        first();
        jdbc.update("UPDATE users SET status='SUSPENDED' WHERE id=?", customer);
        assertThat(browser.send("GET", "/cart", null).statusCode()).isEqualTo(401);
    }

    @Test
    void simultaneousFirstAddsCreateOneActiveCartAndOneConflict() throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        Callable<Integer> request =
                () -> {
                    ready.countDown();
                    start.await();
                    return browser.send("POST", "/cart/items", add(branch, item, 1, null))
                            .statusCode();
                };
        Future<Integer> first = pool.submit(request), second = pool.submit(request);
        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                .containsExactlyInAnyOrder(201, 409);
        pool.shutdownNow();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM carts WHERE customer_id=? AND"
                                        + " status='ACTIVE'",
                                Integer.class,
                                customer))
                .isEqualTo(1);
        assertThat(
                        body(browser.send("GET", "/cart", null), 200)
                                .get("items")
                                .get(0)
                                .get("quantity")
                                .asInt())
                .isEqualTo(1);
    }

    @Test
    void concurrentStaleQuantityUpdateDoesNotLoseChanges() throws Exception {
        JsonNode cart = first();
        JsonNode line = cart.get("items").get(0);
        Map<String, Object> change =
                Map.of(
                        "quantity",
                        2,
                        "cartId",
                        UUID.fromString(cart.get("id").asText()),
                        "cartVersion",
                        cart.get("version").asLong(),
                        "itemVersion",
                        line.get("version").asLong());
        var pool = Executors.newFixedThreadPool(2);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        Callable<Integer> request =
                () -> {
                    ready.countDown();
                    start.await();
                    return browser.send("PATCH", "/cart/items/" + line.get("id").asText(), change)
                            .statusCode();
                };
        Future<Integer> first = pool.submit(request), second = pool.submit(request);
        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                .containsExactlyInAnyOrder(200, 409);
        pool.shutdownNow();
        assertThat(
                        body(browser.send("GET", "/cart", null), 200)
                                .get("items")
                                .get(0)
                                .get("quantity")
                                .asInt())
                .isEqualTo(2);
    }

    @Test
    void simultaneousCrossBranchReplacementsCannotChainThroughAbaVersions() throws Exception {
        JsonNode old = first();
        UUID firstBranch = branch(restaurant, "Concurrent replacement one");
        UUID secondBranch = branch(restaurant, "Concurrent replacement two");
        var pool = Executors.newFixedThreadPool(2);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        Callable<Integer> firstRequest =
                () -> {
                    ready.countDown();
                    start.await();
                    return browser.send(
                                    "POST",
                                    "/cart/replace",
                                    Map.of(
                                            "branchId",
                                            firstBranch,
                                            "menuItemId",
                                            item,
                                            "quantity",
                                            1,
                                            "cartId",
                                            UUID.fromString(old.get("id").asText()),
                                            "cartVersion",
                                            old.get("version").asLong()))
                            .statusCode();
                };
        Callable<Integer> secondRequest =
                () -> {
                    ready.countDown();
                    start.await();
                    return browser.send(
                                    "POST",
                                    "/cart/replace",
                                    Map.of(
                                            "branchId",
                                            secondBranch,
                                            "menuItemId",
                                            item,
                                            "quantity",
                                            1,
                                            "cartId",
                                            UUID.fromString(old.get("id").asText()),
                                            "cartVersion",
                                            old.get("version").asLong()))
                            .statusCode();
                };
        Future<Integer> firstResult = pool.submit(firstRequest),
                secondResult = pool.submit(secondRequest);
        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        assertThat(
                        List.of(
                                firstResult.get(10, TimeUnit.SECONDS),
                                secondResult.get(10, TimeUnit.SECONDS)))
                .containsExactlyInAnyOrder(201, 409);
        pool.shutdownNow();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM carts WHERE customer_id=? AND"
                                        + " status='ACTIVE'",
                                Integer.class,
                                customer))
                .isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM carts WHERE customer_id=?",
                                Integer.class,
                                customer))
                .isEqualTo(2);
    }

    @Test
    void databaseConstraintsProtectActiveCartContextQuantityAndMoney() throws Exception {
        JsonNode cart = first();
        UUID cartId = UUID.fromString(cart.get("id").asText());
        sqlState(
                "23505",
                () ->
                        jdbc.update(
                                "INSERT INTO"
                                    + " carts(id,customer_id,branch_id,restaurant_id,status,created_at,updated_at)"
                                    + " VALUES (?,?,?,?, 'ACTIVE',now(),now())",
                                UUID.randomUUID(),
                                customer,
                                branch,
                                restaurant));
        UUID otherRestaurant = restaurant(customer, "Constraint Kitchen");
        UUID otherBranch = branch(otherRestaurant, "Constraint Branch");
        UUID otherMenu = UUID.randomUUID(),
                otherCategory = UUID.randomUUID(),
                otherItem = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO restaurant_menus(id,restaurant_id,name,created_at,updated_at) VALUES"
                        + " (?,?,'Other',now(),now())",
                otherMenu,
                otherRestaurant);
        jdbc.update(
                "INSERT INTO"
                    + " menu_categories(id,menu_id,restaurant_id,name,position,created_at,updated_at)"
                    + " VALUES (?,?,?,'Other',0,now(),now())",
                otherCategory,
                otherMenu,
                otherRestaurant);
        addItem(otherItem, otherCategory, otherRestaurant, "Other", "10", 0);
        sqlState(
                "23503",
                () ->
                        jdbc.update(
                                "INSERT INTO"
                                    + " cart_items(id,cart_id,menu_item_id,restaurant_id,quantity,acknowledged_unit_price,created_at,updated_at)"
                                    + " VALUES (?,?,?,?,1,10,now(),now())",
                                UUID.randomUUID(),
                                cartId,
                                otherItem,
                                otherRestaurant));
        int invalidPosition = 1;
        for (Object[] values :
                List.of(new Object[] {0, "10"}, new Object[] {100, "10"}, new Object[] {1, "-1"})) {
            UUID invalidLineItem = UUID.randomUUID();
            addItem(
                    invalidLineItem,
                    category,
                    restaurant,
                    "Constraint item " + invalidPosition,
                    "10",
                    invalidPosition++);
            sqlState(
                    "23514",
                    () ->
                            jdbc.update(
                                    "INSERT INTO"
                                        + " cart_items(id,cart_id,menu_item_id,restaurant_id,quantity,acknowledged_unit_price,created_at,updated_at)"
                                        + " VALUES (?,?,?,?,?,?,now(),now())",
                                    UUID.randomUUID(),
                                    cartId,
                                    invalidLineItem,
                                    restaurant,
                                    values[0],
                                    new BigDecimal((String) values[1])));
        }
        sqlState(
                "23514",
                () -> jdbc.update("UPDATE carts SET branch_id=? WHERE id=?", otherBranch, cartId));
        JsonNode line = cart.get("items").get(0);
        sqlState(
                "23514",
                () ->
                        jdbc.update(
                                "UPDATE cart_items SET cart_id=? WHERE id=?",
                                UUID.randomUUID(),
                                UUID.fromString(line.get("id").asText())));
    }

    @Test
    void cartReadUsesTwoQueriesRegardlessOfLineCount() throws Exception {
        JsonNode cart = first();
        UUID cartId = UUID.fromString(cart.get("id").asText());
        for (int index = 1; index < 30; index++) {
            UUID next = UUID.randomUUID();
            addItem(next, category, restaurant, "Item " + index, "1.25", index);
            jdbc.update(
                    "INSERT INTO"
                        + " cart_items(id,cart_id,menu_item_id,restaurant_id,quantity,acknowledged_unit_price,created_at,updated_at)"
                        + " VALUES (?,?,?,?,1,1.25,now(),now())",
                    UUID.randomUUID(),
                    cartId,
                    next,
                    restaurant);
        }
        JdbcTemplate counted = spy(new JdbcTemplate(jdbc.getDataSource()));
        var query = new CartQuery(counted);
        assertThat(query.active(customer, Instant.now()).orElseThrow().items()).hasSize(30);
        var executions =
                mockingDetails(counted).getInvocations().stream()
                        .filter(
                                invocation ->
                                        invocation.getMethod().getName().equals("query")
                                                && invocation.getArguments().length == 2
                                                && invocation.getArguments()[0]
                                                        instanceof PreparedStatementCreator
                                                && invocation.getArguments()[1]
                                                        instanceof ResultSetExtractor)
                        .toList();
        assertThat(executions).hasSize(2);
        StringBuilder plans =
                new StringBuilder(
                        "PostgreSQL "
                                + jdbc.queryForObject("SHOW server_version", String.class)
                                + "; fixture-scale plans, not a load benchmark\n");
        for (var invocation : executions) {
            PreparedStatementCreator creator =
                    (PreparedStatementCreator) invocation.getArguments()[0];
            try (Connection connection = jdbc.getDataSource().getConnection()) {
                Connection explain =
                        (Connection)
                                Proxy.newProxyInstance(
                                        getClass().getClassLoader(),
                                        new Class[] {Connection.class},
                                        (proxy, method, arguments) -> {
                                            if (method.getName().equals("prepareStatement"))
                                                arguments[0] =
                                                        "EXPLAIN (ANALYZE,BUFFERS) " + arguments[0];
                                            return method.invoke(connection, arguments);
                                        });
                try (PreparedStatement statement = creator.createPreparedStatement(explain);
                        ResultSet rows = statement.executeQuery()) {
                    plans.append("\n");
                    while (rows.next()) plans.append(rows.getString(1)).append("\n");
                }
            }
        }
        Files.writeString(Path.of("target/cart-explain.txt"), plans);
    }

    void sqlState(String expected, org.assertj.core.api.ThrowableAssert.ThrowingCallable work) {
        assertThatThrownBy(work)
                .rootCause()
                .isInstanceOfSatisfying(
                        SQLException.class,
                        exception -> assertThat(exception.getSQLState()).isEqualTo(expected));
    }
}
