package com.tayyar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tayyar.support.PostgresIntegrationTest;
import com.tayyar.support.fixture.MigrationProbe;
import com.zaxxer.hikari.HikariDataSource;

import jakarta.persistence.EntityManagerFactory;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;

import javax.sql.DataSource;

@SpringBootTest
@EntityScan(
        basePackageClasses = {
            MigrationProbe.class,
            com.tayyar.user.User.class,
            com.tayyar.restaurant.Restaurant.class,
            com.tayyar.branch.Branch.class,
            com.tayyar.menu.Menu.class,
            com.tayyar.address.CustomerAddress.class,
            com.tayyar.delivery.City.class
        })
class BackendApplicationIT extends PostgresIntegrationTest {
    @Autowired DataSource dataSource;
    @Autowired JdbcTemplate jdbc;
    @Autowired Flyway flyway;
    @Autowired EntityManagerFactory entityManagerFactory;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    void startsWithPostgres18HikariAndValidatedJpa() {
        assertThat(dataSource).isInstanceOf(HikariDataSource.class);
        assertThat(jdbc.queryForObject("SELECT 1", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SHOW server_version_num", Integer.class))
                .isBetween(180000, 189999);
        assertThat(entityManagerFactory.getProperties().get("hibernate.hbm2ddl.auto"))
                .isEqualTo("validate");
        assertThat(entityManagerFactory.getMetamodel().entity(MigrationProbe.class)).isNotNull();
    }

    @Test
    void migrationsValidateAndAreNotAppliedTwice() {
        flyway.validate();
        assertThat(flyway.info().applied()).hasSize(14);
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        assertThatThrownBy(flyway::clean).hasMessageContaining("cleanDisabled");
    }

    @Test
    void runtimeUserCannotCreateSchemaObjectsOrActAsSuperuser() {
        assertThat(jdbc.queryForObject("SELECT current_user", String.class))
                .isEqualTo("tayyar_app");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT rolsuper FROM pg_roles WHERE rolname = current_user",
                                Boolean.class))
                .isFalse();
        assertThatThrownBy(() -> jdbc.execute("CREATE TABLE forbidden_table (id integer)"))
                .rootCause()
                .isInstanceOfSatisfying(
                        SQLException.class,
                        error -> assertThat(error.getSQLState()).isEqualTo("42501"));
        assertThatThrownBy(() -> jdbc.execute("DELETE FROM flyway_schema_history"))
                .rootCause()
                .isInstanceOfSatisfying(
                        SQLException.class,
                        error -> assertThat(error.getSQLState()).isEqualTo("42501"));
    }

    @Test
    void refusesToBaselineAnUnknownNonemptySchema() throws Exception {
        try (var connection =
                        DriverManager.getConnection(
                                POSTGRES.getJdbcUrl(),
                                POSTGRES.getUsername(),
                                POSTGRES.getPassword());
                var statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA unknown_existing_schema");
            statement.execute("CREATE TABLE unknown_existing_schema.existing_data (id integer)");
            statement.execute("INSERT INTO unknown_existing_schema.existing_data VALUES (42)");
            Flyway unknownSchema =
                    Flyway.configure()
                            .dataSource(
                                    POSTGRES.getJdbcUrl(),
                                    POSTGRES.getUsername(),
                                    POSTGRES.getPassword())
                            .schemas("unknown_existing_schema")
                            .locations("classpath:db/foundation-test")
                            .baselineOnMigrate(false)
                            .cleanDisabled(true)
                            .load();
            assertThatThrownBy(unknownSchema::migrate).hasMessageContaining("non-empty schema");
            try (var result =
                    statement.executeQuery(
                            "SELECT id FROM unknown_existing_schema.existing_data")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getInt(1)).isEqualTo(42);
            }
        }
    }

    @Test
    void transactionRollsBackApplicationWrites() {
        UUID id = UUID.randomUUID();
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        assertThatThrownBy(
                        () ->
                                transaction.executeWithoutResult(
                                        status -> {
                                            jdbc.update(
                                                    "INSERT INTO foundation_probe (id, label)"
                                                            + " VALUES (?, ?)",
                                                    id,
                                                    "rollback");
                                            throw new IllegalStateException("rollback requested");
                                        }))
                .isInstanceOf(IllegalStateException.class);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM foundation_probe WHERE id = ?",
                                Integer.class,
                                id))
                .isZero();
    }
}
