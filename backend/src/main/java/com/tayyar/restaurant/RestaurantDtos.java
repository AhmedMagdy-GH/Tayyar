package com.tayyar.restaurant;

import jakarta.validation.constraints.*;

import java.time.Instant;
import java.util.*;

public final class RestaurantDtos {
    private RestaurantDtos() {}

    public record Apply(
            @NotBlank @Size(max = 120) String name,
            @NotNull @Size(max = 2000) String description) {}

    public record Resubmit(
            @NotBlank @Size(max = 120) String name,
            @NotNull @Size(max = 2000) String description,
            @NotNull @PositiveOrZero Long version) {}

    public record Review(
            @NotNull ReviewOutcome outcome,
            @Size(max = 1000) String reason,
            @NotNull @PositiveOrZero Long version) {}

    public record Edit(
            @NotBlank @Size(max = 120) String name,
            @NotNull @Size(max = 2000) String description,
            @NotNull @PositiveOrZero Long version) {}

    public record StatusChange(
            @NotNull RestaurantStatus status,
            @NotBlank @Size(max = 1000) String reason,
            @NotNull @PositiveOrZero Long version) {}

    public record ApplicationView(
            UUID id,
            UUID applicantId,
            ApplicationStatus status,
            int revision,
            long version,
            String name,
            String description,
            UUID restaurantId,
            Instant createdAt,
            Instant updatedAt) {}

    public record Submission(int revision, String name, String description, Instant submittedAt) {}

    public record Decision(
            int revision,
            UUID reviewerId,
            ReviewOutcome outcome,
            String reason,
            UUID restaurantId,
            Instant decidedAt) {}

    public record Membership(UUID userId, String membershipType, Instant createdAt) {}

    public record StatusHistory(
            UUID id,
            UUID actorId,
            RestaurantStatus previousStatus,
            RestaurantStatus newStatus,
            String reason,
            Instant changedAt) {}

    public record RestaurantView(
            UUID id,
            String name,
            String description,
            RestaurantStatus status,
            long version,
            Instant createdAt,
            Instant updatedAt) {
        public static RestaurantView from(Restaurant r) {
            return new RestaurantView(
                    r.getId(),
                    r.getName(),
                    r.getDescription(),
                    r.getStatus(),
                    r.getVersion(),
                    r.getCreatedAt(),
                    r.getUpdatedAt());
        }
    }

    public record Page<T>(List<T> items, int page, int size, long total) {}

    public record Window(int page, int size) {
        public Window {
            if (page < 0 || page > 10000 || size < 1 || size > 100)
                throw new RestaurantException(
                        org.springframework.http.HttpStatus.BAD_REQUEST,
                        "INVALID_PAGE",
                        "Page must be 0–10000 and size 1–100");
        }

        public int offset() {
            return page * size;
        }
    }
}
