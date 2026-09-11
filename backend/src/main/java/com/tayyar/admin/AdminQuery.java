package com.tayyar.admin;

import static com.tayyar.admin.AdminDtos.*;

import com.tayyar.delivery.DriverState;
import com.tayyar.order.OrderStatus;
import com.tayyar.payment.PaymentStatus;
import com.tayyar.restaurant.RestaurantStatus;
import com.tayyar.user.AccountStatus;
import com.tayyar.user.Role;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

@Repository
class AdminQuery {
    private final JdbcTemplate jdbc;

    AdminQuery(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    Page<UserSummary> users(String email, AccountStatus status, Role role, Window window) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (email != null) { where.append(" AND u.email=?"); args.add(email); }
        if (status != null) { where.append(" AND u.status=?"); args.add(status.name()); }
        if (role != null) { where.append(" AND EXISTS(SELECT 1 FROM user_roles f WHERE f.user_id=u.id AND f.role_name=?)"); args.add(role.name()); }
        long total = jdbc.queryForObject("SELECT count(*) FROM users u" + where, Long.class, args.toArray());
        args.add(window.size()); args.add(window.offset());
        var items = jdbc.query("SELECT u.id,u.full_name,u.email,u.status,u.created_at,u.updated_at," +
                        " string_agg(ur.role_name,',' ORDER BY ur.role_name) roles FROM users u" +
                        " JOIN user_roles ur ON ur.user_id=u.id" + where +
                        " GROUP BY u.id ORDER BY u.created_at DESC,u.id DESC LIMIT ? OFFSET ?",
                this::mapUser, args.toArray());
        return new Page<>(items, window.page(), window.size(), total);
    }

    UserSummary user(UUID id) {
        return jdbc.query("SELECT u.id,u.full_name,u.email,u.status,u.created_at,u.updated_at," +
                        " string_agg(ur.role_name,',' ORDER BY ur.role_name) roles FROM users u" +
                        " JOIN user_roles ur ON ur.user_id=u.id WHERE u.id=? GROUP BY u.id", this::mapUser, id)
                .stream().findFirst().orElseThrow(() -> AdminException.missing("USER"));
    }

    RestaurantOverview restaurant(UUID id) {
        var base = jdbc.query("SELECT id,name,description,status,version,created_at,updated_at FROM restaurants WHERE id=?",
                (r,n) -> new RestaurantOverview(r.getObject("id", UUID.class), r.getString("name"),
                        r.getString("description"), RestaurantStatus.valueOf(r.getString("status")),
                        r.getLong("version"), r.getTimestamp("created_at").toInstant(),
                        r.getTimestamp("updated_at").toInstant(), List.of()), id).stream().findFirst()
                .orElseThrow(() -> AdminException.missing("RESTAURANT"));
        var branches = jdbc.query("SELECT id,name,status,paused,city FROM branches WHERE restaurant_id=?" +
                        " ORDER BY created_at DESC,id DESC LIMIT 101",
                (r,n) -> new BranchSummary(r.getObject("id", UUID.class), r.getString("name"),
                        r.getString("status"), r.getBoolean("paused"), r.getString("city")), id);
        if (branches.size() > 100) throw AdminException.conflict("Restaurant branch display limit exceeded");
        return new RestaurantOverview(base.id(), base.name(), base.description(), base.status(), base.version(),
                base.createdAt(), base.updatedAt(), branches);
    }

