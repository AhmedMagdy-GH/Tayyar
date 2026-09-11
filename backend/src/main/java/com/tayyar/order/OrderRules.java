package com.tayyar.order;

import java.util.*;

public final class OrderRules {
    private OrderRules() {}

    private static final Map<OrderStatus, Set<OrderStatus>> TRANSITIONS =
            Map.of(
                    OrderStatus.PENDING_PAYMENT,
                            Set.of(
                                    OrderStatus.PLACED,
                                    OrderStatus.PAYMENT_FAILED,
                                    OrderStatus.CANCELLED),
                    OrderStatus.PLACED,
                            Set.of(
                                    OrderStatus.ACCEPTED,
                                    OrderStatus.REJECTED,
                                    OrderStatus.CANCELLED),
                    OrderStatus.ACCEPTED, Set.of(OrderStatus.PREPARING, OrderStatus.REJECTED),
                    OrderStatus.PREPARING, Set.of(OrderStatus.READY_FOR_PICKUP),
                    OrderStatus.READY_FOR_PICKUP, Set.of(OrderStatus.OUT_FOR_DELIVERY),
                    OrderStatus.OUT_FOR_DELIVERY, Set.of(OrderStatus.DELIVERED));

    public static void initial(OrderStatus status) {
        if (status != OrderStatus.PENDING_PAYMENT && status != OrderStatus.PLACED)
            throw OrderException.invalid("Initial order status must be PENDING_PAYMENT or PLACED");
    }

    public static void transition(OrderStatus from, OrderStatus to, String reason) {
        if (!TRANSITIONS.getOrDefault(from, Set.of()).contains(to))
            throw OrderException.invalid("Invalid order status transition");
        if (Set.of(OrderStatus.PAYMENT_FAILED, OrderStatus.REJECTED, OrderStatus.CANCELLED)
                .contains(to)) reason(reason);
        optionalReason(reason);
    }

    static String optionalReason(String reason) {
        if (reason == null) return null;
        String normalized = reason.strip();
        if (normalized.isEmpty() || normalized.length() > 1000)
            throw OrderException.invalid("Reason must contain 1–1000 characters");
        return normalized;
    }

    static String reason(String reason) {
        if (reason == null) throw OrderException.invalid("A reason is required");
        return optionalReason(reason);
    }
}
