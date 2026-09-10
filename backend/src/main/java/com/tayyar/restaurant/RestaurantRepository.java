package com.tayyar.restaurant;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.*;

import java.util.*;

public interface RestaurantRepository extends JpaRepository<Restaurant, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Restaurant r where r.id=:id")
    Optional<Restaurant> lockById(UUID id);
}
