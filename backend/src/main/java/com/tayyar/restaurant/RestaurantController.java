package com.tayyar.restaurant;

import static com.tayyar.restaurant.RestaurantDtos.*;

import com.tayyar.auth.SessionPrincipal;

import jakarta.validation.Valid;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/restaurants")
public class RestaurantController {
    private final RestaurantService service;

    public RestaurantController(RestaurantService service) {
        this.service = service;
    }

    @GetMapping
    public Page<RestaurantView> list(
            @AuthenticationPrincipal SessionPrincipal actor,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.list(actor, new Window(page, size));
    }

    @GetMapping("/{id}")
    public RestaurantView details(
            @PathVariable UUID id, @AuthenticationPrincipal SessionPrincipal actor) {
        return service.details(id, actor);
    }

    @PutMapping("/{id}")
    public RestaurantView edit(
            @PathVariable UUID id,
            @AuthenticationPrincipal SessionPrincipal actor,
            @Valid @RequestBody Edit input) {
        return service.edit(id, actor, input);
    }

    @GetMapping("/{id}/memberships")
    public Page<Membership> memberships(
            @PathVariable UUID id,
            @AuthenticationPrincipal SessionPrincipal actor,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.memberships(id, actor, new Window(page, size));
    }
}
