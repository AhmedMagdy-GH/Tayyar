package com.tayyar.menu;

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
class MenuIT extends PostgresIntegrationTest {
    @Value("${local.server.port}")
    int port;

    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    @Autowired PlatformTransactionManager transactions;
    @MockitoSpyBean MenuJournal journal;
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

    record Catalog(Fixture f, UUID branch, UUID menu, UUID category, UUID item) {}

    String menu(Fixture f) {
        return "/restaurants/" + f.restaurant() + "/menu";
    }

    String item(Catalog c) {
        return menu(c.f()) + "/items/" + c.item();
    }

    String effective(Catalog c) {
        return path(c.f()) + "/" + c.branch() + "/menu/items";
    }

    Map<String, Object> newItem(long version) {
        return Map.of(
                "name",
                "Zinger",
                "description",
                "Chicken",
                "basePrice",
                new java.math.BigDecimal("120.00"),
                "available",
                true,
                "version",
                version);
    }

    Map<String, Object> editItem(long version, boolean active, boolean available) {
        var result = new HashMap<>(newItem(version));
        result.put("active", active);
        return resultWithAvailability(result, available);
    }

    Map<String, Object> resultWithAvailability(Map<String, Object> input, boolean available) {
        input.put("available", available);
        return input;
    }

    Catalog catalog() throws Exception {
        var f = fixture();
        UUID branch = id(create(f));
        UUID menu = id(body(f.browser().send("POST", menu(f), Map.of("name", "Primary")), 201));
        UUID category =
                id(
                        body(
                                f.browser()
                                        .send(
                                                "POST",
                                                menu(f) + "/categories",
                                                Map.of("name", "Meals", "version", 0)),
                                201));
        UUID item =
                id(
                        body(
                                f.browser()
                                        .send(
                                                "POST",
                                                menu(f) + "/categories/" + category + "/items",
                                                newItem(1)),
                                201));
        return new Catalog(f, branch, menu, category, item);
    }

    long version(Catalog c) throws Exception {
        return body(c.f().browser().send("GET", menu(c.f()), null), 200).get("version").asLong();
    }

    JsonNode resolved(Catalog c) throws Exception {
        return body(c.f().browser().send("GET", effective(c) + "/" + c.item(), null), 200)
                .get("data");
    }

    @Test
    void ownerCreatesMenuCategoryAndItemAndAdminCanEdit() throws Exception {
        var c = catalog();
        var result = body(c.f().browser().send("GET", item(c), null), 200);
        assertThat(result.get("data").get("categoryId").asText())
                .isEqualTo(c.category().toString());
        assertThat(result.get("data").get("basePrice").decimalValue())
                .isEqualByComparingTo("120.00");
        body(c.f().admin().browser().send("PUT", item(c), editItem(2, true, true)), 200);
        body(
                c.f().admin()
                        .browser()
                        .send(
                                "PUT",
                                menu(c.f()) + "/categories/" + c.category(),
                                Map.of("name", "Updated Meals", "active", true, "version", 3)),
                200);
        assertThat(
                        body(c.f().browser().send("GET", menu(c.f()) + "/categories", null), 200)
                                .get("items")
                                .get(0)
                                .get("name")
                                .asText())
                .isEqualTo("Updated Meals");
        assertThat(
                        c.f().browser()
                                .send("POST", menu(c.f()), Map.of("name", "Duplicate"))
                                .statusCode())
                .isEqualTo(409);
    }

    @Test
    void customerStaffAndAnonymousHaveNoManagementAccess() throws Exception {
        var c = catalog();
        for (var actor : List.of(account("CUSTOMER"), account("RESTAURANT_STAFF"))) {
            assertThat(
                            actor.browser()
                                    .send(
                                            "POST",
                                            menu(c.f()) + "/categories",
                                            Map.of("name", "Bad", "version", 2))
                                    .statusCode())
                    .isEqualTo(403);
            assertThat(actor.browser().send("PUT", item(c), editItem(2, true, true)).statusCode())
                    .isEqualTo(403);
            assertThat(
                            actor.browser()
                                    .send(
                                            "PUT",
                                            effective(c) + "/" + c.item() + "/override",
                                            Map.of("price", 1, "version", 2))
                                    .statusCode())
                    .isEqualTo(403);
        }
        var anon = new Browser();
        anon.csrf();
        assertThat(anon.send("GET", menu(c.f()), null).statusCode()).isEqualTo(401);
        c.f().browser().token = null;
        assertThat(c.f().browser().send("PUT", item(c), editItem(2, true, true)).statusCode())
                .isEqualTo(403);
    }

