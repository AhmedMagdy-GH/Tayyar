package com.tayyar.delivery;

import static com.tayyar.delivery.DeliveryDtos.*;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "delivery_zones")
public class DeliveryZone extends DeliveryRecord {
    @Column(name = "city_id", nullable = false, updatable = false)
    UUID cityId;

    @Column(nullable = false, length = 100)
    String name;

    @Column(nullable = false)
    boolean active;

    protected DeliveryZone() {}

    DeliveryZone(ZoneInput input, Instant now) {
        super(now);
        cityId = input.cityId();
        edit(input.profile());
    }

    void edit(GeographyInput input) {
        name = DeliveryRules.name(input.name());
        active = input.active();
    }

    GeographyView view() {
        return new GeographyView(id, cityId, name, active, version, createdAt, updatedAt);
    }
}
