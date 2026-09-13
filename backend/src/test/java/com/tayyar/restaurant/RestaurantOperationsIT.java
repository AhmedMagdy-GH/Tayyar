package com.tayyar.restaurant;

import static org.assertj.core.api.Assertions.assertThat;

import com.tayyar.support.PostgresIntegrationTest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"tayyar.identity.login-limit=1000", "tayyar.identity.csrf-limit=1000"})
class RestaurantOperationsIT extends PostgresIntegrationTest {
    private static final String PASSWORD = "Restaurant operations password!";

    @Value("${local.server.port}")
    int port;

    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    @Autowired PlatformTransactionManager transactions;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void contextRequiresAnOperationalRoleAndOwnersSeeOnlyOwnedRestaurants() throws Exception {
        var owner = account("RESTAURANT_OWNER");
        var foreignOwner = account("RESTAURANT_OWNER");
        var customer = account("CUSTOMER");
        var admin = account("ADMIN");
        UUID owned = restaurant(owner.id(), "Owned Kitchen");
        UUID visibleBranch = branch(owned, "Owned Branch");
        UUID foreign = restaurant(foreignOwner.id(), "Foreign Kitchen");
        branch(foreign, "Foreign Branch");

        assertThat(new Browser().send("GET", "/restaurant-operations/context", null).statusCode())
                .isEqualTo(401);
        assertThat(
                        login(customer)
                                .send("GET", "/restaurant-operations/context", null)
                                .statusCode())
                .isEqualTo(403);
        assertThat(
                        login(admin)
                                .send("GET", "/restaurant-operations/context", null)
                                .statusCode())
                .isEqualTo(403);

        JsonNode contexts =
                body(
                        login(owner)
                                .send("GET", "/restaurant-operations/context", null),
                        200);
        assertThat(contexts.size()).isEqualTo(1);
        assertThat(contexts.get(0).get("restaurantId").asText()).isEqualTo(owned.toString());
        assertThat(contexts.get(0).get("role").asText()).isEqualTo("OWNER");
        assertThat(contexts.get(0).get("branches").get(0).get("branchId").asText())
                .isEqualTo(visibleBranch.toString());
        assertThat(contexts.toString()).doesNotContain(foreign.toString());
    }

    @Test
    void ownerManagesStaffByNormalizedEmailWithSafeResponses() throws Exception {
        var owner = account("RESTAURANT_OWNER");
        var foreignOwner = account("RESTAURANT_OWNER");
        var target = account("CUSTOMER");
        var suspended = account("CUSTOMER");
        jdbc.update("UPDATE users SET status='SUSPENDED' WHERE id=?", suspended.id());
        UUID restaurant = restaurant(owner.id(), "Staff Kitchen");
        UUID foreignRestaurant = restaurant(foreignOwner.id(), "Other Kitchen");
        Browser ownerBrowser = login(owner);

        assertThat(
                        login(foreignOwner)
                                .send("GET", staffPath(restaurant), null)
                                .statusCode())
                .isEqualTo(404);
        JsonNode created =
                body(
                        ownerBrowser.send(
                                "POST",
                                staffPath(restaurant),
                                Map.of("email", "  " + target.email().toUpperCase() + "  ")),
                        201);
        assertThat(fieldNames(created))
                .containsExactlyInAnyOrder("userId", "fullName", "email", "createdAt", "branches");
        assertThat(created.get("userId").asText()).isEqualTo(target.id().toString());
        assertThat(created.get("email").asText()).isEqualTo(target.email());
        assertThat(created.get("branches").isEmpty()).isTrue();

        JsonNode listed = body(ownerBrowser.send("GET", staffPath(restaurant), null), 200);
        assertThat(listed.get("total").asLong()).isEqualTo(1);
        assertThat(fieldNames(listed.get("items").get(0)))
                .containsExactlyInAnyOrder("userId", "fullName", "email", "createdAt", "branches");
        assertThat(
                        login(target)
                                .send("GET", staffPath(restaurant), null)
                                .statusCode())
                .isEqualTo(403);
        assertThat(
                        ownerBrowser
                                .send(
                                        "POST",
                                        staffPath(restaurant),
                                        Map.of("email", target.email()))
                                .statusCode())
                .isEqualTo(409);
        assertThat(
                        ownerBrowser
                                .send(
                                        "POST",
                                        staffPath(restaurant),
                                        Map.of("email", "missing@example.com"))
                                .statusCode())
                .isEqualTo(404);
        assertThat(
                        ownerBrowser
                                .send(
                                        "POST",
                                        staffPath(restaurant),
                                        Map.of("email", suspended.email()))
                                .statusCode())
                .isEqualTo(409);
        assertThat(
                        ownerBrowser
                                .send(
                                        "POST",
                                        staffPath(restaurant),
                                        Map.of("email", owner.email()))
                                .statusCode())
                .isEqualTo(409);
        assertThat(
                        ownerBrowser
                                .send(
                                        "POST",
                                        staffPath(restaurant),
                                        Map.of("email", "bad", "membershipType", "OWNER"))
                                .statusCode())
                .isEqualTo(400);
        assertThat(
                        login(foreignOwner)
                                .send(
                                        "DELETE",
                                        staffPath(restaurant) + "/" + target.id(),
                                        null)
                                .statusCode())
                .isEqualTo(404);
        assertThat(
                        ownerBrowser
                                .send(
                                        "DELETE",
                                        staffPath(foreignRestaurant) + "/" + target.id(),
                                        null)
                                .statusCode())
                .isEqualTo(404);
        body(
                ownerBrowser.send(
                        "DELETE", staffPath(restaurant) + "/" + target.id(), null),
                204);
        assertThat(
                        body(ownerBrowser.send("GET", staffPath(restaurant), null), 200)
                                .get("total")
                                .asLong())
                .isZero();
    }

