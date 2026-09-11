package com.tayyar.payment;

import java.util.*;

public final class PaymentRules {
    private PaymentRules() {}

    public static void transition(
            PaymentMethod method, PaymentStatus from, PaymentStatus to, String reason) {
        boolean allowed =
                switch (method) {
                    case CASH ->
                            (from == PaymentStatus.PENDING && to == PaymentStatus.PAID)
                                    || (from == PaymentStatus.PAID && to == PaymentStatus.REFUNDED);
                    case CARD ->
                            (from == PaymentStatus.PENDING
                                            && Set.of(
                                                            PaymentStatus.AUTHORIZED,
                                                            PaymentStatus.FAILED)
                                                    .contains(to))
                                    || (from == PaymentStatus.AUTHORIZED
                                            && Set.of(PaymentStatus.PAID, PaymentStatus.FAILED)
                                                    .contains(to))
                                    || (from == PaymentStatus.PAID && to == PaymentStatus.REFUNDED);
                };
        if (!allowed) throw PaymentException.invalid("Invalid payment status transition");
        if (Set.of(PaymentStatus.FAILED, PaymentStatus.REFUNDED).contains(to)) reason(reason);
        optionalReason(reason);
    }

    static String optionalReason(String reason) {
        if (reason == null) return null;
        String normalized = reason.strip();
        if (normalized.isEmpty() || normalized.length() > 1000)
            throw PaymentException.invalid("Reason must contain 1–1000 characters");
        return normalized;
    }

    static String reason(String reason) {
        if (reason == null) throw PaymentException.invalid("A reason is required");
        return optionalReason(reason);
    }
}
