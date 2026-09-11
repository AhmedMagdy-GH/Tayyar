package com.tayyar.review;

import static com.tayyar.review.ReviewDtos.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@Repository
class ReviewStore {
    private final JdbcTemplate jdbc;
    ReviewStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    record OrderContext(UUID customer, UUID restaurant, UUID branch, String status) {}

    OrderContext order(UUID order, UUID customer) {
        return jdbc.query("SELECT customer_id,restaurant_id,branch_id,status FROM orders " +
                        "WHERE id=? AND customer_id=? FOR UPDATE",
                (r, n) -> new OrderContext(r.getObject(1, UUID.class), r.getObject(2, UUID.class),
                        r.getObject(3, UUID.class), r.getString(4)), order, customer)
                .stream().findFirst().orElseThrow(ReviewException::missing);
    }

    CustomerReview create(UUID order, OrderContext context, Create input, Instant now) {
        try {
            jdbc.update("INSERT INTO reviews(id,order_id,customer_id,restaurant_id,branch_id,rating," +
                            "comment,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?)",
                    UUID.randomUUID(), order, context.customer(), context.restaurant(), context.branch(),
                    input.rating(), input.comment(), Timestamp.from(now), Timestamp.from(now));
        } catch (DataIntegrityViolationException error) {
            throw ReviewException.conflict("Order already has a review");
        }
        return owned(order, context.customer());
    }

    CustomerReview owned(UUID order, UUID customer) {
        return jdbc.query("SELECT * FROM reviews WHERE order_id=? AND customer_id=?", this::customer,
                order, customer).stream().findFirst().orElseThrow(ReviewException::missing);
    }

    CustomerReview update(UUID order, UUID customer, Update input, Instant now) {
        int changed = jdbc.update("UPDATE reviews SET rating=?,comment=?,version=version+1,updated_at=? " +
                        "WHERE order_id=? AND customer_id=? AND version=?",
                input.rating(), input.comment(), Timestamp.from(now), order, customer, input.version());
        if (changed != 1) {
            owned(order, customer);
            throw ReviewException.conflict("Review changed; reload before editing");
        }
        return owned(order, customer);
    }

    CustomerReview moderate(UUID review, Moderation input, Instant now) {
        int changed = jdbc.update("UPDATE reviews SET moderation_status=?,version=version+1,updated_at=? " +
                        "WHERE id=? AND version=?", input.status().name(), Timestamp.from(now), review, input.version());
        if (changed != 1) {
            require(review);
            throw ReviewException.conflict("Review changed; reload before moderating");
        }
        return require(review);
    }

    PublicPage publicReviews(UUID restaurant, ReviewParameters p) {
        boolean exists = Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM restaurants r WHERE r.id=? AND r.status='ACTIVE' " +
                        "AND EXISTS(SELECT 1 FROM branches b WHERE b.restaurant_id=r.id AND b.status='ACTIVE'))",
                Boolean.class, restaurant));
        if (!exists) throw ReviewException.restaurantMissing();
        long total = jdbc.queryForObject("SELECT count(*) FROM reviews WHERE restaurant_id=? AND moderation_status='VISIBLE'",
                Long.class, restaurant);
        var rows = jdbc.query("SELECT id,branch_id,rating,comment,created_at,updated_at FROM reviews " +
                        "WHERE restaurant_id=? AND moderation_status='VISIBLE' " +
                        "ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?",
                (r, n) -> new PublicReview(r.getObject(1, UUID.class), r.getObject(2, UUID.class),
                        r.getInt(3), r.getString(4), r.getTimestamp(5).toInstant(), r.getTimestamp(6).toInstant()),
                restaurant, p.size(), p.offset());
        var average = jdbc.queryForObject("SELECT round(avg(rating),2) FROM reviews WHERE restaurant_id=? " +
                "AND moderation_status='VISIBLE'", java.math.BigDecimal.class, restaurant);
        if (average != null) average = average.setScale(2, RoundingMode.HALF_UP);
        return new PublicPage(rows, p.page(), p.size(), total, new RatingSummary(average, total));
    }

    private CustomerReview require(UUID review) {
        return jdbc.query("SELECT * FROM reviews WHERE id=?", this::customer, review).stream()
                .findFirst().orElseThrow(ReviewException::missing);
    }

    private CustomerReview customer(java.sql.ResultSet r, int n) throws java.sql.SQLException {
        return new CustomerReview(r.getObject("id", UUID.class), r.getObject("order_id", UUID.class),
                r.getObject("restaurant_id", UUID.class), r.getObject("branch_id", UUID.class),
                r.getInt("rating"), r.getString("comment"), ReviewStatus.valueOf(r.getString("moderation_status")),
                r.getLong("version"), r.getTimestamp("created_at").toInstant(), r.getTimestamp("updated_at").toInstant());
    }
}