    @Test
    void staffDiscoversOnlyAssignedBranchesAcrossMultipleRestaurantsAndCanUseQueue()
            throws Exception {
        var firstOwner = account("RESTAURANT_OWNER");
        var secondOwner = account("RESTAURANT_OWNER");
        var foreignOwner = account("RESTAURANT_OWNER");
        var staff = account("CUSTOMER");
        UUID first = restaurant(firstOwner.id(), "First Kitchen");
        UUID second = restaurant(secondOwner.id(), "Second Kitchen");
        UUID foreign = restaurant(foreignOwner.id(), "Foreign Kitchen");
        UUID firstAssigned = branch(first, "First Assigned");
        UUID firstUnassigned = branch(first, "First Hidden");
        UUID secondAssigned = branch(second, "Second Assigned");
        UUID foreignBranch = branch(foreign, "Foreign Hidden");

        Browser firstBrowser = login(firstOwner);
        Browser secondBrowser = login(secondOwner);
        addStaff(firstBrowser, first, staff.email());
        addStaff(secondBrowser, second, staff.email());
        body(
                firstBrowser.send(
                        "PUT",
                        branchStaffPath(first, firstAssigned, staff.id()),
                        Map.of("version", 0)),
                200);
        body(
                secondBrowser.send(
                        "PUT",
                        branchStaffPath(second, secondAssigned, staff.id()),
                        Map.of("version", 0)),
                200);

        JsonNode firstStaff = body(firstBrowser.send("GET", staffPath(first), null), 200);
        assertThat(firstStaff.toString())
                .contains(firstAssigned.toString())
                .doesNotContain(secondAssigned.toString());

        Browser staffBrowser = login(staff);
        JsonNode contexts =
                body(staffBrowser.send("GET", "/restaurant-operations/context", null), 200);
        assertThat(contexts.size()).isEqualTo(2);
        assertThat(contexts.toString())
                .contains(first.toString(), second.toString(), firstAssigned.toString(), secondAssigned.toString())
                .doesNotContain(firstUnassigned.toString(), foreign.toString(), foreignBranch.toString());
        assertThat(contexts.get(0).get("role").asText()).isEqualTo("STAFF");

        body(
                staffBrowser.send(
                        "GET",
                        "/restaurant-orders?restaurantId="
                                + first
                                + "&branchId="
                                + firstAssigned,
                        null),
                200);
        assertThat(
                        staffBrowser
                                .send(
                                        "GET",
                                        "/restaurant-orders?restaurantId="
                                                + first
                                                + "&branchId="
                                                + firstUnassigned,
                                        null)
                                .statusCode())
                .isEqualTo(404);
        assertThat(
                        staffBrowser
                                .send(
                                        "GET",
                                        "/restaurant-orders?restaurantId="
                                                + foreign
                                                + "&branchId="
                                                + foreignBranch,
                                        null)
                                .statusCode())
                .isEqualTo(404);
    }

