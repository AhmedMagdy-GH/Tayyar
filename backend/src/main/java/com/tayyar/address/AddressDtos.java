package com.tayyar.address;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

public final class AddressDtos {
    private AddressDtos() {}

    public record Profile(
            @NotBlank @Size(max = 80) String label,
            @NotBlank @Size(max = 200) String street,
            @NotBlank @Size(max = 80) String building,
            @Size(max = 40) String floor,
            @Size(max = 40) String apartment,
            @Size(max = 200) String landmark,
            @Size(max = 1000) String instructions,
            @NotBlank @Size(max = 100) String city,
            @Size(max = 100) String region,
            @Size(max = 20) String postalCode,
            @NotNull @Pattern(regexp = "[A-Z]{2}") String countryCode,
            @DecimalMin("-90") @DecimalMax("90") @Digits(integer = 3, fraction = 6)
                    BigDecimal latitude,
            @DecimalMin("-180") @DecimalMax("180") @Digits(integer = 3, fraction = 6)
                    BigDecimal longitude) {}

    public record Edit(@NotNull @Valid Profile profile, @NotNull @PositiveOrZero Long version) {}

    public record VersionInput(@NotNull @PositiveOrZero Long version) {}

    public record View(
            UUID id,
            Profile profile,
            boolean isDefault,
            long version,
            Instant createdAt,
            Instant updatedAt) {}

    public record Page(List<View> items, int page, int size, long total) {}

    public record Window(int page, int size) {
        public Window {
            if (page < 0 || page > 10000 || size < 1 || size > 100)
                throw AddressException.invalid("Page must be 0–10000 and size 1–100");
        }
    }
}
