package com.tayyar.discovery;

import static com.tayyar.discovery.DiscoveryDtos.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.tayyar.auth.SessionPrincipal;
import com.tayyar.branch.*;
import com.tayyar.delivery.ServiceabilityQuery;
import com.tayyar.support.PostgresIntegrationTest;
import com.tayyar.user.Role;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import tools.jackson.databind.*;

import java.lang.reflect.Proxy;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"tayyar.identity.login-limit=1000", "tayyar.identity.csrf-limit=1000"})
class DiscoveryIT extends PostgresIntegrationTest {
    @Value("${local.server.port}")
    int port;

    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired PlatformTransactionManager transactions;
    @Autowired DiscoveryService service;
    @Autowired PasswordEncoder passwords;
    @Autowired ServiceabilityQuery eligibility;
    @Autowired BranchScheduleQuery schedules;
    final HttpClient http = HttpClient.newHttpClient();
    UUID user, restaurant, branch, city, zone, menu, category, item;
    String name;

    @BeforeEach
    void fixture() {
        user = UUID.randomUUID();
        restaurant = UUID.randomUUID();
        branch = UUID.randomUUID();
        city = UUID.randomUUID();
        zone = UUID.randomUUID();
        menu = UUID.randomUUID();
        category = UUID.randomUUID();
        item = UUID.randomUUID();
        name = "Kitchen " + restaurant;
        new TransactionTemplate(transactions)
                .executeWithoutResult(
                        tx -> {
                            jdbc.update(
                                    "INSERT INTO"
                                        + " users(id,full_name,email,password_hash,status,created_at,updated_at)"
                                        + " VALUES (?,'Private"
                                        + " Owner',?,'unused','ACTIVE',now(),now())",
                                    user,
                                    user + "@example.com");
                            jdbc.update("INSERT INTO user_roles VALUES (?,'CUSTOMER')", user);
                            UUID app = UUID.randomUUID();
                            jdbc.update(
                                    "INSERT INTO"
                                        + " restaurant_applications(id,applicant_id,status,current_revision,created_at,updated_at)"
                                        + " VALUES (?,?,'APPROVED',1,now(),now())",
                                    app,
                                    user);
                            jdbc.update(
                                    "INSERT INTO application_submissions VALUES (?,1,?,'Private"
                                            + " submission',now())",
                                    app,
                                    name);
                            jdbc.update(
                                    "INSERT INTO"
                                        + " restaurants(id,application_id,name,description,status,created_at,updated_at)"
                                        + " VALUES (?,?,?,'Public"
                                        + " description','ACTIVE',now(),now())",
                                    restaurant,
                                    app,
                                    name);
                            jdbc.update(
                                    "INSERT INTO restaurant_memberships VALUES (?,?,'OWNER',now())",
                                    restaurant,
                                    user);
                            addBranch(branch, "Central");
                            jdbc.update(
                                    "INSERT INTO cities(id,name,active,created_at,updated_at)"
                                            + " VALUES (?, ?,true,now(),now())",
                                    city,
                                    "City " + city);
                            jdbc.update(
                                    "INSERT INTO"
                                        + " delivery_zones(id,city_id,name,active,created_at,updated_at)"
                                        + " VALUES (?,?,'Selected area',true,now(),now())",
                                    zone,
                                    city);
                            rule(branch, 25, 100, 30);
                            jdbc.update(
                                    "INSERT INTO"
                                        + " restaurant_menus(id,restaurant_id,name,created_at,updated_at)"
                                        + " VALUES (?,?,'Main',now(),now())",
                                    menu,
                                    restaurant);
                            jdbc.update(
                                    "INSERT INTO"
                                        + " menu_categories(id,menu_id,restaurant_id,name,position,created_at,updated_at)"
                                        + " VALUES (?,?,?,'Grilled Specialties',0,now(),now())",
                                    category,
                                    menu,
                                    restaurant);
                            addItem(item, category, "Lemon Chicken", 0);
                        });
    }

