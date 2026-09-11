package com.tayyar.auth;

import jakarta.validation.constraints.*;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@ConfigurationProperties("tayyar.identity")
@Validated
public record IdentityProperties(
        @NotNull Duration absoluteLifetime,
        @Min(1) int loginLimit,
        @Min(1) int registrationLimit,
        @Min(1) int csrfLimit,
        @Min(1) int checkoutLimit) {
    @AssertTrue(message = "Absolute session lifetime must be positive")
    public boolean isLifetimeValid() {
        return absoluteLifetime != null
                && !absoluteLifetime.isNegative()
                && !absoluteLifetime.isZero();
    }
}