    @Test
    void rejectsCrossRestaurantCategoryItemAndBranchAccess() throws Exception {
        var a = catalog();
        var b = catalog();
        assertThat(b.f().browser().send("PUT", item(a), editItem(2, true, true)).statusCode())
                .isEqualTo(404);
        assertThat(
                        b.f().browser()
                                .send(
                                        "POST",
                                        menu(a.f()) + "/categories",
                                        Map.of("name", "Bad", "version", 2))
                                .statusCode())
                .isEqualTo(404);
        assertThat(
                        b.f().browser()
                                .send(
                                        "POST",
                                        menu(b.f()) + "/categories/" + a.category() + "/items",
                                        newItem(2))
                                .statusCode())
                .isEqualTo(404);
        assertThat(
                        b.f().browser()
                                .send(
                                        "PUT",
                                        menu(b.f()) + "/items/" + a.item(),
                                        editItem(2, true, true))
                                .statusCode())
                .isEqualTo(404);
        assertThat(
                        b.f().browser()
                                .send(
                                        "PUT",
                                        effective(b) + "/" + a.item() + "/override",
                                        Map.of("price", 1, "version", 2))
                                .statusCode())
                .isEqualTo(404);
        assertThat(
                        b.f().browser()
                                .send(
                                        "PUT",
                                        effective(a) + "/" + a.item() + "/override",
                                        Map.of("price", 1, "version", 2))
                                .statusCode())
                .isEqualTo(404);
        assertThat(
                        a.f().admin()
                                .browser()
                                .send("GET", path(b.f()) + "/" + a.branch() + "/menu/items", null)
                                .statusCode())
                .isEqualTo(404);
    }

    @Test
    void rejectsParentRoleCurrencyAndPositionInjection() throws Exception {
        var c = catalog();
        for (String field :
                List.of("restaurantId", "menuId", "categoryId", "position", "role", "currency")) {
            var request = editItem(2, true, true);
            request.put(field, "ADMIN");
            assertThat(c.f().browser().send("PUT", item(c), request).statusCode()).isEqualTo(400);
        }
        assertThat(version(c)).isEqualTo(2);
    }

    @Test
    void rejectsNegativeExcessScaleOverflowAndInvalidOverridePrices() throws Exception {
        var c = catalog();
        for (String value : List.of("-0.01", "0.001", "10000000000.00")) {
            var request = editItem(2, true, true);
            request.put("basePrice", new java.math.BigDecimal(value));
            assertThat(c.f().browser().send("PUT", item(c), request).statusCode()).isEqualTo(400);
            assertThat(
                            c.f().browser()
                                    .send(
                                            "PUT",
                                            effective(c) + "/" + c.item() + "/override",
                                            Map.of(
                                                    "price",
                                                    new java.math.BigDecimal(value),
                                                    "version",
                                                    2))
                                    .statusCode())
                    .isEqualTo(400);
        }
        var zero = editItem(2, true, true);
        zero.put("basePrice", java.math.BigDecimal.ZERO);
        body(c.f().browser().send("PUT", item(c), zero), 200);
        assertThat(resolved(c).get("effectivePrice").decimalValue()).isEqualByComparingTo("0.00");
    }

