package com.tayyar.restaurant;

import static com.tayyar.restaurant.RestaurantOperationsDtos.RestaurantContext;

import com.tayyar.auth.SessionPrincipal;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/restaurant-operations")
public class RestaurantOperationsController {
    private final RestaurantOperationsService service;

    public RestaurantOperationsController(RestaurantOperationsService service) {
        this.service = service;
    }

    @GetMapping("/context")
    public List<RestaurantContext> context(
            @AuthenticationPrincipal SessionPrincipal actor) {
        return service.contexts(actor);
    }
}
