package com.tayyar.restaurant;

import static com.tayyar.restaurant.RestaurantOperationsDtos.RestaurantContext;

import com.tayyar.auth.SessionPrincipal;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
@PreAuthorize("hasAnyRole('RESTAURANT_OWNER','RESTAURANT_STAFF')")
public class RestaurantOperationsService {
    private final RestaurantOperationsQuery query;

    public RestaurantOperationsService(RestaurantOperationsQuery query) {
        this.query = query;
    }

    public List<RestaurantContext> contexts(SessionPrincipal actor) {
        return query.contexts(actor);
    }
}
