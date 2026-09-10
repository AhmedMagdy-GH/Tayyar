package com.tayyar.delivery;

import static com.tayyar.delivery.DeliveryDtos.*;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "cities")
public class City extends DeliveryRecord {
    @Column(nullable = false, length = 100)
    String name;

    @Column(nullable = false)
    boolean active;

    protected City() {}

    City(GeographyInput input, Instant now) {
        super(now);
        edit(input);
    }

    void edit(GeographyInput input) {
        name = DeliveryRules.name(input.name());
        active = input.active();
    }

    GeographyView view() {
        return new GeographyView(id, null, name, active, version, createdAt, updatedAt);
    }
}
