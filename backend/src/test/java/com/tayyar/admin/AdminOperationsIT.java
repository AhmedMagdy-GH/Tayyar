package com.tayyar.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tayyar.support.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
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
import java.util.Map;
import java.util.UUID;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"tayyar.identity.login-limit=1000", "tayyar.identity.csrf-limit=1000"})
class AdminOperationsIT extends PostgresIntegrationTest {
    static final String PASSWORD = "Admin operations password!";

    @Value("${local.server.port}") int port;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    @Autowired PlatformTransactionManager transactions;
    final HttpClient http = HttpClient.newHttpClient();

    UUID admin, customer, target, owner, restaurant, branch, order, driver, assignment;
    Browser adminBrowser, customerBrowser, targetBrowser;

    final class Browser {
        String cookie;
        String csrf;

        HttpResponse<String> send(String method, String path, Object body) throws Exception {
            var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1" + path));
            if (cookie != null) builder.header("Cookie", cookie);
            if (csrf != null) builder.header("X-CSRF-TOKEN", csrf);
            if (body != null) builder.header("Content-Type", "application/json");
            var response = http.send(builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(),
                    HttpResponse.BodyHandlers.ofString());
            for (String value : response.headers().allValues("Set-Cookie"))
                if (value.startsWith("SESSION=")) cookie = value.substring(0, value.indexOf(';'));
            return response;
        }

        void csrf() throws Exception {
            var response = send("GET", "/auth/csrf", null);
            assertThat(response.statusCode()).isEqualTo(200);
            csrf = json.readTree(response.body()).get("token").asText();
        }
    }

    @BeforeEach
    void fixture() throws Exception {
        admin = account("ADMIN");
        customer = account("CUSTOMER");
        target = account("CUSTOMER");
        owner = account("RESTAURANT_OWNER");
        driver = account("DRIVER");
        createOperationsFixture();
        adminBrowser = login(admin);
        customerBrowser = login(customer);
        targetBrowser = login(target);
    }

