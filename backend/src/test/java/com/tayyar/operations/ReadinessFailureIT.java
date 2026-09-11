package com.tayyar.operations;

import static org.assertj.core.api.Assertions.assertThat;

import com.tayyar.support.PostgresIntegrationTest;
import com.zaxxer.hikari.HikariDataSource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ReadinessFailureIT extends PostgresIntegrationTest {
    @Value("${local.server.port}") int port;
    @Autowired DataSource dataSource;

    @Test
    void databaseFailureRemovesReadinessWithoutRemovingLiveness() throws Exception {
        ((HikariDataSource) dataSource).close();
        HttpClient http = HttpClient.newHttpClient();
        var readiness = http.send(HttpRequest.newBuilder(uri("/actuator/health/readiness")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        var liveness = http.send(HttpRequest.newBuilder(uri("/actuator/health/liveness")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(readiness.statusCode()).isEqualTo(503);
        assertThat(readiness.body()).isEqualTo("{\"status\":\"DOWN\"}");
        assertThat(liveness.statusCode()).isEqualTo(200);
        assertThat(liveness.body()).isEqualTo("{\"status\":\"UP\"}");
    }

    private URI uri(String path) { return URI.create("http://localhost:" + port + path); }
}
