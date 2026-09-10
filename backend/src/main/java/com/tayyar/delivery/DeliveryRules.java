package com.tayyar.delivery;

import static com.tayyar.delivery.DeliveryDtos.*;

import java.math.BigDecimal;

public final class DeliveryRules {
    private DeliveryRules() {}

    static String name(String value) {
        if (value == null
                || value.length() > 100
                || value.replaceAll("[\\s\\p{Z}-]+", "").isEmpty())
            throw DeliveryException.invalid("A geography name is required");
        return value.strip();
    }

    public static void rule(RuleInput input) {
        money(input.deliveryFee());
        money(input.minimumOrder());
        if (input.etaMinMinutes() == null
                || input.etaMaxMinutes() == null
                || input.etaMinMinutes() <= 0
                || input.etaMaxMinutes() < input.etaMinMinutes()
                || input.enabled() == null)
            throw DeliveryException.invalid(
                    "ETA requires positive minutes and maximum at least minimum");
    }

    private static void money(BigDecimal value) {
        if (value == null
                || value.signum() < 0
                || value.scale() > 2
                || value.compareTo(new BigDecimal("10000000000")) >= 0)
            throw DeliveryException.invalid(
                    "Money must be nonnegative with at most ten integer and two fractional digits");
    }
}
