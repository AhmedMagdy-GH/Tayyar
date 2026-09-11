package com.tayyar.promotion;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
public class PromotionEvaluator {
    private final PromotionStore store;

    public PromotionEvaluator(PromotionStore store) { this.store = store; }

    /** Called inside the Checkout transaction; the row lock serializes limit decisions. */
    public Applied evaluate(UUID restaurant, UUID customer, String submittedCode,
            BigDecimal merchandiseSubtotal, Instant now) {
        String code = PromotionRules.code(submittedCode);
        var promotion = store.lockByCode(restaurant, code)
                .orElseThrow(() -> ineligible("Promotion code is invalid for this restaurant"));
        if (!promotion.active()) throw ineligible("Promotion is inactive");
        if (promotion.starts() != null && now.isBefore(promotion.starts()))
            throw ineligible("Promotion has not started");
        if (promotion.ends() != null && !now.isBefore(promotion.ends()))
            throw ineligible("Promotion has expired");
        if (merchandiseSubtotal.compareTo(promotion.minimum()) < 0)
            throw ineligible("Merchandise subtotal is below the promotion minimum");
        if (promotion.totalLimit() != null && store.totalUses(promotion.id()) >= promotion.totalLimit())
            throw ineligible("Promotion usage limit has been reached");
        if (promotion.customerLimit() != null
                && store.customerUses(promotion.id(), customer) >= promotion.customerLimit())
            throw ineligible("Customer promotion usage limit has been reached");
        BigDecimal discount = PromotionRules.discount(promotion.type(), promotion.percentage(),
                promotion.fixed(), promotion.maximum(), merchandiseSubtotal);
        if (discount.signum() == 0) throw ineligible("Promotion produces no discount");
        return new Applied(promotion, discount);
    }

    public void redeem(Applied applied, UUID customer, UUID order, Instant now) {
        store.redeem(applied.promotion(), customer, order, applied.discount(), now);
    }

    private PromotionException ineligible(String message) {
        return PromotionException.conflict("PROMOTION_INELIGIBLE", message);
    }

    public record Applied(PromotionStore.Locked promotion, BigDecimal discount) {}
}
