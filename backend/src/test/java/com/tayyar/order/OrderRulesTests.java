package com.tayyar.order;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;

class OrderRulesTests {
    @Test
    void approvedLifecycleTransitionsAreExplicit() {
        var approved =
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
        for (var entry : approved.entrySet())
            for (OrderStatus target : entry.getValue())
                assertThatCode(
                                () ->
                                        OrderRules.transition(
                                                entry.getKey(),
                                                target,
                                                Set.of(
                                                                        OrderStatus.PAYMENT_FAILED,
                                                                        OrderStatus.REJECTED,
                                                                        OrderStatus.CANCELLED)
                                                                .contains(target)
                                                        ? "required reason"
                                                        : null))
                        .doesNotThrowAnyException();
    }

    @Test
    void invalidAndTerminalTransitionsAreRejected() {
        for (OrderStatus terminal :
                Set.of(
                        OrderStatus.DELIVERED,
                        OrderStatus.PAYMENT_FAILED,
                        OrderStatus.REJECTED,
                        OrderStatus.CANCELLED))
            for (OrderStatus target : OrderStatus.values())
                assertThatThrownBy(() -> OrderRules.transition(terminal, target, "reason"))
                        .isInstanceOf(OrderException.class);
        assertThatThrownBy(
                        () ->
                                OrderRules.transition(
                                        OrderStatus.DELIVERED, OrderStatus.PREPARING, null))
                .isInstanceOf(OrderException.class);
        assertThatThrownBy(
                        () ->
                                OrderRules.transition(
                                        OrderStatus.CANCELLED, OrderStatus.ACCEPTED, null))
                .isInstanceOf(OrderException.class);
    }

    @Test
    void cancellationRejectionAndPaymentFailureRequireReasons() {
        assertThatThrownBy(
                        () ->
                                OrderRules.transition(
                                        OrderStatus.PLACED, OrderStatus.CANCELLED, null))
                .isInstanceOf(OrderException.class);
        assertThatThrownBy(
                        () -> OrderRules.transition(OrderStatus.PLACED, OrderStatus.REJECTED, " "))
                .isInstanceOf(OrderException.class);
        assertThatThrownBy(
                        () ->
                                OrderRules.transition(
                                        OrderStatus.PENDING_PAYMENT,
                                        OrderStatus.PAYMENT_FAILED,
                                        "x".repeat(1001)))
                .isInstanceOf(OrderException.class);
    }

    @Test
    void moneyUsesBoundedDecimalEgpValues() {
        assertThat(MoneyRules.amount(new BigDecimal("9999999999.99"), "amount"))
                .isEqualByComparingTo("9999999999.99");
        for (String value : List.of("-0.01", "1.001", "10000000000", "1E+20"))
            assertThatThrownBy(() -> MoneyRules.amount(new BigDecimal(value), "amount"))
                    .isInstanceOf(OrderException.class);
    }
}
