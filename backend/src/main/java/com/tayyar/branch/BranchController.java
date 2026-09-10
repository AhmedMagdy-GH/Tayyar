package com.tayyar.branch;

import static com.tayyar.branch.BranchDtos.*;

import com.tayyar.auth.SessionPrincipal;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/restaurants/{restaurantId}/branches")
public class BranchController {
    private final BranchService service;

    public BranchController(BranchService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<View> create(
            @PathVariable UUID restaurantId,
            @AuthenticationPrincipal SessionPrincipal actor,
            @Valid @RequestBody Profile input) {
        var result = service.create(restaurantId, actor, input);
        return ResponseEntity.created(
                        URI.create(
                                "/api/v1/restaurants/" + restaurantId + "/branches/" + result.id()))
                .body(result);
    }

    @GetMapping
    public Page<View> list(
            @PathVariable UUID restaurantId,
            @AuthenticationPrincipal SessionPrincipal actor,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.list(restaurantId, actor, new Window(page, size));
    }

    @GetMapping("/{branchId}")
    public View details(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            @AuthenticationPrincipal SessionPrincipal actor) {
        return service.details(restaurantId, branchId, actor);
    }

    @PutMapping("/{branchId}")
    public View edit(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            @AuthenticationPrincipal SessionPrincipal actor,
            @Valid @RequestBody Edit input) {
        return service.edit(restaurantId, branchId, actor, input);
    }

    @PutMapping("/{branchId}/operation")
    public View operation(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            @AuthenticationPrincipal SessionPrincipal actor,
            @Valid @RequestBody Operation input) {
        return service.operation(restaurantId, branchId, actor, input);
    }

    @GetMapping("/{branchId}/hours")
    public Schedule hours(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            @AuthenticationPrincipal SessionPrincipal actor) {
        return service.hours(restaurantId, branchId, actor);
    }

    @PutMapping("/{branchId}/hours")
    public Schedule hours(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            @AuthenticationPrincipal SessionPrincipal actor,
            @Valid @RequestBody Hours input) {
        return service.replaceHours(restaurantId, branchId, actor, input);
    }

    @GetMapping("/{branchId}/availability")
    public Availability availability(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            @AuthenticationPrincipal SessionPrincipal actor) {
        return service.availability(restaurantId, branchId, actor);
    }

    @GetMapping("/{branchId}/staff")
    public Page<Staff> staff(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            @AuthenticationPrincipal SessionPrincipal actor,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.staff(restaurantId, branchId, actor, new Window(page, size));
    }

    @PutMapping("/{branchId}/staff/{userId}")
    public View assign(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            @PathVariable UUID userId,
            @AuthenticationPrincipal SessionPrincipal actor,
            @Valid @RequestBody Version input) {
        return service.assign(restaurantId, branchId, userId, actor, input);
    }

    @DeleteMapping("/{branchId}/staff/{userId}")
    public View unassign(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            @PathVariable UUID userId,
            @AuthenticationPrincipal SessionPrincipal actor,
            @Valid @RequestBody Version input) {
        return service.unassign(restaurantId, branchId, userId, actor, input);
    }
}