    @Test
    void roleLifecycleInvalidatesSessionsOnlyWhenGlobalCapabilityChanges() throws Exception {
        var firstOwner = account("RESTAURANT_OWNER");
        var secondOwner = account("RESTAURANT_OWNER");
        var staff = account("CUSTOMER");
        UUID first = restaurant(firstOwner.id(), "First Role Kitchen");
        UUID second = restaurant(secondOwner.id(), "Second Role Kitchen");
        Browser staleCustomerSession = login(staff);
        Browser firstBrowser = login(firstOwner);
        Browser secondBrowser = login(secondOwner);

        addStaff(firstBrowser, first, staff.email());
        assertThat(staleCustomerSession.send("GET", "/users/me", null).statusCode()).isEqualTo(401);
        addStaff(secondBrowser, second, staff.email());
        assertThat(roleCount(staff.id(), "RESTAURANT_STAFF")).isEqualTo(1);

        Browser staffBrowser = login(staff);
        body(
                firstBrowser.send("DELETE", staffPath(first) + "/" + staff.id(), null),
                204);
        assertThat(roleCount(staff.id(), "RESTAURANT_STAFF")).isEqualTo(1);
        JsonNode remaining =
                body(staffBrowser.send("GET", "/restaurant-operations/context", null), 200);
        assertThat(remaining.size()).isEqualTo(1);
        assertThat(remaining.get(0).get("restaurantId").asText()).isEqualTo(second.toString());

        body(
                secondBrowser.send("DELETE", staffPath(second) + "/" + staff.id(), null),
                204);
        assertThat(roleCount(staff.id(), "RESTAURANT_STAFF")).isZero();
        assertThat(
                        staffBrowser
                                .send("GET", "/restaurant-operations/context", null)
                                .statusCode())
                .isEqualTo(401);
        assertThat(
                        login(staff)
                                .send("GET", "/restaurant-operations/context", null)
                                .statusCode())
                .isEqualTo(403);
    }

