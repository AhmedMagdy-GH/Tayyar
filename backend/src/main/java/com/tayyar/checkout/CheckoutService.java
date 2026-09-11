package com.tayyar.checkout;

import com.tayyar.auth.SessionPrincipal;
import com.tayyar.cart.*;
import com.tayyar.order.*;
import com.tayyar.payment.*;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.math.BigDecimal;
import java.time.Clock;

@Service
@PreAuthorize("hasRole('CUSTOMER')")
public class CheckoutService {
    private final CartStore carts;
    private final CartQuery query;
    private final CartValidationLocks locks;
    private final CheckoutStore store;
    private final OrderService orders;
    private final PaymentService payments;
    private final Clock clock;

    public CheckoutService(
            CartStore carts,
            CartQuery query,
            CartValidationLocks locks,
            CheckoutStore store,
            OrderService orders,
            PaymentService payments,
            Clock clock) {
        this.carts = carts;
        this.query = query;
        this.locks = locks;
        this.store = store;
        this.orders = orders;
        this.payments = payments;
        this.clock = clock;
    }

    public static void validateKey(String key) {
        if (key == null || !key.matches("[A-Za-z0-9_-]{16,128}"))
            throw new CheckoutException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_IDEMPOTENCY_KEY",
                    "Idempotency-Key must contain 16–128 ASCII letters, digits, underscores or"
                            + " hyphens");
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CheckoutDtos.Summary checkout(
            SessionPrincipal actor, String key, CheckoutDtos.Request input) {
        validateKey(key);
        if (input == null
                || input.cartId() == null
                || input.cartVersion() == null
                || input.cartVersion() < 0
                || input.savedAddressId() == null
                || input.paymentMethod() == null)
            throw new CheckoutException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_REQUEST",
                    "Checkout selections and current cart version are required");
        carts.lockCustomer(actor.id());
        store.requireActiveCustomer(actor.id());
        var previous = store.receipt(actor.id(), key);
        if (previous.isPresent()) {
            var receipt = previous.get();
            if (!receipt.cart().equals(input.cartId())
                    || receipt.version() != input.cartVersion()
                    || !receipt.address().equals(input.savedAddressId())
                    || !receipt.method().equals(input.paymentMethod().name()))
                throw CheckoutException.conflict(
                        "IDEMPOTENCY_MISMATCH",
                        "Idempotency key was already used for different checkout selections");
            return receipt.summary();
        }
        if (input.paymentMethod() != PaymentMethod.CASH)
            throw CheckoutException.conflict(
                    "PAYMENT_METHOD_UNAVAILABLE", "CARD checkout is not available; select CASH");
        store.checkCartOwner(actor.id(), input.cartId());
        var cart =
                carts.lockActive(actor.id())
                        .orElseThrow(
                                () ->
                                        CheckoutException.conflict(
                                                "STALE_CART",
                                                "Active cart no longer exists; reload and retry"));
        if (!cart.id().equals(input.cartId()) || cart.version() != input.cartVersion())
            throw CheckoutException.conflict("STALE_CART", "Cart changed; reload and retry");
        locks.lock(cart);
        var view =
                query.active(actor.id(), clock.instant()).orElseThrow(CheckoutException::missing);
        if (view.items().isEmpty())
            throw CheckoutException.conflict("EMPTY_CART", "Add an item before checkout");
        if (!view.branch().openNow())
            throw CheckoutException.conflict(
                    "BRANCH_NOT_ACCEPTING", "Branch is not accepting orders now");
        if (view.items().stream().anyMatch(line -> !line.currentlyAvailable()))
            throw CheckoutException.conflict(
                    "ITEM_UNAVAILABLE", "Cart contains unavailable items; review your cart");
        if (view.items().stream().anyMatch(CartDtos.Line::priceChanged))
            throw CheckoutException.conflict(
                    "PRICE_RECONFIRMATION_REQUIRED",
                    "Prices changed; review your cart and explicitly reconfirm prices");
        var delivery = store.delivery(actor.id(), input.savedAddressId(), cart.branch());
        BigDecimal subtotal;
        try {
            subtotal = MoneyRules.amount(view.merchandiseSubtotal(), "Merchandise subtotal");
            MoneyRules.amount(subtotal.add(delivery.fee()), "Final total");
        } catch (OrderException ex) {
            throw CheckoutException.conflict(
                    "TOTAL_OUT_OF_RANGE", "Cart total exceeds supported amount; reduce quantities");
        }
        if (subtotal.compareTo(delivery.minimum()) < 0)
            throw CheckoutException.conflict(
                    "MINIMUM_ORDER_NOT_MET",
                    "Merchandise subtotal is below the delivery minimum; add items");
        var actorIdentity = TransitionActor.user(actor);
        var order =
                orders.create(
                                new OrderDtos.Draft(
                                        actor.id(),
                                        cart.restaurant(),
                                        cart.branch(),
                                        OrderStatus.PLACED,
                                        view.items().stream()
                                                .map(
                                                        line ->
                                                                new OrderDtos.PurchaseItem(
                                                                        line.menuItemId(),
                                                                        line.name(),
                                                                        line.currentUnitPrice(),
                                                                        line.quantity()))
                                                .toList(),
                                        delivery.address(),
                                        delivery.fee(),
                                        BigDecimal.ZERO.setScale(2)),
                                actorIdentity)
                        .order();
        var payment =
                payments.create(
                        new PaymentDtos.Create(order.id(), PaymentMethod.CASH, null, null),
                        actorIdentity);
        store.consume(cart, clock.instant());
        store.complete(actor.id(), key, input, order.id(), payment.id(), order.createdAt());
        var money = order.money();
        return new CheckoutDtos.Summary(
                order.id(),
                order.status().name(),
                payment.method().name(),
                payment.status().name(),
                money.merchandiseSubtotal(),
                money.deliveryFee(),
                money.discountTotal(),
                money.finalTotal(),
                money.currency(),
                order.createdAt());
    }
}
