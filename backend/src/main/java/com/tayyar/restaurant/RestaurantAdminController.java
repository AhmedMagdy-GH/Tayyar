package com.tayyar.restaurant;

import static com.tayyar.restaurant.RestaurantDtos.*;

import com.tayyar.auth.SessionPrincipal;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
public class RestaurantAdminController {
    private final ApplicationService applications;
    private final RestaurantService restaurants;

    public RestaurantAdminController(
            ApplicationService applications, RestaurantService restaurants) {
        this.applications = applications;
        this.restaurants = restaurants;
    }

    @GetMapping("/restaurant-applications")
    public Page<ApplicationView> queue(
            @RequestParam(defaultValue = "PENDING") ApplicationStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return applications.queue(status, new Window(page, size));
    }

    @GetMapping("/restaurant-applications/{id}")
    public ApplicationView application(
            @PathVariable UUID id, @AuthenticationPrincipal SessionPrincipal actor) {
        return applications.details(id, actor);
    }

    @GetMapping("/restaurant-applications/{id}/submissions")
    public Page<Submission> submissions(
            @PathVariable UUID id,
            @AuthenticationPrincipal SessionPrincipal actor,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return applications.submissions(id, actor, new Window(page, size));
    }

    @GetMapping("/restaurant-applications/{id}/decisions")
    public Page<Decision> decisions(
            @PathVariable UUID id,
            @AuthenticationPrincipal SessionPrincipal actor,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return applications.decisions(id, actor, new Window(page, size));
    }

    @PostMapping("/restaurant-applications/{id}/decisions")
    public ResponseEntity<ApplicationView> review(
            @PathVariable UUID id,
            @AuthenticationPrincipal SessionPrincipal actor,
            @Valid @RequestBody Review input) {
        return ResponseEntity.status(201).body(applications.review(id, actor, input));
    }

    @GetMapping("/restaurants")
    public Page<RestaurantView> restaurants(
            @AuthenticationPrincipal SessionPrincipal actor,
            @RequestParam(required = false) RestaurantStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return restaurants.list(actor, status, new Window(page, size));
    }

    @PutMapping("/restaurants/{id}/status")
    public RestaurantView status(
            @PathVariable UUID id,
            @AuthenticationPrincipal SessionPrincipal actor,
            @Valid @RequestBody StatusChange input) {
        return restaurants.changeStatus(id, actor, input);
    }

    @GetMapping("/restaurants/{id}/status-history")
    public Page<StatusHistory> history(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return restaurants.statusHistory(id, new Window(page, size));
    }
}
