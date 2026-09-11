package com.tayyar.delivery;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.tayyar.order.*;
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
class DriverOperationsIT extends PostgresIntegrationTest {
    static final String PASSWORD = "Driver operations password!";

    @Value("${local.server.port}")
    int port;

    @Autowired ObjectMapper json;
    @MockitoSpyBean JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    @Autowired OrderService orders;
    @Autowired PaymentService payments;
    @Autowired PlatformTransactionManager transactions;
    @MockitoSpyBean DriverOperationsStore deliveryStore;
    @MockitoSpyBean OrderStore orderStore;
    @MockitoSpyBean PaymentStore paymentStore;
    @MockitoSpyBean NotificationStore notificationStore;

    UUID admin, driver, otherDriver, customer, owner, restaurant, tayyarBranch, restaurantBranch;
    UUID menuItem;
    Browser adminBrowser, driverBrowser, otherDriverBrowser, customerBrowser, ownerBrowser;
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
        reset(deliveryStore, orderStore, paymentStore, jdbc);
        admin = account("ADMIN");
        driver = account("DRIVER");
        otherDriver = account("DRIVER");
        customer = account("CUSTOMER");
        owner = account("RESTAURANT_OWNER");
        restaurant = restaurant(owner);
        tayyarBranch = branch(restaurant, "Tayyar pickup", "TAYYAR_DELIVERY");
        restaurantBranch = branch(restaurant, "Restaurant delivery", "RESTAURANT_DELIVERY");
        menuItem = menuItem(restaurant);
        adminBrowser = login(admin);
        driverBrowser = login(driver);
        otherDriverBrowser = login(otherDriver);
        customerBrowser = login(customer);
        ownerBrowser = login(owner);
    }

    UUID account(String... roles) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO users(id,full_name,email,phone,password_hash,status,created_at,updated_at)"
                        + " VALUES (?,'Delivery User',?, '+201000000000',?,'ACTIVE',now(),now())",
                id,
                id + "@example.com",
                passwords.encode(PASSWORD));
        for (String role : roles)
            jdbc.update("INSERT INTO user_roles(user_id,role_name) VALUES (?,?)", id, role);
        return id;
    }

    UUID restaurant(UUID restaurantOwner) {
        UUID application = UUID.randomUUID(), id = UUID.randomUUID();
        new TransactionTemplate(transactions)
                .executeWithoutResult(
                        status -> {
                            jdbc.update(
                                    "INSERT INTO restaurant_applications"
                                            + "(id,applicant_id,status,current_revision,created_at,updated_at)"
                                            + " VALUES (?,?,'APPROVED',1,now(),now())",
                                    application,
                                    restaurantOwner);
                            jdbc.update(
                                    "INSERT INTO application_submissions VALUES"
                                            + " (?,1,?,'Delivery operations',now())",
                                    application,
                                    "Delivery Restaurant");
                            jdbc.update(
                                    "INSERT INTO restaurants"
                                            + "(id,application_id,name,description,status,created_at,updated_at)"
                                            + " VALUES (?,?,'Delivery Restaurant','Public','ACTIVE',now(),now())",
                                    id,
                                    application);
                            jdbc.update(
                                    "INSERT INTO restaurant_memberships VALUES (?,?,'OWNER',now())",
                                    id,
                                    restaurantOwner);
                        });
        return id;
    }

    UUID branch(UUID restaurantId, String name, String model) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO branches"
                        + "(id,restaurant_id,name,address_line1,city,country_code,phone,timezone,"
                        + "delivery_model,status,created_at,updated_at) VALUES"
                        + " (?,?,?,'Pickup Street','Cairo','EG','+201111111111','Africa/Cairo',"
                        + "?,'ACTIVE',now(),now())",
                id,
                restaurantId,
                name,
                model);
        return id;
    }

    UUID menuItem(UUID restaurantId) {
        UUID menu = UUID.randomUUID(), category = UUID.randomUUID(), item = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO restaurant_menus(id,restaurant_id,name,created_at,updated_at)"
                        + " VALUES (?,?,'Main',now(),now())",
                menu,
                restaurantId);
        jdbc.update(
                "INSERT INTO menu_categories"
                        + "(id,menu_id,restaurant_id,name,position,created_at,updated_at)"
                        + " VALUES (?,?,?,'Meals',0,now(),now())",
                category,
                menu,
                restaurantId);
        jdbc.update(
                "INSERT INTO menu_items"
                        + "(id,category_id,restaurant_id,name,base_price,position,created_at,updated_at)"
                        + " VALUES (?,?,?,'Meal',80,0,now(),now())",
                item,
                category,
                restaurantId);
        return item;
    }

    Browser login(UUID user) throws Exception {
        Browser browser = new Browser();
        browser.csrf();
        body(
                browser.send(
                        "POST",
                        "/auth/session",
                        Map.of("email", user + "@example.com", "password", PASSWORD)),
                204);
        browser.csrf();
        return browser;
    }

    JsonNode body(HttpResponse<String> response, int status) throws Exception {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
        return response.body().isBlank() ? null : json.readTree(response.body());
    }

    OrderDtos.Details ready(UUID branchId) {
        var order =
                orders.create(
                        new OrderDtos.Draft(
                                customer,
                                restaurant,
                                branchId,
                                OrderStatus.PLACED,
                                List.of(
                                        new OrderDtos.PurchaseItem(
                                                menuItem,
                                                "Purchased meal",
                                                new BigDecimal("80.00"),
                                                2)),
                                new OrderDtos.AddressSnapshot(
                                        "Home",
                                        "Delivery Street",
                                        "12",
                                        "3",
                                        "7",
                                        "Landmark",
                                        "Leave at reception",
                                        "Cairo",
                                        "Cairo",
                                        "12345",
                                        "EG",
                                        null,
                                        null,
                                        UUID.randomUUID(),
                                        "Zone",
                                        "Cairo"),
                                new BigDecimal("20.00"),
                                BigDecimal.ZERO.setScale(2)),
                        TransitionActor.system());
        for (OrderStatus target :
                List.of(
                        OrderStatus.ACCEPTED,
                        OrderStatus.PREPARING,
                        OrderStatus.READY_FOR_PICKUP)) {
            orders.transition(
                    order.order().id(),
                    order.order().version(),
                    target,
                    TransitionActor.system(),
                    null);
            order = orders.details(order.order().id());
        }
        payments.create(
                new PaymentDtos.Create(order.order().id(), PaymentMethod.CASH, null, null),
                TransitionActor.system());
        return order;
    }

    void provisionAndAvailable(Browser browser, UUID id) throws Exception {
        body(adminBrowser.send("POST", "/admin/drivers", Map.of("userId", id)), 200);
        body(browser.send("POST", "/driver/profile/available", Map.of("version", 0)), 200);
    }

    JsonNode assign(OrderDtos.Details order, UUID driverId) throws Exception {
        return body(
                adminBrowser.send(
                        "POST",
                        "/admin/delivery-assignments",
                        Map.of(
                                "orderId",
                                order.order().id(),
                                "driverId",
                                driverId,
                                "orderVersion",
                                order.order().version())),
                200);
    }

    Map<String, Object> transition(long orderVersion, long assignmentVersion) {
        return Map.of("orderVersion", orderVersion, "assignmentVersion", assignmentVersion);
    }

    @Test
    void driverProfileStateIsVersionedAndBusyCannotGoOffline() throws Exception {
        body(adminBrowser.send("POST", "/admin/drivers", Map.of("userId", driver)), 200);
        JsonNode profile = body(driverBrowser.send("GET", "/driver/profile", null), 200);
        assertThat(profile.get("state").asText()).isEqualTo("OFFLINE");
        body(driverBrowser.send("POST", "/driver/profile/available", Map.of("version", 0)), 200);
        body(driverBrowser.send("POST", "/driver/profile/offline", Map.of("version", 1)), 200);
        body(driverBrowser.send("POST", "/driver/profile/available", Map.of("version", 2)), 200);
        var order = ready(tayyarBranch);
        assign(order, driver);
        body(driverBrowser.send("POST", "/driver/profile/offline", Map.of("version", 4)), 409);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT state FROM driver_profiles WHERE user_id=?", String.class, driver))
                .isEqualTo("BUSY");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM driver_state_history WHERE driver_id=?",
                                Integer.class,
                                driver))
                .isEqualTo(5);
    }

    @Test
    void profileAndAssignmentAuthorizationEligibilityAndModelAreEnforced() throws Exception {
        UUID ordinary = account("CUSTOMER");
        body(adminBrowser.send("POST", "/admin/drivers", Map.of("userId", ordinary)), 404);
        body(customerBrowser.send("POST", "/admin/drivers", Map.of("userId", driver)), 403);
        body(ownerBrowser.send("POST", "/admin/drivers", Map.of("userId", driver)), 403);
        body(driverBrowser.send("POST", "/admin/drivers", Map.of("userId", driver)), 403);
        body(customerBrowser.send("GET", "/driver/profile", null), 403);
        provisionAndAvailable(driverBrowser, driver);
        var restaurantDelivery = ready(restaurantBranch);
        body(
                adminBrowser.send(
                        "POST",
                        "/admin/delivery-assignments",
                        Map.of(
                                "orderId",
                                restaurantDelivery.order().id(),
                                "driverId",
                                driver,
                                "orderVersion",
                                3)),
                409);
        var eligible = ready(tayyarBranch);
        Map<String, Object> assignmentInput =
                Map.of(
                        "orderId",
                        eligible.order().id(),
                        "driverId",
                        driver,
                        "orderVersion",
                        3);
        for (Browser denied : List.of(customerBrowser, ownerBrowser, driverBrowser))
            body(denied.send("POST", "/admin/delivery-assignments", assignmentInput), 403);
        assertThat(
                        new Browser()
                                .send("POST", "/admin/delivery-assignments", assignmentInput)
                                .statusCode())
                .isIn(401, 403);
        body(driverBrowser.send("POST", "/driver/profile/offline", Map.of("version", 1)), 200);
        body(
                adminBrowser.send(
                        "POST",
                        "/admin/delivery-assignments",
                        Map.of(
                                "orderId",
                                eligible.order().id(),
                                "driverId",
                                driver,
                                "orderVersion",
                                3)),
                409);
        jdbc.update("UPDATE users SET status='SUSPENDED' WHERE id=?", driver);
        body(
                adminBrowser.send(
                        "POST",
                        "/admin/delivery-assignments",
                        Map.of(
                                "orderId",
                                eligible.order().id(),
                                "driverId",
                                driver,
                                "orderVersion",
                                3)),
                404);
        body(
                adminBrowser.send(
                        "POST",
                        "/admin/delivery-assignments",
                        Map.of(
                                "orderId",
                                UUID.randomUUID(),
                                "driverId",
                                UUID.randomUUID(),
                                "orderVersion",
                                0)),
                404);
    }

    @Test
    void adminAssignsReadyTayyarOrderAndDriverSeesOnlySafeOwnData() throws Exception {
        provisionAndAvailable(driverBrowser, driver);
        provisionAndAvailable(otherDriverBrowser, otherDriver);
        var order = ready(tayyarBranch);
        JsonNode assignment = assign(order, driver);
        assertThat(assignment.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject(
                "SELECT type FROM notifications WHERE recipient_user_id=? AND related_entity_id=?",
                String.class, driver, UUID.fromString(assignment.get("assignmentId").asText())))
                .isEqualTo("DELIVERY_ASSIGNED");
        JsonNode queue = body(driverBrowser.send("GET", "/driver/orders", null), 200);
        assertThat(queue.get("total").asLong()).isEqualTo(1);
        JsonNode item = queue.get("items").get(0);
        assertThat(item.get("orderId").asText()).isEqualTo(order.order().id().toString());
        assertThat(item.get("destination").get("street").asText())
                .isEqualTo("Delivery Street");
        assertThat(item.get("cashAmountToCollect").decimalValue()).isEqualByComparingTo("180");
        assertThat(item.toString())
                .doesNotContain("password", "email", "customerId", "actorId", "provider");
        body(otherDriverBrowser.send("GET", "/driver/orders/" + order.order().id(), null), 404);
        body(
                otherDriverBrowser.send(
                        "POST",
                        "/driver/orders/" + order.order().id() + "/pickup",
                        transition(3, 0)),
                404);
    }

    @Test
    void assignedDriverPicksUpAndDeliversCashAtomically() throws Exception {
        provisionAndAvailable(driverBrowser, driver);
        var order = ready(tayyarBranch);
        JsonNode assignment = assign(order, driver);
        UUID assignmentId = UUID.fromString(assignment.get("assignmentId").asText());
        JsonNode pickup =
                body(
                        driverBrowser.send(
                                "POST",
                                "/driver/orders/" + order.order().id() + "/pickup",
                                transition(3, 0)),
                        200);
        assertThat(pickup.get("status").asText()).isEqualTo("OUT_FOR_DELIVERY");
        JsonNode delivered =
                body(
                        driverBrowser.send(
                                "POST",
                                "/driver/orders/" + order.order().id() + "/deliver",
                                transition(4, 0)),
                        200);
        assertThat(delivered.get("orderStatus").asText()).isEqualTo("DELIVERED");
        assertThat(delivered.get("paymentStatus").asText()).isEqualTo("PAID");
        assertThat(delivered.get("driverState").asText()).isEqualTo("AVAILABLE");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT status FROM delivery_assignments WHERE id=?",
                                String.class,
                                assignmentId))
                .isEqualTo("COMPLETED");
        assertThat(orders.history(order.order().id())).hasSize(6);
        assertThat(jdbc.queryForList(
                "SELECT type FROM notifications WHERE recipient_user_id=? AND related_entity_id=?"
                        + " AND type LIKE 'ORDER_%' ORDER BY created_at,id",
                String.class, customer, order.order().id()))
                .containsExactly("ORDER_OUT_FOR_DELIVERY", "ORDER_DELIVERED");
        UUID payment =
                jdbc.queryForObject(
                        "SELECT id FROM payments WHERE order_id=?", UUID.class, order.order().id());
        assertThat(payments.history(payment)).hasSize(2);
        assertThat(payments.details(payment).amount()).isEqualByComparingTo("180.00");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM delivery_assignment_history WHERE"
                                        + " assignment_id=?",
                                Integer.class,
                                assignmentId))
                .isEqualTo(2);
        assertThat(body(driverBrowser.send("GET", "/driver/orders", null), 200).get("total").asLong())
                .isZero();
    }

    @Test
    void invalidStaleDuplicateAndPaymentInjectionOperationsConflict() throws Exception {
        provisionAndAvailable(driverBrowser, driver);
        var order = ready(tayyarBranch);
        assign(order, driver);
        body(
                driverBrowser.send(
                        "POST",
                        "/driver/orders/" + order.order().id() + "/deliver",
                        transition(3, 0)),
                409);
        body(
                driverBrowser.send(
                        "POST",
                        "/driver/orders/" + order.order().id() + "/pickup",
                        transition(2, 0)),
                409);
        body(
                driverBrowser.send(
                        "POST",
                        "/driver/orders/" + order.order().id() + "/pickup",
                        Map.of(
                                "orderVersion",
                                3,
                                "assignmentVersion",
                                0,
                                "paymentStatus",
                                "PAID",
                                "amount",
                                1)),
                400);
        body(
                driverBrowser.send(
                        "POST",
                        "/driver/orders/" + order.order().id() + "/pickup",
                        transition(3, 0)),
                200);
        body(
                driverBrowser.send(
                        "POST",
                        "/driver/orders/" + order.order().id() + "/pickup",
                        transition(3, 0)),
                409);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM notifications WHERE recipient_user_id=?"
                        + " AND related_entity_id=? AND type='ORDER_OUT_FOR_DELIVERY'",
                Integer.class, customer, order.order().id())).isEqualTo(1);
    }

    @Test
    void assignmentNotificationFailureRollsBackAssignmentAndDriverState() throws Exception {
        provisionAndAvailable(driverBrowser, driver);
        var order = ready(tayyarBranch);
        doThrow(new IllegalStateException("injected notification failure"))
                .when(notificationStore)
                .insert(any(), any(), anyString(), anyString(), any(), any(), anyString(), any());
        body(adminBrowser.send(
                "POST", "/admin/delivery-assignments",
                Map.of("orderId", order.order().id(), "driverId", driver,
                        "orderVersion", order.order().version())), 500);
        reset(notificationStore);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM delivery_assignments WHERE order_id=?",
                Integer.class, order.order().id())).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT state FROM driver_profiles WHERE user_id=?", String.class, driver))
                .isEqualTo("AVAILABLE");
    }

    @Test
    void paymentHistoryFailureRollsBackEntireDelivery() throws Exception {
        provisionAndAvailable(driverBrowser, driver);
        var order = ready(tayyarBranch);
        JsonNode assignment = assign(order, driver);
        UUID assignmentId = UUID.fromString(assignment.get("assignmentId").asText());
        body(
                driverBrowser.send(
                        "POST",
                        "/driver/orders/" + order.order().id() + "/pickup",
                        transition(3, 0)),
                200);
        doThrow(new IllegalStateException("injected payment history failure"))
                .when(paymentStore)
                .history(any(), eq(PaymentStatus.PENDING), eq(PaymentStatus.PAID), any(), any(), any());
        body(
                driverBrowser.send(
                        "POST",
                        "/driver/orders/" + order.order().id() + "/deliver",
                        transition(4, 0)),
                500);
        reset(paymentStore);
        assertRollback(order.order().id(), assignmentId);
    }

    @Test
    void orderHistoryFailureRollsBackBeforeCashCollection() throws Exception {
        provisionAndAvailable(driverBrowser, driver);
        var order = ready(tayyarBranch);
        UUID assignmentId = UUID.fromString(assign(order, driver).get("assignmentId").asText());
        body(
                driverBrowser.send(
                        "POST",
                        "/driver/orders/" + order.order().id() + "/pickup",
                        transition(3, 0)),
                200);
        doThrow(new IllegalStateException("injected Order history failure"))
                .when(orderStore)
                .history(
                        eq(order.order().id()),
                        eq(OrderStatus.OUT_FOR_DELIVERY),
                        eq(OrderStatus.DELIVERED),
                        any(),
                        any(),
                        any());
        body(
                driverBrowser.send(
                        "POST",
                        "/driver/orders/" + order.order().id() + "/deliver",
                        transition(4, 0)),
                500);
        reset(orderStore);
        assertRollback(order.order().id(), assignmentId);
    }

    @Test
    void assignmentCompletionAndDriverReleaseFailuresRollBackDelivery() throws Exception {
        provisionAndAvailable(driverBrowser, driver);
        var first = ready(tayyarBranch);
        UUID firstAssignment = UUID.fromString(assign(first, driver).get("assignmentId").asText());
        body(
                driverBrowser.send(
                        "POST",
                        "/driver/orders/" + first.order().id() + "/pickup",
                        transition(3, 0)),
                200);
        doThrow(new IllegalStateException("injected assignment completion failure"))
                .when(deliveryStore)
                .completeAssignment(any(), any(), any());
        body(
                driverBrowser.send(
                        "POST",
                        "/driver/orders/" + first.order().id() + "/deliver",
                        transition(4, 0)),
                500);
        reset(deliveryStore);
        assertRollback(first.order().id(), firstAssignment);

        body(
                driverBrowser.send(
                        "POST",
                        "/driver/orders/" + first.order().id() + "/deliver",
                        transition(4, 0)),
                200);
        var second = ready(tayyarBranch);
        UUID secondAssignment = UUID.fromString(assign(second, driver).get("assignmentId").asText());
        body(
                driverBrowser.send(
                        "POST",
                        "/driver/orders/" + second.order().id() + "/pickup",
                        transition(3, 0)),
                200);
        doThrow(new IllegalStateException("injected Driver release failure"))
                .when(deliveryStore)
                .changeDriverState(
                        argThat(value -> value != null && value.state() == DriverState.BUSY),
                        eq(DriverState.AVAILABLE),
                        any(),
                        any(),
                        any());
        body(
                driverBrowser.send(
                        "POST",
                        "/driver/orders/" + second.order().id() + "/deliver",
                        transition(4, 0)),
                500);
        reset(deliveryStore);
        assertRollback(second.order().id(), secondAssignment);
    }

    void assertRollback(UUID order, UUID assignment) {
        assertThat(orders.details(order).order().status()).isEqualTo(OrderStatus.OUT_FOR_DELIVERY);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT status FROM payments WHERE order_id=?", String.class, order))
                .isEqualTo("PENDING");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT status FROM delivery_assignments WHERE id=?",
                                String.class,
                                assignment))
                .isEqualTo("ACTIVE");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT state FROM driver_profiles WHERE user_id=?", String.class, driver))
                .isEqualTo("BUSY");
    }

    @Test
    void concurrentAssignmentRulesAllowOnlyOneWinner() throws Exception {
        provisionAndAvailable(driverBrowser, driver);
        provisionAndAvailable(otherDriverBrowser, otherDriver);
        var order = ready(tayyarBranch);
        Browser secondAdmin = login(admin);
        var sameOrder =
                race(
                        () ->
                                adminBrowser.send(
                                        "POST",
                                        "/admin/delivery-assignments",
                                        Map.of(
                                                "orderId",
                                                order.order().id(),
                                                "driverId",
                                                driver,
                                                "orderVersion",
                                                3)),
                        () ->
                                secondAdmin.send(
                                        "POST",
                                        "/admin/delivery-assignments",
                                        Map.of(
                                                "orderId",
                                                order.order().id(),
                                                "driverId",
                                                otherDriver,
                                                "orderVersion",
                                                3)));
        assertThat(sameOrder.stream().map(HttpResponse::statusCode).toList())
                .containsExactlyInAnyOrder(200, 409);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM delivery_assignments WHERE order_id=? AND"
                                        + " status='ACTIVE'",
                                Integer.class,
                                order.order().id()))
                .isEqualTo(1);

        UUID freeDriver =
                jdbc.queryForObject(
                        "SELECT user_id FROM driver_profiles WHERE state='AVAILABLE' AND user_id IN"
                                + " (?,?)",
                        UUID.class,
                        driver,
                        otherDriver);
        var first = ready(tayyarBranch);
        var second = ready(tayyarBranch);
        var sameDriver =
                race(
                        () ->
                                adminBrowser.send(
                                        "POST",
                                        "/admin/delivery-assignments",
                                        Map.of(
                                                "orderId",
                                                first.order().id(),
                                                "driverId",
                                                freeDriver,
                                                "orderVersion",
                                                3)),
                        () ->
                                secondAdmin.send(
                                        "POST",
                                        "/admin/delivery-assignments",
                                        Map.of(
                                                "orderId",
                                                second.order().id(),
                                                "driverId",
                                                freeDriver,
                                                "orderVersion",
                                                3)));
        assertThat(sameDriver.stream().map(HttpResponse::statusCode).toList())
                .containsExactlyInAnyOrder(200, 409);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM delivery_assignments WHERE driver_id=? AND"
                                        + " status='ACTIVE'",
                                Integer.class,
                                freeDriver))
                .isEqualTo(1);
    }

    @Test
    void duplicatePickupAndDeliveryHaveSingleWinnersAndSingleHistories() throws Exception {
        provisionAndAvailable(driverBrowser, driver);
        var pickupOrder = ready(tayyarBranch);
        assign(pickupOrder, driver);
        Browser secondSession = login(driver);
        var pickupRace =
                race(
                        () ->
                                driverBrowser.send(
                                        "POST",
                                        "/driver/orders/" + pickupOrder.order().id() + "/pickup",
                                        transition(3, 0)),
                        () ->
                                secondSession.send(
                                        "POST",
                                        "/driver/orders/" + pickupOrder.order().id() + "/pickup",
                                        transition(3, 0)));
        assertThat(pickupRace.stream().map(HttpResponse::statusCode).toList())
                .containsExactlyInAnyOrder(200, 409);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM order_status_history WHERE order_id=? AND"
                                        + " new_status='OUT_FOR_DELIVERY'",
                                Integer.class,
                                pickupOrder.order().id()))
                .isEqualTo(1);
        var deliveryRace =
                race(
                        () ->
                                driverBrowser.send(
                                        "POST",
                                        "/driver/orders/" + pickupOrder.order().id() + "/deliver",
                                        transition(4, 0)),
                        () ->
                                secondSession.send(
                                        "POST",
                                        "/driver/orders/" + pickupOrder.order().id() + "/deliver",
                                        transition(4, 0)));
        assertThat(deliveryRace.stream().map(HttpResponse::statusCode).toList())
                .containsExactlyInAnyOrder(200, 409);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM payment_status_history psh JOIN payments p"
                                        + " ON p.id=psh.payment_id WHERE p.order_id=? AND"
                                        + " psh.new_status='PAID'",
                                Integer.class,
                                pickupOrder.order().id()))
                .isEqualTo(1);
    }

    @Test
    void csrfSuspensionMalformedAndUnknownInputsAreRejected() throws Exception {
        provisionAndAvailable(driverBrowser, driver);
        var order = ready(tayyarBranch);
        String csrf = adminBrowser.token;
        adminBrowser.token = null;
        body(
                adminBrowser.send(
                        "POST",
                        "/admin/delivery-assignments",
                        Map.of("orderId", order.order().id(), "driverId", driver, "orderVersion", 3)),
                403);
        adminBrowser.token = csrf;
        body(
                adminBrowser.send(
                        "POST",
                        "/admin/delivery-assignments",
                        Map.of(
                                "orderId",
                                order.order().id(),
                                "driverId",
                                driver,
                                "orderVersion",
                                3,
                                "actingDriverId",
                                driver)),
                400);
        body(driverBrowser.send("GET", "/driver/orders/not-a-uuid", null), 400);
        body(driverBrowser.send("GET", "/driver/orders?size=101", null), 400);
        body(driverBrowser.send("GET", "/driver/orders?driverId=" + otherDriver, null), 400);
        jdbc.update("UPDATE users SET status='SUSPENDED' WHERE id=?", driver);
        assertThat(driverBrowser.send("GET", "/driver/orders", null).statusCode()).isIn(401, 403);
    }

    @Test
    void driverQueueUsesBoundedJoinedReadsAndDeliveryIndexes() throws Exception {
        provisionAndAvailable(driverBrowser, driver);
        var order = ready(tayyarBranch);
        assign(order, driver);
        clearInvocations(jdbc);
        body(driverBrowser.send("GET", "/driver/orders?page=0&size=20", null), 200);
        long reads =
                mockingDetails(jdbc).getInvocations().stream()
                        .filter(call -> call.getMethod().getName().startsWith("query"))
                        .count();
        assertThat(reads).isLessThanOrEqualTo(10);
        List<String> driverPlan =
                jdbc.queryForList(
                        "EXPLAIN SELECT order_id FROM delivery_assignments WHERE driver_id=? AND"
                                + " status='ACTIVE' LIMIT 20",
                        String.class,
                        driver);
        List<String> orderPlan =
                jdbc.queryForList(
                        "EXPLAIN SELECT id FROM delivery_assignments WHERE order_id=? AND"
                                + " status='ACTIVE'",
                        String.class,
                        order.order().id());
        String evidence =
                "JdbcTemplate read invocations (including delegating overloads): Driver queue="
                        + reads
                        + ". The queue uses one profile check, one count and one joined projection;"
                        + " v1 permits at most one active assignment per Driver.\n"
                        + String.join("\n", driverPlan)
                        + "\n"
                        + String.join("\n", orderPlan);
        Files.writeString(Path.of("target/driver-operations-query-review.txt"), evidence);
        assertThat(evidence)
                .contains("delivery_assignments_active_driver_uq", "delivery_assignments_active_order_uq");
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
            return List.of(one.get(20, TimeUnit.SECONDS), two.get(20, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
    }
}
