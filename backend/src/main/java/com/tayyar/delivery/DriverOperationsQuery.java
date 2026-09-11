package com.tayyar.delivery;

import static com.tayyar.delivery.DriverOperationsDtos.*;

import com.tayyar.order.OrderStatus;
import com.tayyar.payment.PaymentMethod;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

@Repository
public class DriverOperationsQuery {
    private static final String SELECT =
            """
SELECT o.id AS order_id,o.status AS order_status,o.version AS order_version,
       da.id AS assignment_id,da.version AS assignment_version,da.assigned_at,
       r.id AS restaurant_id,r.name AS restaurant_name,b.id AS branch_id,b.name AS branch_name,
       b.address_line1,b.city AS branch_city,b.phone AS branch_phone,
       a.label,a.street,a.building,a.floor,a.apartment,a.landmark,a.instructions,
       a.city,a.region,a.postal_code,a.country_code,
       p.method AS payment_method,p.amount AS payment_amount,p.currency
FROM delivery_assignments da
JOIN orders o ON o.id=da.order_id
JOIN restaurants r ON r.id=o.restaurant_id
JOIN branches b ON b.id=o.branch_id
JOIN order_address_snapshots a ON a.order_id=o.id
LEFT JOIN LATERAL (
 SELECT method,amount,currency FROM payments
 WHERE order_id=o.id ORDER BY created_at DESC,id DESC LIMIT 1
) p ON true
""";

    private final JdbcTemplate jdbc;

    public DriverOperationsQuery(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Page<DriverOrder> queue(UUID driver, Window window) {
        long total =
                jdbc.queryForObject(
                        "SELECT count(*) FROM delivery_assignments WHERE driver_id=? AND"
                                + " status='ACTIVE'",
                        Long.class,
                        driver);
        List<DriverOrder> items =
                jdbc.query(
                        SELECT
                                + " WHERE da.driver_id=? AND da.status='ACTIVE'"
                                + " LIMIT ? OFFSET ?",
                        (row, number) -> map(row),
                        driver,
                        window.size(),
                        window.offset());
        return new Page<>(items, window.page(), window.size(), total);
    }

    public DriverOrder details(UUID driver, UUID order) {
        return jdbc
                .query(
                        SELECT
                                + " WHERE da.driver_id=? AND da.order_id=? AND da.status='ACTIVE'",
                        (row, number) -> map(row),
                        driver,
                        order)
                .stream()
                .findFirst()
                .orElseThrow(DriverOperationsException::missing);
    }

    private DriverOrder map(ResultSet row) throws SQLException {
        PaymentMethod method =
                row.getString("payment_method") == null
                        ? null
                        : PaymentMethod.valueOf(row.getString("payment_method"));
        return new DriverOrder(
                row.getObject("order_id", UUID.class),
                OrderStatus.valueOf(row.getString("order_status")),
                row.getLong("order_version"),
                row.getObject("assignment_id", UUID.class),
                row.getLong("assignment_version"),
                new Pickup(
                        row.getObject("restaurant_id", UUID.class),
                        row.getString("restaurant_name"),
                        row.getObject("branch_id", UUID.class),
                        row.getString("branch_name"),
                        row.getString("address_line1"),
                        row.getString("branch_city"),
                        row.getString("branch_phone")),
                new Destination(
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
                        row.getString("country_code")),
                method,
                method == PaymentMethod.CASH ? row.getBigDecimal("payment_amount") : null,
                row.getString("currency"),
                row.getTimestamp("assigned_at").toInstant());
    }
}
