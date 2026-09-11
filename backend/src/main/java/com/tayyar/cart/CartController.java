package com.tayyar.cart;

import static com.tayyar.cart.CartDtos.*;

import com.tayyar.auth.SessionPrincipal;

import jakarta.validation.Valid;

import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/cart")
public class CartController {
    private final CartService service;

    public CartController(CartService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<View> get(
            @AuthenticationPrincipal SessionPrincipal actor,
            @RequestParam MultiValueMap<String, String> parameters) {
        new CartParameters(parameters, Set.of());
        return service.get(actor)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/items")
    public ResponseEntity<View> add(
            @AuthenticationPrincipal SessionPrincipal actor, @Valid @RequestBody AddItem input) {
        Mutation result = service.add(actor, input);
        if (result.created())
            return ResponseEntity.created(URI.create("/api/v1/cart")).body(result.cart());
        return ResponseEntity.ok(result.cart());
    }

    @PatchMapping("/items/{itemId}")
    public View update(
            @AuthenticationPrincipal SessionPrincipal actor,
            @PathVariable UUID itemId,
            @Valid @RequestBody Quantity input) {
        return service.update(actor, itemId, input);
    }

    @DeleteMapping("/items/{itemId}")
    public ResponseEntity<View> remove(
            @AuthenticationPrincipal SessionPrincipal actor,
            @PathVariable UUID itemId,
            @RequestParam MultiValueMap<String, String> parameters) {
        var parsed = new CartParameters(parameters, Set.of("cartId", "cartVersion", "itemVersion"));
        return service.remove(actor, itemId, parsed.versions())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clear(
            @AuthenticationPrincipal SessionPrincipal actor,
            @RequestParam MultiValueMap<String, String> parameters) {
        var parsed = new CartParameters(parameters, Set.of("cartId", "version"));
        service.clear(actor, parsed.requiredUuid("cartId"), parsed.requiredVersion("version"));
    }

    @PostMapping("/reconfirm-prices")
    public View reconfirm(
            @AuthenticationPrincipal SessionPrincipal actor, @Valid @RequestBody Reconfirm input) {
        return service.reconfirm(actor, input);
    }

    @PostMapping("/replace")
    @ResponseStatus(HttpStatus.CREATED)
    public View replace(
            @AuthenticationPrincipal SessionPrincipal actor, @Valid @RequestBody Replace input) {
        return service.replace(actor, input);
    }
}
