package com.tayyar.order;

import static com.tayyar.order.OrderDtos.*;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/** Internal foundation for a later Checkout and actor-authorized operations layer. */
@Service
@Transactional(readOnly = true)
public class OrderService {
    private final OrderStore store;
    private final Clock clock;

    public OrderService(OrderStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    @Transactional
    public Details create(Draft draft, TransitionActor actor) {
        require(draft != null && actor != null, "Order draft and actor are required");
        OrderRules.initial(draft.initialStatus());
        require(
                draft.customerId() != null
                        && draft.restaurantId() != null
                        && draft.branchId() != null,
                "Order context is required");
        require(
                draft.items() != null && !draft.items().isEmpty() && draft.items().size() <= 100,
                "Order requires 1–100 items");
        var items = new ArrayList<Item>();
        BigDecimal merchandise = BigDecimal.ZERO.setScale(2);
        for (PurchaseItem source : draft.items()) {
            require(
                    source != null && source.menuItemId() != null,
                    "Menu item identity is required");
            String name = text(source.name(), 120, "Purchased item name");
            if (source.quantity() < 1 || source.quantity() > 99)
                throw OrderException.invalid("Quantity must be between 1 and 99");
            BigDecimal unit = MoneyRules.amount(source.unitPrice(), "Unit price");
            BigDecimal subtotal =
                    MoneyRules.amount(
                            unit.multiply(BigDecimal.valueOf(source.quantity())), "Line subtotal");
            merchandise = MoneyRules.amount(merchandise.add(subtotal), "Merchandise subtotal");
            items.add(
                    new Item(
                            UUID.randomUUID(),
                            source.menuItemId(),
                            name,
                            unit,
                            source.quantity(),
                            subtotal));
        }
        BigDecimal delivery = MoneyRules.amount(draft.deliveryFee(), "Delivery fee");
        BigDecimal discount = MoneyRules.amount(draft.discountTotal(), "Discount total");
        if (discount.compareTo(merchandise) > 0)
            throw OrderException.invalid("Discount cannot exceed merchandise subtotal");
        BigDecimal total =
                MoneyRules.amount(merchandise.add(delivery).subtract(discount), "Final total");
        Money money = new Money(MoneyRules.CURRENCY, merchandise, delivery, discount, total);
        AddressSnapshot address = address(draft.address());
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        try {
            store.create(id, draft, money, now);
            store.items(id, draft.restaurantId(), items, now);
            store.address(id, address, now);
            store.history(id, null, draft.initialStatus(), actor, null, now);
        } catch (DataIntegrityViolationException exception) {
            throw OrderException.invalid("Order relationships or values are invalid");
        }
        return store.details(id);
    }

    @Transactional
    public View transition(
            UUID id,
            long expectedVersion,
            OrderStatus target,
            TransitionActor actor,
            String reason) {
        if (id == null || target == null || actor == null)
            throw OrderException.invalid("Transition identity, status and actor are required");
        var state = store.state(id);
        if (state.version() != expectedVersion) throw OrderException.conflict();
        OrderRules.transition(state.status(), target, reason);
        String normalizedReason = OrderRules.optionalReason(reason);
        Instant now = clock.instant();
        store.transition(state, target, now);
        store.history(id, state.status(), target, actor, normalizedReason, now);
        return store.details(id).order();
    }

    public Details details(UUID id) {
        return store.details(id);
    }

    public List<History> history(UUID id) {
        store.state(id);
        return store.history(id);
    }

    private AddressSnapshot address(AddressSnapshot source) {
        require(source != null, "Delivery address snapshot is required");
        if ((source.latitude() == null) != (source.longitude() == null))
            throw OrderException.invalid("Address coordinates must be supplied together");
        BigDecimal latitude =
                coordinate(source.latitude(), new BigDecimal("-90"), new BigDecimal("90"));
        BigDecimal longitude =
                coordinate(source.longitude(), new BigDecimal("-180"), new BigDecimal("180"));
        String country = text(source.countryCode(), 2, "Country code");
        if (!country.matches("[A-Z]{2}"))
            throw OrderException.invalid("Country code must be two uppercase letters");
        return new AddressSnapshot(
                optional(source.label(), 80),
                text(source.street(), 200, "Street"),
                text(source.building(), 80, "Building"),
                optional(source.floor(), 40),
                optional(source.apartment(), 40),
                optional(source.landmark(), 200),
                optional(source.instructions(), 1000),
                text(source.city(), 100, "City"),
                optional(source.region(), 100),
                optional(source.postalCode(), 20),
                country,
                latitude,
                longitude,
                source.deliveryZoneId(),
                optional(source.deliveryZoneName(), 100),
                optional(source.managedCityName(), 100));
    }

    private BigDecimal coordinate(BigDecimal value, BigDecimal minimum, BigDecimal maximum) {
        if (value == null) return null;
        if (value.scale() > 6 || value.compareTo(minimum) < 0 || value.compareTo(maximum) > 0)
            throw OrderException.invalid("Invalid address coordinates");
        return value;
    }

    private String text(String value, int maximum, String field) {
        String result = optional(value, maximum);
        if (result == null) throw OrderException.invalid(field + " is required");
        return result;
    }

    private String optional(String value, int maximum) {
        if (value == null) return null;
        String result = value.strip();
        if (result.isEmpty() || result.length() > maximum)
            throw OrderException.invalid("Snapshot text must contain 1–" + maximum + " characters");
        return result;
    }

    private void require(boolean condition, String message) {
        if (!condition) throw OrderException.invalid(message);
    }
}
