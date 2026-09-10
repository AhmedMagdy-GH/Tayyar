package com.tayyar.support;

import java.util.UUID;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.images.builder.Transferable;

@Testcontainers
public abstract class PostgresIntegrationTest {
    private static final String APP_PASSWORD = UUID.randomUUID().toString();

    @Container
    protected static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18")
            .withDatabaseName("tayyar_test")
            .withUsername("tayyar_migrator")
            .withPassword(UUID.randomUUID().toString())
            .withCopyToContainer(Transferable.of("""
                    CREATE ROLE tayyar_app LOGIN PASSWORD '%s' NOSUPERUSER NOCREATEDB NOCREATEROLE;
                    REVOKE CREATE ON SCHEMA public FROM PUBLIC;
                    GRANT USAGE ON SCHEMA public TO tayyar_app;
                    ALTER DEFAULT PRIVILEGES FOR ROLE tayyar_migrator IN SCHEMA public
                      GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO tayyar_app;
                    """.formatted(APP_PASSWORD)), "/docker-entrypoint-initdb.d/01-runtime-role.sql");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        properties.add("spring.datasource.username", () -> "tayyar_app");
        properties.add("spring.datasource.password", () -> APP_PASSWORD);
        properties.add("spring.flyway.enabled", () -> true);
        properties.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        properties.add("spring.flyway.user", POSTGRES::getUsername);
        properties.add("spring.flyway.password", POSTGRES::getPassword);
        properties.add("spring.flyway.locations", () -> "classpath:db/foundation-test");
    }
}