    UUID account(String... roles) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id,full_name,email,phone,password_hash,status,created_at,updated_at)" +
                        " VALUES (?,'Admin Test User',?,NULL,?,'ACTIVE',now(),now())",
                id, id + "@example.com", passwords.encode(PASSWORD));
        for (String role : roles) jdbc.update("INSERT INTO user_roles(user_id,role_name) VALUES (?,?)", id, role);
        return id;
    }

    void createOperationsFixture() {
        UUID application = UUID.randomUUID();
        restaurant = UUID.randomUUID(); branch = UUID.randomUUID(); order = UUID.randomUUID(); assignment = UUID.randomUUID();
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            jdbc.update("INSERT INTO restaurant_applications(id,applicant_id,status,current_revision,created_at,updated_at)" +
                    " VALUES (?,?,'APPROVED',1,now(),now())", application, owner);
            jdbc.update("INSERT INTO application_submissions VALUES (?,1,'Admin Restaurant','Admin fixture',now())", application);
            jdbc.update("INSERT INTO restaurants(id,application_id,name,description,status,created_at,updated_at)" +
                    " VALUES (?,?,'Admin Restaurant','Operational summary','ACTIVE',now(),now())", restaurant, application);
            jdbc.update("INSERT INTO restaurant_memberships VALUES (?,?,'OWNER',now())", restaurant, owner);
        });
        jdbc.update("INSERT INTO branches(id,restaurant_id,name,address_line1,city,country_code,timezone,delivery_model,status,created_at,updated_at)" +
                " VALUES (?,?,'Admin Branch','Street','Cairo','EG','Africa/Cairo','TAYYAR_DELIVERY','ACTIVE',now(),now())", branch, restaurant);
        jdbc.update("INSERT INTO orders(id,customer_id,restaurant_id,branch_id,status,currency,merchandise_subtotal,delivery_fee,discount_total,final_total,created_at,updated_at)" +
                " VALUES (?,?,?,?,'READY_FOR_PICKUP','EGP',100,10,0,110,now(),now())", order, customer, restaurant, branch);
        jdbc.update("INSERT INTO order_address_snapshots(order_id,street,building,city,country_code,created_at)" +
                " VALUES (?,'Customer Street','10','Cairo','EG',now())", order);
        jdbc.update("INSERT INTO order_status_history(id,order_id,new_status,actor_kind,reason,occurred_at)" +
                " VALUES (?,?,'READY_FOR_PICKUP','SYSTEM','Fixture',now())", UUID.randomUUID(), order);
        jdbc.update("INSERT INTO payments(id,order_id,method,status,amount,currency,created_at,updated_at)" +
                " VALUES (?,?,'CASH','PENDING',110,'EGP',now(),now())", UUID.randomUUID(), order);
        jdbc.update("INSERT INTO driver_profiles(user_id,state,created_at,updated_at) VALUES (?,'BUSY',now(),now())", driver);
        jdbc.update("INSERT INTO delivery_assignments(id,order_id,driver_id,status,assigned_by,assigned_at)" +
                " VALUES (?,?,?,'ACTIVE',?,now())", assignment, order, driver, admin);
    }

    Browser login(UUID id) throws Exception {
        Browser browser = new Browser(); browser.csrf();
        assertThat(browser.send("POST", "/auth/session", Map.of("email", id + "@example.com", "password", PASSWORD)).statusCode()).isEqualTo(204);
        browser.csrf(); return browser;
    }

    JsonNode body(HttpResponse<String> response, int status) throws Exception {
        assertThat(response.statusCode()).isEqualTo(status);
        return response.body().isBlank() ? json.createObjectNode() : json.readTree(response.body());
    }

    @Test
    void userAdministrationIsBoundedSafeAuditedAndInvalidatesSessions() throws Exception {
        JsonNode page = body(adminBrowser.send("GET", "/admin/users?status=ACTIVE&role=CUSTOMER&size=20", null), 200);
        assertThat(page.get("items").size()).isGreaterThanOrEqualTo(2);
        String serialized = page.toString();
        assertThat(serialized).doesNotContain("password", "authVersion", "session", "phone");
        body(customerBrowser.send("GET", "/admin/users", null), 403);

        body(adminBrowser.send("POST", "/admin/users/" + target + "/suspension", Map.of("reason", "Support-verified abuse response")), 200);
        assertThat(targetBrowser.send("GET", "/notifications", null).statusCode()).isIn(401, 403);
        JsonNode audit = body(adminBrowser.send("GET", "/admin/audit-log?targetId=" + target, null), 200);
        assertThat(audit.get("total").asLong()).isEqualTo(1);
        assertThat(audit.at("/items/0/actorId").asText()).isEqualTo(admin.toString());
        assertThat(audit.at("/items/0/actionType").asText()).isEqualTo("ACCOUNT_SUSPENDED");

        body(adminBrowser.send("POST", "/admin/users/" + target + "/reactivation", Map.of("reason", "Support review completed")), 200);
        assertThat(body(adminBrowser.send("GET", "/admin/users/" + target, null), 200).get("status").asText()).isEqualTo("ACTIVE");
        body(adminBrowser.send("POST", "/admin/users/" + target + "/reactivation", Map.of("reason", "stale retry")), 409);
        assertThat(body(adminBrowser.send("GET", "/admin/audit-log?targetId=" + target, null), 200).get("total").asLong()).isEqualTo(2);
    }

    @Test
    void unsafeSuspensionsAndGenericMutationsAreRejected() throws Exception {
        body(adminBrowser.send("POST", "/admin/users/" + driver + "/suspension", Map.of("reason", "Driver investigation")), 409);
        body(adminBrowser.send("POST", "/admin/users/" + customer + "/suspension", Map.of("reason", "Customer investigation")), 409);
        body(adminBrowser.send("POST", "/admin/users/" + owner + "/suspension", Map.of("reason", "Owner investigation")), 409);
        body(adminBrowser.send("POST", "/admin/users/" + admin + "/suspension", Map.of("reason", "self")), 409);
        assertThat(adminBrowser.send("PATCH", "/admin/users/" + target + "/roles", Map.of("roles", "ADMIN")).statusCode()).isIn(404,405);
        assertThat(adminBrowser.send("POST", "/admin/orders/" + order + "/status", Map.of("status", "DELIVERED")).statusCode()).isIn(404,405);
    }

    @Test
    void supportAndOversightReadsUseSafeJoinedProjections() throws Exception {
        JsonNode restaurantView = body(adminBrowser.send("GET", "/admin/restaurants/" + restaurant, null), 200);
        assertThat(restaurantView.get("branches").size()).isEqualTo(1);
        assertThat(body(adminBrowser.send("GET", "/admin/restaurants?status=ACTIVE", null), 200).get("total").asLong()).isGreaterThan(0);

        JsonNode orderView = body(adminBrowser.send("GET", "/admin/orders/" + order, null), 200);
        assertThat(orderView.at("/order/payment/status").asText()).isEqualTo("PENDING");
        assertThat(orderView.at("/order/assignment/driverId").asText()).isEqualTo(driver.toString());
        assertThat(orderView.at("/deliveryAddress/street").asText()).isEqualTo("Customer Street");
        assertThat(orderView.toString()).doesNotContain("providerPaymentReference", "password", "session");
        assertThat(body(adminBrowser.send("GET", "/admin/orders?status=READY_FOR_PICKUP&restaurantId=" + restaurant, null), 200)
                .get("total").asLong()).isEqualTo(1);

        JsonNode drivers = body(adminBrowser.send("GET", "/admin/drivers?state=BUSY", null), 200);
        assertThat(drivers.get("items").toString()).contains(order.toString(), driver.toString());
        body(customerBrowser.send("GET", "/admin/orders", null), 403);
        body(customerBrowser.send("GET", "/admin/drivers", null), 403);
    }

    @Test
    void writesRequireCsrfReasonsAreBoundedAndAuditRowsAreImmutable() throws Exception {
        String token = adminBrowser.csrf; adminBrowser.csrf = null;
        body(adminBrowser.send("POST", "/admin/users/" + target + "/suspension", Map.of("reason", "no csrf")), 403);
        adminBrowser.csrf = token;
        body(adminBrowser.send("POST", "/admin/users/" + target + "/suspension", Map.of("reason", "x".repeat(1001))), 400);
        body(adminBrowser.send("POST", "/admin/users/" + target + "/suspension", Map.of("reason", "valid", "actorId", target)), 400);
        body(adminBrowser.send("POST", "/admin/users/" + target + "/suspension", Map.of("reason", "valid")), 200);
        UUID auditId = jdbc.queryForObject("SELECT id FROM admin_audit_log WHERE target_entity_id=?", UUID.class, target);
        assertThatThrownBy(() -> jdbc.update("UPDATE admin_audit_log SET reason='tampered' WHERE id=?", auditId))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM admin_audit_log WHERE id=?", auditId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
