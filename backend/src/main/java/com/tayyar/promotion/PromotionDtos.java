package com.tayyar.promotion;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class PromotionDtos {
    private PromotionDtos() {}

    public record Create(
            @NotBlank @Size(min = 3, max = 32) String code,
            @NotBlank @Size(max = 120) String name,
            @Size(max = 1000) String description,
            @NotNull DiscountType discountType,
            @Digits(integer = 3, fraction = 2) BigDecimal percentageValue,
            @Digits(integer = 10, fraction = 2) BigDecimal fixedAmount,
            @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2)
                    BigDecimal minimumMerchandiseSubtotal,
            @Digits(integer = 10, fraction = 2) BigDecimal maximumDiscount,
            Instant startsAt,
            Instant endsAt,
            @NotNull Boolean active,
            @Positive Long totalUsageLimit,
            @Positive Long perCustomerUsageLimit) {}

    public record Update(
            @NotBlank @Size(min = 3, max = 32) String code,
            @NotBlank @Size(max = 120) String name,
            @Size(max = 1000) String description,
            @NotNull DiscountType discountType,
            @Digits(integer = 3, fraction = 2) BigDecimal percentageValue,
            @Digits(integer = 10, fraction = 2) BigDecimal fixedAmount,
            @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2)
                    BigDecimal minimumMerchandiseSubtotal,
            @Digits(integer = 10, fraction = 2) BigDecimal maximumDiscount,
            Instant startsAt,
            Instant endsAt,
            @NotNull Boolean active,
            @Positive Long totalUsageLimit,
            @Positive Long perCustomerUsageLimit,
            @NotNull @PositiveOrZero Long version) {}

    public record View(
            UUID id,
            UUID restaurantId,
            String code,
            String name,
            String description,
            DiscountType discountType,
            BigDecimal percentageValue,
            BigDecimal fixedAmount,
            BigDecimal minimumMerchandiseSubtotal,
            BigDecimal maximumDiscount,
            Instant startsAt,
            Instant endsAt,
            boolean active,
            Long totalUsageLimit,
            Long perCustomerUsageLimit,
            long version,
            Instant createdAt,
            Instant updatedAt) {}

    public record Page(List<View> items, int page, int size, long total) {}
}
