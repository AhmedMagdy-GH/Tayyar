package com.tayyar.review;

import static com.tayyar.review.ReviewDtos.*;
import com.tayyar.auth.SessionPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
class ReviewService {
    private final ReviewStore store;
    private final Clock clock;
    ReviewService(ReviewStore store, Clock clock) { this.store = store; this.clock = clock; }

    @PreAuthorize("hasRole('CUSTOMER')")
    @Transactional
    CustomerReview create(SessionPrincipal actor, UUID order, Create input) {
        var context = store.order(order, actor.id());
        if (!"DELIVERED".equals(context.status()))
            throw ReviewException.conflict("Only delivered orders may be reviewed");
        return store.create(order, context, input, clock.instant());
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    CustomerReview get(SessionPrincipal actor, UUID order) { return store.owned(order, actor.id()); }

    @PreAuthorize("hasRole('CUSTOMER')")
    @Transactional
    CustomerReview update(SessionPrincipal actor, UUID order, Update input) {
        return store.update(order, actor.id(), input, clock.instant());
    }

    PublicPage list(UUID restaurant, ReviewParameters parameters) {
        return store.publicReviews(restaurant, parameters);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    CustomerReview moderate(UUID review, Moderation input) {
        return store.moderate(review, input, clock.instant());
    }
}
