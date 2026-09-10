package com.tayyar.branch;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public final class BranchDtos {
    private BranchDtos() {}

    public record Profile(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Size(max = 200) String addressLine1,
            @Size(max = 200) String addressLine2,
            @NotBlank @Size(max = 100) String city,
            @Size(max = 100) String region,
            @Size(max = 20) String postalCode,
            @NotNull @Pattern(regexp = "[A-Z]{2}") String countryCode,
            @Pattern(regexp = "\\+[1-9][0-9]{7,14}") String phone,
            @DecimalMin("-90") @DecimalMax("90") @Digits(integer = 3, fraction = 6)
                    BigDecimal latitude,
            @DecimalMin("-180") @DecimalMax("180") @Digits(integer = 3, fraction = 6)
                    BigDecimal longitude,
            @NotBlank @Size(max = 100) String timezone,
            @NotNull DeliveryModel deliveryModel) {}

    public record Edit(@NotNull @Valid Profile profile, @NotNull @PositiveOrZero Long version) {}

    public record Operation(
            @NotNull BranchStatus status,
            @NotNull Boolean paused,
            @NotNull @PositiveOrZero Long version) {}

    public record Weekly(
            @Min(1) @Max(7) int weekday, @NotNull LocalTime opensAt, @NotNull LocalTime closesAt) {}

    public record Special(@NotNull LocalDate date, LocalTime opensAt, LocalTime closesAt) {}

    public record Hours(
            @NotNull @Size(max = 7) List<@NotNull @Valid Weekly> weekly,
            @NotNull @Size(max = 366) List<@NotNull @Valid Special> special,
            @NotNull @PositiveOrZero Long version) {}

    public record Version(@NotNull @PositiveOrZero Long version) {}

    public record View(
            UUID id,
            UUID restaurantId,
            Profile profile,
            BranchStatus status,
            boolean paused,
            long version,
            Instant createdAt,
            Instant updatedAt) {}

    public record Schedule(List<Weekly> weekly, List<Special> special, long version) {}

    public record Availability(
            String state, boolean scheduleOpen, Instant evaluatedAt, String timezone) {}

    public record Staff(UUID userId, UUID assignedBy, Instant createdAt) {}

    public record Page<T>(List<T> items, int page, int size, long total) {}

    public record Window(int page, int size) {
        public Window {
            if (page < 0 || page > 10000 || size < 1 || size > 100)
                throw BranchException.invalid("Page must be 0–10000 and size 1–100");
        }

        public int offset() {
            return page * size;
        }
    }
}
