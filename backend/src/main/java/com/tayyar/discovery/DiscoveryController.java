package com.tayyar.discovery;

import static com.tayyar.discovery.DiscoveryDtos.*;

import com.tayyar.auth.SessionPrincipal;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/discovery")
public class DiscoveryController {
    private final DiscoveryService service;

    public DiscoveryController(DiscoveryService service) {
        this.service = service;
    }

    @GetMapping("/restaurants")
    public Page<Restaurant> restaurants(
            @RequestParam MultiValueMap<String, String> params,
            @AuthenticationPrincipal SessionPrincipal actor) {
        var p = new DiscoveryParameters(params, DiscoveryParameters.LIST);
        return service.restaurants(p.filter(), p.window(), actor);
    }

    @GetMapping("/restaurants/{restaurant}")
    public Restaurant restaurant(
            @PathVariable UUID restaurant, @RequestParam MultiValueMap<String, String> params) {
        new DiscoveryParameters(params, Set.of());
        return service.restaurant(restaurant);
    }

    @GetMapping("/restaurants/{restaurant}/branches")
    public Page<Branch> branches(
            @PathVariable UUID restaurant,
            @RequestParam MultiValueMap<String, String> params,
            @AuthenticationPrincipal SessionPrincipal actor) {
        var p = new DiscoveryParameters(params, DiscoveryParameters.LIST);
        return service.branches(restaurant, p.filter(), p.window(), actor);
    }

    @GetMapping("/restaurants/{restaurant}/branches/{branch}/menu")
    public Menu menu(
            @PathVariable UUID restaurant,
            @PathVariable UUID branch,
            @RequestParam MultiValueMap<String, String> params) {
        var p =
                new DiscoveryParameters(
                        params, Set.of("page", "size", "itemPage", "itemSize", "availableOnly"));
        return service.menu(
                restaurant,
                branch,
                p.window(),
                new Window(p.integer("itemPage", 0), p.integer("itemSize", 20)),
                p.bool("availableOnly", false));
    }

    @GetMapping("/delivery-zones")
    public Page<Zone> zones(@RequestParam MultiValueMap<String, String> params) {
        var p = new DiscoveryParameters(params, Set.of("cityId", "page", "size"));
        return service.zones(p.uuid("cityId"), p.window());
    }
}
