package com.tayyar.delivery;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

public final class DeliveryDtos {
    private DeliveryDtos() {}

    public record GeographyInput(@NotBlank @Size(max = 100) String name, @NotNull Boolean active) {}

    public record ZoneInput(@NotNull UUID cityId, @NotNull @Valid GeographyInput profile) {}

    public record GeographyEdit(
            @NotNull @Valid GeographyInput profile, @NotNull @PositiveOrZero Long version) {}

    public record RuleInput(
            @NotNull @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal deliveryFee,
            @NotNull @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal minimumOrder,
            @NotNull @Positive Integer etaMinMinutes,
            @NotNull @Positive Integer etaMaxMinutes,
            @NotNull Boolean enabled) {}

    public record RuleEdit(@NotNull @Valid RuleInput rule, @NotNull @PositiveOrZero Long version) {}

    public record GeographyView(
            UUID id,
            UUID cityId,
            String name,
            boolean active,
            long version,
            Instant createdAt,
            Instant updatedAt) {}

    public record RuleView(
            UUID id,
            UUID branchId,
            UUID deliveryZoneId,
            String currency,
            RuleInput rule,
            long version,
            Instant createdAt,
            Instant updatedAt) {}

    public record Page<T>(List<T> items, int page, int size, long total) {}

    public record Window(int page, int size) {
        public Window {
            if (page < 0 || page > 10000 || size < 1 || size > 100)
                throw DeliveryException.invalid("Page must be 0–10000 and size 1–100");
        }
    }

    public enum Reason {
        SERVICEABLE,
        ADDRESS_ZONE_REQUIRED,
        RESTAURANT_SUSPENDED,
        BRANCH_INACTIVE,
        BRANCH_PAUSED,
        CITY_INACTIVE,
        ZONE_INACTIVE,
        ZONE_NOT_SERVED,
        RELATIONSHIP_DISABLED
    }

    public record Eligibility(
            UUID branchId,
            UUID deliveryZoneId,
            boolean serviceable,
            Reason reason,
            String currency,
            BigDecimal deliveryFee,
            BigDecimal minimumOrder,
            Integer etaMinMinutes,
            Integer etaMaxMinutes) {}
}
