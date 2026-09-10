package com.tayyar.menu;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.*;

public interface MenuRepository extends JpaRepository<Menu, UUID> {
    Optional<Menu> findByRestaurantId(UUID restaurantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Menu m where m.restaurantId=:restaurant")
    Optional<Menu> lock(@Param("restaurant") UUID restaurant);
}
