package com.tayyar.order;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

public final class OrderOperationsDtos {
    private OrderOperationsDtos() {}

    public record PublicPlace(UUID id, String name) {}

    public record Summary(
            UUID id,
            OrderStatus status,
            PublicPlace restaurant,
            PublicPlace branch,
            BigDecimal merchandiseSubtotal,
            BigDecimal deliveryFee,
            BigDecimal discountTotal,
            BigDecimal finalTotal,
            String currency,
            long version,
            Instant createdAt) {}

    public record PurchasedItem(
            UUID menuItemId,
            String name,
            BigDecimal unitPrice,
            int quantity,
            BigDecimal lineSubtotal) {}

    public record DeliveryAddress(
            String label,
            String street,
            String building,
            String floor,
            String apartment,
            String landmark,
            String instructions,
            String city,
            String region,
            String postalCode,
            String countryCode,
            BigDecimal latitude,
            BigDecimal longitude,
            String deliveryZoneName,
            String managedCityName) {}

    public record PaymentView(String method, String status) {}

    public record HistoryView(
            OrderStatus previousStatus, OrderStatus newStatus, String reason, Instant occurredAt) {}

    public record Details(
            Summary order,
            List<PurchasedItem> items,
            DeliveryAddress deliveryAddress,
            PaymentView payment,
            List<HistoryView> history) {}

    public record Page<T>(List<T> items, int page, int size, long total) {}

    public record Version(@NotNull @PositiveOrZero Long version) {}

    public record Reason(
            @NotNull @PositiveOrZero Long version, @NotBlank @Size(max = 1000) String reason) {}

    public record Window(int page, int size) {
        public Window {
            if (page < 0 || page > 10000 || size < 1 || size > 100)
                throw OrderOperationsException.invalid(
                        "INVALID_PAGE", "Page must be 0–10000 and size 1–100");
        }

        public int offset() {
            return Math.multiplyExact(page, size);
        }
    }
}
