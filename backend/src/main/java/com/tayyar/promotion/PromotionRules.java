package com.tayyar.promotion;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Locale;

public final class PromotionRules {
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal MONEY_LIMIT = new BigDecimal("10000000000");

    private PromotionRules() {}

    public static String code(String input) {
        if (input == null) throw PromotionException.invalid("Promotion code is required");
        String value = input.strip().toUpperCase(Locale.ROOT);
        if (!value.matches("[A-Z0-9][A-Z0-9_-]{2,31}"))
            throw PromotionException.invalid(
                    "Promotion code must contain 3–32 ASCII letters, digits, underscores or hyphens");
        return value;
    }

    public static Values values(
            DiscountType type,
            BigDecimal percentage,
            BigDecimal fixed,
            BigDecimal minimum,
            BigDecimal maximum,
            Instant starts,
            Instant ends) {
        if (type == null) throw PromotionException.invalid("Discount type is required");
        minimum = money(minimum, true, "Minimum merchandise subtotal");
        if (starts != null && ends != null && !starts.isBefore(ends))
            throw PromotionException.invalid("Promotion start must be before its end");
        if (type == DiscountType.PERCENTAGE) {
            if (percentage == null || percentage.signum() <= 0
                    || percentage.compareTo(HUNDRED) > 0 || percentage.scale() > 2 || fixed != null)
                throw PromotionException.invalid(
                        "Percentage promotions require only a percentage greater than 0 and at most 100");
            maximum = maximum == null ? null : money(maximum, false, "Maximum discount");
            return new Values(percentage.setScale(2), null, minimum, maximum);
        }
        if (fixed == null || fixed.signum() <= 0 || percentage != null || maximum != null)
            throw PromotionException.invalid(
                    "Fixed promotions require only a fixed amount greater than zero");
        return new Values(null, money(fixed, false, "Fixed amount"), minimum, null);
    }

    public static BigDecimal discount(
            DiscountType type,
            BigDecimal percentage,
            BigDecimal fixed,
            BigDecimal maximum,
            BigDecimal subtotal) {
        BigDecimal calculated = type == DiscountType.PERCENTAGE
                ? subtotal.multiply(percentage).divide(HUNDRED, 2, RoundingMode.HALF_UP)
                : fixed;
        if (maximum != null && calculated.compareTo(maximum) > 0) calculated = maximum;
        return calculated.min(subtotal).setScale(2);
    }

    private static BigDecimal money(BigDecimal value, boolean zeroAllowed, String field) {
        if (value == null || value.scale() > 2 || value.compareTo(MONEY_LIMIT) >= 0
                || (zeroAllowed ? value.signum() < 0 : value.signum() <= 0))
            throw PromotionException.invalid(field + " is outside the supported EGP range");
        return value.setScale(2);
    }

    public record Values(
            BigDecimal percentage, BigDecimal fixed, BigDecimal minimum, BigDecimal maximum) {}
}
