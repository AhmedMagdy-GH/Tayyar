package com.tayyar.delivery;

import static com.tayyar.delivery.DriverOperationsDtos.*;

import com.tayyar.auth.SessionPrincipal;
import com.tayyar.order.*;
import com.tayyar.payment.*;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.time.*;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class DriverOrderService {
    private final DriverOperationsStore store;
    private final DriverOperationsQuery query;
    private final OrderService orders;
    private final PaymentService payments;
    private final Clock clock;

    public DriverOrderService(
            DriverOperationsStore store,
            DriverOperationsQuery query,
            OrderService orders,
            PaymentService payments,
            Clock clock) {
        this.store = store;
        this.query = query;
        this.orders = orders;
        this.payments = payments;
        this.clock = clock;
    }

    @PreAuthorize("hasRole('DRIVER')")
    public Page<DriverOrder> queue(SessionPrincipal actor, Window window) {
        store.profile(actor.id());
        return query.queue(actor.id(), window);
    }

    @PreAuthorize("hasRole('DRIVER')")
    public DriverOrder details(SessionPrincipal actor, UUID order) {
        store.profile(actor.id());
        return query.details(actor.id(), order);
    }

    @PreAuthorize("hasRole('DRIVER')")
    @Transactional
    public DriverOrder pickup(SessionPrincipal actor, UUID orderId, Transition input) {
        var locked = authorized(actor, orderId, input, OrderStatus.READY_FOR_PICKUP);
        transitionOrder(
                orderId,
                input.orderVersion(),
                OrderStatus.OUT_FOR_DELIVERY,
                actor,
                "Order collected by assigned Driver");
        return query.details(actor.id(), locked.orderId());
    }

    @PreAuthorize("hasRole('DRIVER')")
    @Transactional
    public Completion deliver(SessionPrincipal actor, UUID orderId, Transition input) {
        var assignment = authorized(actor, orderId, input, OrderStatus.OUT_FOR_DELIVERY);
        var payment = store.lockPayment(orderId);
        if (payment.method() == PaymentMethod.CASH && payment.status() != PaymentStatus.PENDING)
            throw DriverOperationsException.conflict("Cash Payment is not awaiting collection");
        if (payment.method() == PaymentMethod.CARD && payment.status() != PaymentStatus.PAID)
            throw DriverOperationsException.conflict(
                    "Card Payment must be completed by an approved provider workflow");

        var order =
                transitionOrder(
                        orderId,
                        input.orderVersion(),
                        OrderStatus.DELIVERED,
                        actor,
                        "Delivery completed by assigned Driver");
        PaymentStatus paymentStatus = payment.status();
        if (payment.method() == PaymentMethod.CASH) {
            try {
                paymentStatus =
                        payments
                                .transition(
                                        payment.id(),
                                        payment.version(),
                                        PaymentStatus.PAID,
                                        TransitionActor.user(actor),
                                        "Cash collected on delivery")
                                .status();
            } catch (PaymentException exception) {
                throw DriverOperationsException.conflict(
                        "Payment changed; delivery was not completed");
            }
        }

        Instant now = clock.instant();
        store.completeAssignment(assignment, actor.id(), now);
        var driver = store.lockDriver(actor.id());
        if (driver.state() != DriverState.BUSY)
            throw DriverOperationsException.conflict("Driver is not busy with this delivery");
        store.changeDriverState(
                driver, DriverState.AVAILABLE, actor.id(), "Delivery completed", now);
        return new Completion(
                order.id(),
                order.status(),
                order.version(),
                assignment.id(),
                AssignmentStatus.COMPLETED,
                assignment.version() + 1,
                DriverState.AVAILABLE,
                driver.version() + 1,
                payment.method(),
                paymentStatus);
    }

    private DriverOperationsStore.AssignmentLock authorized(
            SessionPrincipal actor, UUID orderId, Transition input, OrderStatus expected) {
        var order = store.lockOrder(orderId);
        var assignment =
                store.findActiveAssignment(orderId, true)
                        .orElseThrow(
                                () -> {
                                    if (store.hasCompletedAssignment(orderId, actor.id()))
                                        return DriverOperationsException.conflict(
                                                "Delivery assignment is already completed");
                                    return DriverOperationsException.missing();
                                });
        if (!assignment.driverId().equals(actor.id())) throw DriverOperationsException.missing();
        if (order.version() != input.orderVersion()
                || assignment.version() != input.assignmentVersion())
            throw DriverOperationsException.conflict("Order or assignment changed; reload and retry");
        if (order.status() != expected)
            throw DriverOperationsException.conflict("Delivery transition is not allowed");
        var driver = store.lockDriver(actor.id());
        if (driver.state() != DriverState.BUSY)
            throw DriverOperationsException.conflict("Driver is not busy with this delivery");
        return assignment;
    }

    private com.tayyar.order.OrderDtos.View transitionOrder(
            UUID order,
            long version,
            OrderStatus target,
            SessionPrincipal actor,
            String reason) {
        try {
            return orders.transition(order, version, target, TransitionActor.user(actor), reason);
        } catch (OrderException exception) {
            throw DriverOperationsException.conflict("Order changed; reload and retry");
        }
    }
}
