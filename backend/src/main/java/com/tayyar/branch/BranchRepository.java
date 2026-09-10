package com.tayyar.branch;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.*;

public interface BranchRepository extends JpaRepository<Branch, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Branch b where b.id=:id and b.restaurantId=:restaurant")
    Optional<Branch> lock(@Param("restaurant") UUID restaurant, @Param("id") UUID id);

    Optional<Branch> findByIdAndRestaurantId(UUID id, UUID restaurantId);

    org.springframework.data.domain.Page<Branch> findByRestaurantId(
            UUID restaurantId, Pageable pageable);
}