    @Test
    void branchOverridesResolveAndRemovalRestoresInheritance() throws Exception {
        var c = catalog();
        var base = resolved(c);
        assertThat(base.get("effectivePrice").decimalValue()).isEqualByComparingTo("120.00");
        assertThat(base.get("effectiveAvailable").asBoolean()).isTrue();
        String override = effective(c) + "/" + c.item() + "/override";
        body(
                c.f().browser()
                        .send(
                                "PUT",
                                override,
                                Map.of(
                                        "price",
                                        new java.math.BigDecimal("125.50"),
                                        "available",
                                        false,
                                        "version",
                                        2)),
                200);
        assertThat(resolved(c).get("effectivePrice").decimalValue()).isEqualByComparingTo("125.50");
        assertThat(resolved(c).get("effectiveAvailable").asBoolean()).isFalse();
        body(c.f().browser().send("PUT", override, Map.of("available", true, "version", 3)), 200);
        assertThat(resolved(c).get("effectivePrice").decimalValue()).isEqualByComparingTo("120.00");
        body(c.f().browser().send("DELETE", override, Map.of("version", 4)), 200);
        assertThat(resolved(c).get("availabilityOverride").isNull()).isTrue();
    }

    @Test
    void inactiveItemCannotBeReenabledByOverrideAndBaseAvailabilityCan() throws Exception {
        var c = catalog();
        body(c.f().browser().send("PUT", item(c), editItem(2, true, false)), 200);
        assertThat(resolved(c).get("effectiveAvailable").asBoolean()).isFalse();
        body(
                c.f().browser()
                        .send(
                                "PUT",
                                effective(c) + "/" + c.item() + "/override",
                                Map.of("available", true, "version", 3)),
                200);
        assertThat(resolved(c).get("effectiveAvailable").asBoolean()).isTrue();
        body(c.f().browser().send("PUT", item(c), editItem(4, false, true)), 200);
        assertThat(resolved(c).get("effectiveAvailable").asBoolean()).isFalse();
        assertThat(c.f().browser().send("DELETE", item(c), Map.of("version", 5)).statusCode())
                .isEqualTo(405);
    }

    @Test
    void categoryAndMenuDeactivationGateEffectiveAvailability() throws Exception {
        var c = catalog();
        String category = menu(c.f()) + "/categories/" + c.category();
        body(
                c.f().browser()
                        .send(
                                "PUT",
                                category,
                                Map.of("name", "Meals", "active", false, "version", 2)),
                200);
        assertThat(resolved(c).get("effectiveAvailable").asBoolean()).isFalse();
        body(
                c.f().browser()
                        .send(
                                "PUT",
                                category,
                                Map.of("name", "Meals", "active", true, "version", 3)),
                200);
        body(
                c.f().browser()
                        .send(
                                "PUT",
                                menu(c.f()),
                                Map.of("name", "Primary", "active", false, "version", 4)),
                200);
        assertThat(resolved(c).get("effectiveAvailable").asBoolean()).isFalse();
    }

    @Test
    void orderingIsDeterministicAndRejectsDuplicatesMissingAndForeignIds() throws Exception {
        var c = catalog();
        UUID category2 =
                id(
                        body(
                                c.f().browser()
                                        .send(
                                                "POST",
                                                menu(c.f()) + "/categories",
                                                Map.of("name", "Drinks", "version", 2)),
                                201));
        String categoryOrder = menu(c.f()) + "/categories/order";
        for (var ids :
                List.of(
                        List.of(c.category(), c.category()),
                        List.of(c.category()),
                        List.of(c.category(), UUID.randomUUID())))
            assertThat(
                            c.f().browser()
                                    .send("PUT", categoryOrder, Map.of("ids", ids, "version", 3))
                                    .statusCode())
                    .isEqualTo(400);
        body(
                c.f().browser()
                        .send(
                                "PUT",
                                categoryOrder,
                                Map.of("ids", List.of(category2, c.category()), "version", 3)),
                200);
        assertThat(
                        body(
                                        c.f().browser()
                                                .send(
                                                        "GET",
                                                        menu(c.f()) + "/categories?size=1",
                                                        null),
                                        200)
                                .get("items")
                                .get(0)
                                .get("id")
                                .asText())
                .isEqualTo(category2.toString());
        UUID item2 =
                id(
                        body(
                                c.f().browser()
                                        .send(
                                                "POST",
                                                menu(c.f())
                                                        + "/categories/"
                                                        + c.category()
                                                        + "/items",
                                                newItem(4)),
                                201));
        body(
                c.f().browser()
                        .send(
                                "PUT",
                                menu(c.f()) + "/categories/" + c.category() + "/items/order",
                                Map.of("ids", List.of(item2, c.item()), "version", 5)),
                200);
        assertThat(
                        body(
                                        c.f().browser()
                                                .send(
                                                        "GET",
                                                        menu(c.f())
                                                                + "/categories/"
                                                                + c.category()
                                                                + "/items",
                                                        null),
                                        200)
                                .get("items")
                                .get(0)
                                .get("id")
                                .asText())
                .isEqualTo(item2.toString());
    }

