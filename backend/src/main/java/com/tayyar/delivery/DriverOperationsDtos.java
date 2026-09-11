package com.tayyar.delivery;

import com.tayyar.order.OrderStatus;
import com.tayyar.payment.PaymentMethod;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

public final class DriverOperationsDtos {
    private DriverOperationsDtos() {}

    public record Profile(UUID driverId, DriverState state, long version, Instant updatedAt) {}

    public record Provision(@NotNull UUID userId) {}

    public record Version(@NotNull @PositiveOrZero Long version) {}

    public record AssignmentRequest(
            @NotNull UUID orderId,
            @NotNull UUID driverId,
            @NotNull @PositiveOrZero Long orderVersion) {}

    public record Transition(
            @NotNull @PositiveOrZero Long orderVersion,
            @NotNull @PositiveOrZero Long assignmentVersion) {}

    public record Assignment(
            UUID assignmentId,
            UUID orderId,
            UUID driverId,
            AssignmentStatus status,
            long version,
            Instant assignedAt,
            Instant completedAt) {}

    public record Pickup(
            UUID restaurantId,
            String restaurantName,
            UUID branchId,
            String branchName,
            String addressLine1,
            String city,
            String phone) {}

    public record Destination(
            String label,
            String street,
            String building,
            String floor,
            String apartment,
            String landmark,
            String instructions,
            String city,
            String region,
            String postalCode,
            String countryCode) {}

    public record DriverOrder(
            UUID orderId,
            OrderStatus status,
            long orderVersion,
            UUID assignmentId,
            long assignmentVersion,
            Pickup pickup,
            Destination destination,
            PaymentMethod paymentMethod,
            BigDecimal cashAmountToCollect,
            String currency,
            Instant assignedAt) {}

    public record Completion(
            UUID orderId,
            OrderStatus orderStatus,
            long orderVersion,
            UUID assignmentId,
            AssignmentStatus assignmentStatus,
            long assignmentVersion,
            DriverState driverState,
            long driverVersion,
            PaymentMethod paymentMethod,
            com.tayyar.payment.PaymentStatus paymentStatus) {}

    public record Page<T>(List<T> items, int page, int size, long total) {
        public Page {
            items = List.copyOf(items);
        }
    }

    public record Window(int page, int size) {
        public Window {
            if (page < 0 || page > 10000 || size < 1 || size > 100)
                throw DriverOperationsException.invalid("Pagination is out of range");
        }

        int offset() {
            return Math.multiplyExact(page, size);
        }
    }
}
