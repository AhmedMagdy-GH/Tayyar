package com.tayyar.order;

public enum OrderStatus {
    PENDING_PAYMENT,
    PLACED,
    ACCEPTED,
    PREPARING,
    READY_FOR_PICKUP,
    OUT_FOR_DELIVERY,
    DELIVERED,
    PAYMENT_FAILED,
    REJECTED,
    CANCELLED
}
