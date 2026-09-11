package com.tayyar.cart;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@Repository
public class CartStore {
    private final JdbcTemplate jdbc;

    public CartStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    record LockedCart(UUID id, UUID branch, UUID restaurant, long version) {}

    record LockedLine(UUID id, int quantity, long version) {}

    public void lockCustomer(UUID customer) {
        if (jdbc.query(
                        "SELECT id FROM users WHERE id=? FOR UPDATE",
                        (row, number) -> row.getObject(1, UUID.class),
                        customer)
                .isEmpty()) throw CartException.missing();
    }

    public Optional<LockedCart> lockActive(UUID customer) {
        return jdbc
                .query(
                        "SELECT id,branch_id,restaurant_id,version FROM carts WHERE"
                                + " customer_id=? AND status='ACTIVE' FOR UPDATE",
                        (row, number) ->
                                new LockedCart(
                                        row.getObject("id", UUID.class),
                                        row.getObject("branch_id", UUID.class),
                                        row.getObject("restaurant_id", UUID.class),
                                        row.getLong("version")),
                        customer)
                .stream()
                .findFirst();
    }

    public UUID createCart(UUID customer, UUID branch, UUID restaurant, Instant now) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO"
                    + " carts(id,customer_id,branch_id,restaurant_id,status,created_at,updated_at)"
                    + " VALUES (?,?,?,?,'ACTIVE',?,?)",
                id,
                customer,
                branch,
                restaurant,
                Timestamp.from(now),
                Timestamp.from(now));
        return id;
    }

    public LockedLine addOrIncrement(
            UUID cart,
            UUID item,
            UUID restaurant,
            int added,
            BigDecimal acknowledgedPrice,
            Instant now) {
        var existing = lockLineByMenuItem(cart, item);
        if (existing.isPresent()) {
            LockedLine line = existing.get();
            int quantity;
            try {
                quantity = Math.addExact(line.quantity(), added);
            } catch (ArithmeticException exception) {
                throw CartException.invalid("Quantity must be between 1 and 99");
            }
            CartRules.quantity(quantity);
            if (jdbc.update(
                            "UPDATE cart_items SET quantity=?,version=version+1,updated_at=? WHERE"
                                    + " id=? AND version=?",
                            quantity,
                            Timestamp.from(now),
                            line.id(),
                            line.version())
                    != 1) throw CartException.conflict("Cart item changed; reload and retry");
            return new LockedLine(line.id(), quantity, line.version() + 1);
        }
        if (lineCount(cart) >= CartQuery.MAX_LINES)
            throw CartException.invalid("Cart line limit reached");
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO"
                    + " cart_items(id,cart_id,menu_item_id,restaurant_id,quantity,acknowledged_unit_price,created_at,updated_at)"
                    + " VALUES (?,?,?,?,?,?,?,?)",
                id,
                cart,
                item,
                restaurant,
                added,
                acknowledgedPrice,
                Timestamp.from(now),
                Timestamp.from(now));
        return new LockedLine(id, added, 0);
    }

    public Optional<LockedLine> lockLineByMenuItem(UUID cart, UUID item) {
        return jdbc
                .query(
                        "SELECT id,quantity,version FROM cart_items WHERE cart_id=? AND"
                                + " menu_item_id=? FOR UPDATE",
                        (row, number) ->
                                new LockedLine(
                                        row.getObject("id", UUID.class),
                                        row.getInt("quantity"),
                                        row.getLong("version")),
                        cart,
                        item)
                .stream()
                .findFirst();
    }

    public LockedLine lockLine(UUID cart, UUID line) {
        return jdbc
                .query(
                        "SELECT id,quantity,version FROM cart_items WHERE cart_id=? AND id=? FOR"
                                + " UPDATE",
                        (row, number) ->
                                new LockedLine(
                                        row.getObject("id", UUID.class),
                                        row.getInt("quantity"),
                                        row.getLong("version")),
                        cart,
                        line)
                .stream()
                .findFirst()
                .orElseThrow(CartException::missing);
    }

    public void updateQuantity(LockedLine line, int quantity, Instant now) {
        if (jdbc.update(
                        "UPDATE cart_items SET quantity=?,version=version+1,updated_at=? WHERE id=?"
                                + " AND version=?",
                        quantity,
                        Timestamp.from(now),
                        line.id(),
                        line.version())
                != 1) throw CartException.conflict("Cart item changed; reload and retry");
    }

    public void remove(LockedLine line) {
        if (jdbc.update(
                        "DELETE FROM cart_items WHERE id=? AND version=?",
                        line.id(),
                        line.version())
                != 1) throw CartException.conflict("Cart item changed; reload and retry");
    }

    public int lineCount(UUID cart) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM cart_items WHERE cart_id=?", Integer.class, cart);
    }

    public void touch(LockedCart cart, Instant now) {
        if (jdbc.update(
                        "UPDATE carts SET version=version+1,updated_at=? WHERE id=? AND version=?"
                                + " AND status='ACTIVE'",
                        Timestamp.from(now),
                        cart.id(),
                        cart.version())
                != 1) throw CartException.conflict("Cart changed; reload and retry");
    }

    public void retire(LockedCart cart, String status, Instant now) {
        if (!Set.of("ABANDONED", "REPLACED").contains(status))
            throw new IllegalArgumentException("Unsupported cart retirement status");
        if (jdbc.update(
                        "UPDATE carts SET status=?,version=version+1,updated_at=? WHERE id=? AND"
                                + " version=? AND status='ACTIVE'",
                        status,
                        Timestamp.from(now),
                        cart.id(),
                        cart.version())
                != 1) throw CartException.conflict("Cart changed; reload and retry");
    }
}
