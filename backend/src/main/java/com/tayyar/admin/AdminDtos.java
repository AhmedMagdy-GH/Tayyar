package com.tayyar.admin;

import com.tayyar.delivery.DriverState;
import com.tayyar.order.OrderStatus;
import com.tayyar.payment.PaymentStatus;
import com.tayyar.restaurant.RestaurantStatus;
import com.tayyar.user.AccountStatus;
import com.tayyar.user.Role;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class AdminDtos {
    private AdminDtos() {}

    public record Page<T>(List<T> items, int page, int size, long total) {
        public Page { items = List.copyOf(items); }
    }

    public record Window(int page, int size) {
        public Window {
            if (page < 0 || page > 10000 || size < 1 || size > 100)
                throw AdminException.invalid("INVALID_PAGE", "Page must be 0–10000 and size 1–100");
        }
        int offset() { return Math.multiplyExact(page, size); }
    }

    public record AccountCommand(@NotBlank @Size(max = 1000) String reason) {}

    public record UserSummary(
            UUID id, String fullName, String email, AccountStatus status, Set<Role> roles,
            Instant createdAt, Instant updatedAt) {}

    public record BranchSummary(UUID id, String name, String status, boolean paused, String city) {}

    public record RestaurantOverview(
            UUID id, String name, String description, RestaurantStatus status, long version,
            Instant createdAt, Instant updatedAt, List<BranchSummary> branches) {}

    public record PaymentSummary(String method, PaymentStatus status, BigDecimal amount, String currency) {}
    public record AssignmentSummary(UUID assignmentId, UUID driverId, String status, Instant assignedAt) {}
    public record OrderSummary(
            UUID id, UUID customerId, UUID restaurantId, String restaurantName, UUID branchId,
            String branchName, OrderStatus status, BigDecimal finalTotal, String currency,
            PaymentSummary payment, AssignmentSummary assignment, Instant createdAt) {}
    public record PurchasedItem(String name, BigDecimal unitPrice, int quantity, BigDecimal lineSubtotal) {}
    public record AddressSnapshot(
            String label, String street, String building, String floor, String apartment,
            String landmark, String instructions, String city, String region, String postalCode,
            String countryCode) {}
    public record StatusHistory(OrderStatus previousStatus, OrderStatus newStatus, String actorKind,
                                UUID actorId, String reason, Instant occurredAt) {}
    public record OrderDetails(OrderSummary order, List<PurchasedItem> items,
                               AddressSnapshot deliveryAddress, List<StatusHistory> history) {}

    public record DriverSummary(
            UUID driverId, AccountStatus accountStatus, DriverState state, long version,
            AssignmentSummary activeAssignment, Instant createdAt, Instant updatedAt) {}

    public enum AdminAction { ACCOUNT_SUSPENDED, ACCOUNT_REACTIVATED }
    public enum AdminTarget { USER }
    public record AuditRecord(
            UUID id, UUID actorId, AdminAction actionType, AdminTarget targetEntityType,
            UUID targetEntityId, String reason, String beforeState, String afterState,
            Instant occurredAt) {}
}
