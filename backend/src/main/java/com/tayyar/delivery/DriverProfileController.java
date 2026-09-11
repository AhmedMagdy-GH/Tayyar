package com.tayyar.delivery;

import static com.tayyar.delivery.DriverOperationsDtos.*;

import com.tayyar.auth.SessionPrincipal;

import jakarta.validation.Valid;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/driver/profile")
public class DriverProfileController {
    private final DriverProfileService profiles;

    public DriverProfileController(DriverProfileService profiles) {
        this.profiles = profiles;
    }

    @GetMapping
    public Profile profile(@AuthenticationPrincipal SessionPrincipal actor) {
        return profiles.profile(actor);
    }

    @PostMapping("/available")
    public Profile available(
            @AuthenticationPrincipal SessionPrincipal actor,
            @Valid @RequestBody Version input) {
        return profiles.available(actor, input);
    }

    @PostMapping("/offline")
    public Profile offline(
            @AuthenticationPrincipal SessionPrincipal actor,
            @Valid @RequestBody Version input) {
        return profiles.offline(actor, input);
    }
}
