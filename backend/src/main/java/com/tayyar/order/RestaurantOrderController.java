package com.tayyar.order;

import static com.tayyar.order.OrderOperationsDtos.*;

import com.tayyar.auth.SessionPrincipal;

import jakarta.validation.Valid;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/restaurant-orders")
public class RestaurantOrderController {
    private final RestaurantOrderOperationsService service;

    public RestaurantOrderController(RestaurantOrderOperationsService service) {
        this.service = service;
    }

    @GetMapping
    public Page<Summary> queue(
            @AuthenticationPrincipal SessionPrincipal actor,
            @RequestParam MultiValueMap<String, String> parameters) {
        var parsed =
                new OrderOperationsParameters(
                        parameters, Set.of("restaurantId", "branchId", "page", "size", "status"));
        return service.queue(
                actor,
                parsed.requiredUuid("restaurantId"),
                parsed.uuid("branchId"),
                parsed.status(true),
                parsed.window());
    }

    @GetMapping("/{orderId}")
    public Details details(
            @AuthenticationPrincipal SessionPrincipal actor, @PathVariable UUID orderId) {
        return service.details(actor, orderId);
    }

    @PostMapping("/{orderId}/accept")
    public Summary accept(
            @AuthenticationPrincipal SessionPrincipal actor,
            @PathVariable UUID orderId,
            @Valid @RequestBody Version input) {
        return service.accept(actor, orderId, input);
    }

    @PostMapping("/{orderId}/reject")
    public Summary reject(
            @AuthenticationPrincipal SessionPrincipal actor,
            @PathVariable UUID orderId,
            @Valid @RequestBody Reason input) {
        return service.reject(actor, orderId, input);
    }

    @PostMapping("/{orderId}/start-preparation")
    public Summary startPreparation(
            @AuthenticationPrincipal SessionPrincipal actor,
            @PathVariable UUID orderId,
            @Valid @RequestBody Version input) {
        return service.startPreparation(actor, orderId, input);
    }

    @PostMapping("/{orderId}/ready-for-pickup")
    public Summary ready(
            @AuthenticationPrincipal SessionPrincipal actor,
            @PathVariable UUID orderId,
            @Valid @RequestBody Version input) {
        return service.ready(actor, orderId, input);
    }
}
