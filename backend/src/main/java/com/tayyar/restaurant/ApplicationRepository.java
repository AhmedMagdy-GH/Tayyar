package com.tayyar.restaurant;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.*;

import java.util.*;

public interface ApplicationRepository extends JpaRepository<RestaurantApplication, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from RestaurantApplication a where a.id=:id")
    Optional<RestaurantApplication> lockById(UUID id);
}
