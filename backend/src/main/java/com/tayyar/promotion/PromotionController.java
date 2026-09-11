package com.tayyar.promotion;

import static com.tayyar.promotion.PromotionDtos.*;

import com.tayyar.auth.SessionPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/restaurants/{restaurantId}/promotions")
public class PromotionController {
    private final PromotionService service;

    public PromotionController(PromotionService service) { this.service = service; }

    @PostMapping
    public ResponseEntity<View> create(@PathVariable UUID restaurantId,
            @AuthenticationPrincipal SessionPrincipal actor, @Valid @RequestBody Create input) {
        View result = service.create(restaurantId, actor, input);
        return ResponseEntity.created(URI.create("/api/v1/restaurants/" + restaurantId
                + "/promotions/" + result.id())).body(result);
    }

    @GetMapping
    public Page list(@PathVariable UUID restaurantId,
            @AuthenticationPrincipal SessionPrincipal actor,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.list(restaurantId, actor, page, size);
    }

    @GetMapping("/{promotionId}")
    public View get(@PathVariable UUID restaurantId, @PathVariable UUID promotionId,
            @AuthenticationPrincipal SessionPrincipal actor) {
        return service.get(restaurantId, promotionId, actor);
    }

    @PatchMapping("/{promotionId}")
    public View update(@PathVariable UUID restaurantId, @PathVariable UUID promotionId,
            @AuthenticationPrincipal SessionPrincipal actor, @Valid @RequestBody Update input) {
        return service.update(restaurantId, promotionId, actor, input);
    }
}
