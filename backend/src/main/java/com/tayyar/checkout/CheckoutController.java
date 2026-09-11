package com.tayyar.checkout;

import com.tayyar.auth.SessionPrincipal;

import jakarta.validation.Valid;

import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/checkout")
public class CheckoutController {
    private final CheckoutService service;

    public CheckoutController(CheckoutService service) {
        this.service = service;
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
        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .body(service.checkout(actor, keys.getFirst(), request));
    }
}
