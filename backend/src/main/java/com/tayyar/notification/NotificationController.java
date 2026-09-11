package com.tayyar.notification;

import static com.tayyar.notification.NotificationDtos.*;

import com.tayyar.auth.SessionPrincipal;

import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {
    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<Page> list(
            @AuthenticationPrincipal SessionPrincipal actor,
            @RequestParam MultiValueMap<String, String> parameters) {
        var parsed = new NotificationParameters(parameters);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.list(actor, parsed.read(), parsed.window()));
    }

    @GetMapping("/{notificationId}")
    public ResponseEntity<View> details(
            @AuthenticationPrincipal SessionPrincipal actor,
            @PathVariable UUID notificationId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.details(actor, notificationId));
    }

    @PostMapping("/{notificationId}/read")
    public ResponseEntity<View> markRead(
            @AuthenticationPrincipal SessionPrincipal actor,
            @PathVariable UUID notificationId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.markRead(actor, notificationId));
    }

    @PostMapping("/read-all")
    public ResponseEntity<MarkAllResult> markAllRead(
            @AuthenticationPrincipal SessionPrincipal actor) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.markAllRead(actor));
    }
}
