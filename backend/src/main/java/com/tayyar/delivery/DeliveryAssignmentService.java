package com.tayyar.delivery;

import static com.tayyar.delivery.DriverOperationsDtos.*;

import com.tayyar.auth.SessionPrincipal;
import com.tayyar.order.OrderStatus;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;

@Service
public class DeliveryAssignmentService {
    private final DriverOperationsStore store;
    private final Clock clock;

    public DeliveryAssignmentService(DriverOperationsStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public Assignment assign(SessionPrincipal actor, AssignmentRequest input) {
        var order = store.lockOrder(input.orderId());
        if (order.version() != input.orderVersion())
            throw DriverOperationsException.conflict("Order changed; reload and retry");
        if (order.status() != OrderStatus.READY_FOR_PICKUP)
            throw DriverOperationsException.conflict("Order is not ready for Driver assignment");
        if (!"TAYYAR_DELIVERY".equals(store.lockDeliveryModel(order.branchId())))
            throw DriverOperationsException.conflict(
                    "Platform Drivers cannot be assigned to this delivery model");
        if (store.findActiveAssignment(order.id(), true).isPresent())
            throw DriverOperationsException.conflict("Order already has an active assignment");
        var driver = store.lockDriver(input.driverId());
        if (driver.state() != DriverState.AVAILABLE)
            throw DriverOperationsException.conflict("Driver is not available");
        Instant now = clock.instant();
        Assignment assignment =
                store.createAssignment(order.id(), input.driverId(), actor.id(), now);
        store.changeDriverState(
                driver, DriverState.BUSY, actor.id(), "Active delivery assigned", now);
        return assignment;
    }
}