    @Test
    void failedReorderRollsBackPositionsAndVersion() throws Exception {
        var c = catalog();
        UUID item2 =
                id(
                        body(
                                c.f().browser()
                                        .send(
                                                "POST",
                                                menu(c.f())
                                                        + "/categories/"
                                                        + c.category()
                                                        + "/items",
                                                newItem(2)),
                                201));
        doAnswer(
                        invocation -> {
                            invocation.callRealMethod();
                            throw new IllegalStateException("Injected reorder failure");
                        })
                .when(journal)
                .reorderItems(eq(c.category()), any(), any());
        try {
            assertThat(
                            c.f().browser()
                                    .send(
                                            "PUT",
                                            menu(c.f())
                                                    + "/categories/"
                                                    + c.category()
                                                    + "/items/order",
                                            Map.of("ids", List.of(item2, c.item()), "version", 3))
                                    .statusCode())
                    .isEqualTo(500);
        } finally {
            reset(journal);
        }
        assertThat(version(c)).isEqualTo(3);
        assertThat(
                        body(
                                        c.f().browser()
                                                .send(
                                                        "GET",
                                                        menu(c.f())
                                                                + "/categories/"
                                                                + c.category()
                                                                + "/items",
                                                        null),
                                        200)
                                .get("items")
                                .get(0)
                                .get("id")
                                .asText())
                .isEqualTo(c.item().toString());
    }

    @Test
    void concurrentItemChangesHaveOneWinnerAndStaleOverrideFails() throws Exception {
        var c = catalog();
        var start = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var one =
                    executor.submit(
                            () -> {
                                start.await();
                                return c.f().browser()
                                        .send("PUT", item(c), editItem(2, true, true))
                                        .statusCode();
                            });
            var two =
                    executor.submit(
                            () -> {
                                start.await();
                                return c.f().admin()
                                        .browser()
                                        .send("PUT", item(c), editItem(2, false, true))
                                        .statusCode();
                            });
            start.countDown();
            assertThat(List.of(one.get(15, TimeUnit.SECONDS), two.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 409);
        }
        String override = effective(c) + "/" + c.item() + "/override";
        body(c.f().browser().send("PUT", override, Map.of("price", 125, "version", 3)), 200);
        assertThat(
                        c.f().browser()
                                .send("PUT", override, Map.of("price", 1, "version", 3))
                                .statusCode())
                .isEqualTo(409);
        assertThat(resolved(c).get("effectivePrice").decimalValue()).isEqualByComparingTo("125.00");
    }

    @Test
    void effectiveCollectionsAreBoundedAndEmptyOverridesRejected() throws Exception {
        var c = catalog();
        assertThat(c.f().browser().send("GET", effective(c) + "?size=101", null).statusCode())
                .isEqualTo(400);
        assertThat(
                        body(c.f().browser().send("GET", effective(c) + "?size=1", null), 200)
                                .get("items")
                                .size())
                .isEqualTo(1);
        assertThat(
                        c.f().browser()
                                .send(
                                        "PUT",
                                        effective(c) + "/" + c.item() + "/override",
                                        Map.of("version", 2))
                                .statusCode())
                .isEqualTo(400);
    }

