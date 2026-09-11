package com.tayyar.order;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.tayyar.payment.*;
import com.tayyar.notification.*;
import com.tayyar.support.PostgresIntegrationTest;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import tools.jackson.databind.*;

import java.math.BigDecimal;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"tayyar.identity.login-limit=1000", "tayyar.identity.csrf-limit=1000"})
class OrderOperationsIT extends PostgresIntegrationTest {
    static final String PASSWORD = "Order operations password!";

    @Value("${local.server.port}")
    int port;

    @Autowired ObjectMapper json;
    @MockitoSpyBean JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    @Autowired OrderService orders;
    @Autowired PaymentService payments;
    @Autowired PlatformTransactionManager transactions;
    @MockitoSpyBean OrderStore orderStore;
    @MockitoSpyBean NotificationStore notificationStore;

    UUID customer,
            foreignCustomer,
            owner,
            staff,
            restaurant,
            branch,
            secondBranch,
            menu,
            category,
            item;
    Browser customerBrowser, ownerBrowser, staffBrowser;
    final HttpClient http = HttpClient.newHttpClient();

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
        customer = account("CUSTOMER");
        foreignCustomer = account("CUSTOMER");
        owner = account("RESTAURANT_OWNER");
        staff = account("RESTAURANT_STAFF");
        restaurant = restaurant(owner, "Operations Kitchen");
        branch = branch(restaurant, "Primary");
        secondBranch = branch(restaurant, "Secondary");
        member(restaurant, staff);
        jdbc.update(
                "INSERT INTO branch_staff_assignments VALUES (?,?,?,?,now())",
                branch,
                restaurant,
                staff,
                owner);
        menu = UUID.randomUUID();
        category = UUID.randomUUID();
        item = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO restaurant_menus(id,restaurant_id,name,created_at,updated_at) VALUES"
                        + " (?,?,'Main',now(),now())",
                menu,
                restaurant);
        jdbc.update(
                "INSERT INTO"
                    + " menu_categories(id,menu_id,restaurant_id,name,position,created_at,updated_at)"
                    + " VALUES (?,?,?,'Meals',0,now(),now())",
                category,
                menu,
                restaurant);
        jdbc.update(
                "INSERT INTO"
                    + " menu_items(id,category_id,restaurant_id,name,base_price,position,created_at,updated_at)"
                    + " VALUES (?,?,?,'Live name',80,0,now(),now())",
                item,
                category,
                restaurant);
        customerBrowser = login(customer);
        ownerBrowser = login(owner);
        staffBrowser = login(staff);
    }

    UUID account(String... roles) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO users(id,full_name,email,password_hash,status,created_at,updated_at)"
                        + " VALUES (?,'Order User',?,?,'ACTIVE',now(),now())",
                id,
                id + "@example.com",
                passwords.encode(PASSWORD));
        for (String role : roles)
            jdbc.update("INSERT INTO user_roles(user_id,role_name) VALUES (?,?)", id, role);
        return id;
    }

    UUID restaurant(UUID restaurantOwner, String name) {
        UUID application = UUID.randomUUID(), id = UUID.randomUUID();
        new TransactionTemplate(transactions)
                .executeWithoutResult(
                        status -> {
                            jdbc.update(
                                    "INSERT INTO"
                                        + " restaurant_applications(id,applicant_id,status,current_revision,created_at,updated_at)"
                                        + " VALUES (?,?,'APPROVED',1,now(),now())",
                                    application,
                                    restaurantOwner);
                            jdbc.update(
                                    "INSERT INTO application_submissions VALUES"
                                            + " (?,1,?,'Order operations',now())",
                                    application,
                                    name);
                            jdbc.update(
                                    "INSERT INTO"
                                        + " restaurants(id,application_id,name,description,status,created_at,updated_at)"
                                        + " VALUES (?,?,?,'Public','ACTIVE',now(),now())",
                                    id,
                                    application,
                                    name);
                            member(id, restaurantOwner);
                        });
        return id;
    }

    void member(UUID restaurantId, UUID user) {
        jdbc.update(
                "INSERT INTO restaurant_memberships VALUES (?,?,'OWNER',now())",
                restaurantId,
                user);
    }

    UUID branch(UUID restaurantId, String name) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO"
                    + " branches(id,restaurant_id,name,address_line1,city,country_code,timezone,delivery_model,status,created_at,updated_at)"
                    + " VALUES"
                    + " (?,?,?,'Street','Cairo','EG','Africa/Cairo','RESTAURANT_DELIVERY','ACTIVE',now(),now())",
                id,
                restaurantId,
                name);
        return id;
    }

    Browser login(UUID user) throws Exception {
        Browser browser = new Browser();
        browser.csrf();
        assertThat(
                        browser.send(
                                        "POST",
                                        "/auth/session",
                                        Map.of(
                                                "email",
                                                user + "@example.com",
                                                "password",
                                                PASSWORD))
                                .statusCode())
                .isEqualTo(204);
        browser.csrf();
        return browser;
    }

    JsonNode body(HttpResponse<String> response, int status) throws Exception {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
        return response.body().isBlank() ? null : json.readTree(response.body());
    }

    OrderDtos.Details create(UUID buyer, UUID restaurantId, UUID branchId, OrderStatus status) {
        UUID sourceItem = restaurantId.equals(restaurant) ? item : sourceItem(restaurantId);
        var created =
                orders.create(
                        new OrderDtos.Draft(
                                buyer,
                                restaurantId,
                                branchId,
                                OrderStatus.PLACED,
                                List.of(
                                        new OrderDtos.PurchaseItem(
                                                sourceItem,
                                                "Purchased snapshot",
                                                new BigDecimal("80.00"),
                                                2)),
                                new OrderDtos.AddressSnapshot(
                                        "Home",
                                        "Snapshot Street",
                                        "12",
                                        "3",
                                        "7",
                                        "Landmark",
                                        "Instructions",
                                        "Cairo",
                                        "Region",
                                        "12345",
                                        "EG",
                                        null,
                                        null,
                                        UUID.randomUUID(),
                                        "Snapshot Zone",
                                        "Managed Cairo"),
                                new BigDecimal("20.00"),
                                BigDecimal.ZERO.setScale(2)),
                        TransitionActor.system());
        while (created.order().status() != status) {
            OrderStatus target =
                    switch (created.order().status()) {
                        case PLACED -> OrderStatus.ACCEPTED;
                        case ACCEPTED -> OrderStatus.PREPARING;
                        case PREPARING -> OrderStatus.READY_FOR_PICKUP;
                        default -> throw new IllegalArgumentException("Unsupported fixture status");
                    };
            orders.transition(
                    created.order().id(),
                    created.order().version(),
                    target,
                    TransitionActor.system(),
                    null);
            created = orders.details(created.order().id());
        }
        payments.create(
                new PaymentDtos.Create(created.order().id(), PaymentMethod.CASH, null, null),
                TransitionActor.system());
        return created;
    }

    UUID sourceItem(UUID restaurantId) {
        UUID localMenu = UUID.randomUUID(),
                localCategory = UUID.randomUUID(),
                localItem = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO restaurant_menus(id,restaurant_id,name,created_at,updated_at) VALUES"
                        + " (?,?,'Other',now(),now())",
                localMenu,
                restaurantId);
        jdbc.update(
                "INSERT INTO"
                    + " menu_categories(id,menu_id,restaurant_id,name,position,created_at,updated_at)"
                    + " VALUES (?,?,?,'Other',0,now(),now())",
                localCategory,
                localMenu,
                restaurantId);
        jdbc.update(
                "INSERT INTO"
                    + " menu_items(id,category_id,restaurant_id,name,base_price,position,created_at,updated_at)"
                    + " VALUES (?,?,?,'Other',80,0,now(),now())",
                localItem,
                localCategory,
                restaurantId);
        return localItem;
    }

    Map<String, Object> version(long value) {
        return Map.of("version", value);
    }

    Map<String, Object> reason(long value, String reason) {
        return Map.of("version", value, "reason", reason);
    }

    @Test
    void customerHistoryIsOwnedPaginatedFilteredAndDeterministic() throws Exception {
        var first = create(customer, restaurant, branch, OrderStatus.PLACED);
        var second = create(customer, restaurant, branch, OrderStatus.ACCEPTED);
        create(foreignCustomer, restaurant, branch, OrderStatus.PLACED);
        JsonNode page = body(customerBrowser.send("GET", "/orders?page=0&size=1", null), 200);
        assertThat(page.get("total").asLong()).isEqualTo(2);
        assertThat(page.get("items")).hasSize(1);
        String expected =
                jdbc.queryForObject(
                                "SELECT id FROM orders WHERE customer_id=? ORDER BY created_at"
                                        + " DESC,id DESC LIMIT 1",
                                UUID.class,
                                customer)
                        .toString();
        assertThat(page.get("items").get(0).get("id").asText()).isEqualTo(expected);
        JsonNode filtered = body(customerBrowser.send("GET", "/orders?status=ACCEPTED", null), 200);
        assertThat(filtered.get("items")).hasSize(1);
        assertThat(filtered.get("items").get(0).get("id").asText())
                .isEqualTo(second.order().id().toString());
        body(customerBrowser.send("GET", "/orders?page=0&page=1", null), 400);
        body(customerBrowser.send("GET", "/orders?sort=created_at", null), 400);
        body(customerBrowser.send("GET", "/orders?size=101", null), 400);
        assertThat(first.order().id()).isNotNull();
    }

    @Test
    void customerDetailUsesImmutableSnapshotsAndSafeHistory() throws Exception {
        var order = create(customer, restaurant, branch, OrderStatus.PLACED);
        jdbc.update(
                "UPDATE menu_items SET name='Changed live name',base_price=99 WHERE id=?", item);
        JsonNode detail =
                body(customerBrowser.send("GET", "/orders/" + order.order().id(), null), 200);
        assertThat(detail.get("items").get(0).get("name").asText()).isEqualTo("Purchased snapshot");
        assertThat(detail.get("items").get(0).get("unitPrice").decimalValue())
                .isEqualByComparingTo("80");
        assertThat(detail.get("deliveryAddress").get("street").asText())
                .isEqualTo("Snapshot Street");
        assertThat(detail.get("payment").get("method").asText()).isEqualTo("CASH");
        assertThat(detail.get("payment").get("status").asText()).isEqualTo("PENDING");
        assertThat(detail.get("history").get(0).has("actorId")).isFalse();
        assertThat(detail.toString()).doesNotContain("provider", owner.toString());
    }

    @Test
    void customerOwnershipAndRolePrivacyAreEnforced() throws Exception {
        var order = create(customer, restaurant, branch, OrderStatus.PLACED);
        Browser foreign = login(foreignCustomer);
        body(foreign.send("GET", "/orders/" + order.order().id(), null), 404);
        body(
                foreign.send(
                        "POST", "/orders/" + order.order().id() + "/cancel", reason(0, "Not mine")),
                404);
        body(ownerBrowser.send("GET", "/orders", null), 403);
        Browser anonymous = new Browser();
        body(anonymous.send("GET", "/orders", null), 401);
        body(customerBrowser.send("GET", "/orders/not-a-uuid", null), 400);
        body(customerBrowser.send("GET", "/orders/" + UUID.randomUUID(), null), 404);
    }

    @Test
    void customerCanCancelOnlyPlacedOrderAndHistoryIsAppended() throws Exception {
        var placed = create(customer, restaurant, branch, OrderStatus.PLACED);
        JsonNode result =
                body(
                        customerBrowser.send(
                                "POST",
                                "/orders/" + placed.order().id() + "/cancel",
                                reason(0, "Changed my mind")),
                        200);
        assertThat(result.get("status").asText()).isEqualTo("CANCELLED");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM order_status_history WHERE order_id=? AND"
                                        + " new_status='CANCELLED' AND actor_id=? AND reason=?",
                                Integer.class,
                                placed.order().id(),
                                customer,
                                "Changed my mind"))
                .isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT status FROM payments WHERE order_id=?",
                                String.class,
                                placed.order().id()))
                .isEqualTo("PENDING");
        for (OrderStatus status :
                List.of(
                        OrderStatus.ACCEPTED,
                        OrderStatus.PREPARING,
                        OrderStatus.READY_FOR_PICKUP)) {
            var blocked = create(customer, restaurant, branch, status);
            body(
                    customerBrowser.send(
                            "POST",
                            "/orders/" + blocked.order().id() + "/cancel",
                            reason(blocked.order().version(), "Too late")),
                    409);
        }
        body(
                customerBrowser.send(
                        "POST", "/orders/" + placed.order().id() + "/cancel", reason(0, "Again")),
                409);
    }

    @Test
    void staleCancellationAndHistoryFailureAreAtomic() throws Exception {
        var order = create(customer, restaurant, branch, OrderStatus.PLACED);
        body(
                customerBrowser.send(
                        "POST", "/orders/" + order.order().id() + "/cancel", reason(9, "Stale")),
                409);
        doThrow(new IllegalStateException("injected history failure"))
                .when(orderStore)
                .history(
                        eq(order.order().id()),
                        eq(OrderStatus.PLACED),
                        eq(OrderStatus.CANCELLED),
                        any(),
                        anyString(),
                        any());
        body(
                customerBrowser.send(
                        "POST", "/orders/" + order.order().id() + "/cancel", reason(0, "Rollback")),
                500);
        reset(orderStore);
        assertThat(orders.details(order.order().id()).order().status())
                .isEqualTo(OrderStatus.PLACED);
        assertThat(orders.history(order.order().id())).hasSize(1);
    }

    @Test
    void ownerQueueIsBoundedScopedFilteredAndOldestFirst() throws Exception {
        var placed = create(customer, restaurant, branch, OrderStatus.PLACED);
        var accepted = create(customer, restaurant, secondBranch, OrderStatus.ACCEPTED);
        var terminal = create(customer, restaurant, branch, OrderStatus.PLACED);
        orders.transition(
                terminal.order().id(),
                0,
                OrderStatus.CANCELLED,
                TransitionActor.system(),
                "fixture");
        JsonNode queue =
                body(
                        ownerBrowser.send(
                                "GET",
                                "/restaurant-orders?restaurantId=" + restaurant + "&size=1",
                                null),
                        200);
        assertThat(queue.get("total").asLong()).isEqualTo(2);
        String expected =
                jdbc.queryForObject(
                                "SELECT id FROM orders WHERE restaurant_id=? AND status IN"
                                    + " ('PLACED','ACCEPTED','PREPARING','READY_FOR_PICKUP') ORDER"
                                    + " BY created_at,id LIMIT 1",
                                UUID.class,
                                restaurant)
                        .toString();
        assertThat(queue.get("items").get(0).get("id").asText()).isEqualTo(expected);
        JsonNode filtered =
                body(
                        ownerBrowser.send(
                                "GET",
                                "/restaurant-orders?restaurantId="
                                        + restaurant
                                        + "&branchId="
                                        + secondBranch
                                        + "&status=ACCEPTED",
                                null),
                        200);
        assertThat(filtered.get("items").get(0).get("id").asText())
                .isEqualTo(accepted.order().id().toString());
        body(
                ownerBrowser.send(
                        "GET",
                        "/restaurant-orders?restaurantId=" + restaurant + "&status=DELIVERED",
                        null),
                400);
        assertThat(placed.order().id()).isNotNull();
    }

    @Test
    void ownerCanProgressAndRejectOnlyOwnedPlacedOrders() throws Exception {
        var progression = create(customer, restaurant, branch, OrderStatus.PLACED);
        UUID id = progression.order().id();
        JsonNode accepted = body(ownerBrowser.send("POST", path(id, "accept"), version(0)), 200);
        JsonNode preparing =
                body(ownerBrowser.send("POST", path(id, "start-preparation"), version(1)), 200);
        JsonNode ready =
                body(ownerBrowser.send("POST", path(id, "ready-for-pickup"), version(2)), 200);
        assertThat(accepted.get("status").asText()).isEqualTo("ACCEPTED");
        assertThat(preparing.get("status").asText()).isEqualTo("PREPARING");
        assertThat(ready.get("status").asText()).isEqualTo("READY_FOR_PICKUP");
        assertThat(orders.history(id)).hasSize(4);
        assertThat(jdbc.queryForList(
                        "SELECT type FROM notifications WHERE recipient_user_id=? AND related_entity_id=?"
                                + " ORDER BY created_at,id",
                        String.class, customer, id))
                .containsExactly("ORDER_ACCEPTED", "ORDER_PREPARING", "ORDER_READY_FOR_PICKUP");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT status FROM payments WHERE order_id=?", String.class, id))
                .isEqualTo("PENDING");
        var rejected = create(customer, restaurant, branch, OrderStatus.PLACED);
        body(
                ownerBrowser.send("POST", path(rejected.order().id(), "reject"), reason(0, "Busy")),
                200);
        assertThat(orders.details(rejected.order().id()).order().status())
                .isEqualTo(OrderStatus.REJECTED);
        assertThat(jdbc.queryForObject(
                "SELECT type FROM notifications WHERE recipient_user_id=? AND related_entity_id=?",
                String.class, customer, rejected.order().id())).isEqualTo("ORDER_REJECTED");
        body(ownerBrowser.send("POST", path(id, "reject"), reason(3, "Too late")), 409);
        body(ownerBrowser.send("POST", path(id, "start-preparation"), version(3)), 409);
    }

    String path(UUID order, String operation) {
        return "/restaurant-orders/" + order + "/" + operation;
    }

    @Test
    void ownerCannotReadOrOperateAnotherRestaurantsOrders() throws Exception {
        UUID otherOwner = account("RESTAURANT_OWNER");
        UUID otherRestaurant = restaurant(otherOwner, "Other Restaurant");
        UUID otherBranch = branch(otherRestaurant, "Other Branch");
        var foreign = create(customer, otherRestaurant, otherBranch, OrderStatus.PLACED);
        body(ownerBrowser.send("GET", "/restaurant-orders/" + foreign.order().id(), null), 404);
        body(ownerBrowser.send("POST", path(foreign.order().id(), "accept"), version(0)), 404);
        body(
                ownerBrowser.send(
                        "GET", "/restaurant-orders?restaurantId=" + otherRestaurant, null),
                404);
        body(
                ownerBrowser.send(
                        "GET",
                        "/restaurant-orders?restaurantId="
                                + restaurant
                                + "&branchId="
                                + otherBranch,
                        null),
                404);
    }

    @Test
    void assignedStaffIsStrictlyLimitedToItsBranch() throws Exception {
        var assigned = create(customer, restaurant, branch, OrderStatus.PLACED);
        var unassigned = create(customer, restaurant, secondBranch, OrderStatus.PLACED);
        JsonNode queue =
                body(
                        staffBrowser.send(
                                "GET", "/restaurant-orders?restaurantId=" + restaurant, null),
                        200);
        assertThat(queue.get("total").asLong()).isEqualTo(1);
        body(staffBrowser.send("GET", "/restaurant-orders/" + assigned.order().id(), null), 200);
        body(staffBrowser.send("POST", path(assigned.order().id(), "accept"), version(0)), 200);
        body(staffBrowser.send("GET", "/restaurant-orders/" + unassigned.order().id(), null), 404);
        body(staffBrowser.send("POST", path(unassigned.order().id(), "accept"), version(0)), 404);
        body(
                staffBrowser.send(
                        "GET",
                        "/restaurant-orders?restaurantId="
                                + restaurant
                                + "&branchId="
                                + secondBranch,
                        null),
                404);
        Browser unassignedStaff = login(account("RESTAURANT_STAFF"));
        body(
                unassignedStaff.send("GET", "/restaurant-orders?restaurantId=" + restaurant, null),
                404);
    }

    @Test
    void restaurantRoleCsrfSuspensionAndInputSecurityAreEnforced() throws Exception {
        var order = create(customer, restaurant, branch, OrderStatus.PLACED);
        Browser customerOnly = customerBrowser;
        body(customerOnly.send("GET", "/restaurant-orders?restaurantId=" + restaurant, null), 403);
        for (String role : List.of("ADMIN", "DRIVER")) {
            Browser denied = login(account(role));
            body(denied.send("GET", "/restaurant-orders?restaurantId=" + restaurant, null), 403);
        }
        Browser anonymous = new Browser();
        body(anonymous.send("GET", "/restaurant-orders?restaurantId=" + restaurant, null), 401);
        String csrf = ownerBrowser.token;
        ownerBrowser.token = null;
        body(ownerBrowser.send("POST", path(order.order().id(), "accept"), version(0)), 403);
        ownerBrowser.token = csrf;
        body(
                ownerBrowser.send(
                        "POST",
                        path(order.order().id(), "accept"),
                        Map.of("version", 0, "status", "ACCEPTED")),
                400);
        body(
                ownerBrowser.send(
                        "POST", path(order.order().id(), "reject"), reason(0, "x".repeat(1001))),
                400);
        body(
                ownerBrowser.send(
                        "POST",
                        path(order.order().id(), "reject"),
                        Map.of("version", 0, "reason", "Busy", "actorId", owner)),
                400);
        jdbc.update("UPDATE users SET status='SUSPENDED' WHERE id=?", owner);
        assertThat(
                        ownerBrowser
                                .send("POST", path(order.order().id(), "accept"), version(0))
                                .statusCode())
                .isIn(401, 403);
    }

    @Test
    void cancelVersusAcceptHasOneWinnerAndCompleteHistory() throws Exception {
        var order = create(customer, restaurant, branch, OrderStatus.PLACED);
        var responses =
                race(
                        () ->
                                customerBrowser.send(
                                        "POST",
                                        "/orders/" + order.order().id() + "/cancel",
                                        reason(0, "Race")),
                        () ->
                                ownerBrowser.send(
                                        "POST", path(order.order().id(), "accept"), version(0)));
        assertThat(responses.stream().map(HttpResponse::statusCode).toList())
                .containsExactlyInAnyOrder(200, 409);
        assertThat(orders.history(order.order().id())).hasSize(2);
    }

    @Test
    void acceptVersusRejectAndDuplicateAcceptHaveOneWinner() throws Exception {
        var order = create(customer, restaurant, branch, OrderStatus.PLACED);
        Browser secondOwnerSession = login(owner);
        var race =
                race(
                        () ->
                                ownerBrowser.send(
                                        "POST", path(order.order().id(), "accept"), version(0)),
                        () ->
                                secondOwnerSession.send(
                                        "POST",
                                        path(order.order().id(), "reject"),
                                        reason(0, "Busy")));
        assertThat(race.stream().map(HttpResponse::statusCode).toList())
                .containsExactlyInAnyOrder(200, 409);
        assertThat(orders.history(order.order().id())).hasSize(2);
        var duplicate = create(customer, restaurant, branch, OrderStatus.PLACED);
        race =
                race(
                        () ->
                                ownerBrowser.send(
                                        "POST", path(duplicate.order().id(), "accept"), version(0)),
                        () ->
                                secondOwnerSession.send(
                                        "POST",
                                        path(duplicate.order().id(), "accept"),
                                        version(0)));
        assertThat(race.stream().map(HttpResponse::statusCode).toList())
                .containsExactlyInAnyOrder(200, 409);
        assertThat(orders.history(duplicate.order().id())).hasSize(2);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM notifications WHERE related_entity_id=?",
                Integer.class, duplicate.order().id())).isEqualTo(1);
    }

    @Test
    void requiredNotificationFailureRollsBackOrderTransition() throws Exception {
        var order = create(customer, restaurant, branch, OrderStatus.PLACED);
        doThrow(new IllegalStateException("injected notification failure"))
                .when(notificationStore)
                .insert(any(), any(), anyString(), anyString(), any(), any(), anyString(), any());
        body(ownerBrowser.send("POST", path(order.order().id(), "accept"), version(0)), 500);
        reset(notificationStore);
        assertThat(orders.details(order.order().id()).order().status()).isEqualTo(OrderStatus.PLACED);
        assertThat(orders.history(order.order().id())).hasSize(1);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM notifications WHERE related_entity_id=?",
                Integer.class, order.order().id())).isZero();
    }

    @Test
    void duplicatePreparationAndStaleTransitionsAreConflicts() throws Exception {
        var order = create(customer, restaurant, branch, OrderStatus.ACCEPTED);
        Browser secondOwnerSession = login(owner);
        var race =
                race(
                        () ->
                                ownerBrowser.send(
                                        "POST",
                                        path(order.order().id(), "start-preparation"),
                                        version(1)),
                        () ->
                                secondOwnerSession.send(
                                        "POST",
                                        path(order.order().id(), "start-preparation"),
                                        version(1)));
        assertThat(race.stream().map(HttpResponse::statusCode).toList())
                .containsExactlyInAnyOrder(200, 409);
        assertThat(orders.history(order.order().id())).hasSize(3);
        body(
                ownerBrowser.send("POST", path(order.order().id(), "ready-for-pickup"), version(1)),
                409);
    }

    List<HttpResponse<String>> race(
            Callable<HttpResponse<String>> first, Callable<HttpResponse<String>> second)
            throws Exception {
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var one =
                    pool.submit(
                            () -> {
                                start.await();
                                return first.call();
                            });
            var two =
                    pool.submit(
                            () -> {
                                start.await();
                                return second.call();
                            });
            start.countDown();
            return List.of(one.get(15, TimeUnit.SECONDS), two.get(15, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void listQueueAndDetailReadsStayBoundedWithoutItemNPlusOne() throws Exception {
        var one = create(customer, restaurant, branch, OrderStatus.PLACED);
        clearInvocations(jdbc);
        body(customerBrowser.send("GET", "/orders", null), 200);
        long customerOne = reads();
        body(ownerBrowser.send("GET", "/restaurant-orders?restaurantId=" + restaurant, null), 200);
        long queueOne = reads() - customerOne;
        clearInvocations(jdbc);
        body(customerBrowser.send("GET", "/orders/" + one.order().id(), null), 200);
        long detailOne = reads();
        for (int number = 0; number < 4; number++)
            create(customer, restaurant, branch, OrderStatus.PLACED);
        clearInvocations(jdbc);
        body(customerBrowser.send("GET", "/orders", null), 200);
        long customerMany = reads();
        body(ownerBrowser.send("GET", "/restaurant-orders?restaurantId=" + restaurant, null), 200);
        long queueMany = reads() - customerMany;
        clearInvocations(jdbc);
        body(customerBrowser.send("GET", "/orders/" + one.order().id(), null), 200);
        long detailMany = reads();
        assertThat(customerMany).isEqualTo(customerOne);
        assertThat(queueMany).isEqualTo(queueOne);
        assertThat(detailMany).isEqualTo(detailOne);
        Files.writeString(
                Path.of("target/order-operations-query-review.txt"),
                "JdbcTemplate read invocations (including delegating overloads): customer list="
                        + customerOne
                        + ", restaurant queue="
                        + queueOne
                        + ", detail="
                        + detailOne
                        + "; unchanged after five orders.\n"
                        + String.join(
                                "\n",
                                jdbc.queryForList(
                                        "EXPLAIN SELECT id FROM orders WHERE customer_id=? ORDER BY"
                                                + " created_at DESC,id DESC LIMIT 20",
                                        String.class,
                                        customer))
                        + "\n"
                        + String.join(
                                "\n",
                                jdbc.queryForList(
                                        "EXPLAIN SELECT id FROM orders WHERE branch_id=? AND"
                                            + " status='PLACED' ORDER BY created_at,id LIMIT 20",
                                        String.class,
                                        branch)));
    }

    long reads() {
        return mockingDetails(jdbc).getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().startsWith("query"))
                .count();
    }
}
