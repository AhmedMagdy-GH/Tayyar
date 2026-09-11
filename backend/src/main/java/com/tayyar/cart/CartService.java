package com.tayyar.cart;

import static com.tayyar.cart.CartDtos.*;

import com.tayyar.auth.SessionPrincipal;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.time.Clock;
import java.util.*;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
@PreAuthorize("hasRole('CUSTOMER')")
public class CartService {
    private final CartQuery query;
    private final CartStore store;
    private final Clock clock;
    private final CartValidationLocks validationLocks;

    public CartService(
            CartQuery query, CartStore store, Clock clock, CartValidationLocks validationLocks) {
        this.query = query;
        this.store = store;
        this.clock = clock;
        this.validationLocks = validationLocks;
    }

    public Optional<View> get(SessionPrincipal actor) {
        return query.active(actor.id(), clock.instant());
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public View reconfirm(SessionPrincipal actor, Reconfirm input) {
        store.lockCustomer(actor.id());
        var cart = store.lockActive(actor.id()).orElseThrow(CartException::missing);
        CartRules.cart(cart.id(), input.cartId(), cart.version(), input.cartVersion());
        validationLocks.lock(cart);
        var view = readRequired(actor.id());
        if (view.items().isEmpty()
                || view.items().stream().anyMatch(line -> !line.currentlyAvailable()))
            throw CartException.conflict(
                    "Cart contains unavailable items; review it before reconfirming prices");
        var now = clock.instant();
        store.reconfirm(cart, view.items(), now);
        store.touch(cart, now);
        return readRequired(actor.id());
    }

    @Transactional
    public Mutation add(SessionPrincipal actor, AddItem input) {
        CartRules.quantity(input.quantity());
        var now = clock.instant();
        store.lockCustomer(actor.id());
        var current = store.lockActive(actor.id());
        if (current.isPresent()) {
            var cart = current.get();
            CartRules.cart(cart.id(), input.cartId(), cart.version(), input.cartVersion());
            if (!cart.branch().equals(input.branchId()))
                throw CartException.conflict(
                        "Cart belongs to another branch; explicitly replace it to continue");
            var effective = requireAddable(input.branchId(), input.menuItemId());
            store.addOrIncrement(
                    cart.id(),
                    effective.item(),
                    effective.restaurant(),
                    input.quantity(),
                    effective.price(),
                    now);
            store.touch(cart, now);
            return new Mutation(readRequired(actor.id()), false);
        }
        if (input.cartId() != null || input.cartVersion() != null)
            throw CartException.conflict("Active cart no longer exists; reload and retry");
        var effective = requireAddable(input.branchId(), input.menuItemId());
        try {
            UUID cart =
                    store.createCart(actor.id(), effective.branch(), effective.restaurant(), now);
            store.addOrIncrement(
                    cart,
                    effective.item(),
                    effective.restaurant(),
                    input.quantity(),
                    effective.price(),
                    now);
        } catch (DataIntegrityViolationException exception) {
            throw CartException.conflict("Cart changed concurrently; reload and retry");
        }
        return new Mutation(readRequired(actor.id()), true);
    }

    @Transactional
    public View update(SessionPrincipal actor, UUID lineId, Quantity input) {
        CartRules.quantity(input.quantity());
        var now = clock.instant();
        store.lockCustomer(actor.id());
        var cart = store.lockActive(actor.id()).orElseThrow(CartException::missing);
        CartRules.cart(cart.id(), input.cartId(), cart.version(), input.cartVersion());
        var line = store.lockLine(cart.id(), lineId);
        CartRules.version(line.version(), input.itemVersion(), "Cart item");
        store.updateQuantity(line, input.quantity(), now);
        store.touch(cart, now);
        return readRequired(actor.id());
    }

    @Transactional
    public Optional<View> remove(SessionPrincipal actor, UUID lineId, Versions input) {
        var now = clock.instant();
        store.lockCustomer(actor.id());
        var cart = store.lockActive(actor.id()).orElseThrow(CartException::missing);
        CartRules.cart(cart.id(), input.cartId(), cart.version(), input.cartVersion());
        var line = store.lockLine(cart.id(), lineId);
        CartRules.version(line.version(), input.itemVersion(), "Cart item");
        store.remove(line);
        if (store.lineCount(cart.id()) == 0) {
            store.retire(cart, "ABANDONED", now);
            return Optional.empty();
        }
        store.touch(cart, now);
        return Optional.of(readRequired(actor.id()));
    }

    @Transactional
    public void clear(SessionPrincipal actor, UUID cartId, long version) {
        var now = clock.instant();
        store.lockCustomer(actor.id());
        var cart = store.lockActive(actor.id()).orElseThrow(CartException::missing);
        CartRules.cart(cart.id(), cartId, cart.version(), version);
        store.retire(cart, "ABANDONED", now);
    }

    @Transactional
    public View replace(SessionPrincipal actor, Replace input) {
        CartRules.quantity(input.quantity());
        var now = clock.instant();
        store.lockCustomer(actor.id());
        var old = store.lockActive(actor.id()).orElseThrow(CartException::missing);
        CartRules.cart(old.id(), input.cartId(), old.version(), input.cartVersion());
        if (old.branch().equals(input.branchId()))
            throw CartException.invalid("Use the item endpoint for the current branch");
        var effective = requireAddable(input.branchId(), input.menuItemId());
        try {
            store.retire(old, "REPLACED", now);
            UUID replacement =
                    store.createCart(actor.id(), effective.branch(), effective.restaurant(), now);
            store.addOrIncrement(
                    replacement,
                    effective.item(),
                    effective.restaurant(),
                    input.quantity(),
                    effective.price(),
                    now);
        } catch (DataIntegrityViolationException exception) {
            throw CartException.conflict("Cart changed concurrently; reload and retry");
        }
        return readRequired(actor.id());
    }

    private CartQuery.Effective requireAddable(UUID branch, UUID item) {
        var effective = query.effective(branch, item);
        if (!"ACTIVE".equals(effective.restaurantStatus())
                || !"ACTIVE".equals(effective.branchStatus()))
            throw CartException.conflict("Restaurant or branch is not accepting new cart items");
        if (!effective.available())
            throw CartException.conflict("Menu item is not currently available");
        return effective;
    }

    private View readRequired(UUID customer) {
        return query.active(customer, clock.instant()).orElseThrow(CartException::missing);
    }
}
