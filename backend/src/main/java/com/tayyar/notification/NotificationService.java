package com.tayyar.notification;

import static com.tayyar.notification.NotificationDtos.*;

import com.tayyar.auth.SessionPrincipal;
import com.tayyar.order.OrderStatus;
import com.tayyar.restaurant.ReviewOutcome;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.time.Clock;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class NotificationService {
    private final NotificationStore store;
    private final Clock clock;

    public NotificationService(NotificationStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    @PreAuthorize("isAuthenticated()")
    public Page list(SessionPrincipal actor, Boolean read, Window window) {
        return store.list(actor.id(), read, window);
    }

    @PreAuthorize("isAuthenticated()")
    public View details(SessionPrincipal actor, UUID id) {
        return store.owned(actor.id(), id);
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional
    public View markRead(SessionPrincipal actor, UUID id) {
        return store.markRead(actor.id(), id, clock.instant());
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional
    public MarkAllResult markAllRead(SessionPrincipal actor) {
        return new MarkAllResult(store.markAllRead(actor.id(), clock.instant()));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void orderTransition(UUID orderId, OrderStatus status) {
        NotificationType type;
        String title;
        String action;
        switch (status) {
            case ACCEPTED -> { type = NotificationType.ORDER_ACCEPTED; title = "Order accepted"; action = "accepted"; }
            case REJECTED -> { type = NotificationType.ORDER_REJECTED; title = "Order rejected"; action = "rejected"; }
            case PREPARING -> { type = NotificationType.ORDER_PREPARING; title = "Order is being prepared"; action = "being prepared"; }
            case READY_FOR_PICKUP -> { type = NotificationType.ORDER_READY_FOR_PICKUP; title = "Order is ready"; action = "ready for pickup"; }
            case OUT_FOR_DELIVERY -> { type = NotificationType.ORDER_OUT_FOR_DELIVERY; title = "Order is on the way"; action = "out for delivery"; }
            case DELIVERED -> { type = NotificationType.ORDER_DELIVERED; title = "Order delivered"; action = "delivered"; }
            default -> throw new IllegalArgumentException("Unsupported notification transition");
        }
        var context = store.orderContext(orderId);
        store.insert(context.customerId(), type, title,
                "Your order from " + context.restaurantName() + " is " + action + ".",
                RelatedEntityType.ORDER, orderId,
                "order:" + orderId + ":" + status + ":" + context.customerId(), clock.instant());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void deliveryAssigned(UUID assignmentId, UUID driverId, UUID orderId) {
        var context = store.orderContext(orderId);
        store.insert(driverId, NotificationType.DELIVERY_ASSIGNED, "New delivery assignment",
                "You have a new delivery assignment for " + context.restaurantName() + ".",
                RelatedEntityType.DELIVERY_ASSIGNMENT, assignmentId,
                "assignment:" + assignmentId + ":" + driverId, clock.instant());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void applicationDecision(UUID applicationId, int revision, UUID applicant, ReviewOutcome outcome) {
        NotificationType type = outcome == ReviewOutcome.APPROVED
                ? NotificationType.RESTAURANT_APPLICATION_APPROVED
                : NotificationType.RESTAURANT_APPLICATION_REJECTED;
        String approved = outcome == ReviewOutcome.APPROVED ? "approved" : "rejected";
        store.insert(applicant, type, "Restaurant application " + approved,
                "Your restaurant application was " + approved + ".",
                RelatedEntityType.RESTAURANT_APPLICATION, applicationId,
                "application:" + applicationId + ":revision:" + revision + ":" + outcome + ":" + applicant,
                clock.instant());
    }
}
