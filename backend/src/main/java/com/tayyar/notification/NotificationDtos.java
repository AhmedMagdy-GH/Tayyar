package com.tayyar.notification;

import java.time.Instant;
import java.util.*;

public final class NotificationDtos {
    private NotificationDtos() {}

    public record View(
            UUID id,
            NotificationType type,
            String channel,
            String title,
            String body,
            RelatedEntityType relatedEntityType,
            UUID relatedEntityId,
            boolean read,
            Instant createdAt,
            Instant readAt) {}

    public record Page(List<View> items, int page, int size, long total) {}

    public record MarkAllResult(int markedRead) {}

    public record Window(int page, int size) {
        public Window {
            if (page < 0 || page > 10000 || size < 1 || size > 100)
                throw NotificationException.invalid(
                        "INVALID_PAGE", "Page must be 0–10000 and size 1–100");
        }

        int offset() {
            return Math.multiplyExact(page, size);
        }
    }
}
