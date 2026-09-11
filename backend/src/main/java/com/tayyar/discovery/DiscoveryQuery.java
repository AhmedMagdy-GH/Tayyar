package com.tayyar.discovery;

import static com.tayyar.discovery.DiscoveryDtos.*;

import com.tayyar.branch.BranchReadSql;
import com.tayyar.delivery.DeliveryReadSql;
import com.tayyar.menu.MenuReadSql;

import org.springframework.jdbc.core.*;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;

import java.sql.*;
import java.time.Instant;
import java.util.*;

/** Bounded scalar reads; SQL fragments below are exclusively server-owned. */
@Repository
public class DiscoveryQuery {
    private final NamedParameterJdbcTemplate jdbc;

    public DiscoveryQuery(JdbcTemplate jdbc) {
        this.jdbc = new NamedParameterJdbcTemplate(jdbc);
    }

    public UUID ownedZone(UUID customer, UUID address) {
        var rows =
                jdbc.query(
                        "SELECT delivery_zone_id FROM customer_addresses WHERE id=:address AND"
                            + " user_id=:user",
                        Map.of("address", address, "user", customer),
                        (rs, n) -> rs.getObject(1, UUID.class));
        if (rows.isEmpty()) throw DiscoveryException.missing();
        if (rows.getFirst() == null)
            throw DiscoveryException.invalid("Select a delivery zone for this address");
        return rows.getFirst();
    }

    private MapSqlParameterSource parameters(Filter f, UUID zone, Window w, Instant now) {
        return new MapSqlParameterSource()
                .addValue("zone", zone, Types.OTHER)
                .addValue("now", Timestamp.from(now))
                .addValue("query", f.pattern())
                .addValue("category", f.categoryId(), Types.OTHER)
                .addValue("fee", f.maxDeliveryFee())
                .addValue("minimum", f.maxMinimumOrder())
                .addValue("limit", w.size())
                .addValue("offset", w.offset());
    }

    private String candidates(Filter f, UUID zone, UUID restaurant) {
        String sql =
                """
                WITH candidates AS (
                SELECT r.id AS restaurant_id,r.name AS restaurant_name,r.description,
                       b.id,b.name,b.address_line1,b.city,b.timezone,b.paused,
                       %s AS schedule_open, (%s)='SERVICEABLE' AS serviceable,
                       d.delivery_fee,d.minimum_order,d.eta_min_minutes,d.eta_max_minutes
                FROM restaurants r JOIN branches b ON b.restaurant_id=r.id
                LEFT JOIN delivery_zones z ON z.id=:zone
                LEFT JOIN cities c ON c.id=z.city_id
                LEFT JOIN branch_delivery_zones d ON d.branch_id=b.id AND d.delivery_zone_id=z.id
                %s
                WHERE r.status='ACTIVE' AND b.status='ACTIVE'
                """
                        .formatted(
                                BranchReadSql.OPEN,
                                DeliveryReadSql.REASON,
                                BranchReadSql.HOURS_JOIN);
        if (restaurant != null) sql += " AND r.id=:restaurant";
        if (!f.query().isEmpty() || f.categoryId() != null || f.availableOnly()) {
            sql += " AND (";
            if (f.categoryId() == null && !f.availableOnly())
                sql += "r.name ILIKE :query ESCAPE '!' OR ";
            sql +=
                    """
                    EXISTS (SELECT 1 FROM restaurant_menus m
                    JOIN menu_categories c ON c.menu_id=m.id AND c.active
                    LEFT JOIN menu_items i ON i.category_id=c.id AND i.active
                    LEFT JOIN branch_menu_item_overrides o ON o.item_id=i.id AND o.branch_id=b.id
                    WHERE m.restaurant_id=r.id AND m.active
                    """;
            if (f.categoryId() != null) sql += " AND c.id=:category";
            if (f.availableOnly()) sql += " AND " + MenuReadSql.AVAILABLE;
            if (!f.query().isEmpty())
                sql +=
                        " AND (r.name ILIKE :query ESCAPE '!' OR c.name ILIKE :query ESCAPE '!' OR"
                            + " i.name ILIKE :query ESCAPE '!')";
            sql += "))";
        }
        sql +=
                "), eligible AS (SELECT *, (schedule_open AND NOT paused) AS open_now FROM"
                    + " candidates WHERE true";
        if (zone != null) sql += " AND serviceable";
        if (f.openNow() != null)
            sql +=
                    f.openNow()
                            ? " AND schedule_open AND NOT paused"
                            : " AND NOT (schedule_open AND NOT paused)";
        if (f.maxDeliveryFee() != null) sql += " AND delivery_fee<=:fee";
        if (f.maxMinimumOrder() != null) sql += " AND minimum_order<=:minimum";
        return sql + ") ";
    }

