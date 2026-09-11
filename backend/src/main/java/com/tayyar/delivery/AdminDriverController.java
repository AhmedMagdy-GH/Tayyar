package com.tayyar.delivery;

import static com.tayyar.delivery.DriverOperationsDtos.*;

import com.tayyar.auth.SessionPrincipal;

import jakarta.validation.Valid;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/drivers")
public class AdminDriverController {
    private final DriverProfileService profiles;

    public AdminDriverController(DriverProfileService profiles) {
        this.profiles = profiles;
    }

    @PostMapping
    public Profile provision(
            @AuthenticationPrincipal SessionPrincipal actor,
            @Valid @RequestBody Provision input) {
        return profiles.provision(actor, input);
    }
}
