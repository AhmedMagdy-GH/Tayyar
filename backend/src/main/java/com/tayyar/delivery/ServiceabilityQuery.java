package com.tayyar.delivery;

import static com.tayyar.delivery.DeliveryDtos.*;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/** Scalar, joined reads: no lazy collections or one-query-per-branch evaluation. */
@Repository
public class ServiceabilityQuery {
    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;

    public ServiceabilityQuery(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.named = new NamedParameterJdbcTemplate(jdbc);
    }

    public void requireActiveZone(UUID zone) {
        if (!Boolean.TRUE.equals(
                jdbc.queryForObject(
                        """
SELECT EXISTS(SELECT 1 FROM delivery_zones z JOIN cities c ON c.id=z.city_id
              WHERE z.id=? AND z.active AND c.active)
""",
                        Boolean.class,
                        zone))) throw DeliveryException.invalid("Select an active delivery zone");
    }

    public Eligibility forOwnedAddress(UUID user, UUID branch, UUID address) {
        var rows =
                jdbc.query(
                        """
SELECT a.delivery_zone_id, b.id AS branch_id, r.status AS restaurant_status,
       b.status AS branch_status, b.paused, z.active AS zone_active,
       c.active AS city_active, d.id AS rule_id, d.enabled,
       d.delivery_fee, d.minimum_order, d.eta_min_minutes, d.eta_max_minutes, %s AS reason
FROM customer_addresses a
CROSS JOIN branches b
JOIN restaurants r ON r.id=b.restaurant_id
LEFT JOIN delivery_zones z ON z.id=a.delivery_zone_id
LEFT JOIN cities c ON c.id=z.city_id
LEFT JOIN branch_delivery_zones d ON d.branch_id=b.id AND d.delivery_zone_id=z.id
WHERE a.id=? AND a.user_id=? AND b.id=?
"""
                                .formatted(DeliveryReadSql.REASON),
                        (rs, n) -> map(rs),
                        address,
                        user,
                        branch);
        if (rows.isEmpty()) throw DeliveryException.missing();
        return rows.getFirst();
    }

    /** Internal bounded batch primitive for a future caller; missing branches are omitted. */
    public List<Eligibility> forZone(UUID zone, Collection<UUID> branches) {
        if (zone == null || branches == null || branches.size() > 100)
            throw DeliveryException.invalid("A zone and at most 100 branches are required");
        if (branches.isEmpty()) return List.of();
        return named.query(
                """
SELECT z.id AS delivery_zone_id, b.id AS branch_id, r.status AS restaurant_status,
       b.status AS branch_status, b.paused, z.active AS zone_active,
       c.active AS city_active, d.id AS rule_id, d.enabled,
       d.delivery_fee, d.minimum_order, d.eta_min_minutes, d.eta_max_minutes, %s AS reason
FROM branches b JOIN restaurants r ON r.id=b.restaurant_id
JOIN delivery_zones z ON z.id=:zone
JOIN cities c ON c.id=z.city_id
LEFT JOIN branch_delivery_zones d ON d.branch_id=b.id AND d.delivery_zone_id=z.id
WHERE b.id IN (:branches)
"""
                        .formatted(DeliveryReadSql.REASON),
                Map.of("zone", zone, "branches", branches),
                (rs, n) -> map(rs));
    }

    private Eligibility map(ResultSet rs) throws SQLException {
        UUID zone = rs.getObject("delivery_zone_id", UUID.class);
        Reason reason = Reason.valueOf(rs.getString("reason"));
        boolean ok = reason == Reason.SERVICEABLE;
        return new Eligibility(
                rs.getObject("branch_id", UUID.class),
                zone,
                ok,
                reason,
                "EGP",
                ok ? rs.getBigDecimal("delivery_fee") : null,
                ok ? rs.getBigDecimal("minimum_order") : null,
                ok ? rs.getInt("eta_min_minutes") : null,
                ok ? rs.getInt("eta_max_minutes") : null);
    }
}
