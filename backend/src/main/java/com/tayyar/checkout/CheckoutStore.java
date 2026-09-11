package com.tayyar.checkout;

import com.tayyar.cart.CartStore;
import com.tayyar.order.OrderDtos;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@Repository
public class CheckoutStore {
    private final JdbcTemplate jdbc;

    public CheckoutStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Receipt(
            UUID cart, long version, UUID address, String method, CheckoutDtos.Summary summary) {}

    public record Delivery(OrderDtos.AddressSnapshot address, BigDecimal fee, BigDecimal minimum) {}

    public void requireActiveCustomer(UUID customer) {
        if (!Boolean.TRUE.equals(
                jdbc.queryForObject(
                        "SELECT status='ACTIVE' FROM users WHERE id=?", Boolean.class, customer)))
            throw new CheckoutException(
                    org.springframework.http.HttpStatus.FORBIDDEN,
                    "FORBIDDEN",
                    "Account is not active");
    }

    public Optional<Receipt> receipt(UUID customer, String key) {
        return jdbc
                .query(
                        """
SELECT cr.cart_id,cr.cart_version,cr.saved_address_id,cr.payment_method,
       o.id,o.merchandise_subtotal,o.delivery_fee,o.discount_total,o.final_total,o.currency,o.created_at
FROM checkout_receipts cr JOIN orders o ON o.id=cr.order_id
WHERE cr.customer_id=? AND cr.idempotency_key=?
""",
                        (r, n) ->
                                new Receipt(
                                        r.getObject("cart_id", UUID.class),
                                        r.getLong("cart_version"),
                                        r.getObject("saved_address_id", UUID.class),
                                        r.getString("payment_method"),
                                        new CheckoutDtos.Summary(
                                                r.getObject("id", UUID.class),
                                                "PLACED",
                                                "CASH",
                                                "PENDING",
                                                r.getBigDecimal("merchandise_subtotal"),
                                                r.getBigDecimal("delivery_fee"),
                                                r.getBigDecimal("discount_total"),
                                                r.getBigDecimal("final_total"),
                                                r.getString("currency"),
                                                r.getTimestamp("created_at").toInstant())),
                        customer,
                        key)
                .stream()
                .findFirst();
    }

    public void checkCartOwner(UUID customer, UUID cart) {
        if (jdbc.query(
                        "SELECT id FROM carts WHERE id=? AND customer_id=?",
                        (r, n) -> r.getObject(1, UUID.class),
                        cart,
                        customer)
                .isEmpty()) throw CheckoutException.missing();
    }

    public Delivery delivery(UUID customer, UUID address, UUID branch) {
        var addresses =
                jdbc.query(
                        "SELECT id,delivery_zone_id FROM customer_addresses WHERE id=? AND"
                            + " user_id=? FOR SHARE",
                        (r, n) -> r.getObject("delivery_zone_id", UUID.class),
                        address,
                        customer);
        if (addresses.isEmpty()) throw CheckoutException.missing();
        UUID zone = addresses.getFirst();
        if (zone == null)
            throw CheckoutException.conflict(
                    "ADDRESS_ZONE_REQUIRED",
                    "Select a managed delivery zone for the saved address");
        jdbc.query(
                "SELECT c.id FROM cities c JOIN delivery_zones z ON z.city_id=c.id WHERE z.id=? FOR"
                    + " SHARE OF c",
                (r, n) -> r.getObject(1, UUID.class),
                zone);
        jdbc.query(
                "SELECT id FROM delivery_zones WHERE id=? FOR SHARE",
                (r, n) -> r.getObject(1, UUID.class),
                zone);
        jdbc.query(
                "SELECT id FROM branch_delivery_zones WHERE branch_id=? AND delivery_zone_id=? FOR"
                    + " SHARE",
                (r, n) -> r.getObject(1, UUID.class),
                branch,
                zone);
        var values =
                jdbc.query(
                        """
SELECT a.*,z.name AS zone_name,c.name AS managed_city,d.delivery_fee,d.minimum_order
FROM customer_addresses a JOIN delivery_zones z ON z.id=a.delivery_zone_id
JOIN cities c ON c.id=z.city_id JOIN branch_delivery_zones d ON d.delivery_zone_id=z.id AND d.branch_id=?
WHERE a.id=? AND a.user_id=? AND z.active AND c.active AND d.enabled
""",
                        (r, n) ->
                                new Delivery(
                                        new OrderDtos.AddressSnapshot(
                                                r.getString("label"),
                                                r.getString("street"),
                                                r.getString("building"),
                                                r.getString("floor"),
                                                r.getString("apartment"),
                                                r.getString("landmark"),
                                                r.getString("instructions"),
                                                r.getString("city"),
                                                r.getString("region"),
                                                r.getString("postal_code"),
                                                r.getString("country_code"),
                                                r.getBigDecimal("latitude"),
                                                r.getBigDecimal("longitude"),
                                                zone,
                                                r.getString("zone_name"),
                                                r.getString("managed_city")),
                                        r.getBigDecimal("delivery_fee"),
                                        r.getBigDecimal("minimum_order")),
                        branch,
                        address,
                        customer);
        if (values.isEmpty())
            throw CheckoutException.conflict(
                    "NOT_SERVICEABLE", "Branch cannot deliver to the selected address zone");
        return values.getFirst();
    }

    public void consume(CartStore.LockedCart cart, Instant now) {
        if (jdbc.update(
                        "UPDATE carts SET status='CHECKED_OUT',version=version+1,updated_at=? WHERE"
                            + " id=? AND version=? AND status='ACTIVE'",
                        Timestamp.from(now),
                        cart.id(),
                        cart.version())
                != 1)
            throw CheckoutException.conflict("STALE_CART", "Cart changed; reload and retry");
    }

    public void complete(
            UUID customer,
            String key,
            CheckoutDtos.Request request,
            UUID order,
            UUID payment,
            Instant now) {
        jdbc.update(
                """
INSERT INTO checkout_receipts(customer_id,idempotency_key,cart_id,cart_version,saved_address_id,payment_method,order_id,payment_id,created_at)
VALUES (?,?,?,?,?,'CASH',?,?,?)
""",
                customer,
                key,
                request.cartId(),
                request.cartVersion(),
                request.savedAddressId(),
                order,
                payment,
                Timestamp.from(now));
    }
}
