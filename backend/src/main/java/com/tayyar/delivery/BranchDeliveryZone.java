package com.tayyar.delivery;

import static com.tayyar.delivery.DeliveryDtos.*;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "branch_delivery_zones")
public class BranchDeliveryZone extends DeliveryRecord {
    @Column(name = "branch_id", nullable = false, updatable = false)
    UUID branchId;

    @Column(name = "delivery_zone_id", nullable = false, updatable = false)
    UUID deliveryZoneId;

    @Column(name = "delivery_fee", nullable = false, precision = 12, scale = 2)
    BigDecimal deliveryFee;

    @Column(name = "minimum_order", nullable = false, precision = 12, scale = 2)
    BigDecimal minimumOrder;

    @Column(name = "eta_min_minutes", nullable = false)
    int etaMinMinutes;

    @Column(name = "eta_max_minutes", nullable = false)
    int etaMaxMinutes;

    @Column(nullable = false)
    boolean enabled;

    protected BranchDeliveryZone() {}

    BranchDeliveryZone(UUID branch, UUID zone, RuleInput input, Instant now) {
        super(now);
        branchId = branch;
        deliveryZoneId = zone;
        edit(input);
    }

    void edit(RuleInput input) {
        DeliveryRules.rule(input);
        deliveryFee = input.deliveryFee();
        minimumOrder = input.minimumOrder();
        etaMinMinutes = input.etaMinMinutes();
        etaMaxMinutes = input.etaMaxMinutes();
        enabled = input.enabled();
    }

    RuleView view() {
        return new RuleView(
                id,
                branchId,
                deliveryZoneId,
                "EGP",
                new RuleInput(deliveryFee, minimumOrder, etaMinMinutes, etaMaxMinutes, enabled),
                version,
                createdAt,
                updatedAt);
    }
}
