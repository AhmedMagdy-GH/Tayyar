package com.tayyar.cart;

import static com.tayyar.cart.CartDtos.*;

import com.tayyar.branch.BranchReadSql;
import com.tayyar.menu.MenuReadSql;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

/** Current cart state is loaded in two bounded, scalar statements. */
@Repository
public class CartQuery {
    static final int MAX_LINES = 100;
    private final NamedParameterJdbcTemplate jdbc;

    public CartQuery(JdbcTemplate jdbc) {
        this.jdbc = new NamedParameterJdbcTemplate(jdbc);
    }

    record Header(
            UUID id,
            UUID branch,
            UUID restaurant,
            String branchName,
            String restaurantName,
            String state,
            boolean openNow,
            long version) {}

    public Optional<View> active(UUID customer, Instant now) {
        var parameters =
                new MapSqlParameterSource()
                        .addValue("customer", customer)
                        .addValue("now", Timestamp.from(now));
        var headers =
                jdbc.query(
                        """
SELECT ca.id,ca.branch_id,ca.restaurant_id,ca.version,b.name AS branch_name,
       r.name AS restaurant_name,%s AS schedule_open,b.paused,b.status AS branch_status,
       r.status AS restaurant_status
FROM carts ca JOIN branches b ON b.id=ca.branch_id JOIN restaurants r ON r.id=ca.restaurant_id
%s
WHERE ca.customer_id=:customer AND ca.status='ACTIVE'
"""
                                .formatted(BranchReadSql.OPEN, BranchReadSql.HOURS_JOIN),
                        parameters,
                        (row, number) -> {
                            boolean scheduleOpen = row.getBoolean("schedule_open");
                            boolean open =
                                    scheduleOpen
                                            && !row.getBoolean("paused")
                                            && "ACTIVE".equals(row.getString("branch_status"))
                                            && "ACTIVE".equals(row.getString("restaurant_status"));
                            String state =
                                    !"ACTIVE".equals(row.getString("restaurant_status"))
                                            ? "RESTAURANT_SUSPENDED"
                                            : !"ACTIVE".equals(row.getString("branch_status"))
                                                    ? "INACTIVE"
                                                    : row.getBoolean("paused")
                                                            ? "PAUSED"
                                                            : open ? "OPEN" : "CLOSED";
                            return new Header(
                                    row.getObject("id", UUID.class),
                                    row.getObject("branch_id", UUID.class),
                                    row.getObject("restaurant_id", UUID.class),
                                    row.getString("branch_name"),
                                    row.getString("restaurant_name"),
                                    state,
                                    open,
                                    row.getLong("version"));
                        });
        if (headers.isEmpty()) return Optional.empty();
        Header header = headers.getFirst();
        var lines =
                jdbc.query(
                        """
SELECT ci.id,ci.menu_item_id,ci.quantity,ci.acknowledged_unit_price,ci.version,
       i.name,%s AS current_unit_price,%s AS currently_available
FROM cart_items ci JOIN menu_items i ON i.id=ci.menu_item_id
JOIN menu_categories c ON c.id=i.category_id
JOIN restaurant_menus m ON m.id=c.menu_id
LEFT JOIN branch_menu_item_overrides o ON o.item_id=i.id AND o.branch_id=:branch
WHERE ci.cart_id=:cart ORDER BY ci.created_at,ci.id LIMIT :limit
"""
                                .formatted(MenuReadSql.PRICE, MenuReadSql.AVAILABLE),
                        new MapSqlParameterSource()
                                .addValue("cart", header.id())
                                .addValue("branch", header.branch())
                                .addValue("limit", MAX_LINES + 1),
                        (row, number) -> {
                            BigDecimal current = row.getBigDecimal("current_unit_price");
                            BigDecimal acknowledged = row.getBigDecimal("acknowledged_unit_price");
                            int quantity = row.getInt("quantity");
                            return new Line(
                                    row.getObject("id", UUID.class),
                                    row.getObject("menu_item_id", UUID.class),
                                    row.getString("name"),
                                    quantity,
                                    acknowledged,
                                    current,
                                    acknowledged.compareTo(current) != 0,
                                    row.getBoolean("currently_available"),
                                    current.multiply(BigDecimal.valueOf(quantity)),
                                    row.getLong("version"));
                        });
        if (lines.size() > MAX_LINES)
            throw CartException.conflict("Cart line limit exceeded; contact support");
        BigDecimal subtotal =
                lines.stream()
                        .map(Line::lineSubtotal)
                        .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
        return Optional.of(
                new View(
                        header.id(),
                        new BranchView(
                                header.branch(),
                                header.restaurant(),
                                header.branchName(),
                                header.restaurantName(),
                                header.state(),
                                header.openNow()),
                        lines,
                        subtotal,
                        "EGP",
                        header.version()));
    }

    record Effective(
            UUID branch,
            UUID restaurant,
            UUID item,
            String restaurantStatus,
            String branchStatus,
            BigDecimal price,
            boolean available) {}

    public Effective effective(UUID branch, UUID item) {
        var rows =
                jdbc.query(
                        """
SELECT b.id AS branch_id,b.restaurant_id,i.id AS item_id,r.status AS restaurant_status,
       b.status AS branch_status,%s AS effective_price,%s AS effective_available
FROM branches b JOIN restaurants r ON r.id=b.restaurant_id
JOIN menu_items i ON i.restaurant_id=b.restaurant_id AND i.id=:item
JOIN menu_categories c ON c.id=i.category_id
JOIN restaurant_menus m ON m.id=c.menu_id
LEFT JOIN branch_menu_item_overrides o ON o.item_id=i.id AND o.branch_id=b.id
WHERE b.id=:branch
"""
                                .formatted(MenuReadSql.PRICE, MenuReadSql.AVAILABLE),
                        Map.of("branch", branch, "item", item),
                        (row, number) ->
                                new Effective(
                                        row.getObject("branch_id", UUID.class),
                                        row.getObject("restaurant_id", UUID.class),
                                        row.getObject("item_id", UUID.class),
                                        row.getString("restaurant_status"),
                                        row.getString("branch_status"),
                                        row.getBigDecimal("effective_price"),
                                        row.getBoolean("effective_available")));
        if (rows.isEmpty()) throw CartException.missing();
        return rows.getFirst();
    }
}
