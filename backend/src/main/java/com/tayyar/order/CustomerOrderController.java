package com.tayyar.order;

import static com.tayyar.order.OrderOperationsDtos.*;

import com.tayyar.auth.SessionPrincipal;

import jakarta.validation.Valid;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/orders")
public class CustomerOrderController {
    private final CustomerOrderOperationsService service;

    public CustomerOrderController(CustomerOrderOperationsService service) {
        this.service = service;
    }

    @GetMapping
    public Page<Summary> list(
            @AuthenticationPrincipal SessionPrincipal actor,
            @RequestParam MultiValueMap<String, String> parameters) {
        var parsed = new OrderOperationsParameters(parameters, Set.of("page", "size", "status"));
        return service.list(actor, parsed.status(false), parsed.window());
    }

    @GetMapping("/{orderId}")
    public Details details(
            @AuthenticationPrincipal SessionPrincipal actor, @PathVariable UUID orderId) {
        return service.details(actor, orderId);
    }

    @PostMapping("/{orderId}/cancel")
    public Summary cancel(
            @AuthenticationPrincipal SessionPrincipal actor,
            @PathVariable UUID orderId,
            @Valid @RequestBody Reason input) {
        return service.cancel(actor, orderId, input);
    }
}
