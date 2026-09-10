package com.tayyar.delivery;

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
class DeliveryZonesIT extends PostgresIntegrationTest {
    @Value("${local.server.port}")
    int port;

    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    @Autowired PlatformTransactionManager transactions;
    @MockitoSpyBean BranchDeliveryZoneRepository rules;
    @Autowired ServiceabilityQuery query;
    @jakarta.persistence.PersistenceContext jakarta.persistence.EntityManager entityManager;
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

    record Area(Fixture f, UUID branch, UUID city, UUID zone) {}

    JsonNode city(Account admin, String name) throws Exception {
        return body(
                admin.browser().send("POST", "/admin/cities", Map.of("name", name, "active", true)),
                201);
    }

    JsonNode zone(Account admin, UUID city, String name) throws Exception {
        return body(
                admin.browser()
                        .send(
                                "POST",
                                "/admin/delivery-zones",
                                Map.of(
                                        "cityId",
                                        city,
                                        "profile",
                                        Map.of("name", name, "active", true))),
                201);
    }

    Area area() throws Exception {
        var f = fixture();
        UUID branch = id(create(f));
        UUID city = id(city(f.admin(), "City " + UUID.randomUUID()));
        UUID zone = id(zone(f.admin(), city, "Nasr Street"));
        return new Area(f, branch, city, zone);
    }

    String rulePath(Area a) {
        return path(a.f()) + "/" + a.branch() + "/delivery-zones/" + a.zone();
    }

    Map<String, Object> rule() {
        return new HashMap<>(
                Map.of(
                        "deliveryFee",
                        new java.math.BigDecimal("25.00"),
                        "minimumOrder",
                        new java.math.BigDecimal("100.00"),
                        "etaMinMinutes",
                        30,
                        "etaMaxMinutes",
                        45,
                        "enabled",
                        true));
    }

    JsonNode enable(Area a) throws Exception {
        return body(a.f().browser().send("POST", rulePath(a), rule()), 201);
    }

    JsonNode address(Account customer) throws Exception {
        return body(
                customer.browser()
                        .send(
                                "POST",
                                "/users/me/addresses",
                                Map.of(
                                        "label",
                                        "Home",
                                        "street",
                                        "Street",
                                        "building",
                                        "12",
                                        "city",
                                        "Legacy Cairo",
                                        "countryCode",
                                        "EG")),
                201);
    }

    JsonNode select(Account customer, JsonNode address, UUID zone) throws Exception {
        Map<String, Object> input = new HashMap<>();
        input.put("deliveryZoneId", zone);
        input.put("version", address.get("version").asLong());
        return body(
                customer.browser()
                        .send(
                                "PUT",
                                "/users/me/addresses/" + id(address) + "/delivery-zone",
                                input),
                200);
    }

    JsonNode eligibility(Area a, Account customer, UUID address) throws Exception {
        return body(
                customer.browser()
                        .send(
                                "GET",
                                "/branches/" + a.branch() + "/serviceability?addressId=" + address,
                                null),
                200);
    }

    void reason(Area a, Account customer, UUID address, String expected) throws Exception {
        var result = eligibility(a, customer, address);
        assertThat(result.get("reason").asText()).isEqualTo(expected);
        assertThat(result.get("serviceable").asBoolean()).isEqualTo(expected.equals("SERVICEABLE"));
        if (!expected.equals("SERVICEABLE"))
            assertThat(result.get("deliveryFee").isNull()).isTrue();
    }

    @Test
    void adminGeographyNormalizationAndVersioning() throws Exception {
        var admin = account("ADMIN");
        String unique = UUID.randomUUID().toString();
        var city = city(admin, "Nasr City " + unique);
        body(
                admin.browser()
                        .send(
                                "POST",
                                "/admin/cities",
                                Map.of("name", "nasr-city" + unique, "active", true)),
                409);
        var zone = zone(admin, id(city), "El Maadi");
        body(
                admin.browser()
                        .send(
                                "POST",
                                "/admin/delivery-zones",
                                Map.of(
                                        "cityId",
                                        id(city),
                                        "profile",
                                        Map.of("name", "elmaadi", "active", true))),
                409);
        UUID other = id(city(admin, "Other " + unique));
        zone(admin, other, "El Maadi");
        var edit = Map.of("profile", Map.of("name", "El Maadi", "active", false), "version", 0);
        body(admin.browser().send("PUT", "/admin/delivery-zones/" + id(zone), edit), 200);
        body(admin.browser().send("PUT", "/admin/delivery-zones/" + id(zone), edit), 409);
        body(
                admin.browser()
                        .send(
                                "PUT",
                                "/admin/cities/" + id(city),
                                Map.of(
                                        "profile",
                                        Map.of("name", "Updated " + unique, "active", false),
                                        "version",
                                        0)),
                200);
        body(admin.browser().send("GET", "/admin/delivery-zones?cityId=" + id(city), null), 200);
        body(
                admin.browser()
                        .send(
                                "POST",
                                "/admin/delivery-zones",
                                Map.of(
                                        "cityId",
                                        UUID.randomUUID(),
                                        "profile",
                                        Map.of("name", "Unknown", "active", true))),
                404);
    }

