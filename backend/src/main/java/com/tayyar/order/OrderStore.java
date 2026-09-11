package com.tayyar.order;

import static com.tayyar.order.OrderDtos.*;

import org.springframework.jdbc.core.*;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@Repository
public class OrderStore {
    private final JdbcTemplate jdbc;

    public OrderStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    record State(UUID id, OrderStatus status, long version) {}

    public void create(UUID id, Draft draft, Money money, Instant now) {
        jdbc.update(
                "INSERT INTO orders(id,customer_id,restaurant_id,branch_id,status,currency,"
                    + "merchandise_subtotal,delivery_fee,discount_total,final_total,created_at,updated_at)"
                    + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                id,
                draft.customerId(),
                draft.restaurantId(),
                draft.branchId(),
                draft.initialStatus().name(),
                money.currency(),
                money.merchandiseSubtotal(),
                money.deliveryFee(),
                money.discountTotal(),
                money.finalTotal(),
                Timestamp.from(now),
                Timestamp.from(now));
    }

    public void items(UUID order, UUID restaurant, List<Item> items, Instant now) {
        List<Object[]> batch =
                items.stream()
                        .map(
                                item ->
                                        new Object[] {
                                            item.id(),
                                            order,
                                            restaurant,
                                            item.originalMenuItemId(),
                                            item.purchasedName(),
                                            item.unitPrice(),
                                            item.quantity(),
                                            item.lineSubtotal(),
                                            Timestamp.from(now)
                                        })
                        .toList();
        jdbc.batchUpdate(
                "INSERT INTO"
                    + " order_items(id,order_id,restaurant_id,menu_item_id,purchased_name,unit_price,quantity,line_subtotal,created_at)"
                    + " VALUES (?,?,?,?,?,?,?,?,?)",
                batch);
    }

    public void address(UUID order, AddressSnapshot address, Instant now) {
        jdbc.update(
                "INSERT INTO"
                    + " order_address_snapshots(order_id,label,street,building,floor,apartment,"
                    + "landmark,instructions,city,region,postal_code,country_code,latitude,longitude,delivery_zone_id,delivery_zone_name,managed_city_name,created_at)"
                    + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                order,
                address.label(),
                address.street(),
                address.building(),
                address.floor(),
                address.apartment(),
                address.landmark(),
                address.instructions(),
                address.city(),
                address.region(),
                address.postalCode(),
                address.countryCode(),
                address.latitude(),
                address.longitude(),
                address.deliveryZoneId(),
                address.deliveryZoneName(),
                address.managedCityName(),
                Timestamp.from(now));
    }

    public State state(UUID id) {
        return jdbc
                .query(
                        "SELECT id,status,version FROM orders WHERE id=?",
                        (row, number) ->
                                new State(
                                        row.getObject("id", UUID.class),
                                        OrderStatus.valueOf(row.getString("status")),
                                        row.getLong("version")),
                        id)
                .stream()
                .findFirst()
                .orElseThrow(OrderException::missing);
    }

    public void transition(State state, OrderStatus target, Instant now) {
        if (jdbc.update(
                        "UPDATE orders SET status=?,version=version+1,updated_at=? WHERE id=? AND"
                                + " version=?",
                        target.name(),
                        Timestamp.from(now),
                        state.id(),
                        state.version())
                != 1) throw OrderException.conflict();
    }

    public void history(
            UUID order,
            OrderStatus previous,
            OrderStatus next,
            TransitionActor actor,
            String reason,
            Instant now) {
        jdbc.update(
                "INSERT INTO"
                    + " order_status_history(id,order_id,previous_status,new_status,actor_kind,actor_id,reason,occurred_at)"
                    + " VALUES (?,?,?,?,?,?,?,?)",
                UUID.randomUUID(),
                order,
                previous == null ? null : previous.name(),
                next.name(),
                actor.kind().name(),
                actor.userId(),
                reason,
                Timestamp.from(now));
    }

    public Details details(UUID id) {
        View order =
                jdbc
                        .query(
                                "SELECT * FROM orders WHERE id=?",
                                (row, number) ->
                                        new View(
                                                row.getObject("id", UUID.class),
                                                row.getObject("customer_id", UUID.class),
                                                row.getObject("restaurant_id", UUID.class),
                                                row.getObject("branch_id", UUID.class),
                                                OrderStatus.valueOf(row.getString("status")),
                                                new Money(
                                                        row.getString("currency"),
                                                        row.getBigDecimal("merchandise_subtotal"),
                                                        row.getBigDecimal("delivery_fee"),
                                                        row.getBigDecimal("discount_total"),
                                                        row.getBigDecimal("final_total")),
                                                row.getLong("version"),
                                                row.getTimestamp("created_at").toInstant(),
                                                row.getTimestamp("updated_at").toInstant()),
                                id)
                        .stream()
                        .findFirst()
                        .orElseThrow(OrderException::missing);
        var items =
                jdbc.query(
                        "SELECT id,menu_item_id,purchased_name,unit_price,quantity,line_subtotal"
                            + " FROM order_items WHERE order_id=? ORDER BY created_at,id LIMIT 101",
                        (row, number) ->
                                new Item(
                                        row.getObject("id", UUID.class),
                                        row.getObject("menu_item_id", UUID.class),
                                        row.getString("purchased_name"),
                                        row.getBigDecimal("unit_price"),
                                        row.getInt("quantity"),
                                        row.getBigDecimal("line_subtotal")),
                        id);
        if (items.size() > 100) throw OrderException.conflict();
        AddressSnapshot address =
                jdbc
                        .query(
                                "SELECT * FROM order_address_snapshots WHERE order_id=?",
                                (row, number) ->
                                        new AddressSnapshot(
                                                row.getString("label"),
                                                row.getString("street"),
                                                row.getString("building"),
                                                row.getString("floor"),
                                                row.getString("apartment"),
                                                row.getString("landmark"),
                                                row.getString("instructions"),
                                                row.getString("city"),
                                                row.getString("region"),
                                                row.getString("postal_code"),
                                                row.getString("country_code"),
                                                row.getBigDecimal("latitude"),
                                                row.getBigDecimal("longitude"),
                                                row.getObject("delivery_zone_id", UUID.class),
                                                row.getString("delivery_zone_name"),
                                                row.getString("managed_city_name")),
                                id)
                        .stream()
                        .findFirst()
                        .orElseThrow(OrderException::missing);
        return new Details(order, items, address);
    }

    public List<History> history(UUID order) {
        return jdbc.query(
                "SELECT * FROM order_status_history WHERE order_id=? ORDER BY occurred_at,id LIMIT"
                        + " 101",
                (row, number) ->
                        new History(
                                row.getObject("id", UUID.class),
                                row.getString("previous_status") == null
                                        ? null
                                        : OrderStatus.valueOf(row.getString("previous_status")),
                                OrderStatus.valueOf(row.getString("new_status")),
                                TransitionActor.Kind.valueOf(row.getString("actor_kind")),
                                row.getObject("actor_id", UUID.class),
                                row.getString("reason"),
                                row.getTimestamp("occurred_at").toInstant()),
                order);
    }
}
