package com.tayyar.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

public final class OrderDtos {
    private OrderDtos() {}

    public record PurchaseItem(UUID menuItemId, String name, BigDecimal unitPrice, int quantity) {}

    public record AddressSnapshot(
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
            UUID deliveryZoneId,
            String deliveryZoneName,
            String managedCityName) {}

    public record Draft(
            UUID customerId,
            UUID restaurantId,
            UUID branchId,
            OrderStatus initialStatus,
            List<PurchaseItem> items,
            AddressSnapshot address,
            BigDecimal deliveryFee,
            BigDecimal discountTotal) {}

    public record Money(
            String currency,
            BigDecimal merchandiseSubtotal,
            BigDecimal deliveryFee,
            BigDecimal discountTotal,
            BigDecimal finalTotal) {}

    public record Item(
            UUID id,
            UUID originalMenuItemId,
            String purchasedName,
            BigDecimal unitPrice,
            int quantity,
            BigDecimal lineSubtotal) {}

    public record View(
            UUID id,
            UUID customerId,
            UUID restaurantId,
            UUID branchId,
            OrderStatus status,
            Money money,
            long version,
            Instant createdAt,
            Instant updatedAt) {}

    public record Details(View order, List<Item> items, AddressSnapshot address) {}

    public record History(
            UUID id,
            OrderStatus previousStatus,
            OrderStatus newStatus,
            TransitionActor.Kind actorKind,
            UUID actorId,
            String reason,
            Instant occurredAt) {}
}
