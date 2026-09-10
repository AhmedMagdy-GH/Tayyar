package com.tayyar.delivery;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

import java.util.*;

public interface BranchDeliveryZoneRepository extends JpaRepository<BranchDeliveryZone, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from BranchDeliveryZone r where r.branchId=:branch and r.deliveryZoneId=:zone")
    Optional<BranchDeliveryZone> lock(UUID branch, UUID zone);

    Optional<BranchDeliveryZone> findByBranchIdAndDeliveryZoneId(UUID branch, UUID zone);

    Page<BranchDeliveryZone> findByBranchId(UUID branch, Pageable page);
}
