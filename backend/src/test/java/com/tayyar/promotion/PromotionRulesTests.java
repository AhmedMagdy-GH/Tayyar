package com.tayyar.promotion;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

class PromotionRulesTests {
    @Test
    void normalizesAsciiCodesDeterministically() {
        assertThat(PromotionRules.code("  Welcome20  ")).isEqualTo("WELCOME20");
        assertThatThrownBy(() -> PromotionRules.code("WELCOME 20"))
                .isInstanceOf(PromotionException.class);
        assertThatThrownBy(() -> PromotionRules.code("WELCÖME"))
                .isInstanceOf(PromotionException.class);
    }

    @Test
    void percentageRoundsCapsAndNeverExceedsMerchandise() {
        assertThat(PromotionRules.discount(DiscountType.PERCENTAGE, new BigDecimal("20"), null,
                new BigDecimal("100"), new BigDecimal("800"))).isEqualByComparingTo("100.00");
        assertThat(PromotionRules.discount(DiscountType.PERCENTAGE, new BigDecimal("33.33"), null,
                null, new BigDecimal("10"))).isEqualByComparingTo("3.33");
    }

    @Test
    void fixedDiscountIsCappedAtMerchandiseSubtotal() {
        assertThat(PromotionRules.discount(DiscountType.FIXED_AMOUNT, null,
                new BigDecimal("50"), null, new BigDecimal("20"))).isEqualByComparingTo("20.00");
    }

    @Test
    void rejectsMutuallyInconsistentValuesAndDateWindows() {
        assertThatThrownBy(() -> PromotionRules.values(DiscountType.PERCENTAGE,
                new BigDecimal("101"), null, BigDecimal.ZERO, null, null, null))
                .isInstanceOf(PromotionException.class);
        assertThatThrownBy(() -> PromotionRules.values(DiscountType.FIXED_AMOUNT, null,
                BigDecimal.ZERO, BigDecimal.ZERO, null, null, null))
                .isInstanceOf(PromotionException.class);
        Instant now = Instant.parse("2026-09-11T00:00:00Z");
        assertThatThrownBy(() -> PromotionRules.values(DiscountType.PERCENTAGE,
                BigDecimal.TEN, null, BigDecimal.ZERO, null, now, now))
                .isInstanceOf(PromotionException.class);
    }
}
