package com.tayyar.payment;

import com.tayyar.order.TransitionActor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class PaymentDtos {
    private PaymentDtos() {}

    public record Create(
            UUID orderId, PaymentMethod method, String provider, String providerReference) {}

    public record View(
            UUID id,
            UUID orderId,
            PaymentMethod method,
            PaymentStatus status,
            BigDecimal amount,
            String currency,
            String provider,
            String providerReference,
            long version,
            Instant createdAt,
            Instant updatedAt) {}

    public record History(
            UUID id,
            PaymentStatus previousStatus,
            PaymentStatus newStatus,
            TransitionActor.Kind actorKind,
            UUID actorId,
            String reason,
            Instant occurredAt) {}
}