    public Page<Restaurant> restaurants(
            Filter f, UUID zone, Window w, Instant now, UUID restaurant) {
        var p = parameters(f, zone, w, now).addValue("restaurant", restaurant);
        String from =
                candidates(f, zone, restaurant)
                        + """
, cards AS (SELECT restaurant_id, restaurant_name, description, count(*) AS branch_count,
    bool_or(open_now) AS open_now, min(delivery_fee) AS delivery_fee,
    min(minimum_order) AS minimum_order,min(eta_min_minutes) AS eta_min_minutes
    FROM eligible GROUP BY restaurant_id,restaurant_name,description)
""";
        long count = jdbc.queryForObject(from + " SELECT count(*) FROM cards", p, Long.class);
        String sort =
                switch (f.sort()) {
                    case NAME -> "lower(restaurant_name)";
                    case DELIVERY_FEE -> "delivery_fee";
                    case MINIMUM_ORDER -> "minimum_order";
                    case ETA -> "eta_min_minutes";
                };
        var rows =
                jdbc.query(
                        from
                                + " SELECT * FROM cards ORDER BY "
                                + sort
                                + " ASC NULLS LAST,restaurant_id LIMIT :limit OFFSET :offset",
                        p,
                        (r, n) ->
                                new Restaurant(
                                        r.getObject("restaurant_id", UUID.class),
                                        r.getString("restaurant_name"),
                                        r.getString("description"),
                                        r.getLong("branch_count"),
                                        r.getBoolean("open_now"),
                                        zone == null ? null : true,
                                        r.getBigDecimal("delivery_fee"),
                                        r.getBigDecimal("minimum_order"),
                                        r.getObject("eta_min_minutes", Integer.class),
                                        "EGP"));
        return new Page<>(rows, w.page(), w.size(), count);
    }

    public void requireRestaurant(UUID id) {
        if (!Boolean.TRUE.equals(
                jdbc.queryForObject(
                        """
SELECT EXISTS(SELECT 1 FROM restaurants r WHERE r.id=:id AND r.status='ACTIVE'
AND EXISTS(SELECT 1 FROM branches b WHERE b.restaurant_id=r.id AND b.status='ACTIVE'))
""",
                        Map.of("id", id),
                        Boolean.class))) throw DiscoveryException.missing();
    }

    public Page<Branch> branches(UUID restaurant, Filter f, UUID zone, Window w, Instant now) {
        requireRestaurant(restaurant);
        var p = parameters(f, zone, w, now).addValue("restaurant", restaurant);
        String from = candidates(f, zone, restaurant);
        long total = jdbc.queryForObject(from + "SELECT count(*) FROM eligible", p, Long.class);
        String sort =
                switch (f.sort()) {
                    case NAME -> "lower(name)";
                    case DELIVERY_FEE -> "delivery_fee";
                    case MINIMUM_ORDER -> "minimum_order";
                    case ETA -> "eta_min_minutes";
                };
        var rows =
                jdbc.query(
                        from
                                + "SELECT * FROM eligible ORDER BY "
                                + sort
                                + " ASC NULLS LAST,id LIMIT :limit OFFSET :offset",
                        p,
                        (r, n) ->
                                new Branch(
                                        r.getObject("id", UUID.class),
                                        r.getString("name"),
                                        r.getString("address_line1"),
                                        r.getString("city"),
                                        r.getString("timezone"),
                                        r.getBoolean("open_now"),
                                        r.getBoolean("paused")
                                                ? "PAUSED"
                                                : r.getBoolean("open_now") ? "OPEN" : "CLOSED",
                                        zone == null ? null : true,
                                        r.getBigDecimal("delivery_fee"),
                                        r.getBigDecimal("minimum_order"),
                                        r.getObject("eta_min_minutes", Integer.class),
                                        r.getObject("eta_max_minutes", Integer.class),
                                        "EGP"));
        return new Page<>(rows, w.page(), w.size(), total);
    }

    record Header(UUID id, String name, String currency) {}

    record CategoryRow(UUID id, String name, String description) {}