    @Test
    void roleAndSessionGuardsPreventManagement() throws Exception {
        var a = area();
        for (String role : List.of("CUSTOMER", "RESTAURANT_STAFF", "DRIVER", "RESTAURANT_OWNER")) {
            var actor = account(role);
            body(
                    actor.browser()
                            .send(
                                    "POST",
                                    "/admin/cities",
                                    Map.of("name", "Forbidden", "active", true)),
                    403);
            body(
                    actor.browser()
                            .send(
                                    "POST",
                                    "/admin/delivery-zones",
                                    Map.of(
                                            "cityId",
                                            a.city(),
                                            "profile",
                                            Map.of("name", "Forbidden", "active", true))),
                    403);
            if (!role.equals("RESTAURANT_OWNER")) {
                body(actor.browser().send("POST", rulePath(a), rule()), 403);
                body(
                        actor.browser()
                                .send("PUT", rulePath(a), Map.of("rule", rule(), "version", 0)),
                        403);
            }
        }
        var anonymous = new Browser();
        anonymous.csrf();
        body(anonymous.send("POST", rulePath(a), rule()), 401);
        String token = a.f().browser().token;
        a.f().browser().token = null;
        body(a.f().browser().send("POST", rulePath(a), rule()), 403);
        a.f().browser().token = token;
        var injection = rule();
        injection.put("role", "ADMIN");
        body(a.f().browser().send("POST", rulePath(a), injection), 400);
    }

    @Test
    void ownershipAndRestaurantPathAreBothEnforced() throws Exception {
        var a = area();
        var other = fixture();
        body(other.browser().send("POST", rulePath(a), rule()), 404);
        String manipulated = path(other) + "/" + a.branch() + "/delivery-zones/" + a.zone();
        body(other.browser().send("POST", manipulated, rule()), 404);
        body(a.f().admin().browser().send("POST", manipulated, rule()), 404);
        enable(a);
        body(other.browser().send("PUT", rulePath(a), Map.of("rule", rule(), "version", 0)), 404);
        body(
                other.browser()
                        .send("GET", path(a.f()) + "/" + a.branch() + "/delivery-zones", null),
                404);
        body(
                a.f().admin()
                        .browser()
                        .send("PUT", rulePath(a), Map.of("rule", rule(), "version", 0)),
                200);
    }

    @Test
    void manyToManyRulesAndBoundedListing() throws Exception {
        var a = area();
        var first = enable(a);
        assertThat(first.get("currency").asText()).isEqualTo("EGP");
        UUID secondZone = id(zone(a.f().admin(), a.city(), "Other zone"));
        body(
                a.f().browser()
                        .send(
                                "POST",
                                path(a.f()) + "/" + a.branch() + "/delivery-zones/" + secondZone,
                                rule()),
                201);
        UUID secondBranch = id(create(a.f()));
        var different = rule();
        different.put("deliveryFee", 40);
        body(
                a.f().browser()
                        .send(
                                "POST",
                                path(a.f()) + "/" + secondBranch + "/delivery-zones/" + a.zone(),
                                different),
                201);
        body(a.f().browser().send("POST", rulePath(a), rule()), 409);
        var list =
                body(
                        a.f().browser()
                                .send(
                                        "GET",
                                        path(a.f()) + "/" + a.branch() + "/delivery-zones?size=1",
                                        null),
                        200);
        assertThat(list.get("total").asInt()).isEqualTo(2);
        assertThat(list.get("items").size()).isEqualTo(1);
        body(
                a.f().browser()
                        .send(
                                "GET",
                                path(a.f()) + "/" + a.branch() + "/delivery-zones?size=101",
                                null),
                400);
        var batch = query.forZone(a.zone(), List.of(a.branch(), secondBranch));
        assertThat(batch).hasSize(2).allMatch(DeliveryDtos.Eligibility::serviceable);
        assertThat(batch)
                .extracting(DeliveryDtos.Eligibility::deliveryFee)
                .containsExactlyInAnyOrder(
                        new java.math.BigDecimal("25.00"), new java.math.BigDecimal("40.00"));
    }

