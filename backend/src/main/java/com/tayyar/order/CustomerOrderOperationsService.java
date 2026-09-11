package com.tayyar.order;

import static com.tayyar.order.OrderOperationsDtos.*;

import com.tayyar.auth.SessionPrincipal;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@PreAuthorize("hasRole('CUSTOMER')")
@Transactional(readOnly = true)
public class CustomerOrderOperationsService {
    private final OrderOperationsAccess access;
    private final OrderOperationsQuery query;
    private final OrderService orders;

    public CustomerOrderOperationsService(
            OrderOperationsAccess access, OrderOperationsQuery query, OrderService orders) {
        this.access = access;
        this.query = query;
        this.orders = orders;
    }

    public Page<Summary> list(SessionPrincipal actor, OrderStatus status, Window window) {
        return query.customer(actor.id(), status, window);
    }

    public Details details(SessionPrincipal actor, UUID order) {
        access.customer(actor.id(), order);
        return query.details(order);
    }

    @Transactional
    public Summary cancel(SessionPrincipal actor, UUID order, Reason input) {
        access.customer(actor.id(), order);
        requireSource(order, OrderStatus.PLACED);
        transition(
                order,
                input.version(),
                OrderStatus.CANCELLED,
                TransitionActor.user(actor),
                input.reason());
        return query.summary(order);
    }

    private void requireSource(UUID order, OrderStatus required) {
        if (query.status(order) != required)
            throw OrderOperationsException.conflict("Order transition is not allowed");
    }

    private void transition(
            UUID order, long version, OrderStatus target, TransitionActor actor, String reason) {
        try {
            orders.transition(order, version, target, actor, reason);
        } catch (OrderException exception) {
            throw OrderOperationsException.conflict("Order changed or transition is not allowed");
        }
    }
}
