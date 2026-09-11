package com.tayyar.favorite;

import static com.tayyar.favorite.FavoriteDtos.Page;
import com.tayyar.auth.SessionPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/favorites")
class FavoriteController {
    private final FavoriteService service;
    FavoriteController(FavoriteService service) { this.service = service; }

    @GetMapping
    Page list(@AuthenticationPrincipal SessionPrincipal actor,
              @RequestParam MultiValueMap<String, String> parameters) {
        return service.list(actor, new FavoriteParameters(parameters));
    }

    @PutMapping("/{restaurantId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void add(@AuthenticationPrincipal SessionPrincipal actor, @PathVariable UUID restaurantId) {
        service.add(actor, restaurantId);
    }

    @DeleteMapping("/{restaurantId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void remove(@AuthenticationPrincipal SessionPrincipal actor, @PathVariable UUID restaurantId) {
        service.remove(actor, restaurantId);
    }
}
