package com.tayyar.address;

import static com.tayyar.address.AddressDtos.*;

import com.tayyar.auth.SessionPrincipal;

import jakarta.validation.Valid;

import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users/me/addresses")
public class AddressController {
    private final AddressService service;

    public AddressController(AddressService service) {
        this.service = service;
    }

    @GetMapping
    public Page list(
            @AuthenticationPrincipal SessionPrincipal actor,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.list(actor, new Window(page, size));
    }

    @PostMapping
    public ResponseEntity<View> create(
            @AuthenticationPrincipal SessionPrincipal actor, @Valid @RequestBody Profile input) {
        var result = service.create(actor, input);
        return ResponseEntity.created(URI.create("/api/v1/users/me/addresses/" + result.id()))
                .body(result);
    }

    @GetMapping("/{id}")
    public View details(@AuthenticationPrincipal SessionPrincipal actor, @PathVariable UUID id) {
        return service.details(actor, id);
    }

    @PutMapping("/{id}")
    public View edit(
            @AuthenticationPrincipal SessionPrincipal actor,
            @PathVariable UUID id,
            @Valid @RequestBody Edit input) {
        return service.edit(actor, id, input);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @AuthenticationPrincipal SessionPrincipal actor,
            @PathVariable UUID id,
            @Valid @RequestBody VersionInput input) {
        service.delete(actor, id, input);
    }

    @PutMapping("/{id}/default")
    public View selectDefault(
            @AuthenticationPrincipal SessionPrincipal actor,
            @PathVariable UUID id,
            @Valid @RequestBody VersionInput input) {
        return service.selectDefault(actor, id, input);
    }

    @PutMapping("/{id}/delivery-zone")
    public View selectZone(
            @AuthenticationPrincipal SessionPrincipal actor,
            @PathVariable UUID id,
            @Valid @RequestBody ZoneSelection input) {
        return service.selectZone(actor, id, input);
    }
}
