package com.tayyar.cart;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.*;

public final class CartDtos {
    private CartDtos() {}

    public static final int MAX_QUANTITY = 99;

    public record AddItem(
            @NotNull UUID branchId,
            @NotNull UUID menuItemId,
            @NotNull @Min(1) @Max(MAX_QUANTITY) Integer quantity,
            UUID cartId,
            @PositiveOrZero Long cartVersion) {}

    public record Replace(
            @NotNull UUID branchId,
            @NotNull UUID menuItemId,
            @NotNull @Min(1) @Max(MAX_QUANTITY) Integer quantity,
            @NotNull UUID cartId,
            @NotNull @PositiveOrZero Long cartVersion) {}

    public record Quantity(
            @NotNull @Min(1) @Max(MAX_QUANTITY) Integer quantity,
            @NotNull UUID cartId,
            @NotNull @PositiveOrZero Long cartVersion,
            @NotNull @PositiveOrZero Long itemVersion) {}

    public record Versions(UUID cartId, long cartVersion, long itemVersion) {}

    public record Reconfirm(@NotNull UUID cartId, @NotNull @PositiveOrZero Long cartVersion) {}

    public record BranchView(
            UUID id,
            UUID restaurantId,
            String name,
            String restaurantName,
            String state,
            boolean openNow) {}

    public record Line(
            UUID id,
            UUID menuItemId,
            String name,
            int quantity,
            BigDecimal acknowledgedUnitPrice,
            BigDecimal currentUnitPrice,
            boolean priceChanged,
            boolean currentlyAvailable,
            BigDecimal lineSubtotal,
            long version) {}

    public record View(
            UUID id,
            BranchView branch,
            List<Line> items,
            BigDecimal merchandiseSubtotal,
            String currency,
            long version) {}

    public record Mutation(View cart, boolean created) {}
}