    Page<OrderSummary> orders(OrderStatus status, UUID restaurant, UUID branch, UUID customer, Window window) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (status != null) { where.append(" AND o.status=?"); args.add(status.name()); }
        if (restaurant != null) { where.append(" AND o.restaurant_id=?"); args.add(restaurant); }
        if (branch != null) { where.append(" AND o.branch_id=?"); args.add(branch); }
        if (customer != null) { where.append(" AND o.customer_id=?"); args.add(customer); }
        long total = jdbc.queryForObject("SELECT count(*) FROM orders o" + where, Long.class, args.toArray());
        args.add(window.size()); args.add(window.offset());
        var rows = jdbc.query(orderSelect() + where + " ORDER BY o.created_at DESC,o.id DESC LIMIT ? OFFSET ?",
                this::mapOrder, args.toArray());
        return new Page<>(rows, window.page(), window.size(), total);
    }

    OrderDetails order(UUID id) {
        OrderSummary summary = jdbc.query(orderSelect() + " WHERE o.id=?", this::mapOrder, id).stream()
                .findFirst().orElseThrow(() -> AdminException.missing("ORDER"));
        var items = jdbc.query("SELECT purchased_name,unit_price,quantity,line_subtotal FROM order_items" +
                        " WHERE order_id=? ORDER BY created_at,id LIMIT 101",
                (r,n) -> new PurchasedItem(r.getString(1), r.getBigDecimal(2), r.getInt(3), r.getBigDecimal(4)), id);
        var address = jdbc.query("SELECT label,street,building,floor,apartment,landmark,instructions,city,region,postal_code,country_code" +
                        " FROM order_address_snapshots WHERE order_id=?",
                (r,n) -> new AddressSnapshot(r.getString(1),r.getString(2),r.getString(3),r.getString(4),
                        r.getString(5),r.getString(6),r.getString(7),r.getString(8),r.getString(9),
                        r.getString(10),r.getString(11)), id).stream().findFirst().orElse(null);
        var history = jdbc.query("SELECT previous_status,new_status,actor_kind,actor_id,reason,occurred_at" +
                        " FROM order_status_history WHERE order_id=? ORDER BY occurred_at,id LIMIT 101",
                (r,n) -> new StatusHistory(r.getString(1)==null?null:OrderStatus.valueOf(r.getString(1)),
                        OrderStatus.valueOf(r.getString(2)),r.getString(3),r.getObject(4,UUID.class),
                        r.getString(5),r.getTimestamp(6).toInstant()), id);
        if (items.size() > 100 || history.size() > 100) throw AdminException.conflict("Order support detail limit exceeded");
        return new OrderDetails(summary, items, address, history);
    }

    Page<DriverSummary> drivers(AccountStatus accountStatus, DriverState state, Window window) {
        StringBuilder where = new StringBuilder(" WHERE 1=1"); List<Object> args = new ArrayList<>();
        if (accountStatus != null) { where.append(" AND u.status=?"); args.add(accountStatus.name()); }
        if (state != null) { where.append(" AND d.state=?"); args.add(state.name()); }
        long total = jdbc.queryForObject("SELECT count(*) FROM driver_profiles d JOIN users u ON u.id=d.user_id" + where,
                Long.class, args.toArray());
        args.add(window.size()); args.add(window.offset());
        var rows = jdbc.query("SELECT d.user_id,u.status account_status,d.state,d.version,d.created_at,d.updated_at," +
                        " da.id assignment_id,da.order_id,da.status assignment_status,da.assigned_at FROM driver_profiles d" +
                        " JOIN users u ON u.id=d.user_id LEFT JOIN delivery_assignments da ON da.driver_id=d.user_id AND da.status='ACTIVE'" +
                        where + " ORDER BY d.created_at DESC,d.user_id DESC LIMIT ? OFFSET ?",
                (r,n) -> new DriverSummary(r.getObject("user_id",UUID.class),AccountStatus.valueOf(r.getString("account_status")),
                        DriverState.valueOf(r.getString("state")),r.getLong("version"),
                        r.getObject("assignment_id",UUID.class)==null?null:new AssignmentSummary(r.getObject("assignment_id",UUID.class),
                                r.getObject("order_id",UUID.class),r.getString("assignment_status"),r.getTimestamp("assigned_at").toInstant()),
                        r.getTimestamp("created_at").toInstant(),r.getTimestamp("updated_at").toInstant()), args.toArray());
        return new Page<>(rows, window.page(), window.size(), total);
    }

    Page<AuditRecord> audit(AdminAction action, AdminTarget target, UUID targetId, UUID actorId, Window window) {
        StringBuilder where = new StringBuilder(" WHERE 1=1"); List<Object> args = new ArrayList<>();
        if (action != null) { where.append(" AND action_type=?"); args.add(action.name()); }
        if (target != null) { where.append(" AND target_entity_type=?"); args.add(target.name()); }
        if (targetId != null) { where.append(" AND target_entity_id=?"); args.add(targetId); }
        if (actorId != null) { where.append(" AND actor_id=?"); args.add(actorId); }
        long total = jdbc.queryForObject("SELECT count(*) FROM admin_audit_log" + where, Long.class, args.toArray());
        args.add(window.size()); args.add(window.offset());
        var rows = jdbc.query("SELECT id,actor_id,action_type,target_entity_type,target_entity_id,reason,before_state,after_state,occurred_at" +
                        " FROM admin_audit_log" + where + " ORDER BY occurred_at DESC,id DESC LIMIT ? OFFSET ?",
                (r,n) -> new AuditRecord(r.getObject("id",UUID.class),r.getObject("actor_id",UUID.class),
                        AdminAction.valueOf(r.getString("action_type")),AdminTarget.valueOf(r.getString("target_entity_type")),
                        r.getObject("target_entity_id",UUID.class),r.getString("reason"),r.getString("before_state"),
                        r.getString("after_state"),r.getTimestamp("occurred_at").toInstant()), args.toArray());
        return new Page<>(rows, window.page(), window.size(), total);
    }

    private UserSummary mapUser(ResultSet r, int n) throws SQLException {
        Set<Role> roles = new LinkedHashSet<>();
        for (String role : r.getString("roles").split(",")) roles.add(Role.valueOf(role));
        return new UserSummary(r.getObject("id",UUID.class),r.getString("full_name"),r.getString("email"),
                AccountStatus.valueOf(r.getString("status")),roles,r.getTimestamp("created_at").toInstant(),
                r.getTimestamp("updated_at").toInstant());
    }

    private static String orderSelect() {
        return "SELECT o.id,o.customer_id,o.restaurant_id,r.name restaurant_name,o.branch_id,b.name branch_name," +
                "o.status,o.final_total,o.currency,o.created_at,p.method payment_method,p.status payment_status,p.amount payment_amount," +
                "p.currency payment_currency,da.id assignment_id,da.driver_id,da.status assignment_status,da.assigned_at " +
                "FROM orders o JOIN restaurants r ON r.id=o.restaurant_id JOIN branches b ON b.id=o.branch_id " +
                "LEFT JOIN LATERAL (SELECT method,status,amount,currency FROM payments WHERE order_id=o.id ORDER BY created_at DESC,id DESC LIMIT 1) p ON true " +
                "LEFT JOIN delivery_assignments da ON da.order_id=o.id AND da.status='ACTIVE'";
    }

    private OrderSummary mapOrder(ResultSet r, int n) throws SQLException {
        PaymentSummary payment = r.getString("payment_method") == null ? null : new PaymentSummary(r.getString("payment_method"),
                PaymentStatus.valueOf(r.getString("payment_status")),r.getBigDecimal("payment_amount"),r.getString("payment_currency"));
        AssignmentSummary assignment = r.getObject("assignment_id",UUID.class) == null ? null : new AssignmentSummary(
                r.getObject("assignment_id",UUID.class),r.getObject("driver_id",UUID.class),r.getString("assignment_status"),
                r.getTimestamp("assigned_at").toInstant());
        return new OrderSummary(r.getObject("id",UUID.class),r.getObject("customer_id",UUID.class),
                r.getObject("restaurant_id",UUID.class),r.getString("restaurant_name"),r.getObject("branch_id",UUID.class),
                r.getString("branch_name"),OrderStatus.valueOf(r.getString("status")),r.getBigDecimal("final_total"),
                r.getString("currency"),payment,assignment,r.getTimestamp("created_at").toInstant());
    }
}
