package com.tayyar.delivery;

import static com.tayyar.delivery.DeliveryDtos.*;

import com.tayyar.auth.SessionPrincipal;

import jakarta.validation.Valid;

import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class DeliveryController {
    private final DeliveryService service;
    private final ServiceabilityService eligibility;

    public DeliveryController(DeliveryService service, ServiceabilityService eligibility) {
        this.service = service;
        this.eligibility = eligibility;
    }

    @GetMapping({"/admin/cities", "/geography/cities"})
    public Page<GeographyView> cities(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.cities(new Window(page, size));
    }

    @GetMapping({"/admin/delivery-zones", "/geography/delivery-zones"})
    public Page<GeographyView> zones(
            @RequestParam UUID cityId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.zones(cityId, new Window(page, size));
    }

    @PostMapping("/admin/cities")
    public ResponseEntity<GeographyView> city(@Valid @RequestBody GeographyInput input) {
        var view = service.createCity(input);
        return ResponseEntity.created(URI.create("/api/v1/admin/cities/" + view.id())).body(view);
    }

    @GetMapping({"/admin/cities/{id}", "/geography/cities/{id}"})
    public GeographyView city(@PathVariable UUID id) {
        return service.city(id);
    }

    @GetMapping({"/admin/delivery-zones/{id}", "/geography/delivery-zones/{id}"})
    public GeographyView zone(@PathVariable UUID id) {
        return service.zone(id);
    }

    @PutMapping("/admin/cities/{id}")
    public GeographyView city(@PathVariable UUID id, @Valid @RequestBody GeographyEdit input) {
        return service.editCity(id, input);
    }

    @PostMapping("/admin/delivery-zones")
    public ResponseEntity<GeographyView> zone(@Valid @RequestBody ZoneInput input) {
        var view = service.createZone(input);
        return ResponseEntity.created(URI.create("/api/v1/admin/delivery-zones/" + view.id()))
                .body(view);
    }

    @PutMapping("/admin/delivery-zones/{id}")
    public GeographyView zone(@PathVariable UUID id, @Valid @RequestBody GeographyEdit input) {
        return service.editZone(id, input);
    }

    private static final String RULES =
            "/restaurants/{restaurantId}/branches/{branchId}/delivery-zones";

    @GetMapping(RULES)
    public Page<RuleView> rules(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            @AuthenticationPrincipal SessionPrincipal actor,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.rules(restaurantId, branchId, actor, new Window(page, size));
    }

    @PostMapping(RULES + "/{zoneId}")
    @ResponseStatus(HttpStatus.CREATED)
    public RuleView createRule(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            @PathVariable UUID zoneId,
            @AuthenticationPrincipal SessionPrincipal actor,
            @Valid @RequestBody RuleInput input) {
        return service.createRule(restaurantId, branchId, zoneId, actor, input);
    }

    @GetMapping(RULES + "/{zoneId}")
    public RuleView rule(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            @PathVariable UUID zoneId,
            @AuthenticationPrincipal SessionPrincipal actor) {
        return service.rule(restaurantId, branchId, zoneId, actor);
    }

    @PutMapping(RULES + "/{zoneId}")
    public RuleView editRule(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            @PathVariable UUID zoneId,
            @AuthenticationPrincipal SessionPrincipal actor,
            @Valid @RequestBody RuleEdit input) {
        return service.editRule(restaurantId, branchId, zoneId, actor, input);
    }

    @GetMapping("/branches/{branchId}/serviceability")
    public Eligibility eligibility(
            @PathVariable UUID branchId,
            @RequestParam UUID addressId,
            @AuthenticationPrincipal SessionPrincipal actor) {
        return eligibility.forAddress(actor, branchId, addressId);
    }
}
