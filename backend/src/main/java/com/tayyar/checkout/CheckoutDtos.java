package com.tayyar.checkout;

import com.tayyar.payment.PaymentMethod;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class CheckoutDtos {
    private CheckoutDtos() {}

    public record Request(
            @NotNull UUID cartId,
            @NotNull @PositiveOrZero Long cartVersion,
            @NotNull UUID savedAddressId,
            @NotNull PaymentMethod paymentMethod,
            @Size(max = 64) String promotionCode) {}

    public record Summary(
            UUID orderId,
            String orderStatus,
            String paymentMethod,
            String paymentStatus,
            BigDecimal merchandiseSubtotal,
            BigDecimal deliveryFee,
            BigDecimal discountTotal,
            BigDecimal finalTotal,
            String currency,
            Instant createdAt) {}
}
