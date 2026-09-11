package com.tayyar.operations;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;
import org.springframework.mock.env.MockEnvironment;

class OperationsPropertiesTests {
    @Test
    void acceptsSameOriginAndExplicitProductionHttpsOrigins() {
        assertThatCode(() -> new OperationsProperties(List.of(), DataSize.ofMegabytes(1)).validate(true))
                .doesNotThrowAnyException();
        assertThatCode(() -> new OperationsProperties(
                List.of("https://app.example.com"), DataSize.ofMegabytes(1)).validate(true))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsUnsafeProductionOriginsAndUnboundedBodyLimits() {
        assertThatThrownBy(() -> new OperationsProperties(
                List.of("http://app.example.com"), DataSize.ofMegabytes(1)).validate(true))
                .hasMessageContaining("HTTPS");
        assertThatThrownBy(() -> new OperationsProperties(
                List.of("https://app.example.com/path"), DataSize.ofMegabytes(1)).validate(true))
                .hasMessageContaining("without paths");
        assertThatThrownBy(() -> new OperationsProperties(
                List.of(), DataSize.ofGigabytes(1)).validate(false))
                .hasMessageContaining("between 64KB and 10MB");
    }

    @Test
    void productionFailsFastForUnsafeCookieOrFlywaySettings() {
        var environment = new MockEnvironment();
        environment.setActiveProfiles("production");
        var properties = new OperationsProperties(List.of(), DataSize.ofMegabytes(1));
        assertThatThrownBy(() -> new OperationsConfiguration(
                properties, environment, false, true, false).validate())
                .hasMessageContaining("Secure session cookies");
        assertThatThrownBy(() -> new OperationsConfiguration(
                properties, environment, true, false, false).validate())
                .hasMessageContaining("Flyway clean disabled");
        assertThatThrownBy(() -> new OperationsConfiguration(
                properties, environment, true, true, true).validate())
                .hasMessageContaining("baseline disabled");
    }
}
