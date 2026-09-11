package com.tayyar.delivery;

import static com.tayyar.delivery.DriverOperationsDtos.*;

import com.tayyar.auth.SessionPrincipal;

import jakarta.validation.Valid;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/driver/orders")
public class DriverOrderController {
    private final DriverOrderService orders;

    public DriverOrderController(DriverOrderService orders) {
        this.orders = orders;
    }

    @GetMapping
    public Page<DriverOrder> queue(
            @AuthenticationPrincipal SessionPrincipal actor,
            @RequestParam MultiValueMap<String, String> parameters) {
        return orders.queue(
                actor, new DriverOperationsParameters(parameters, Set.of("page", "size")).window());
    }

    @GetMapping("/{orderId}")
    public DriverOrder details(
            @AuthenticationPrincipal SessionPrincipal actor, @PathVariable UUID orderId) {
        return orders.details(actor, orderId);
    }

    @PostMapping("/{orderId}/pickup")
    public DriverOrder pickup(
            @AuthenticationPrincipal SessionPrincipal actor,
            @PathVariable UUID orderId,
            @Valid @RequestBody Transition input) {
        return orders.pickup(actor, orderId, input);
    }

    @PostMapping("/{orderId}/deliver")
    public Completion deliver(
            @AuthenticationPrincipal SessionPrincipal actor,
            @PathVariable UUID orderId,
            @Valid @RequestBody Transition input) {
        return orders.deliver(actor, orderId, input);
    }
}
