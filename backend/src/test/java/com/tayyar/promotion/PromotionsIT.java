package com.tayyar.promotion;

import static org.assertj.core.api.Assertions.*;

import com.tayyar.support.PostgresIntegrationTest;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.*;

import java.net.*;
import java.net.http.*;
import java.util.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"tayyar.identity.login-limit=1000", "tayyar.identity.csrf-limit=1000"})
class PromotionsIT extends PostgresIntegrationTest {
    static final String PASSWORD = "Promotions integration password!";
    @Value("${local.server.port}") int port;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    @Autowired PlatformTransactionManager transactions;
    final HttpClient http = HttpClient.newHttpClient();
    UUID owner, foreignOwner, customer, staff, restaurant, foreignRestaurant;
    Browser ownerBrowser, foreignBrowser, customerBrowser, staffBrowser;

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
        owner = account("RESTAURANT_OWNER"); foreignOwner = account("RESTAURANT_OWNER");
        customer = account("CUSTOMER"); staff = account("RESTAURANT_STAFF");
        restaurant = restaurant(owner, "Promotion Kitchen");
        foreignRestaurant = restaurant(foreignOwner, "Foreign Kitchen");
        ownerBrowser = login(owner); foreignBrowser = login(foreignOwner);
        customerBrowser = login(customer); staffBrowser = login(staff);
    }

    UUID account(String role) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id,full_name,email,password_hash,status,created_at,updated_at) "
                        + "VALUES (?,'Promotion User',?,?,'ACTIVE',now(),now())",
                id, id + "@example.com", passwords.encode(PASSWORD));
        jdbc.update("INSERT INTO user_roles VALUES (?,?)", id, role);
        return id;
    }

    UUID restaurant(UUID restaurantOwner, String name) {
        UUID app = UUID.randomUUID(), id = UUID.randomUUID();
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            jdbc.update("INSERT INTO restaurant_applications(id,applicant_id,status,current_revision,created_at,updated_at) "
                    + "VALUES (?,?,'APPROVED',1,now(),now())", app, restaurantOwner);
            jdbc.update("INSERT INTO application_submissions VALUES (?,1,?,'Promotion test',now())", app, name);
            jdbc.update("INSERT INTO restaurants(id,application_id,name,description,status,created_at,updated_at) "
                    + "VALUES (?,?,?,'Public','ACTIVE',now(),now())", id, app, name);
            jdbc.update("INSERT INTO restaurant_memberships VALUES (?,?,'OWNER',now())", id, restaurantOwner);
        });
        return id;
    }

    Browser login(UUID user) throws Exception {
        Browser browser = new Browser(); browser.csrf();
        assertThat(browser.send("POST", "/auth/session",
                Map.of("email", user + "@example.com", "password", PASSWORD)).statusCode()).isEqualTo(204);
        browser.csrf(); return browser;
    }

    JsonNode body(HttpResponse<String> response, int status) throws Exception {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
        return response.body().isBlank() ? null : json.readTree(response.body());
    }

    Map<String,Object> percentage(String code) {
        Map<String,Object> input = new HashMap<>();
        input.put("code", code); input.put("name", "Twenty off");
        input.put("discountType", "PERCENTAGE"); input.put("percentageValue", 20);
        input.put("minimumMerchandiseSubtotal", 50); input.put("maximumDiscount", 100);
        input.put("active", true); input.put("totalUsageLimit", 10);
        input.put("perCustomerUsageLimit", 1);
        return input;
    }

    @Test void ownerCrudNormalizesCodeAndUsesOptimisticVersion() throws Exception {
        var created = body(ownerBrowser.send("POST", "/restaurants/" + restaurant + "/promotions",
                percentage("  Welcome20  ")), 201);
        UUID id = UUID.fromString(created.get("id").asText());
        assertThat(created.get("code").asText()).isEqualTo("WELCOME20");
        assertThat(body(ownerBrowser.send("GET", "/restaurants/" + restaurant + "/promotions", null), 200)
                .get("total").asLong()).isEqualTo(1);
        var edit = percentage("WELCOME20"); edit.put("version", 0); edit.put("active", false);
        var updated = body(ownerBrowser.send("PATCH", "/restaurants/" + restaurant + "/promotions/" + id,
                edit), 200);
        assertThat(updated.get("active").asBoolean()).isFalse();
        body(ownerBrowser.send("PATCH", "/restaurants/" + restaurant + "/promotions/" + id, edit), 409);
    }

    @Test void enforcesOwnershipRolesCsrfAndMassAssignmentBoundary() throws Exception {
        String path = "/restaurants/" + restaurant + "/promotions";
        body(foreignBrowser.send("POST", path, percentage("FOREIGN1")), 404);
        body(customerBrowser.send("POST", path, percentage("CUSTOMER1")), 403);
        body(staffBrowser.send("POST", path, percentage("STAFF111")), 403);
        String saved = ownerBrowser.token; ownerBrowser.token = null;
        body(ownerBrowser.send("POST", path, percentage("NO_CSRF1")), 403);
        ownerBrowser.token = saved;
        var forged = percentage("FORGED11"); forged.put("restaurantId", foreignRestaurant);
        body(ownerBrowser.send("POST", path, forged), 400);
    }

    @Test void rejectsInvalidDefinitionsAndNormalizedDuplicates() throws Exception {
        String path = "/restaurants/" + restaurant + "/promotions";
        body(ownerBrowser.send("POST", path, percentage("Duplicate")), 201);
        body(ownerBrowser.send("POST", path, percentage(" duplicate ")), 409);
        var invalid = percentage("BADPERCENT"); invalid.put("percentageValue", 101);
        body(ownerBrowser.send("POST", path, invalid), 400);
        var fixed = percentage("BADFIXED"); fixed.put("discountType", "FIXED_AMOUNT");
        fixed.remove("percentageValue"); fixed.remove("maximumDiscount"); fixed.put("fixedAmount", 0);
        body(ownerBrowser.send("POST", path, fixed), 400);
        var dates = percentage("BADDATES");
        dates.put("startsAt", "2026-09-12T00:00:00Z"); dates.put("endsAt", "2026-09-11T00:00:00Z");
        body(ownerBrowser.send("POST", path, dates), 400);
    }
}
