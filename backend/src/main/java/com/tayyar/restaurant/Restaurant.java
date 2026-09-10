package com.tayyar.restaurant;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "restaurants")
public class Restaurant {
    @Id private UUID id;

    @Column(name = "application_id", nullable = false, unique = true)
    private UUID applicationId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RestaurantStatus status;

    @Version private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Restaurant() {}

    public Restaurant(UUID applicationId, String name, String description, Instant now) {
        id = UUID.randomUUID();
        this.applicationId = applicationId;
        this.name = name.strip();
        this.description = description;
        status = RestaurantStatus.ACTIVE;
        createdAt = now;
        updatedAt = now;
    }

    public void edit(String name, String description, long expectedVersion, Instant now) {
        checkVersion(expectedVersion);
        this.name = name.strip();
        this.description = description;
        updatedAt = now;
    }

    public void changeStatus(RestaurantStatus next, long expectedVersion, Instant now) {
        checkVersion(expectedVersion);
        if (status == next) throw RestaurantException.conflict();
        status = next;
        updatedAt = now;
    }

    private void checkVersion(long expected) {
        if (version != expected) throw RestaurantException.conflict();
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public RestaurantStatus getStatus() {
        return status;
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
