package com.tayyar.review;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

final class ReviewDtos {
    private ReviewDtos() {}

    record Create(@Min(1) @Max(5) int rating, @Size(max = 2000) String comment) {
        Create { comment = clean(comment); }
    }
    record Update(@Min(1) @Max(5) int rating, @Size(max = 2000) String comment,
                  @PositiveOrZero long version) {
        Update { comment = clean(comment); }
    }
    record Moderation(@NotNull ReviewStatus status, @PositiveOrZero long version) {}
    record CustomerReview(UUID id, UUID orderId, UUID restaurantId, UUID branchId, int rating,
                          String comment, ReviewStatus status, long version,
                          Instant createdAt, Instant updatedAt) {}
    record PublicReview(UUID id, UUID branchId, int rating, String comment,
                        Instant createdAt, Instant updatedAt) {}
    record RatingSummary(BigDecimal averageRating, long reviewCount) {}
    record PublicPage(List<PublicReview> items, int page, int size, long total,
                      RatingSummary ratingSummary) {}

    static String clean(String value) {
        if (value == null) return null;
        if (value.indexOf('\0') >= 0) throw ReviewException.invalid("Comment must not contain NUL");
        String cleaned = value.strip();
        return cleaned.isEmpty() ? null : cleaned;
    }
}
