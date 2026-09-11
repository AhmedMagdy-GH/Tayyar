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
class CheckoutIT extends PostgresIntegrationTest {
    static final String PASSWORD = "Cart integration password!";

    @Value("${local.server.port}")
    int port;

    @Autowired ObjectMapper json;
    @MockitoSpyBean JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    @Autowired PlatformTransactionManager transactions;
    @MockitoSpyBean CartStore store;
    @MockitoSpyBean com.tayyar.checkout.CheckoutStore checkoutStore;
    @MockitoSpyBean com.tayyar.order.OrderStore orderStore;
    @MockitoSpyBean com.tayyar.payment.PaymentService paymentStore;
    @MockitoSpyBean CartValidationLocks validationLocks;
    @Autowired com.tayyar.order.OrderService orders;
    @MockitoSpyBean java.time.Clock clock;

    UUID customer, restaurant, branch, menu, category, item;
    Browser browser;
    final HttpClient http = HttpClient.newHttpClient();

    record Account(UUID id, Browser browser) {}

    class Browser {
        String cookie;
        String token;
        String key;

        HttpResponse<String> send(String method, String path, Object input) throws Exception {
            var request =
                    HttpRequest.newBuilder(
                            URI.create("http://localhost:" + port + "/api/v1" + path));
            if (key != null) request.header("Idempotency-Key", key);
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
        doReturn(Instant.parse("2026-09-11T09:00:00Z")).when(clock).instant();
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
    void successfulCheckoutReadQueryCountDoesNotGrowWithCartLines() throws Exception {
        ready();
        clearInvocations(jdbc);
        checkout(201);
        long single = readCalls();
        cart = first();
        for (int position = 1; position <= 4; position++) {
            UUID next = UUID.randomUUID();
            addItem(next, category, restaurant, "Item " + position, "10", position);
            cart =
                    body(
                            browser.send(
                                    "POST",
                                    "/cart/items",
                                    add(branch, next, 1, cart.get("version").asLong())),
                            200);
        }
        browser.key = UUID.randomUUID().toString();
        clearInvocations(jdbc);
        checkout(201);
        long multiple = readCalls();
        assertThat(multiple).isEqualTo(single);
        assertThat(single).isPositive();
        Files.writeString(
                Path.of("target/checkout-query-review.txt"),
                "JdbcTemplate read invocations (including delegating overloads): one line="
                        + single
                        + ", five lines="
                        + multiple
                        + "\n"
                        + "Item revalidation: one bounded joined CartQuery line query; purchase"
                        + " item insertion: one JDBC batch.\n"
                        + String.join(
                                "\n",
                                jdbc.queryForList(
                                        "EXPLAIN SELECT order_id FROM checkout_receipts WHERE"
                                                + " customer_id=? AND idempotency_key=?",
                                        String.class,
                                        customer,
                                        browser.key))
                        + "\n"
                        + String.join(
                                "\n",
                                jdbc.queryForList(
                                        "EXPLAIN SELECT id FROM cart_items WHERE cart_id=?",
                                        String.class,
                                        cartId())));
    }

    long readCalls() {
        return mockingDetails(jdbc).getInvocations().stream()
                .filter(inv -> inv.getMethod().getName().startsWith("query"))
                .count();
    }

    UUID city, zone, address, rule;
    JsonNode cart;

    void ready() throws Exception {
        city = UUID.randomUUID();
        zone = UUID.randomUUID();
        address = UUID.randomUUID();
        rule = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO cities(id,name,active,created_at,updated_at) VALUES (?,"
                        + " ?,true,now(),now())",
                city,
                "City " + city);
        jdbc.update(
                "INSERT INTO delivery_zones(id,city_id,name,active,created_at,updated_at) VALUES"
                        + " (?,?,'Zone',true,now(),now())",
                zone,
                city);
        jdbc.update(
                "INSERT INTO"
                    + " branch_delivery_zones(id,branch_id,delivery_zone_id,delivery_fee,minimum_order,eta_min_minutes,eta_max_minutes,enabled,created_at,updated_at)"
                    + " VALUES (?,?,?,20,60,20,40,true,now(),now())",
                rule,
                branch,
                zone);
        jdbc.update(
                "INSERT INTO"
                    + " customer_addresses(id,user_id,label,street,building,city,country_code,delivery_zone_id,created_at,updated_at)"
                    + " VALUES (?,?,'Home','Original Street','12','Cairo','EG',?,now(),now())",
                address,
                customer,
                zone);
        jdbc.update(
                "INSERT INTO branch_opening_hours SELECT ?,n,'00:00'::time,'23:59'::time FROM"
                        + " generate_series(1,7) n",
                branch);
        cart = first();
        browser.key = UUID.randomUUID().toString();
    }

    Map<String, Object> request() {
        return new HashMap<>(
                Map.of(
                        "cartId",
                        cart.get("id").asText(),
                        "cartVersion",
                        cart.get("version").asLong(),
                        "savedAddressId",
                        address,
                        "paymentMethod",
                        "CASH"));
    }

    UUID cartId() {
        return UUID.fromString(cart.get("id").asText());
    }

    JsonNode checkout(int status) throws Exception {
        return body(browser.send("POST", "/checkout", request()), status);
    }

    void intact() {
        assertThat(
                        jdbc.queryForObject(
                                "SELECT status FROM carts WHERE id=?", String.class, cartId()))
                .isEqualTo("ACTIVE");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM orders WHERE customer_id=?",
                                Integer.class,
                                customer))
                .isZero();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM checkout_receipts WHERE customer_id=?",
                                Integer.class,
                                customer))
                .isZero();
    }

    @Test
    void cashCreatesAllSnapshotsHistoriesAndConsumesCart() throws Exception {
        ready();
        jdbc.update("UPDATE branch_delivery_zones SET delivery_fee=25 WHERE id=?", rule);
        var result = checkout(201);
        UUID order = UUID.fromString(result.get("orderId").asText());
        assertThat(result.get("orderStatus").asText()).isEqualTo("PLACED");
        assertThat(result.get("paymentStatus").asText()).isEqualTo("PENDING");
        assertThat(result.get("finalTotal").decimalValue()).isEqualByComparingTo("105");
        assertThat(result.get("discountTotal").decimalValue()).isEqualByComparingTo("0");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT status FROM carts WHERE id=?", String.class, cartId()))
                .isEqualTo("CHECKED_OUT");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT amount FROM payments WHERE order_id=?",
                                BigDecimal.class,
                                order))
                .isEqualByComparingTo("105");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM order_status_history WHERE order_id=? AND"
                                        + " actor_id=? AND new_status='PLACED'",
                                Integer.class,
                                order,
                                customer))
                .isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM payment_status_history h JOIN payments p ON"
                                        + " p.id=h.payment_id WHERE p.order_id=? AND"
                                        + " h.new_status='PENDING'",
                                Integer.class,
                                order))
                .isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT purchased_name FROM order_items WHERE order_id=?",
                                String.class,
                                order))
                .isEqualTo("Chicken");
        jdbc.update("UPDATE menu_items SET name='Changed',base_price=90 WHERE id=?", item);
        jdbc.update("DELETE FROM customer_addresses WHERE id=?", address);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT unit_price FROM order_items WHERE order_id=?",
                                BigDecimal.class,
                                order))
                .isEqualByComparingTo("80");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT street FROM order_address_snapshots WHERE order_id=?",
                                String.class,
                                order))
                .isEqualTo("Original Street");
        body(browser.send("GET", "/cart", null), 204);
        orders.transition(
                order,
                0,
                com.tayyar.order.OrderStatus.ACCEPTED,
                com.tayyar.order.TransitionActor.system(),
                null);
        UUID payment =
                jdbc.queryForObject("SELECT id FROM payments WHERE order_id=?", UUID.class, order);
        paymentStore.transition(
                payment,
                0,
                com.tayyar.payment.PaymentStatus.PAID,
                com.tayyar.order.TransitionActor.system(),
                null);
        assertThat(checkout(201)).isEqualTo(result);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM orders WHERE customer_id=?",
                                Integer.class,
                                customer))
                .isEqualTo(1);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM cart_items WHERE cart_id=?", cartId()))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(
                        () -> jdbc.update("UPDATE carts SET status='ACTIVE' WHERE id=?", cartId()))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void invalidAndStaleCartsDoNotCreateOrders() throws Exception {
        ready();
        var input = request();
        input.put("cartId", UUID.randomUUID());
        body(browser.send("POST", "/checkout", input), 404);
        input = request();
        input.put("cartVersion", 999);
        body(browser.send("POST", "/checkout", input), 409);
        intact();
        jdbc.update("DELETE FROM cart_items WHERE cart_id=?", cartId());
        checkout(409);
        intact();
        jdbc.update("UPDATE carts SET status='ABANDONED' WHERE id=?", cartId());
        checkout(409);
    }

    @Test
    void priceChangesRequireExplicitVersionedReconfirmationAndRespectOverride() throws Exception {
        ready();
        jdbc.update("UPDATE menu_items SET base_price=90 WHERE id=?", item);
        assertThat(checkout(409).get("code").asText()).isEqualTo("PRICE_RECONFIRMATION_REQUIRED");
        intact();
        var confirm = Map.of("cartId", cartId(), "cartVersion", 0);
        cart = body(browser.send("POST", "/cart/reconfirm-prices", confirm), 200);
        assertThat(cart.get("items").get(0).get("acknowledgedUnitPrice").decimalValue())
                .isEqualByComparingTo("90");
        body(browser.send("POST", "/cart/reconfirm-prices", confirm), 409);
        jdbc.update(
                "INSERT INTO"
                    + " branch_menu_item_overrides(branch_id,item_id,restaurant_id,price,created_at,updated_at)"
                    + " VALUES (?,?,?,95,now(),now())",
                branch,
                item,
                restaurant);
        checkout(409);
        intact();
        cart =
                body(
                        browser.send(
                                "POST",
                                "/cart/reconfirm-prices",
                                Map.of(
                                        "cartId",
                                        cartId(),
                                        "cartVersion",
                                        cart.get("version").asLong())),
                        200);
        assertThat(checkout(201).get("merchandiseSubtotal").decimalValue())
                .isEqualByComparingTo("95");
    }

    @Test
    void allAvailabilityAndOperatingStatesBlockCheckout() throws Exception {
        ready();
        for (String table : List.of("restaurant_menus", "menu_categories", "menu_items")) {
            UUID id =
                    table.equals("restaurant_menus")
                            ? menu
                            : table.equals("menu_categories") ? category : item;
            jdbc.update("UPDATE " + table + " SET active=false WHERE id=?", id);
            checkout(409);
            intact();
            jdbc.update("UPDATE " + table + " SET active=true WHERE id=?", id);
        }
        jdbc.update("UPDATE menu_items SET available=false WHERE id=?", item);
        checkout(409);
        intact();
        jdbc.update("UPDATE menu_items SET available=true WHERE id=?", item);
        jdbc.update("UPDATE branches SET status='INACTIVE' WHERE id=?", branch);
        checkout(409);
        intact();
        jdbc.update("UPDATE branches SET status='ACTIVE',paused=true WHERE id=?", branch);
        checkout(409);
        intact();
        jdbc.update("UPDATE branches SET paused=false WHERE id=?", branch);
        jdbc.update("UPDATE restaurants SET status='SUSPENDED' WHERE id=?", restaurant);
        checkout(409);
        intact();
        jdbc.update("UPDATE restaurants SET status='ACTIVE' WHERE id=?", restaurant);
        jdbc.update("DELETE FROM branch_opening_hours WHERE branch_id=?", branch);
        checkout(409);
        intact();
    }

    @Test
    void ownedAddressAndActiveGeographyAndRuleAreRequired() throws Exception {
        ready();
        UUID own = address;
        address = UUID.randomUUID();
        checkout(404);
        address = own;
        UUID foreign = accountRow("CUSTOMER");
        UUID foreignAddress = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO"
                    + " customer_addresses(id,user_id,label,street,building,city,country_code,delivery_zone_id,created_at,updated_at)"
                    + " VALUES (?,?,'Other','Private','1','Cairo','EG',?,now(),now())",
                foreignAddress,
                foreign,
                zone);
        address = foreignAddress;
        checkout(404);
        address = own;
        intact();
        jdbc.update("UPDATE customer_addresses SET delivery_zone_id=null WHERE id=?", address);
        checkout(409);
        intact();
        jdbc.update("UPDATE customer_addresses SET delivery_zone_id=? WHERE id=?", zone, address);
        jdbc.update("UPDATE cities SET active=false WHERE id=?", city);
        checkout(409);
        intact();
        jdbc.update("UPDATE cities SET active=true WHERE id=?", city);
        jdbc.update("UPDATE delivery_zones SET active=false WHERE id=?", zone);
        checkout(409);
        intact();
        jdbc.update("UPDATE delivery_zones SET active=true WHERE id=?", zone);
        jdbc.update("UPDATE branch_delivery_zones SET enabled=false WHERE id=?", rule);
        checkout(409);
        intact();
        jdbc.update("DELETE FROM branch_delivery_zones WHERE id=?", rule);
        checkout(409);
        intact();
    }

    @Test
    void minimumExcludesDeliveryAndUnsupportedCardDoesNotCreatePayment() throws Exception {
        ready();
        var card = request();
        card.put("paymentMethod", "CARD");
        assertThat(body(browser.send("POST", "/checkout", card), 409).get("code").asText())
                .isEqualTo("PAYMENT_METHOD_UNAVAILABLE");
        jdbc.update(
                "UPDATE branch_delivery_zones SET minimum_order=90,delivery_fee=100 WHERE id=?",
                rule);
        checkout(409);
        intact();
        jdbc.update("UPDATE branch_delivery_zones SET minimum_order=80 WHERE id=?", rule);
        assertThat(checkout(201).get("finalTotal").decimalValue()).isEqualByComparingTo("180");
    }

    @Test
    void keyValidationMassAssignmentAndMismatch() throws Exception {
        ready();
        for (String invalid : List.of("", "short", "x".repeat(129), "invalid key with spaces")) {
            browser.key = invalid;
            checkout(400);
            intact();
        }
        browser.key = null;
        checkout(400);
        browser.key = UUID.randomUUID().toString();
        for (String field :
                List.of(
                        "customerId",
                        "restaurantId",
                        "branchId",
                        "unitPrice",
                        "deliveryFee",
                        "finalTotal",
                        "orderStatus",
                        "paymentStatus",
                        "zoneId")) {
            var forged = request();
            forged.put(field, "injected");
            body(browser.send("POST", "/checkout", forged), 400);
            intact();
        }
        checkout(201);
        var changed = request();
        changed.put("savedAddressId", UUID.randomUUID());
        body(browser.send("POST", "/checkout", changed), 409);
        changed = request();
        changed.put("cartVersion", 1);
        body(browser.send("POST", "/checkout", changed), 409);
        changed = request();
        changed.put("paymentMethod", "CARD");
        body(browser.send("POST", "/checkout", changed), 409);
    }

    @Test
    void authenticationRolesCsrfAndSuspensionAreEnforced() throws Exception {
        ready();
        var anonymous = new Browser();
        anonymous.csrf();
        anonymous.key = browser.key;
        body(anonymous.send("POST", "/checkout", request()), 401);
        for (String role : List.of("ADMIN", "RESTAURANT_OWNER", "RESTAURANT_STAFF", "DRIVER")) {
            var other = login(accountRow(role));
            other.key = browser.key;
            body(other.send("POST", "/checkout", request()), 403);
            body(
                    other.send(
                            "POST",
                            "/cart/reconfirm-prices",
                            Map.of("cartId", cartId(), "cartVersion", 0)),
                    403);
        }
        String token = browser.token;
        browser.token = null;
        checkout(403);
        browser.token = token;
        var stranger = login(accountRow("CUSTOMER"));
        stranger.key = browser.key;
        body(stranger.send("POST", "/checkout", request()), 404);
        jdbc.update("UPDATE users SET status='SUSPENDED' WHERE id=?", customer);
        int status = browser.send("POST", "/checkout", request()).statusCode();
        assertThat(status).isIn(401, 403);
        intact();
    }

    @Test
    void failuresAtEveryWriteStageRollBackAndAllowSameKeyRetry() throws Exception {
        ready();
        for (String stage : List.of("order", "items", "address", "payment", "cart")) {
            if (stage.equals("order"))
                doAnswer(
                                inv -> {
                                    inv.callRealMethod();
                                    throw new IllegalStateException("injected");
                                })
                        .when(orderStore)
                        .create(any(), any(), any(), any());
            if (stage.equals("items"))
                doAnswer(
                                inv -> {
                                    inv.callRealMethod();
                                    throw new IllegalStateException("injected");
                                })
                        .when(orderStore)
                        .items(any(), any(), anyList(), any());
            if (stage.equals("address"))
                doAnswer(
                                inv -> {
                                    inv.callRealMethod();
                                    throw new IllegalStateException("injected");
                                })
                        .when(orderStore)
                        .address(any(), any(), any());
            if (stage.equals("payment"))
                doAnswer(
                                inv -> {
                                    inv.callRealMethod();
                                    throw new IllegalStateException("injected");
                                })
                        .when(paymentStore)
                        .create(any(), any());
            if (stage.equals("cart"))
                doAnswer(
                                inv -> {
                                    inv.callRealMethod();
                                    throw new IllegalStateException("injected");
                                })
                        .when(checkoutStore)
                        .consume(any(), any());
            checkout(500);
            intact();
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT count(*) FROM payments p JOIN orders o ON"
                                            + " o.id=p.order_id WHERE o.customer_id=?",
                                    Integer.class,
                                    customer))
                    .isZero();
            reset(orderStore, paymentStore, checkoutStore);
        }
        checkout(201);
    }

    @Test
    void concurrentIdenticalKeysReturnSameOrder() throws Exception {
        ready();
        var other = new Browser();
        other.cookie = browser.cookie;
        other.token = browser.token;
        other.key = browser.key;
        var results =
                race(
                        () -> browser.send("POST", "/checkout", request()),
                        () -> other.send("POST", "/checkout", request()));
        var first = body(results.get(0), 201);
        assertThat(body(results.get(1), 201)).isEqualTo(first);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM orders WHERE customer_id=?",
                                Integer.class,
                                customer))
                .isEqualTo(1);
    }

    @Test
    void concurrentDifferentKeysConsumeCartOnlyOnce() throws Exception {
        ready();
        var other = new Browser();
        other.cookie = browser.cookie;
        other.token = browser.token;
        other.key = UUID.randomUUID().toString();
        var results =
                race(
                        () -> browser.send("POST", "/checkout", request()),
                        () -> other.send("POST", "/checkout", request()));
        assertThat(results.stream().map(HttpResponse::statusCode).toList())
                .containsExactlyInAnyOrder(201, 409);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM orders WHERE customer_id=?",
                                Integer.class,
                                customer))
                .isEqualTo(1);
    }

    @Test
    void quantityMutationRacingCheckoutHasOneWinner() throws Exception {
        ready();
        String line = cart.get("items").get(0).get("id").asText();
        var change = Map.of("quantity", 2, "cartId", cartId(), "cartVersion", 0, "itemVersion", 0);
        var results =
                race(
                        () -> browser.send("POST", "/checkout", request()),
                        () -> browser.send("PATCH", "/cart/items/" + line, change));
        if (results.get(0).statusCode() == 201)
            assertThat(results.get(1).statusCode()).isIn(404, 409);
        else {
            assertThat(results.get(0).statusCode()).isEqualTo(409);
            assertThat(results.get(1).statusCode()).isEqualTo(200);
            intact();
        }
    }

    @Test
    void replacementRacingCheckoutHasOneWinner() throws Exception {
        ready();
        UUID otherBranch = branch(restaurant, "Other");
        var replace =
                Map.of(
                        "branchId",
                        otherBranch,
                        "menuItemId",
                        item,
                        "quantity",
                        1,
                        "cartId",
                        cartId(),
                        "cartVersion",
                        0);
        var results =
                race(
                        () -> browser.send("POST", "/checkout", request()),
                        () -> browser.send("POST", "/cart/replace", replace));
        if (results.get(0).statusCode() == 201)
            assertThat(results.get(1).statusCode()).isIn(404, 409);
        else {
            assertThat(results.get(0).statusCode()).isEqualTo(409);
            assertThat(results.get(1).statusCode()).isEqualTo(201);
        }
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM orders WHERE customer_id=?",
                                Integer.class,
                                customer))
                .isLessThanOrEqualTo(1);
    }

    List<HttpResponse<String>> race(
            Callable<HttpResponse<String>> first, Callable<HttpResponse<String>> second)
            throws Exception {
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var a =
                    pool.submit(
                            () -> {
                                start.await();
                                return first.call();
                            });
            var b =
                    pool.submit(
                            () -> {
                                start.await();
                                return second.call();
                            });
            start.countDown();
            return List.of(a.get(25, TimeUnit.SECONDS), b.get(25, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void concurrentConfigurationWritersCannotChangeLockedPurchaseInputs() throws Exception {
        ready();
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        doAnswer(
                        inv -> {
                            inv.callRealMethod();
                            locked.countDown();
                            if (!release.await(10, TimeUnit.SECONDS))
                                throw new IllegalStateException("timeout");
                            return null;
                        })
                .when(checkoutStore)
                .consume(any(), any());
        var pool = Executors.newFixedThreadPool(4);
        try {
            var checkout = pool.submit(() -> browser.send("POST", "/checkout", request()));
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
            var price =
                    pool.submit(
                            () ->
                                    jdbc.update(
                                            "UPDATE menu_items SET base_price=99 WHERE id=?",
                                            item));
            var availability =
                    pool.submit(
                            () ->
                                    jdbc.update(
                                            "UPDATE menu_categories SET active=false WHERE id=?",
                                            category));
            var fee =
                    pool.submit(
                            () ->
                                    jdbc.update(
                                            "UPDATE branch_delivery_zones SET delivery_fee=99 WHERE"
                                                    + " id=?",
                                            rule));
            assertThatThrownBy(() -> price.get(150, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);
            assertThatThrownBy(() -> availability.get(150, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);
            assertThatThrownBy(() -> fee.get(150, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);
            release.countDown();
            assertThat(
                            body(checkout.get(15, TimeUnit.SECONDS), 201)
                                    .get("finalTotal")
                                    .decimalValue())
                    .isEqualByComparingTo("100");
            assertThat(price.get(10, TimeUnit.SECONDS)).isEqualTo(1);
            assertThat(availability.get(10, TimeUnit.SECONDS)).isEqualTo(1);
            assertThat(fee.get(10, TimeUnit.SECONDS)).isEqualTo(1);
        } finally {
            release.countDown();
            pool.shutdownNow();
            reset(checkoutStore);
        }
    }

    @Test
    void checkoutWaitsForEarlierConfigurationWritersAndRevalidatesCommittedValues()
            throws Exception {
        ready();
        for (String field : List.of("price", "availability", "serviceability")) {
            var written = new CountDownLatch(1);
            var release = new CountDownLatch(1);
            var pool = Executors.newFixedThreadPool(2);
            try {
                var writer =
                        pool.submit(
                                () ->
                                        new TransactionTemplate(transactions)
                                                .executeWithoutResult(
                                                        status -> {
                                                            if (field.equals("price"))
                                                                jdbc.update(
                                                                        "UPDATE menu_items SET"
                                                                            + " base_price=99 WHERE"
                                                                            + " id=?",
                                                                        item);
                                                            if (field.equals("availability"))
                                                                jdbc.update(
                                                                        "UPDATE menu_categories SET"
                                                                            + " active=false WHERE"
                                                                            + " id=?",
                                                                        category);
                                                            if (field.equals("serviceability"))
                                                                jdbc.update(
                                                                        "UPDATE"
                                                                            + " branch_delivery_zones"
                                                                            + " SET enabled=false"
                                                                            + " WHERE id=?",
                                                                        rule);
                                                            written.countDown();
                                                            try {
                                                                if (!release.await(
                                                                        10, TimeUnit.SECONDS))
                                                                    throw new IllegalStateException(
                                                                            "timeout");
                                                            } catch (InterruptedException ex) {
                                                                Thread.currentThread().interrupt();
                                                                throw new IllegalStateException(ex);
                                                            }
                                                        }));
                assertThat(written.await(10, TimeUnit.SECONDS)).isTrue();
                var pending = pool.submit(() -> browser.send("POST", "/checkout", request()));
                assertThatThrownBy(() -> pending.get(150, TimeUnit.MILLISECONDS))
                        .isInstanceOf(TimeoutException.class);
                release.countDown();
                writer.get(10, TimeUnit.SECONDS);
                body(pending.get(15, TimeUnit.SECONDS), 409);
                intact();
            } finally {
                release.countDown();
                pool.shutdownNow();
            }
            jdbc.update("UPDATE menu_items SET base_price=80 WHERE id=?", item);
            jdbc.update("UPDATE menu_categories SET active=true WHERE id=?", category);
            jdbc.update("UPDATE branch_delivery_zones SET enabled=true WHERE id=?", rule);
        }
    }
}
