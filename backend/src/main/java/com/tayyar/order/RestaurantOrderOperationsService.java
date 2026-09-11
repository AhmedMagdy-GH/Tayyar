package com.tayyar.order;

import static com.tayyar.order.OrderOperationsDtos.*;

import com.tayyar.auth.SessionPrincipal;
import com.tayyar.notification.NotificationService;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@PreAuthorize("hasAnyRole('RESTAURANT_OWNER','RESTAURANT_STAFF')")
@Transactional(readOnly = true)
public class RestaurantOrderOperationsService {
    private final OrderOperationsAccess access;
    private final OrderOperationsQuery query;
    private final OrderService orders;
    private final NotificationService notifications;

    public RestaurantOrderOperationsService(
            OrderOperationsAccess access, OrderOperationsQuery query, OrderService orders,
            NotificationService notifications) {
        this.access = access;
        this.query = query;
        this.orders = orders;
        this.notifications = notifications;
    }

    public Page<Summary> queue(
            SessionPrincipal actor,
            UUID restaurant,
            UUID branch,
            OrderStatus status,
            Window window) {
        boolean owner = access.restaurantScope(actor, restaurant, branch);
        return query.queue(actor.id(), owner, restaurant, branch, status, window);
    }

    public Details details(SessionPrincipal actor, UUID order) {
        access.order(actor, order, false);
        return query.details(order);
    }

    @Transactional
    public Summary accept(SessionPrincipal actor, UUID order, Version input) {
        return transition(
                actor, order, input.version(), OrderStatus.PLACED, OrderStatus.ACCEPTED, null);
    }

    @Transactional
    public Summary reject(SessionPrincipal actor, UUID order, Reason input) {
        return transition(
                actor,
                order,
                input.version(),
                OrderStatus.PLACED,
                OrderStatus.REJECTED,
                input.reason());
    }

    @Transactional
    public Summary startPreparation(SessionPrincipal actor, UUID order, Version input) {
        return transition(
                actor, order, input.version(), OrderStatus.ACCEPTED, OrderStatus.PREPARING, null);
    }

    @Transactional
    public Summary ready(SessionPrincipal actor, UUID order, Version input) {
        return transition(
                actor,
                order,
                input.version(),
                OrderStatus.PREPARING,
                OrderStatus.READY_FOR_PICKUP,
                null);
    }

    private Summary transition(
            SessionPrincipal actor,
            UUID order,
            long version,
            OrderStatus source,
            OrderStatus target,
            String reason) {
        access.order(actor, order, true);
        if (query.status(order) != source)
            throw OrderOperationsException.conflict("Order transition is not allowed");
        try {
            orders.transition(order, version, target, TransitionActor.user(actor), reason);
            notifications.orderTransition(order, target);
        } catch (OrderException exception) {
            throw OrderOperationsException.conflict("Order changed or transition is not allowed");
        }
        return query.summary(order);
    }
}
