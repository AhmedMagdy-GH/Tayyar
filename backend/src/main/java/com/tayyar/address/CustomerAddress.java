package com.tayyar.address;

import static com.tayyar.address.AddressDtos.*;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "customer_addresses")
public class CustomerAddress {
    @Id private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "label", length = 80)
    private String label;

    @Column(name = "street", length = 200)
    private String street;

    @Column(name = "building", length = 80)
    private String building;

    @Column(name = "floor", length = 40)
    private String floor;

    @Column(name = "apartment", length = 40)
    private String apartment;

    @Column(name = "landmark", length = 200)
    private String landmark;

    @Column(name = "instructions", length = 1000)
    private String instructions;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "region", length = 100)
    private String region;

    @Column(name = "postal_code", length = 20)
    private String postalCode;

    @Column(name = "country_code", length = 2)
    private String countryCode;

    @Column(name = "latitude", precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 9, scale = 6)
    private BigDecimal longitude;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    @jakarta.persistence.Version private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CustomerAddress() {}

    public CustomerAddress(UUID user, Profile input, boolean first, Instant now) {
        id = UUID.randomUUID();
        userId = user;
        isDefault = first;
        createdAt = now;
        updatedAt = now;
        profile(input);
    }

    public void profile(Profile input) {
        if ((input.latitude() == null) != (input.longitude() == null))
            throw AddressException.invalid("Coordinates must be supplied together");
        label = input.label();
        street = input.street();
        building = input.building();
        floor = input.floor();
        apartment = input.apartment();
        landmark = input.landmark();
        instructions = input.instructions();
        city = input.city();
        region = input.region();
        postalCode = input.postalCode();
        countryCode = input.countryCode();
        latitude = input.latitude();
        longitude = input.longitude();
        label = label.strip();
        street = street.strip();
        building = building.strip();
        city = city.strip();
    }

    public void checkVersion(long expected) {
        if (version != expected) throw AddressException.conflict();
    }

    public void touch(long expected, Instant now) {
        checkVersion(expected);
        updatedAt = now.isAfter(updatedAt) ? now : updatedAt.plusNanos(1000);
    }

    public void selectDefault() {
        isDefault = true;
    }

    public UUID id() {
        return id;
    }

    public View view() {
        return new View(
                id,
                new Profile(
                        label,
                        street,
                        building,
                        floor,
                        apartment,
                        landmark,
                        instructions,
                        city,
                        region,
                        postalCode,
                        countryCode,
                        latitude,
                        longitude),
                isDefault,
                version,
                createdAt,
                updatedAt);
    }
}
