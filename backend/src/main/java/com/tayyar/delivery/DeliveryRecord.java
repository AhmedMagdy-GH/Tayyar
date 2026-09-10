package com.tayyar.delivery;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@MappedSuperclass
abstract class DeliveryRecord {
    @Id UUID id;
    @Version long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    Instant updatedAt;

    protected DeliveryRecord() {}

    DeliveryRecord(Instant now) {
        id = UUID.randomUUID();
        createdAt = now;
        updatedAt = now;
    }

    void touch(long expected, Instant now) {
        if (version != expected) throw DeliveryException.conflict();
        updatedAt = now.isAfter(updatedAt) ? now : updatedAt.plusNanos(1000);
    }
}
