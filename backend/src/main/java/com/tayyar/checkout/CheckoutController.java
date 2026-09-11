package com.tayyar.checkout;

import com.tayyar.auth.SessionPrincipal;
import com.tayyar.auth.AuthRateLimiter;
import com.tayyar.auth.IdentityProperties;
import com.tayyar.operations.CheckoutMetrics;

import jakarta.validation.Valid;

import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/checkout")
public class CheckoutController {
    private final CheckoutService service;
    private final AuthRateLimiter limiter;
    private final IdentityProperties properties;
    private final CheckoutMetrics metrics;

    public CheckoutController(CheckoutService service, AuthRateLimiter limiter,
            IdentityProperties properties, CheckoutMetrics metrics) {
        this.service = service;
        this.limiter = limiter;
        this.properties = properties;
        this.metrics = metrics;
    }

    @PostMapping
    public ResponseEntity<CheckoutDtos.Summary> checkout(
            @AuthenticationPrincipal SessionPrincipal actor,
            @RequestHeader(value = "Idempotency-Key", required = false) List<String> keys,
            @Valid @RequestBody CheckoutDtos.Request request) {
        if (keys == null || keys.size() != 1)
            throw new CheckoutException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_IDEMPOTENCY_KEY",
                    "Exactly one Idempotency-Key is required");
        limiter.acquire("checkout-user:" + actor.id(), properties.checkoutLimit());
        try {
            var result = service.checkout(actor, keys.getFirst(), request);
            metrics.success();
            return ResponseEntity.status(HttpStatus.CREATED)
                    .cacheControl(CacheControl.noStore())
                    .body(result);
        } catch (CheckoutException error) {
            if (error.status() == HttpStatus.CONFLICT) metrics.conflict();
            throw error;
        }
    }
}
