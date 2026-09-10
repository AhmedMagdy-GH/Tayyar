package com.tayyar.delivery;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

import java.util.*;

public interface DeliveryZoneRepository extends JpaRepository<DeliveryZone, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select z from DeliveryZone z where z.id=:id")
    Optional<DeliveryZone> lock(UUID id);

    Page<DeliveryZone> findByCityId(UUID cityId, Pageable page);
}
