package com.tayyar.branch;

import static com.tayyar.branch.BranchDtos.*;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "branches")
public class Branch {
    @Id private UUID id;

    @Column(name = "restaurant_id", nullable = false, updatable = false)
    private UUID restaurantId;

    @Column(name = "name", length = 120)
    private String name;

    @Column(name = "address_line1", length = 200)
    private String addressLine1;

    @Column(name = "address_line2", length = 200)
    private String addressLine2;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "region", length = 100)
    private String region;

    @Column(name = "postal_code", length = 20)
    private String postalCode;

    @Column(name = "country_code", length = 2)
    private String countryCode;

    @Column(name = "phone", length = 16)
    private String phone;

    @Column(name = "latitude", precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 9, scale = 6)
    private BigDecimal longitude;

    @Column(name = "timezone", length = 100)
    private String timezone;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_model", length = 24)
    private DeliveryModel deliveryModel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private BranchStatus status;

    @Column(nullable = false)
    private boolean paused;

    @jakarta.persistence.Version private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Branch() {}

    public Branch(UUID restaurant, Profile input, Instant now) {
        id = UUID.randomUUID();
        restaurantId = restaurant;
        status = BranchStatus.ACTIVE;
        createdAt = now;
        updatedAt = now;
        profile(input);
    }

    public void profile(Profile input) {
        name = input.name();
        addressLine1 = input.addressLine1();
        addressLine2 = input.addressLine2();
        city = input.city();
        region = input.region();
        postalCode = input.postalCode();
        countryCode = input.countryCode();
        phone = input.phone();
        latitude = input.latitude();
        longitude = input.longitude();
        timezone = input.timezone();
        deliveryModel = input.deliveryModel();
        name = name.strip();
        addressLine1 = addressLine1.strip();
        city = city.strip();
    }

    public void touch(long expected, Instant now) {
        if (version != expected) throw BranchException.conflict();
        // Ensure even an identical replacement advances the aggregate version.
        updatedAt = now.isAfter(updatedAt) ? now : updatedAt.plusNanos(1000);
    }

    public void operation(Operation input, Instant now) {
        touch(input.version(), now);
        status = input.status();
        paused = input.paused();
    }

    public UUID id() {
        return id;
    }

    public UUID restaurantId() {
        return restaurantId;
    }

    public long version() {
        return version;
    }

    public BranchStatus status() {
        return status;
    }

    public boolean paused() {
        return paused;
    }

    public String timezone() {
        return timezone;
    }

    public View view() {
        return new View(
                id,
                restaurantId,
                new Profile(
                        name,
                        addressLine1,
                        addressLine2,
                        city,
                        region,
                        postalCode,
                        countryCode,
                        phone,
                        latitude,
                        longitude,
                        timezone,
                        deliveryModel),
                status,
                paused,
                version,
                createdAt,
                updatedAt);
    }
}
