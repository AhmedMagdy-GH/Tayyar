package com.tayyar.menu;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

public final class MenuDtos {
    private MenuDtos() {}

    public record CreateMenu(@NotBlank @Size(max = 120) String name) {}

    public record EditMenu(
            @NotBlank @Size(max = 120) String name,
            @NotNull Boolean active,
            @NotNull @PositiveOrZero Long version) {}

    public record CreateCategory(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 2000) String description,
            @NotNull @PositiveOrZero Long version) {}

    public record EditCategory(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 2000) String description,
            @NotNull Boolean active,
            @NotNull @PositiveOrZero Long version) {}

    public record CreateItem(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 2000) String description,
            @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal basePrice,
            @NotNull Boolean available,
            @NotNull @PositiveOrZero Long version) {}

    public record EditItem(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 2000) String description,
            @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal basePrice,
            @NotNull Boolean available,
            @NotNull Boolean active,
            @NotNull @PositiveOrZero Long version) {}

    public record OverrideInput(
            Boolean available,
            @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal price,
            @NotNull @PositiveOrZero Long version) {}

    public record Reorder(
            @NotNull @Size(max = 500) List<@NotNull UUID> ids,
            @NotNull @PositiveOrZero Long version) {}

    public record VersionInput(@NotNull @PositiveOrZero Long version) {}

    public record Mutation(UUID id, long version) {}

    public record MenuView(
            UUID id,
            UUID restaurantId,
            String name,
            boolean active,
            String currency,
            long version,
            Instant createdAt,
            Instant updatedAt) {}

    public record CategoryView(
            UUID id, String name, String description, boolean active, int position) {}

    public record ItemView(
            UUID id,
            UUID categoryId,
            String name,
            String description,
            BigDecimal basePrice,
            boolean active,
            boolean available,
            int position) {}

    public record EffectiveItem(
            UUID id,
            UUID categoryId,
            String name,
            String description,
            BigDecimal basePrice,
            BigDecimal priceOverride,
            Boolean availabilityOverride,
            BigDecimal effectivePrice,
            boolean effectiveAvailable,
            String currency) {}

    public record Snapshot<T>(T data, long version) {}

    public record Page<T>(List<T> items, int page, int size, long total, long version) {}

    public record Window(int page, int size) {
        public Window {
            if (page < 0 || page > 10000 || size < 1 || size > 100)
                throw MenuException.invalid("Page must be 0–10000 and size 1–100");
        }

        public int offset() {
            return page * size;
        }
    }
}