    public Menu menu(
            UUID restaurant, UUID branch, Window categories, Window items, boolean availableOnly) {
        var p =
                new MapSqlParameterSource()
                        .addValue("restaurant", restaurant)
                        .addValue("branch", branch)
                        .addValue("limit", categories.size())
                        .addValue("offset", categories.offset())
                        .addValue("itemOffset", items.offset())
                        .addValue("itemEnd", items.offset() + items.size());
        var headers =
                jdbc.query(
                        """
SELECT m.id,m.name,m.currency FROM restaurant_menus m JOIN restaurants r ON r.id=m.restaurant_id
JOIN branches b ON b.restaurant_id=r.id WHERE r.id=:restaurant AND b.id=:branch
AND r.status='ACTIVE' AND b.status='ACTIVE' AND m.active
""",
                        p,
                        (r, n) ->
                                new Header(
                                        r.getObject("id", UUID.class),
                                        r.getString("name"),
                                        r.getString("currency")));
        if (headers.isEmpty()) throw DiscoveryException.missing();
        var header = headers.getFirst();
        p.addValue("menu", header.id());
        long total =
                jdbc.queryForObject(
                        "SELECT count(*) FROM menu_categories WHERE menu_id=:menu AND active",
                        p,
                        Long.class);
        var cats =
                jdbc.query(
                        "SELECT id,name,description FROM menu_categories WHERE menu_id=:menu AND"
                            + " active ORDER BY position,id LIMIT :limit OFFSET :offset",
                        p,
                        (r, n) ->
                                new CategoryRow(
                                        r.getObject("id", UUID.class),
                                        r.getString("name"),
                                        r.getString("description")));
        Map<UUID, List<Item>> contents = new HashMap<>();
        Map<UUID, Long> totals = new HashMap<>();
        if (!cats.isEmpty()) {
            p.addValue("categories", cats.stream().map(CategoryRow::id).toList());
            // One batch, including count-only rows for empty/out-of-range item pages.
            String effective =
                    """
WITH effective AS (SELECT i.id,i.category_id,i.name,i.description,i.position,
%s AS effective_price,%s AS effective_available
FROM menu_items i JOIN menu_categories c ON c.id=i.category_id
JOIN restaurant_menus m ON m.id=c.menu_id
LEFT JOIN branch_menu_item_overrides o ON o.item_id=i.id AND o.branch_id=:branch
WHERE c.id IN (:categories) AND m.active AND c.active AND i.active %s),
ranked AS (SELECT *,row_number() OVER(PARTITION BY category_id ORDER BY position,id) AS rn FROM effective),
totals AS (SELECT category_id,count(*) AS total FROM effective GROUP BY category_id)
SELECT totals.category_id,totals.total,r.id,r.name,r.description,r.effective_price,r.effective_available
FROM totals LEFT JOIN ranked r ON r.category_id=totals.category_id AND r.rn>:itemOffset AND r.rn<=:itemEnd
ORDER BY totals.category_id,r.rn
"""
                            .formatted(
                                    MenuReadSql.PRICE,
                                    MenuReadSql.AVAILABLE,
                                    availableOnly ? " AND " + MenuReadSql.AVAILABLE : "");
            jdbc.query(
                    effective,
                    p,
                    (RowCallbackHandler)
                            r -> {
                                UUID cat = r.getObject("category_id", UUID.class);
                                totals.put(cat, r.getLong("total"));
                                if (r.getObject("id") != null)
                                    contents.computeIfAbsent(cat, key -> new ArrayList<>())
                                            .add(
                                                    new Item(
                                                            r.getObject("id", UUID.class),
                                                            r.getString("name"),
                                                            r.getString("description"),
                                                            r.getBigDecimal("effective_price"),
                                                            r.getBoolean("effective_available")));
                            });
        }
        var rows =
                cats.stream()
                        .map(
                                c ->
                                        new Category(
                                                c.id(),
                                                c.name(),
                                                c.description(),
                                                new Page<>(
                                                        contents.getOrDefault(c.id(), List.of()),
                                                        items.page(),
                                                        items.size(),
                                                        totals.getOrDefault(c.id(), 0L))))
                        .toList();
        return new Menu(
                header.id(),
                branch,
                header.name(),
                header.currency(),
                new Page<>(rows, categories.page(), categories.size(), total));
    }

    public Page<Zone> zones(UUID city, Window w) {
        var p =
                new MapSqlParameterSource()
                        .addValue("city", city)
                        .addValue("limit", w.size())
                        .addValue("offset", w.offset());
        String from =
                " FROM delivery_zones z JOIN cities c ON c.id=z.city_id WHERE z.active AND c.active"
                        + (city == null ? "" : " AND c.id=:city");
        long total = jdbc.queryForObject("SELECT count(*)" + from, p, Long.class);
        var rows =
                jdbc.query(
                        "SELECT z.id,z.name,z.city_id,c.name AS city_name"
                                + from
                                + " ORDER BY lower(c.name),lower(z.name),z.id LIMIT :limit OFFSET"
                                + " :offset",
                        p,
                        (r, n) ->
                                new Zone(
                                        r.getObject("id", UUID.class),
                                        r.getString("name"),
                                        r.getObject("city_id", UUID.class),
                                        r.getString("city_name")));
        return new Page<>(rows, w.page(), w.size(), total);
    }
}
