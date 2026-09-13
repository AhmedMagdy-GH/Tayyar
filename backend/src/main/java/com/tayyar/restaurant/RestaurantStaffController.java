package com.tayyar.restaurant;

import static com.tayyar.restaurant.RestaurantOperationsDtos.*;

import com.tayyar.auth.SessionPrincipal;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/restaurants/{restaurantId}/staff")
public class RestaurantStaffController {
    private final RestaurantStaffService service;

    public RestaurantStaffController(RestaurantStaffService service) {
        this.service = service;
    }

    @GetMapping
    public StaffPage list(
            @PathVariable UUID restaurantId,
            @AuthenticationPrincipal SessionPrincipal actor,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.list(restaurantId, actor, new RestaurantDtos.Window(page, size));
    }

    @PostMapping
    public ResponseEntity<StaffView> create(
            @PathVariable UUID restaurantId,
            @AuthenticationPrincipal SessionPrincipal actor,
            @Valid @RequestBody CreateStaff input) {
        var result = service.create(restaurantId, actor, input);
        return ResponseEntity.created(
                        URI.create(
                                "/api/v1/restaurants/"
                                        + restaurantId
                                        + "/staff/"
                                        + result.userId()))
                .body(result);
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> remove(
            @PathVariable UUID restaurantId,
            @PathVariable UUID userId,
            @AuthenticationPrincipal SessionPrincipal actor) {
        service.remove(restaurantId, userId, actor);
        return ResponseEntity.noContent().build();
    }
}
