package com.tayyar.payment;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

class PaymentRulesTests {
    @Test
    void cashHasNoAuthorizationState() {
        assertThatCode(
                        () ->
                                PaymentRules.transition(
                                        PaymentMethod.CASH,
                                        PaymentStatus.PENDING,
                                        PaymentStatus.PAID,
                                        null))
                .doesNotThrowAnyException();
        assertThatThrownBy(
                        () ->
                                PaymentRules.transition(
                                        PaymentMethod.CASH,
                                        PaymentStatus.PENDING,
                                        PaymentStatus.AUTHORIZED,
                                        null))
                .isInstanceOf(PaymentException.class);
    }

    @Test
    void cardFoundationRequiresAuthorizationOrFailureBeforePaid() {
        assertThatCode(
                        () ->
                                PaymentRules.transition(
                                        PaymentMethod.CARD,
                                        PaymentStatus.PENDING,
                                        PaymentStatus.AUTHORIZED,
                                        null))
                .doesNotThrowAnyException();
        assertThatCode(
                        () ->
                                PaymentRules.transition(
                                        PaymentMethod.CARD,
                                        PaymentStatus.AUTHORIZED,
                                        PaymentStatus.PAID,
                                        null))
                .doesNotThrowAnyException();
        assertThatThrownBy(
                        () ->
                                PaymentRules.transition(
                                        PaymentMethod.CARD,
                                        PaymentStatus.PENDING,
                                        PaymentStatus.PAID,
                                        null))
                .isInstanceOf(PaymentException.class);
    }

    @Test
    void failedAndRefundedAreTerminalAndReasonsAreRequired() {
        assertThatThrownBy(
                        () ->
                                PaymentRules.transition(
                                        PaymentMethod.CARD,
                                        PaymentStatus.PENDING,
                                        PaymentStatus.FAILED,
                                        null))
                .isInstanceOf(PaymentException.class);
        assertThatThrownBy(
                        () ->
                                PaymentRules.transition(
                                        PaymentMethod.CARD,
                                        PaymentStatus.PAID,
                                        PaymentStatus.REFUNDED,
                                        " "))
                .isInstanceOf(PaymentException.class);
        for (PaymentStatus terminal :
                new PaymentStatus[] {PaymentStatus.FAILED, PaymentStatus.REFUNDED})
            for (PaymentStatus target : PaymentStatus.values())
                assertThatThrownBy(
                                () ->
                                        PaymentRules.transition(
                                                PaymentMethod.CARD, terminal, target, "reason"))
                        .isInstanceOf(PaymentException.class);
    }
}