    void addBranch(UUID id, String branchName) {
        jdbc.update(
                """
INSERT INTO branches(id,restaurant_id,name,address_line1,city,country_code,timezone,delivery_model,status,created_at,updated_at)
VALUES (?,?,?,'Public street','Cairo','EG','Africa/Cairo','RESTAURANT_DELIVERY','ACTIVE',now(),now())
""",
                id,
                restaurant,
                branchName);
    }

    void rule(UUID b, int fee, int minimum, int eta) {
        jdbc.update(
                "INSERT INTO"
                    + " branch_delivery_zones(id,branch_id,delivery_zone_id,delivery_fee,minimum_order,eta_min_minutes,eta_max_minutes,enabled,created_at,updated_at)"
                    + " VALUES (?,?,?,?,?,?,?,true,now(),now())",
                UUID.randomUUID(),
                b,
                zone,
                fee,
                minimum,
                eta,
                eta + 15);
    }

    void addItem(UUID id, UUID cat, String itemName, int position) {
        jdbc.update(
                "INSERT INTO"
                    + " menu_items(id,category_id,restaurant_id,name,base_price,position,created_at,updated_at)"
                    + " VALUES (?,?,?, ?,80,?,now(),now())",
                id,
                cat,
                restaurant,
                itemName,
                position);
    }

    HttpResponse<String> get(String path) throws Exception {
        return get(path, null);
    }

