package com.tayyar.delivery;

import com.tayyar.auth.SessionPrincipal;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
@PreAuthorize("hasRole('CUSTOMER')")
public class ServiceabilityService {
    private final ServiceabilityQuery query;

    public ServiceabilityService(ServiceabilityQuery query) {
        this.query = query;
    }

    public DeliveryDtos.Eligibility forAddress(SessionPrincipal actor, UUID branch, UUID address) {
        return query.forOwnedAddress(actor.id(), branch, address);
    }
}
