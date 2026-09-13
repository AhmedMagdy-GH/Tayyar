package com.tayyar.restaurant;

import com.tayyar.branch.BranchStatus;
import com.tayyar.auth.EmailAddress;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class RestaurantOperationsDtos {
    private RestaurantOperationsDtos() {}

    public record BranchContext(
            UUID branchId,
            String branchName,
            BranchStatus branchStatus,
            String operationalState) {}

    public record RestaurantContext(
            UUID restaurantId,
            String restaurantName,
            RestaurantStatus restaurantStatus,
            String role,
            List<BranchContext> branches) {}

    public record CreateStaff(@NotBlank @Email @Size(max = 254) String email) {
        public CreateStaff {
            if (email != null) email = EmailAddress.normalize(email);
        }
    }

    public record StaffBranch(UUID branchId, String branchName, BranchStatus branchStatus) {}

    public record StaffView(
            UUID userId,
            String fullName,
            String email,
            Instant createdAt,
            List<StaffBranch> branches) {}

    public record StaffPage(List<StaffView> items, int page, int size, long total) {}
}