    HttpResponse<String> get(String path, String cookie) throws Exception {
        var r = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1" + path));
        if (cookie != null) r.header("Cookie", cookie);
        return http.send(r.GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    JsonNode body(HttpResponse<String> r, int status) throws Exception {
        assertThat(r.statusCode()).as(r.body()).isEqualTo(status);
        return json.readTree(r.body());
    }

    String list() {
        return "/discovery/restaurants";
    }

    String branches() {
        return list() + "/" + restaurant + "/branches";
    }

    String menuPath() {
        return branches() + "/" + branch + "/menu";
    }

    String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    JsonNode find(String q) throws Exception {
        return body(get(list() + "?zoneId=" + zone + "&query=" + enc(q)), 200);
    }

    Filter filter(UUID z) {
        return new Filter("", z, null, null, null, null, null, false, Sort.NAME);
    }

    SessionPrincipal actor(UUID id, Role... roles) {
        return new SessionPrincipal(id, Set.of(roles), 0, Instant.now());
    }

    UUID address() {
        UUID a = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO"
                    + " customer_addresses(id,user_id,label,street,building,city,country_code,delivery_zone_id,created_at,updated_at)"
                    + " VALUES (?,?,'Private Home','Private"
                    + " Street','1','Cairo','EG',?,now(),now())",
                a,
                user,
                zone);
        return a;
    }

    String login(UUID id) throws Exception {
        String password = "Discovery test password!";
        jdbc.update("UPDATE users SET password_hash=? WHERE id=?", passwords.encode(password), id);
        var csrf = get("/auth/csrf");
        String cookie = csrf.headers().firstValue("Set-Cookie").orElseThrow().split(";")[0];
        String token = body(csrf, 200).get("token").asText();
        var req =
                HttpRequest.newBuilder(
                                URI.create("http://localhost:" + port + "/api/v1/auth/session"))
                        .header("Cookie", cookie)
                        .header("X-CSRF-TOKEN", token)
                        .header("Content-Type", "application/json")
                        .POST(
                                HttpRequest.BodyPublishers.ofString(
                                        json.writeValueAsString(
                                                Map.of(
                                                        "email",
                                                        id + "@example.com",
                                                        "password",
                                                        password))))
                        .build();
        var response = http.send(req, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as(response.body()).isEqualTo(204);
        return response.headers().firstValue("Set-Cookie").map(v -> v.split(";")[0]).orElse(cookie);
    }

    @Test
    void anonymousBrowsingAndPrivacy() throws Exception {
        var r = body(get(list() + "?query=" + enc(name)), 200);
        assertThat(r.get("total").asLong()).isEqualTo(1);
        assertThat(r.get("items").get(0).get("serviceable").isNull()).isTrue();
        var details = get(list() + "/" + restaurant);
        body(details, 200);
        assertThat(details.body())
                .doesNotContain(
                        "applicationId",
                        "owner",
                        "membership",
                        "version",
                        "createdAt",
                        user.toString());
        assertThat(body(get(branches()), 200).get("total").asLong()).isEqualTo(1);
        body(get(menuPath()), 200);
        assertThat(get("/restaurants/" + restaurant).statusCode()).isEqualTo(401);
        assertThat(get("/restaurants/" + restaurant + "/memberships").statusCode()).isEqualTo(401);
        assertThat(details.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
    }

    @Test
    void suspendedAndInactiveResourcesAreHidden() throws Exception {
        jdbc.update("UPDATE branches SET status='INACTIVE' WHERE id=?", branch);
        assertThat(find("").get("total").asLong()).isZero();
        body(get(menuPath()), 404);
        body(get(list() + "/" + restaurant), 404);
        jdbc.update("UPDATE branches SET status='ACTIVE' WHERE id=?", branch);
        jdbc.update("UPDATE restaurants SET status='SUSPENDED' WHERE id=?", restaurant);
        assertThat(find("").get("total").asLong()).isZero();
        body(get(branches()), 404);
        body(get(menuPath()), 404);
    }

    @Test
    void searchesNamesCategoriesAndItemsWithCaseAndWhitespace() throws Exception {
        for (String q : List.of(name, "grilled specialties", "  LEMON chicken  ", "", "   "))
            assertThat(find(q).get("total").asLong()).as(q).isEqualTo(1);
        assertThat(find("not present").get("total").asLong()).isZero();
        jdbc.update("UPDATE menu_categories SET active=false WHERE id=?", category);
        assertThat(find("grilled").get("total").asLong()).isZero();
        assertThat(find("chicken").get("total").asLong()).isZero();
        assertThat(find(name).get("total").asLong()).isEqualTo(1);
    }

    @Test
    void injectionWildcardsAndMalformedParameters() throws Exception {
        for (String q : List.of("%' OR 1=1 --", "%", "_", "!"))
            assertThat(find(q).get("total").asLong()).isZero();
        for (String params :
                List.of(
                        "query=" + "a".repeat(201),
                        "sort=name;DROP%20TABLE%20restaurants",
                        "sort=rating",
                        "page=-1",
                        "page=10001",
                        "page=no",
                        "size=0",
                        "size=101",
                        "size=999999999999",
                        "zoneId=bad",
                        "addressId=bad",
                        "zoneId=1-1-1-1-1",
                        "query=a&query=b",
                        "deliveryFee=0",
                        "serviceability=true",
                        "openNow=maybe",
                        "maxDeliveryFee=1",
                        "maxDeliveryFee=NaN",
                        "maxMinimumOrder=1e100000",
                        "zoneId=" + zone + "&maxDeliveryFee=-1",
                        "zoneId=" + zone + "&maxDeliveryFee=1.001",
                        "zoneId=" + zone + "&addressId=" + UUID.randomUUID()))
            body(get(list() + "?" + params), 400);
        body(get(list() + "/not-a-uuid"), 400);
        body(get(list() + "/" + UUID.randomUUID()), 404);
        assertThat(find("").get("total").asLong()).isEqualTo(1);
    }

    @Test
    void stableBranchPaginationAndMaximumPageSize() throws Exception {
        UUID second = UUID.randomUUID();
        addBranch(second, "Central");
        var first = body(get(branches() + "?size=1"), 200);
        var next = body(get(branches() + "?size=1&page=1"), 200);
        assertThat(first.get("total").asLong()).isEqualTo(2);
        assertThat(first.get("items").get(0).get("id"))
                .isNotEqualTo(next.get("items").get(0).get("id"));
        assertThat(body(get(branches() + "?size=1"), 200)).isEqualTo(first);
        var emptyBranchPage = body(get(branches() + "?size=100&page=10000"), 200);
        assertThat(emptyBranchPage.get("items").size()).isZero();
        assertThat(emptyBranchPage.get("total").asLong()).isEqualTo(2);
        body(get(branches() + "?size=101"), 400);
        var listing = body(get(list() + "?size=1"), 200);
        assertThat(listing.get("items").size()).isEqualTo(1);
        assertThat(body(get(list() + "?size=1"), 200)).isEqualTo(listing);
        var emptyListingPage = body(get(list() + "?size=100&page=10000"), 200);
        assertThat(emptyListingPage.get("items").size()).isZero();
        assertThat(emptyListingPage.get("total").asLong()).isEqualTo(1);
    }

    @Test
    void effectiveMenuOverridesFallbackAndInactiveGates() throws Exception {
        UUID fallback = UUID.randomUUID();
        addItem(fallback, category, "Bread", 1);
        jdbc.update(
                "INSERT INTO branch_menu_item_overrides VALUES (?,?,?,false,95,now(),now())",
                branch,
                item,
                restaurant);
        var rows = body(get(menuPath()), 200).get("categories").get("items").get(0).get("items");
        assertThat(rows.get("total").asLong()).isEqualTo(2);
        assertThat(rows.get("items").get(0).get("effectivePrice").decimalValue())
                .isEqualByComparingTo("95");
        assertThat(rows.get("items").get(0).get("effectiveAvailability").asBoolean()).isFalse();
        assertThat(rows.get("items").get(1).get("effectivePrice").decimalValue())
                .isEqualByComparingTo("80");
        assertThat(rows.get("items").get(1).get("effectiveAvailability").asBoolean()).isTrue();
        assertThat(
                        body(get(menuPath() + "?availableOnly=true"), 200)
                                .get("categories")
                                .get("items")
                                .get(0)
                                .get("items")
                                .get("total")
                                .asLong())
                .isEqualTo(1);
        jdbc.update("UPDATE menu_items SET active=false WHERE id=?", item);
        assertThat(
                        body(get(menuPath()), 200)
                                .get("categories")
                                .get("items")
                                .get(0)
                                .get("items")
                                .get("total")
                                .asLong())
                .isEqualTo(1);
        jdbc.update("UPDATE menu_categories SET active=false WHERE id=?", category);
        assertThat(body(get(menuPath()), 200).get("categories").get("total").asLong()).isZero();
        jdbc.update("UPDATE restaurant_menus SET active=false WHERE id=?", menu);
        body(get(menuPath()), 404);
    }

    @Test
    void menuPaginationIncludesEmptyCategoriesAndOutOfRangeItemTotals() throws Exception {
        UUID cat = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO"
                    + " menu_categories(id,menu_id,restaurant_id,name,position,created_at,updated_at)"
                    + " VALUES (?,?,?,'Empty',1,now(),now())",
                cat,
                menu,
                restaurant);
        for (int n = 1; n < 6; n++) addItem(UUID.randomUUID(), category, "Item " + n, n);
        var r = body(get(menuPath() + "?size=1&itemSize=2&itemPage=2"), 200);
        assertThat(r.get("categories").get("total").asLong()).isEqualTo(2);
        var items = r.get("categories").get("items").get(0).get("items");
        assertThat(items.get("total").asLong()).isEqualTo(6);
        assertThat(items.get("items").size()).isEqualTo(2);
        r = body(get(menuPath() + "?itemPage=100"), 200);
        assertThat(r.get("categories").get("items").get(0).get("items").get("total").asLong())
                .isEqualTo(6);
        assertThat(r.get("categories").get("items").get(0).get("items").get("items").size())
                .isZero();
        assertThat(r.get("categories").get("items").get(1).get("items").get("total").asLong())
                .isZero();
        body(get(menuPath() + "?itemSize=101"), 400);
        body(get(menuPath() + "?page=10001"), 400);
        body(get(list() + "/" + UUID.randomUUID() + "/branches/" + branch + "/menu"), 404);
    }

    @Test
    void serviceableClosedBranchesAndAuthoritativeFees() throws Exception {
        var r = body(get(branches() + "?zoneId=" + zone), 200).get("items").get(0);
        assertThat(r.get("serviceable").asBoolean()).isTrue();
        assertThat(r.get("openNow").asBoolean()).isFalse();
        assertThat(r.get("deliveryFee").decimalValue()).isEqualByComparingTo("25");
        assertThat(r.get("minimumOrder").decimalValue()).isEqualByComparingTo("100");
        assertThat(r.get("etaMinMinutes").asInt()).isEqualTo(30);
        assertThat(r.get("etaMaxMinutes").asInt()).isEqualTo(45);
        assertThat(
                        body(get(branches() + "?zoneId=" + zone + "&openNow=true"), 200)
                                .get("total")
                                .asLong())
                .isZero();
        assertThat(
                        body(get(branches() + "?zoneId=" + zone + "&openNow=false"), 200)
                                .get("total")
                                .asLong())
                .isEqualTo(1);
        jdbc.update("UPDATE branch_delivery_zones SET delivery_fee=39 WHERE branch_id=?", branch);
        assertThat(
                        body(get(branches() + "?zoneId=" + zone), 200)
                                .get("items")
                                .get(0)
                                .get("deliveryFee")
                                .decimalValue())
                .isEqualByComparingTo("39");
    }

    @Test
    void eligibilityParityAndDisabledGeography() throws Exception {
        UUID a = address();
        for (String mutation :
                List.of(
                        "UPDATE branch_delivery_zones SET enabled=false WHERE branch_id=?",
                        "UPDATE branches SET paused=true WHERE id=?",
                        "UPDATE branches SET status='INACTIVE' WHERE id=?")) {
            jdbc.update(mutation, branch);
            assertThat(eligibility.forOwnedAddress(user, branch, a).serviceable()).isFalse();
            assertThat(find("").get("total").asLong()).isZero();
            jdbc.update("UPDATE branches SET paused=false,status='ACTIVE' WHERE id=?", branch);
            jdbc.update("UPDATE branch_delivery_zones SET enabled=true WHERE branch_id=?", branch);
        }
        jdbc.update("UPDATE delivery_zones SET active=false WHERE id=?", zone);
        assertThat(find("").get("total").asLong()).isZero();
        jdbc.update("UPDATE delivery_zones SET active=true WHERE id=?", zone);
        jdbc.update("UPDATE cities SET active=false WHERE id=?", city);
        assertThat(find("").get("total").asLong()).isZero();
        assertThat(body(get(list() + "?zoneId=" + UUID.randomUUID()), 200).get("total").asLong())
                .isZero();
    }

    @Test
    void branchChoicesSortsAndSameBranchFilters() throws Exception {
        UUID second = UUID.randomUUID();
        addBranch(second, "Second");
        rule(second, 5, 200, 10);
        String p = branches() + "?zoneId=" + zone;
        assertThat(body(get(p + "&sort=DELIVERY_FEE"), 200).get("items").get(0).get("id").asText())
                .isEqualTo(second.toString());
        assertThat(body(get(p + "&sort=MINIMUM_ORDER"), 200).get("items").get(0).get("id").asText())
                .isEqualTo(branch.toString());
        assertThat(body(get(p + "&sort=ETA"), 200).get("items").get(0).get("id").asText())
                .isEqualTo(second.toString());
        assertThat(
                        body(
                                        get(
                                                list()
                                                        + "?zoneId="
                                                        + zone
                                                        + "&maxDeliveryFee=10&maxMinimumOrder=150"),
                                        200)
                                .get("total")
                                .asLong())
                .isZero();
        assertThat(
                        body(get(list() + "?zoneId=" + zone + "&sort=DELIVERY_FEE"), 200)
                                .get("items")
                                .get(0)
                                .get("branchCount")
                                .asLong())
                .isEqualTo(2);
    }

    @Test
    void categoryAndAvailabilitySearchUseSelectedBranches() throws Exception {
        jdbc.update(
                "INSERT INTO branch_menu_item_overrides VALUES (?,?,?,false,NULL,now(),now())",
                branch,
                item,
                restaurant);
        String p = list() + "?zoneId=" + zone + "&categoryId=" + category;
        assertThat(body(get(p), 200).get("total").asLong()).isEqualTo(1);
        assertThat(body(get(p + "&availableOnly=true&query=chicken"), 200).get("total").asLong())
                .isZero();
        jdbc.update(
                "UPDATE branch_menu_item_overrides SET available=true WHERE branch_id=?", branch);
        assertThat(body(get(p + "&availableOnly=true&query=chicken"), 200).get("total").asLong())
                .isEqualTo(1);
        jdbc.update("UPDATE restaurant_menus SET active=false WHERE id=?", menu);
        assertThat(find("chicken").get("total").asLong()).isZero();
    }

    @Test
    void savedAddressesRequireCustomerOwnershipAndNeverExposePrivateData() throws Exception {
        UUID a = address();
        String p = list() + "?addressId=" + a;
        body(get(p), 401);
        String cookie = login(user);
        var response = get(p, cookie);
        assertThat(body(response, 200).get("total").asLong()).isEqualTo(1);
        assertThat(response.body()).doesNotContain("Private", a.toString(), user.toString());
        UUID other = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO users(id,full_name,email,password_hash,status,created_at,updated_at)"
                        + " VALUES (?,'Other',?,'unused','ACTIVE',now(),now())",
                other,
                other + "@example.com");
        jdbc.update("INSERT INTO user_roles VALUES (?,'CUSTOMER')", other);
        body(get(p, login(other)), 404);
        body(get(list() + "?addressId=" + UUID.randomUUID(), cookie), 404);
        jdbc.update("UPDATE customer_addresses SET delivery_zone_id=NULL WHERE id=?", a);
        body(get(p, cookie), 400);
        jdbc.update("UPDATE users SET status='SUSPENDED' WHERE id=?", user);
        assertThat(get(list(), cookie).statusCode()).isEqualTo(401);
    }

    @Test
    void adminHasNoSavedAddressBypass() throws Exception {
        UUID a = address();
        jdbc.update("DELETE FROM user_roles WHERE user_id=?", user);
        jdbc.update("INSERT INTO user_roles VALUES (?,'ADMIN')", user);
        body(get(list() + "?addressId=" + a, login(user)), 403);
        var f = new Filter("", null, a, null, null, null, null, false, Sort.NAME);
        assertThatThrownBy(
                        () ->
                                service.restaurants(
                                        f,
                                        new Window(0, 20),
                                        actor(UUID.randomUUID(), Role.ADMIN, Role.CUSTOMER)))
                .isInstanceOf(DiscoveryException.class);
    }

    @Test
    void safePublicZoneCatalogOnlyShowsActiveDefinitions() throws Exception {
        String p = "/discovery/delivery-zones?cityId=" + city;
        var r = body(get(p), 200);
        assertThat(r.get("total").asLong()).isEqualTo(1);
        assertThat(r.toString()).doesNotContain("version", "createdAt", "updatedAt");
        jdbc.update("UPDATE cities SET active=false WHERE id=?", city);
        assertThat(body(get(p), 200).get("total").asLong()).isZero();
        body(get(p + "&size=101"), 400);
    }

    @Test
    void sharedOpeningSqlMatchesEstablishedScheduleRules() {
        var weekly = List.of(new BranchDtos.Weekly(1, LocalTime.of(9, 0), LocalTime.of(17, 0)));
        jdbc.update("INSERT INTO branch_opening_hours VALUES (?,1,'09:00','17:00')", branch);
        var date = LocalDate.of(2026, 9, 14);
        for (LocalTime time :
                List.of(
                        LocalTime.of(8, 59),
                        LocalTime.of(9, 0),
                        LocalTime.NOON,
                        LocalTime.of(17, 0))) {
            Instant now = date.atTime(time).atZone(ZoneId.of("Africa/Cairo")).toInstant();
            assertThat(schedules.open(branch, now))
                    .isEqualTo(BranchRules.open(now, "Africa/Cairo", weekly, List.of()));
        }
        jdbc.update("INSERT INTO branch_special_hours VALUES (?,? ,NULL,NULL)", branch, date);
        Instant now = date.atTime(12, 0).atZone(ZoneId.of("Africa/Cairo")).toInstant();
        assertThat(schedules.open(branch, now)).isFalse();
        jdbc.update(
                "UPDATE branch_special_hours SET opens_at='11:00',closes_at='13:00' WHERE"
                        + " branch_id=?",
                branch);
        assertThat(schedules.open(branch, now)).isTrue();
    }

    @Test
    void boundedQueryCountsAndExplainPlans() throws Exception {
        JdbcTemplate counted = spy(new JdbcTemplate(jdbc.getDataSource()));
        var query = new DiscoveryQuery(counted);
        for (int n = 1; n <= 25; n++) addItem(UUID.randomUUID(), category, "Meal " + n, n);
        for (int n = 1; n <= 8; n++) {
            UUID b = UUID.randomUUID();
            addBranch(b, "Branch " + n);
            rule(b, n, 50, 20);
        }
        StringBuilder plans =
                new StringBuilder(
                        "PostgreSQL "
                                + jdbc.queryForObject("SHOW server_version", String.class)
                                + "; fixture-scale plans, not a load benchmark\n");
        query.restaurants(filter(null), null, new Window(0, 20), Instant.now(), null);
        assertQueries(counted, 1, plans, "listing");
        query.restaurants(
                new Filter("chicken", null, null, null, null, null, null, false, Sort.NAME),
                null,
                new Window(0, 20),
                Instant.now(),
                null);
        assertQueries(counted, 1, plans, "search");
        query.restaurants(filter(zone), zone, new Window(0, 20), Instant.now(), null);
        assertQueries(counted, 1, plans, "zone listing");
        query.menu(restaurant, branch, new Window(0, 20), new Window(0, 20), false);
        assertQueries(counted, 3, plans, "menu");
        Files.writeString(Path.of("target/discovery-explain.txt"), plans);
    }

    void assertQueries(JdbcTemplate counted, int expected, StringBuilder plans, String label)
            throws Exception {
        var calls =
                mockingDetails(counted).getInvocations().stream()
                        .filter(
                                i ->
                                        i.getMethod().getName().equals("query")
                                                && i.getArguments().length == 2
                                                && i.getArguments()[0]
                                                        instanceof PreparedStatementCreator
                                                && i.getArguments()[1]
                                                        instanceof ResultSetExtractor)
                        .toList();
        assertThat(calls).as(label).hasSize(expected);
        for (var call : calls) {
            PreparedStatementCreator creator = (PreparedStatementCreator) call.getArguments()[0];
            try (Connection connection = jdbc.getDataSource().getConnection()) {
                Connection explain =
                        (Connection)
                                Proxy.newProxyInstance(
                                        getClass().getClassLoader(),
                                        new Class[] {Connection.class},
                                        (proxy, method, args) -> {
                                            if (method.getName().equals("prepareStatement"))
                                                args[0] = "EXPLAIN (ANALYZE,BUFFERS) " + args[0];
                                            return method.invoke(connection, args);
                                        });
                try (PreparedStatement statement = creator.createPreparedStatement(explain);
                        ResultSet rows = statement.executeQuery()) {
                    plans.append("\n").append(label).append("\n");
                    while (rows.next()) plans.append(rows.getString(1)).append("\n");
                }
            }
        }
        clearInvocations(counted);
    }
}