    @Test
    void collectionCapsPreventUnboundedReorderSets() throws Exception {
        var c = catalog();
        jdbc.update(
                "INSERT INTO"
                    + " menu_categories(id,menu_id,restaurant_id,name,position,created_at,updated_at)"
                    + " SELECT gen_random_uuid(),?,?,'Category',n,now(),now() FROM"
                    + " generate_series(1,99) n",
                c.menu(),
                c.f().restaurant());
        jdbc.update(
                "INSERT INTO"
                    + " menu_items(id,category_id,restaurant_id,name,base_price,position,created_at,updated_at)"
                    + " SELECT gen_random_uuid(),?,?,'Item',1,n,now(),now() FROM"
                    + " generate_series(1,499) n",
                c.category(),
                c.f().restaurant());
        assertThat(
                        c.f().browser()
                                .send(
                                        "POST",
                                        menu(c.f()) + "/categories",
                                        Map.of("name", "Overflow", "version", 2))
                                .statusCode())
                .isEqualTo(400);
        assertThat(
                        c.f().browser()
                                .send(
                                        "POST",
                                        menu(c.f()) + "/categories/" + c.category() + "/items",
                                        newItem(2))
                                .statusCode())
                .isEqualTo(400);
        assertThat(version(c)).isEqualTo(2);
    }

    @Test
    void databaseProtectsCrossRestaurantContextsDuplicateOverridesOrderingAndMoney()
            throws Exception {
        var a = catalog();
        var b = catalog();
        sqlState(
                "23503",
                () ->
                        jdbc.update(
                                "INSERT INTO branch_menu_item_overrides VALUES"
                                        + " (?,?,?,true,125,now(),now())",
                                a.branch(),
                                b.item(),
                                a.f().restaurant()));
        jdbc.update(
                "INSERT INTO branch_menu_item_overrides VALUES (?,?,?,true,125,now(),now())",
                a.branch(),
                a.item(),
                a.f().restaurant());
        sqlState(
                "23505",
                () ->
                        jdbc.update(
                                "INSERT INTO branch_menu_item_overrides VALUES"
                                        + " (?,?,?,true,125,now(),now())",
                                a.branch(),
                                a.item(),
                                a.f().restaurant()));
        sqlState(
                "23514",
                () ->
                        jdbc.update(
                                "UPDATE menu_items SET category_id=? WHERE id=?",
                                b.category(),
                                a.item()));
        sqlState(
                "23514",
                () -> jdbc.update("UPDATE menu_items SET base_price=-1 WHERE id=?", a.item()));
        sqlState(
                "23514",
                () -> jdbc.update("UPDATE menu_items SET base_price='NaN' WHERE id=?", a.item()));
        sqlState(
                "22003",
                () ->
                        jdbc.update(
                                "UPDATE menu_items SET base_price=10000000000 WHERE id=?",
                                a.item()));
        sqlState(
                "23503",
                () ->
                        jdbc.update(
                                "INSERT INTO"
                                    + " menu_categories(id,menu_id,restaurant_id,name,position,created_at,updated_at)"
                                    + " VALUES (?,?,?,'Bad',5,now(),now())",
                                UUID.randomUUID(),
                                a.menu(),
                                b.f().restaurant()));
        sqlState(
                "23503",
                () ->
                        jdbc.update(
                                "INSERT INTO"
                                    + " menu_items(id,category_id,restaurant_id,name,base_price,position,created_at,updated_at)"
                                    + " VALUES (?,?,?,'Bad',1,5,now(),now())",
                                UUID.randomUUID(),
                                a.category(),
                                b.f().restaurant()));
        sqlState(
                "23505",
                () ->
                        jdbc.update(
                                "INSERT INTO"
                                    + " menu_items(id,category_id,restaurant_id,name,base_price,position,created_at,updated_at)"
                                    + " VALUES (?,?,?,'Bad',1,0,now(),now())",
                                UUID.randomUUID(),
                                a.category(),
                                a.f().restaurant()));
    }
}
