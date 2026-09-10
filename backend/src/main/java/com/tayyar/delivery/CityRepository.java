package com.tayyar.delivery;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.*;

import java.util.*;

public interface CityRepository extends JpaRepository<City, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from City c where c.id=:id")
    Optional<City> lock(UUID id);
}
