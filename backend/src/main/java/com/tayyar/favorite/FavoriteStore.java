package com.tayyar.favorite;

import static com.tayyar.favorite.FavoriteDtos.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

@Repository
class FavoriteStore {
    private final JdbcTemplate jdbc;
    FavoriteStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    void add(UUID customer, UUID restaurant, Instant now) {
        int inserted = jdbc.update("INSERT INTO favorites(customer_id,restaurant_id,created_at) " +
                        "SELECT ?,r.id,? FROM restaurants r WHERE r.id=? AND r.status='ACTIVE' " +
                        "AND EXISTS(SELECT 1 FROM branches b WHERE b.restaurant_id=r.id AND b.status='ACTIVE') " +
                        "ON CONFLICT(customer_id,restaurant_id) DO NOTHING",
                customer, Timestamp.from(now), restaurant);
        if (inserted == 0 && !exists(customer, restaurant)) throw FavoriteException.restaurantMissing();
    }

    void remove(UUID customer, UUID restaurant) {
        jdbc.update("DELETE FROM favorites WHERE customer_id=? AND restaurant_id=?", customer, restaurant);
    }

    Page list(UUID customer, FavoriteParameters p) {
        String visible = " FROM favorites f JOIN restaurants r ON r.id=f.restaurant_id " +
                "WHERE f.customer_id=? AND r.status='ACTIVE' AND EXISTS(SELECT 1 FROM branches b " +
                "WHERE b.restaurant_id=r.id AND b.status='ACTIVE')";
        long total = jdbc.queryForObject("SELECT count(*)" + visible, Long.class, customer);
        var rows = jdbc.query("SELECT r.id,r.name,r.description,f.created_at" + visible +
                        " ORDER BY f.created_at DESC,r.id DESC LIMIT ? OFFSET ?",
                (r, n) -> new Favorite(r.getObject(1, UUID.class), r.getString(2), r.getString(3),
                        r.getTimestamp(4).toInstant()), customer, p.size(), p.offset());
        return new Page(rows, p.page(), p.size(), total);
    }

    private boolean exists(UUID customer, UUID restaurant) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM favorites WHERE customer_id=? AND restaurant_id=?)",
                Boolean.class, customer, restaurant));
    }
}
