package com.tayyar.address;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.*;

public interface AddressRepository extends JpaRepository<CustomerAddress, UUID> {
    Optional<CustomerAddress> findByIdAndUserId(UUID id, UUID userId);

    org.springframework.data.domain.Page<CustomerAddress> findByUserId(
            UUID userId, Pageable pageable);

    long countByUserId(UUID userId);
}