    @Test
    void monetaryAndEtaValidationRejectsInvalidInput() throws Exception {
        var a = area();
        for (String field : List.of("deliveryFee", "minimumOrder")) {
            for (Object value :
                    List.of(
                            -1,
                            new java.math.BigDecimal("0.001"),
                            new java.math.BigDecimal("10000000000"))) {
                var invalid = rule();
                invalid.put(field, value);
                body(a.f().browser().send("POST", rulePath(a), invalid), 400);
            }
        }
        for (int value : List.of(0, -1, 46)) {
            var invalid = rule();
            invalid.put("etaMinMinutes", value);
            body(a.f().browser().send("POST", rulePath(a), invalid), 400);
        }
        var invalid = rule();
        invalid.remove("enabled");
        body(a.f().browser().send("POST", rulePath(a), invalid), 400);
        var valid = rule();
        valid.put("deliveryFee", 0);
        valid.put("minimumOrder", 0);
        body(a.f().browser().send("POST", rulePath(a), valid), 201);
        invalid = rule();
        invalid.put("etaMaxMinutes", 29);
        body(a.f().browser().send("PUT", rulePath(a), Map.of("rule", invalid, "version", 0)), 400);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT version FROM branch_delivery_zones WHERE branch_id=?",
                                Long.class,
                                a.branch()))
                .isZero();
    }

    @Test
    void addressZoneAndEligibilityDoNotUseLegacyCityOrCoordinates() throws Exception {
        var a = area();
        enable(a);
        var customer = account("CUSTOMER");
        var address = address(customer);
        reason(a, customer, id(address), "ADDRESS_ZONE_REQUIRED");
        var selected = select(customer, address, a.zone());
        assertThat(selected.get("deliveryZoneId").asText()).isEqualTo(a.zone().toString());
        var result = eligibility(a, customer, id(address));
        assertThat(result.get("deliveryFee").decimalValue()).isEqualByComparingTo("25");
        assertThat(result.get("minimumOrder").decimalValue()).isEqualByComparingTo("100");
        assertThat(result.get("etaMinMinutes").asInt()).isEqualTo(30);
        reason(a, customer, id(address), "SERVICEABLE"); // No opening hours configured.
        var edited =
                body(
                        customer.browser()
                                .send(
                                        "PUT",
                                        "/users/me/addresses/" + id(address),
                                        Map.of(
                                                "profile",
                                                selected.get("profile"),
                                                "version",
                                                selected.get("version").asLong())),
                        200);
        assertThat(edited.get("deliveryZoneId").asText()).isEqualTo(a.zone().toString());
        select(customer, edited, null);
        reason(a, customer, id(address), "ADDRESS_ZONE_REQUIRED");
    }

    @Test
    void customerCannotSubstituteAnothersAddressIncludingAdminCustomer() throws Exception {
        var a = area();
        enable(a);
        var customer = account("CUSTOMER");
        var address = address(customer);
        select(customer, address, a.zone());
        for (var other : List.of(account("CUSTOMER"), account("CUSTOMER", "ADMIN"))) {
            body(
                    other.browser()
                            .send(
                                    "GET",
                                    "/branches/"
                                            + a.branch()
                                            + "/serviceability?addressId="
                                            + id(address),
                                    null),
                    404);
            body(
                    other.browser()
                            .send(
                                    "PUT",
                                    "/users/me/addresses/" + id(address) + "/delivery-zone",
                                    Map.of("deliveryZoneId", a.zone(), "version", 1)),
                    404);
        }
        body(
                customer.browser()
                        .send(
                                "GET",
                                "/branches/"
                                        + UUID.randomUUID()
                                        + "/serviceability?addressId="
                                        + id(address),
                                null),
                404);
        body(
                customer.browser()
                        .send(
                                "PUT",
                                "/users/me/addresses/" + id(address) + "/delivery-zone",
                                Map.of("deliveryZoneId", a.zone(), "version", 0)),
                409);
        body(
                customer.browser()
                        .send(
                                "PUT",
                                "/users/me/addresses/" + id(address) + "/delivery-zone",
                                Map.of("deliveryZoneId", UUID.randomUUID(), "version", 1)),
                400);
        reason(a, customer, id(address), "SERVICEABLE");
    }

    @Test
    void unavailableReasonsAndRetention() throws Exception {
        var a = area();
        var customer = account("CUSTOMER");
        var address = select(customer, address(customer), a.zone());
        reason(a, customer, id(address), "ZONE_NOT_SERVED");
        enable(a);
        var disabled = rule();
        disabled.put("enabled", false);
        body(a.f().browser().send("PUT", rulePath(a), Map.of("rule", disabled, "version", 0)), 200);
        reason(a, customer, id(address), "RELATIONSHIP_DISABLED");
        body(a.f().browser().send("PUT", rulePath(a), Map.of("rule", rule(), "version", 1)), 200);
        jdbc.update("UPDATE delivery_zones SET active=false WHERE id=?", a.zone());
        reason(a, customer, id(address), "ZONE_INACTIVE");
        body(
                customer.browser()
                        .send(
                                "PUT",
                                "/users/me/addresses/" + id(address) + "/delivery-zone",
                                Map.of("deliveryZoneId", a.zone(), "version", 1)),
                400);
        jdbc.update("UPDATE delivery_zones SET active=true WHERE id=?", a.zone());
        jdbc.update("UPDATE cities SET active=false WHERE id=?", a.city());
        reason(a, customer, id(address), "CITY_INACTIVE");
        jdbc.update("UPDATE cities SET active=true WHERE id=?", a.city());
        jdbc.update("UPDATE branches SET status='INACTIVE' WHERE id=?", a.branch());
        reason(a, customer, id(address), "BRANCH_INACTIVE");
        jdbc.update("UPDATE branches SET status='ACTIVE',paused=true WHERE id=?", a.branch());
        reason(a, customer, id(address), "BRANCH_PAUSED");
        jdbc.update("UPDATE branches SET paused=false WHERE id=?", a.branch());
        jdbc.update("UPDATE restaurants SET status='SUSPENDED' WHERE id=?", a.f().restaurant());
        reason(a, customer, id(address), "RESTAURANT_SUSPENDED");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM branch_delivery_zones WHERE branch_id=?",
                                Integer.class,
                                a.branch()))
                .isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT delivery_zone_id FROM customer_addresses WHERE id=?",
                                UUID.class,
                                id(address)))
                .isEqualTo(a.zone());
    }

    @Test
    void concurrentEditsHaveOneWinnerAndOneConflict() throws Exception {
        var a = area();
        enable(a);
        var b1 = login(a.f().owner().email());
        var b2 = login(a.f().owner().email());
        var start = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = new ArrayList<Future<Integer>>();
            for (var browser : List.of(b1, b2))
                futures.add(
                        executor.submit(
                                () -> {
                                    start.await();
                                    return browser.send(
                                                    "PUT",
                                                    rulePath(a),
                                                    Map.of("rule", rule(), "version", 0))
                                            .statusCode();
                                }));
            start.countDown();
            assertThat(
                            List.of(
                                    futures.get(0).get(20, TimeUnit.SECONDS),
                                    futures.get(1).get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 409);
        }
        assertThat(
                        jdbc.queryForObject(
                                "SELECT version FROM branch_delivery_zones WHERE branch_id=?",
                                Long.class,
                                a.branch()))
                .isEqualTo(1);
    }

    @Test
    void concurrentCreatesPreventDuplicateAssociation() throws Exception {
        var a = area();
        var b1 = login(a.f().owner().email());
        var b2 = login(a.f().owner().email());
        var start = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = new ArrayList<Future<Integer>>();
            for (var browser : List.of(b1, b2))
                futures.add(
                        executor.submit(
                                () -> {
                                    start.await();
                                    return browser.send("POST", rulePath(a), rule()).statusCode();
                                }));
            start.countDown();
            assertThat(
                            List.of(
                                    futures.get(0).get(20, TimeUnit.SECONDS),
                                    futures.get(1).get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(201, 409);
        }
    }

    @Test
    void failedTransactionRestoresRuleAndVersion() throws Exception {
        var a = area();
        enable(a);
        var changed = rule();
        changed.put("deliveryFee", 99);
        var flushed = new java.util.concurrent.atomic.AtomicBoolean();
        doAnswer(
                        invocation -> {
                            entityManager.flush();
                            assertThat(
                                            jdbc.queryForObject(
                                                    "SELECT delivery_fee FROM branch_delivery_zones"
                                                        + " WHERE branch_id=?",
                                                    java.math.BigDecimal.class,
                                                    a.branch()))
                                    .isEqualByComparingTo("99");
                            flushed.set(true);
                            throw new IllegalStateException("Injected after flush");
                        })
                .when(rules)
                .flush();
        try {
            body(
                    a.f().browser().send("PUT", rulePath(a), Map.of("rule", changed, "version", 0)),
                    500);
        } finally {
            reset(rules);
        }
        assertThat(flushed).isTrue();
        var stored =
                jdbc.queryForMap(
                        "SELECT delivery_fee,version FROM branch_delivery_zones WHERE branch_id=?",
                        a.branch());
        assertThat(stored.get("delivery_fee")).isEqualTo(new java.math.BigDecimal("25.00"));
        assertThat(stored.get("version")).isEqualTo(0L);
    }

    @Test
    void databaseChecksForeignKeysUniqueAndImmutableIdentity() throws Exception {
        var a = area();
        enable(a);
        for (String assignment :
                List.of(
                        "delivery_fee=-1",
                        "minimum_order=-1",
                        "eta_min_minutes=0",
                        "eta_max_minutes=1",
                        "version=-1",
                        "delivery_fee='NaN'"))
            sqlState(
                    "23514",
                    () ->
                            jdbc.update(
                                    "UPDATE branch_delivery_zones SET "
                                            + assignment
                                            + " WHERE branch_id=?",
                                    a.branch()));
        sqlState(
                "22003",
                () ->
                        jdbc.update(
                                "UPDATE branch_delivery_zones SET delivery_fee=10000000000 WHERE"
                                    + " branch_id=?",
                                a.branch()));
        sqlState(
                "23505",
                () ->
                        jdbc.update(
                                "INSERT INTO branch_delivery_zones SELECT"
                                    + " ?,branch_id,delivery_zone_id,delivery_fee,minimum_order,eta_min_minutes,eta_max_minutes,enabled,version,created_at,updated_at"
                                    + " FROM branch_delivery_zones WHERE branch_id=?",
                                UUID.randomUUID(),
                                a.branch()));
        sqlState(
                "23514",
                () ->
                        jdbc.update(
                                "UPDATE delivery_zones SET city_id=? WHERE id=?",
                                UUID.randomUUID(),
                                a.zone()));
        sqlState(
                "23514",
                () ->
                        jdbc.update(
                                "UPDATE branch_delivery_zones SET branch_id=? WHERE branch_id=?",
                                UUID.randomUUID(),
                                a.branch()));
        sqlState("23503", () -> jdbc.update("DELETE FROM delivery_zones WHERE id=?", a.zone()));
        sqlState(
                "23503",
                () ->
                        jdbc.update(
                                "INSERT INTO"
                                    + " delivery_zones(id,city_id,name,active,created_at,updated_at)"
                                    + " VALUES (?,?,'Orphan',true,now(),now())",
                                UUID.randomUUID(),
                                UUID.randomUUID()));
        sqlState("23514", () -> jdbc.update("UPDATE cities SET name='---' WHERE id=?", a.city()));
        var customer = account("CUSTOMER");
        var address = address(customer);
        sqlState(
                "23503",
                () ->
                        jdbc.update(
                                "UPDATE customer_addresses SET delivery_zone_id=? WHERE id=?",
                                UUID.randomUUID(),
                                id(address)));
    }

    @Test
    void geographyReadAndMutationInjectionGuards() throws Exception {
        var a = area();
        var customer = account("CUSTOMER");
        body(customer.browser().send("GET", "/geography/cities/" + a.city(), null), 200);
        body(customer.browser().send("GET", "/geography/delivery-zones/" + a.zone(), null), 200);
        body(
                a.f().browser().send("GET", "/geography/delivery-zones?cityId=" + a.city(), null),
                200);
        body(customer.browser().send("GET", "/admin/cities", null), 403);
        var unknown = rule();
        unknown.put("branchId", UUID.randomUUID());
        body(a.f().browser().send("POST", rulePath(a), unknown), 400);
        var zoneEdit =
                new HashMap<String, Object>(
                        Map.of(
                                "profile",
                                Map.of("name", "Moved", "active", true),
                                "version",
                                0,
                                "cityId",
                                UUID.randomUUID()));
        body(
                a.f().admin().browser().send("PUT", "/admin/delivery-zones/" + a.zone(), zoneEdit),
                400);
        enable(a);
        body(a.f().browser().send("GET", rulePath(a), null), 200);
        var address = select(customer, address(customer), a.zone());
        body(
                customer.browser()
                        .send(
                                "GET",
                                "/branches/"
                                        + a.branch()
                                        + "/serviceability?addressId="
                                        + id(address)
                                        + "&deliveryFee=0",
                                null),
                200);
        assertThat(eligibility(a, customer, id(address)).get("deliveryFee").decimalValue())
                .isEqualByComparingTo("25");
        jdbc.update("UPDATE users SET status='SUSPENDED' WHERE id=?", customer.id());
        body(
                customer.browser()
                        .send(
                                "GET",
                                "/branches/"
                                        + a.branch()
                                        + "/serviceability?addressId="
                                        + id(address),
                                null),
                401);
    }
}
