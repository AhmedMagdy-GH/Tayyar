package com.tayyar.order;

import java.math.BigDecimal;

public final class MoneyRules {
    public static final String CURRENCY = "EGP";
    private static final BigDecimal LIMIT = new BigDecimal("10000000000");

    private MoneyRules() {}

    public static BigDecimal amount(BigDecimal value, String field) {
        if (value == null || value.signum() < 0 || value.scale() > 2 || value.compareTo(LIMIT) >= 0)
            throw OrderException.invalid(field + " must be a valid nonnegative EGP amount");
        return value.setScale(2);
    }
}
