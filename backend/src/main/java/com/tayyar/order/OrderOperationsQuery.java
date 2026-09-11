package com.tayyar.order;

import static com.tayyar.order.OrderOperationsDtos.*;

import org.springframework.jdbc.core.*;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

@Repository
public class OrderOperationsQuery {
    private static final String SUMMARY =
            """
SELECT o.id,o.status,o.merchandise_subtotal,o.delivery_fee,o.discount_total,o.final_total,
       o.currency,o.version,o.created_at,r.id AS restaurant_id,r.name AS restaurant_name,
       b.id AS branch_id,b.name AS branch_name
FROM orders o JOIN restaurants r ON r.id=o.restaurant_id JOIN branches b ON b.id=o.branch_id
""";

    private final JdbcTemplate jdbc;

    public OrderOperationsQuery(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Page<Summary> customer(UUID customer, OrderStatus status, Window window) {
        String filter = status == null ? "" : " AND o.status=?";
        List<Object> arguments = new ArrayList<>(List.of(customer));
        if (status != null) arguments.add(status.name());
        long total =
                jdbc.queryForObject(
                        "SELECT count(*) FROM orders o WHERE o.customer_id=?" + filter,
                        Long.class,
                        arguments.toArray());
        arguments.add(window.size());
        arguments.add(window.offset());
        var rows =
                jdbc.query(
                        SUMMARY
                                + " WHERE o.customer_id=?"
                                + filter
                                + " ORDER BY o.created_at DESC,o.id DESC LIMIT ? OFFSET ?",
                        this::mapSummary,
                        arguments.toArray());
        return new Page<>(rows, window.page(), window.size(), total);
    }

    public Page<Summary> queue(
            UUID actor,
            boolean owner,
            UUID restaurant,
            UUID branch,
            OrderStatus status,
            Window window) {
        StringBuilder filter =
                new StringBuilder(
                        " WHERE o.restaurant_id=? AND o.status IN"
                                + " ('PLACED','ACCEPTED','PREPARING','READY_FOR_PICKUP')");
        List<Object> arguments = new ArrayList<>(List.of(restaurant));
        if (branch != null) {
            filter.append(" AND o.branch_id=?");
            arguments.add(branch);
        }
        if (status != null) {
            filter.append(" AND o.status=?");
            arguments.add(status.name());
        }
        if (!owner) {
            filter.append(
                    " AND EXISTS(SELECT 1 FROM branch_staff_assignments s WHERE"
                            + " s.branch_id=o.branch_id AND s.restaurant_id=o.restaurant_id AND"
                            + " s.user_id=?)");
            arguments.add(actor);
        }
        long total =
                jdbc.queryForObject(
                        "SELECT count(*) FROM orders o" + filter, Long.class, arguments.toArray());
        arguments.add(window.size());
        arguments.add(window.offset());
        var rows =
                jdbc.query(
                        SUMMARY + filter + " ORDER BY o.created_at,o.id LIMIT ? OFFSET ?",
                        this::mapSummary,
                        arguments.toArray());
        return new Page<>(rows, window.page(), window.size(), total);
    }

    public Details details(UUID order) {
        Summary summary = summary(order);
        var items =
                jdbc.query(
                        "SELECT menu_item_id,purchased_name,unit_price,quantity,line_subtotal FROM"
                                + " order_items WHERE order_id=? ORDER BY created_at,id LIMIT 101",
                        (row, number) ->
                                new PurchasedItem(
                                        row.getObject(1, UUID.class),
                                        row.getString(2),
                                        row.getBigDecimal(3),
                                        row.getInt(4),
                                        row.getBigDecimal(5)),
                        order);
        if (items.size() > 100)
            throw OrderOperationsException.conflict("Order item limit exceeded");
        DeliveryAddress address =
                jdbc
                        .query(
                                "SELECT"
                                    + " label,street,building,floor,apartment,landmark,instructions,"
                                    + "city,region,postal_code,country_code,latitude,longitude,delivery_zone_name,managed_city_name"
                                    + " FROM order_address_snapshots WHERE order_id=?",
                                (row, number) ->
                                        new DeliveryAddress(
                                                row.getString(1),
                                                row.getString(2),
                                                row.getString(3),
                                                row.getString(4),
                                                row.getString(5),
                                                row.getString(6),
                                                row.getString(7),
                                                row.getString(8),
                                                row.getString(9),
                                                row.getString(10),
                                                row.getString(11),
                                                row.getBigDecimal(12),
                                                row.getBigDecimal(13),
                                                row.getString(14),
                                                row.getString(15)),
                                order)
                        .stream()
                        .findFirst()
                        .orElseThrow(OrderOperationsException::missing);
        PaymentView payment =
                jdbc
                        .query(
                                "SELECT method,status FROM payments WHERE order_id=? ORDER BY"
                                        + " created_at,id LIMIT 1",
                                (row, number) ->
                                        new PaymentView(row.getString(1), row.getString(2)),
                                order)
                        .stream()
                        .findFirst()
                        .orElse(null);
        var history =
                jdbc.query(
                        "SELECT previous_status,new_status,reason,occurred_at FROM"
                            + " order_status_history WHERE order_id=? ORDER BY occurred_at,id LIMIT"
                            + " 101",
                        (row, number) ->
                                new HistoryView(
                                        row.getString(1) == null
                                                ? null
                                                : OrderStatus.valueOf(row.getString(1)),
                                        OrderStatus.valueOf(row.getString(2)),
                                        row.getString(3),
                                        row.getTimestamp(4).toInstant()),
                        order);
        if (history.size() > 100)
            throw OrderOperationsException.conflict("Order history limit exceeded");
        return new Details(summary, items, address, payment, history);
    }

    public Summary summary(UUID order) {
        return jdbc.query(SUMMARY + " WHERE o.id=?", this::mapSummary, order).stream()
                .findFirst()
                .orElseThrow(OrderOperationsException::missing);
    }

    public OrderStatus status(UUID order) {
        return jdbc
                .query(
                        "SELECT status FROM orders WHERE id=?",
                        (row, number) -> OrderStatus.valueOf(row.getString(1)),
                        order)
                .stream()
                .findFirst()
                .orElseThrow(OrderOperationsException::missing);
    }

    private Summary mapSummary(ResultSet row, int number) throws SQLException {
        return new Summary(
                row.getObject("id", UUID.class),
                OrderStatus.valueOf(row.getString("status")),
                new PublicPlace(
                        row.getObject("restaurant_id", UUID.class),
                        row.getString("restaurant_name")),
                new PublicPlace(
                        row.getObject("branch_id", UUID.class), row.getString("branch_name")),
                row.getBigDecimal("merchandise_subtotal"),
                row.getBigDecimal("delivery_fee"),
                row.getBigDecimal("discount_total"),
                row.getBigDecimal("final_total"),
                row.getString("currency"),
                row.getLong("version"),
                row.getTimestamp("created_at").toInstant());
    }
}
