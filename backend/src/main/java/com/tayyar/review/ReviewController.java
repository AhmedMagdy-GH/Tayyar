package com.tayyar.review;

import static com.tayyar.review.ReviewDtos.*;
import com.tayyar.auth.SessionPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
class ReviewController {
    private final ReviewService service;
    ReviewController(ReviewService service) { this.service = service; }

    @PostMapping("/api/v1/orders/{orderId}/review")
    @ResponseStatus(HttpStatus.CREATED)
    CustomerReview create(@AuthenticationPrincipal SessionPrincipal actor, @PathVariable UUID orderId,
                          @Valid @RequestBody Create input) { return service.create(actor, orderId, input); }

    @GetMapping("/api/v1/orders/{orderId}/review")
    CustomerReview get(@AuthenticationPrincipal SessionPrincipal actor, @PathVariable UUID orderId) {
        return service.get(actor, orderId);
    }

    @PatchMapping("/api/v1/orders/{orderId}/review")
    CustomerReview update(@AuthenticationPrincipal SessionPrincipal actor, @PathVariable UUID orderId,
                          @Valid @RequestBody Update input) { return service.update(actor, orderId, input); }

    @GetMapping("/api/v1/discovery/restaurants/{restaurantId}/reviews")
    PublicPage list(@PathVariable UUID restaurantId, @RequestParam MultiValueMap<String, String> parameters) {
        return service.list(restaurantId, new ReviewParameters(parameters));
    }

    @PatchMapping("/api/v1/admin/reviews/{reviewId}/moderation")
    CustomerReview moderate(@PathVariable UUID reviewId, @Valid @RequestBody Moderation input) {
        return service.moderate(reviewId, input);
    }
}
