package com.tayyar.restaurant;

import static com.tayyar.restaurant.RestaurantDtos.*;

import com.tayyar.auth.SessionPrincipal;

import jakarta.validation.Valid;

import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/restaurant-applications")
public class ApplicationController {
    private final ApplicationService service;

    public ApplicationController(ApplicationService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ApplicationView> apply(
            @AuthenticationPrincipal SessionPrincipal actor, @Valid @RequestBody Apply input) {
        var result = service.apply(actor, input);
        return ResponseEntity.created(
                        java.net.URI.create("/api/v1/restaurant-applications/" + result.id()))
                .body(result);
    }

    @GetMapping
    public Page<ApplicationView> mine(
            @AuthenticationPrincipal SessionPrincipal actor,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.mine(actor, new Window(page, size));
    }

    @GetMapping("/{id}")
    public ApplicationView details(
            @PathVariable UUID id, @AuthenticationPrincipal SessionPrincipal actor) {
        return service.details(id, actor);
    }

    @PostMapping("/{id}/submissions")
    public ResponseEntity<ApplicationView> resubmit(
            @PathVariable UUID id,
            @AuthenticationPrincipal SessionPrincipal actor,
            @Valid @RequestBody Resubmit input) {
        return ResponseEntity.status(201).body(service.resubmit(id, actor, input));
    }

    @GetMapping("/{id}/submissions")
    public Page<Submission> submissions(
            @PathVariable UUID id,
            @AuthenticationPrincipal SessionPrincipal actor,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.submissions(id, actor, new Window(page, size));
    }

    @GetMapping("/{id}/decisions")
    public Page<Decision> decisions(
            @PathVariable UUID id,
            @AuthenticationPrincipal SessionPrincipal actor,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.decisions(id, actor, new Window(page, size));
    }
}