    @Test
    void concurrentDuplicateCreationHasOneWinner() throws Exception {
        var owner = account("RESTAURANT_OWNER");
        var target = account("CUSTOMER");
        UUID restaurant = restaurant(owner.id(), "Concurrent Kitchen");
        Browser first = login(owner);
        Browser second = login(owner);
        var start = new CountDownLatch(1);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var one =
                    executor.submit(
                            () -> {
                                start.await();
                                return first.send(
                                                "POST",
                                                staffPath(restaurant),
                                                Map.of("email", target.email()))
                                        .statusCode();
                            });
            var two =
                    executor.submit(
                            () -> {
                                start.await();
                                return second.send(
                                                "POST",
                                                staffPath(restaurant),
                                                Map.of("email", target.email()))
                                        .statusCode();
                            });
            start.countDown();
            assertThat(List.of(one.get(15, TimeUnit.SECONDS), two.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(201, 409);
        }
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM restaurant_memberships WHERE restaurant_id=?"
                                        + " AND user_id=? AND membership_type='STAFF'",
                                Integer.class,
                                restaurant,
                                target.id()))
                .isEqualTo(1);
        assertThat(roleCount(target.id(), "RESTAURANT_STAFF")).isEqualTo(1);
    }

    @Test
    void membershipRemovalAndBranchAssignmentRemainAtomic() throws Exception {
        var owner = account("RESTAURANT_OWNER");
        var target = account("CUSTOMER");
        UUID restaurant = restaurant(owner.id(), "Removal Kitchen");
        UUID branch = branch(restaurant, "Removal Branch");
        Browser creator = login(owner);
        Browser remover = login(owner);
        addStaff(creator, restaurant, target.email());
        var start = new CountDownLatch(1);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var assignment =
                    executor.submit(
                            () -> {
                                start.await();
                                return creator.send(
                                                "PUT",
                                                branchStaffPath(restaurant, branch, target.id()),
                                                Map.of("version", 0))
                                        .statusCode();
                            });
            var removal =
                    executor.submit(
                            () -> {
                                start.await();
                                return remover.send(
                                                "DELETE",
                                                staffPath(restaurant) + "/" + target.id(),
                                                null)
                                        .statusCode();
                            });
            start.countDown();
            assertThat(removal.get(15, TimeUnit.SECONDS)).isEqualTo(204);
            assertThat(assignment.get(15, TimeUnit.SECONDS)).isIn(200, 400);
        }
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM restaurant_memberships WHERE restaurant_id=?"
                                        + " AND user_id=?",
                                Integer.class,
                                restaurant,
                                target.id()))
                .isZero();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM branch_staff_assignments WHERE restaurant_id=?"
                                        + " AND user_id=?",
                                Integer.class,
                                restaurant,
                                target.id()))
                .isZero();
    }

    private Account account(String... roles) {
        UUID id = UUID.randomUUID();
        String email = id + "@example.com";
        jdbc.update(
                "INSERT INTO users(id,full_name,email,password_hash,status,created_at,updated_at)"
                        + " VALUES (?,'Restaurant User',?,?,'ACTIVE',now(),now())",
                id,
                email,
                passwords.encode(PASSWORD));
        for (String role : roles)
            jdbc.update("INSERT INTO user_roles(user_id,role_name) VALUES (?,?)", id, role);
        return new Account(id, email);
    }

    private UUID restaurant(UUID owner, String name) {
        UUID application = UUID.randomUUID();
        UUID restaurant = UUID.randomUUID();
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
                                    "INSERT INTO application_submissions VALUES"
                                            + " (?,1,?,'Restaurant operations',now())",
                                    application,
                                    name);
                            jdbc.update(
                                    "INSERT INTO"
                                            + " restaurants(id,application_id,name,description,status,created_at,updated_at)"
                                            + " VALUES (?,?,?,'Operations','ACTIVE',now(),now())",
                                    restaurant,
                                    application,
                                    name);
                            jdbc.update(
                                    "INSERT INTO restaurant_memberships VALUES"
                                            + " (?,?,'OWNER',now())",
                                    restaurant,
                                    owner);
                        });
        return restaurant;
    }

    private UUID branch(UUID restaurant, String name) {
        UUID branch = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO"
                        + " branches(id,restaurant_id,name,address_line1,city,country_code,timezone,delivery_model,status,created_at,updated_at)"
                        + " VALUES"
                        + " (?,?,?,'Street','Cairo','EG','Africa/Cairo','RESTAURANT_DELIVERY','ACTIVE',now(),now())",
                branch,
                restaurant,
                name);
        return branch;
    }

    private void addStaff(Browser owner, UUID restaurant, String email) throws Exception {
        body(owner.send("POST", staffPath(restaurant), Map.of("email", email)), 201);
    }

    private int roleCount(UUID user, String role) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM user_roles WHERE user_id=? AND role_name=?",
                Integer.class,
                user,
                role);
    }

    private String staffPath(UUID restaurant) {
        return "/restaurants/" + restaurant + "/staff";
    }

    private String branchStaffPath(UUID restaurant, UUID branch, UUID user) {
        return "/restaurants/" + restaurant + "/branches/" + branch + "/staff/" + user;
    }

    private Set<String> fieldNames(JsonNode node) {
        var fields = new java.util.HashSet<String>();
        node.propertyStream().forEach(entry -> fields.add(entry.getKey()));
        return fields;
    }

    private Browser login(Account account) throws Exception {
        Browser browser = new Browser();
        browser.csrf();
        body(
                browser.send(
                        "POST",
                        "/auth/session",
                        Map.of("email", account.email(), "password", PASSWORD)),
                204);
        browser.csrf();
        return browser;
    }

    private JsonNode body(HttpResponse<String> response, int status) throws Exception {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
        return response.body().isBlank() ? null : json.readTree(response.body());
    }

    private record Account(UUID id, String email) {}

    private final class Browser {
        private String cookie;
        private String token;

        private HttpResponse<String> send(String method, String path, Object input)
                throws Exception {
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
                if (value.startsWith("SESSION="))
                    cookie = value.substring(0, value.indexOf(';'));
            return response;
        }

        private void csrf() throws Exception {
            JsonNode response = body(send("GET", "/auth/csrf", null), 200);
            token = response.get("token").asText();
        }
    }
}
