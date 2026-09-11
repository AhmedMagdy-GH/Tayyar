package com.tayyar.promotion;

import static com.tayyar.promotion.PromotionDtos.*;

import com.tayyar.auth.SessionPrincipal;
import com.tayyar.restaurant.RestaurantService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
@PreAuthorize("hasAnyRole('RESTAURANT_OWNER','ADMIN')")
public class PromotionService {
    private final PromotionStore store;
    private final RestaurantService restaurants;
    private final Clock clock;

    public PromotionService(PromotionStore store, RestaurantService restaurants, Clock clock) {
        this.store = store;
        this.restaurants = restaurants;
        this.clock = clock;
    }

    @Transactional
    public View create(UUID restaurant, SessionPrincipal actor, Create input) {
        restaurants.details(restaurant, actor);
        validateLimits(input.totalUsageLimit(), input.perCustomerUsageLimit());
        var values = PromotionRules.values(input.discountType(), input.percentageValue(),
                input.fixedAmount(), input.minimumMerchandiseSubtotal(), input.maximumDiscount(),
                input.startsAt(), input.endsAt());
        UUID id = UUID.randomUUID();
        store.create(id, restaurant, PromotionRules.code(input.code()), input, values, clock.instant());
        return store.get(restaurant, id).orElseThrow(PromotionException::missing);
    }

    public Page list(UUID restaurant, SessionPrincipal actor, int page, int size) {
        restaurants.details(restaurant, actor);
        if (page < 0 || page > 10000 || size < 1 || size > 100)
            throw PromotionException.invalid("Page must be 0–10000 and size 1–100");
        return store.list(restaurant, page, size);
    }

    public View get(UUID restaurant, UUID id, SessionPrincipal actor) {
        restaurants.details(restaurant, actor);
        return store.get(restaurant, id).orElseThrow(PromotionException::missing);
    }

    @Transactional
    public View update(UUID restaurant, UUID id, SessionPrincipal actor, Update input) {
        restaurants.details(restaurant, actor);
        if (store.get(restaurant, id).isEmpty()) throw PromotionException.missing();
        validateLimits(input.totalUsageLimit(), input.perCustomerUsageLimit());
        var values = PromotionRules.values(input.discountType(), input.percentageValue(),
                input.fixedAmount(), input.minimumMerchandiseSubtotal(), input.maximumDiscount(),
                input.startsAt(), input.endsAt());
        if (!store.update(id, restaurant, PromotionRules.code(input.code()), input, values, clock.instant()))
            throw PromotionException.conflict("STALE_PROMOTION",
                    "Promotion changed; reload and retry");
        return store.get(restaurant, id).orElseThrow(PromotionException::missing);
    }

    private void validateLimits(Long total, Long customer) {
        if (total != null && customer != null && customer > total)
            throw new PromotionException(HttpStatus.BAD_REQUEST, "INVALID_PROMOTION",
                    "Per-customer usage limit cannot exceed total usage limit");
    }
}
