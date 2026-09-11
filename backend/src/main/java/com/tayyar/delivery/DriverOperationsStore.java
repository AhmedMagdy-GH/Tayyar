package com.tayyar.delivery;

import static com.tayyar.delivery.DriverOperationsDtos.*;

import com.tayyar.order.OrderStatus;
import com.tayyar.payment.*;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@Repository
public class DriverOperationsStore {
    private final JdbcTemplate jdbc;

    public DriverOperationsStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    record OrderLock(UUID id, UUID branchId, OrderStatus status, long version) {}

    record DriverLock(UUID id, DriverState state, long version) {}

    record AssignmentLock(
            UUID id, UUID orderId, UUID driverId, AssignmentStatus status, long version) {}

    record PaymentLock(UUID id, PaymentMethod method, PaymentStatus status, long version) {}

    public void provision(UUID driver, UUID actor, Instant now) {
        try {
            jdbc.update(
                    "INSERT INTO driver_profiles(user_id,state,created_at,updated_at)"
                            + " SELECT u.id,'OFFLINE',?,? FROM users u JOIN user_roles ur"
                            + " ON ur.user_id=u.id AND ur.role_name='DRIVER'"
                            + " WHERE u.id=? AND u.status='ACTIVE'",
                    Timestamp.from(now),
                    Timestamp.from(now),
                    driver);
            if (jdbc.queryForObject(
                            "SELECT count(*) FROM driver_profiles WHERE user_id=?", Long.class, driver)
                    != 1L) throw DriverOperationsException.missing();
            driverHistory(driver, null, DriverState.OFFLINE, actor, "Driver profile provisioned", now);
        } catch (DataIntegrityViolationException exception) {
            throw DriverOperationsException.conflict("Driver profile already exists");
        }
    }

    public Profile profile(UUID driver) {
        return jdbc
                .query(
                        "SELECT user_id,state,version,updated_at FROM driver_profiles WHERE user_id=?",
                        (row, number) ->
                                new Profile(
                                        row.getObject("user_id", UUID.class),
                                        DriverState.valueOf(row.getString("state")),
                                        row.getLong("version"),
                                        row.getTimestamp("updated_at").toInstant()),
                        driver)
                .stream()
                .findFirst()
                .orElseThrow(DriverOperationsException::missing);
    }

    public OrderLock lockOrder(UUID order) {
        return jdbc
                .query(
                        "SELECT id,branch_id,status,version FROM orders WHERE id=? FOR UPDATE",
                        (row, number) ->
                                new OrderLock(
                                        row.getObject("id", UUID.class),
                                        row.getObject("branch_id", UUID.class),
                                        OrderStatus.valueOf(row.getString("status")),
                                        row.getLong("version")),
                        order)
                .stream()
                .findFirst()
                .orElseThrow(DriverOperationsException::missing);
    }

    public String lockDeliveryModel(UUID branch) {
        return jdbc
                .query(
                        "SELECT delivery_model FROM branches WHERE id=? FOR KEY SHARE",
                        (row, number) -> row.getString("delivery_model"),
                        branch)
                .stream()
                .findFirst()
                .orElseThrow(DriverOperationsException::missing);
    }

    public DriverLock lockDriver(UUID driver) {
        return jdbc
                .query(
                        "SELECT dp.user_id,dp.state,dp.version FROM driver_profiles dp JOIN users u"
                            + " ON u.id=dp.user_id JOIN user_roles ur ON ur.user_id=dp.user_id AND"
                            + " ur.role_name='DRIVER' WHERE dp.user_id=? AND u.status='ACTIVE' FOR"
                            + " UPDATE OF dp,u",
                        (row, number) ->
                                new DriverLock(
                                        row.getObject("user_id", UUID.class),
                                        DriverState.valueOf(row.getString("state")),
                                        row.getLong("version")),
                        driver)
                .stream()
                .findFirst()
                .orElseThrow(DriverOperationsException::missing);
    }

    public AssignmentLock activeAssignment(UUID order, boolean lock) {
        return findActiveAssignment(order, lock)
                .orElseThrow(DriverOperationsException::missing);
    }

    public Optional<AssignmentLock> findActiveAssignment(UUID order, boolean lock) {
        String suffix = lock ? " FOR UPDATE" : "";
        return jdbc
                .query(
                        "SELECT id,order_id,driver_id,status,version FROM delivery_assignments"
                                + " WHERE order_id=? AND status='ACTIVE'"
                                + suffix,
                        (row, number) -> assignmentLock(row),
                        order)
                .stream()
                .findFirst();
    }

    public Optional<AssignmentLock> activeAssignmentForDriver(UUID driver) {
        return jdbc
                .query(
                        "SELECT id,order_id,driver_id,status,version FROM delivery_assignments"
                                + " WHERE driver_id=? AND status='ACTIVE'",
                        (row, number) -> assignmentLock(row),
                        driver)
                .stream()
                .findFirst();
    }

    public boolean hasCompletedAssignment(UUID order, UUID driver) {
        return jdbc.queryForObject(
                        "SELECT EXISTS(SELECT 1 FROM delivery_assignments WHERE order_id=? AND"
                                + " driver_id=? AND status='COMPLETED')",
                        Boolean.class,
                        order,
                        driver);
    }

    private AssignmentLock assignmentLock(java.sql.ResultSet row) throws java.sql.SQLException {
        return new AssignmentLock(
                row.getObject("id", UUID.class),
                row.getObject("order_id", UUID.class),
                row.getObject("driver_id", UUID.class),
                AssignmentStatus.valueOf(row.getString("status")),
                row.getLong("version"));
    }

    public Assignment createAssignment(UUID order, UUID driver, UUID actor, Instant now) {
        UUID id = UUID.randomUUID();
        try {
            jdbc.update(
                    "INSERT INTO delivery_assignments"
                            + "(id,order_id,driver_id,status,assigned_by,assigned_at)"
                            + " VALUES (?,?,?,'ACTIVE',?,?)",
                    id,
                    order,
                    driver,
                    actor,
                    Timestamp.from(now));
            assignmentHistory(id, null, AssignmentStatus.ACTIVE, actor, "Driver assigned", now);
            return new Assignment(id, order, driver, AssignmentStatus.ACTIVE, 0, now, null);
        } catch (DataIntegrityViolationException exception) {
            throw DriverOperationsException.conflict(
                    "Order or driver already has an active assignment");
        }
    }

    public void changeDriverState(
            DriverLock driver, DriverState target, UUID actor, String reason, Instant now) {
        int updated =
                jdbc.update(
                        "UPDATE driver_profiles SET state=?,version=version+1,updated_at=?"
                                + " WHERE user_id=? AND version=?",
                        target.name(),
                        Timestamp.from(now),
                        driver.id(),
                        driver.version());
        if (updated != 1) throw DriverOperationsException.conflict("Driver state changed");
        driverHistory(driver.id(), driver.state(), target, actor, reason, now);
    }

    public void completeAssignment(AssignmentLock assignment, UUID actor, Instant now) {
        int updated =
                jdbc.update(
                        "UPDATE delivery_assignments SET status='COMPLETED',version=version+1,"
                                + "completed_at=? WHERE id=? AND version=? AND status='ACTIVE'",
                        Timestamp.from(now),
                        assignment.id(),
                        assignment.version());
        if (updated != 1) throw DriverOperationsException.conflict("Assignment changed");
        assignmentHistory(
                assignment.id(),
                AssignmentStatus.ACTIVE,
                AssignmentStatus.COMPLETED,
                actor,
                "Delivery completed",
                now);
    }

    public PaymentLock lockPayment(UUID order) {
        List<PaymentLock> payments =
                jdbc.query(
                        "SELECT id,method,status,version FROM payments WHERE order_id=?"
                                + " ORDER BY created_at DESC,id DESC LIMIT 2 FOR UPDATE",
                        (row, number) ->
                                new PaymentLock(
                                        row.getObject("id", UUID.class),
                                        PaymentMethod.valueOf(row.getString("method")),
                                        PaymentStatus.valueOf(row.getString("status")),
                                        row.getLong("version")),
                        order);
        if (payments.size() != 1)
            throw DriverOperationsException.conflict("Order payment is not operationally eligible");
        return payments.getFirst();
    }

    public void driverHistory(
            UUID driver,
            DriverState previous,
            DriverState target,
            UUID actor,
            String reason,
            Instant now) {
        jdbc.update(
                "INSERT INTO driver_state_history"
                        + "(id,driver_id,previous_state,new_state,actor_id,reason,occurred_at)"
                        + " VALUES (?,?,?,?,?,?,?)",
                UUID.randomUUID(),
                driver,
                previous == null ? null : previous.name(),
                target.name(),
                actor,
                reason,
                Timestamp.from(now));
    }

    public void assignmentHistory(
            UUID assignment,
            AssignmentStatus previous,
            AssignmentStatus target,
            UUID actor,
            String reason,
            Instant now) {
        jdbc.update(
                "INSERT INTO delivery_assignment_history"
                        + "(id,assignment_id,previous_status,new_status,actor_id,reason,occurred_at)"
                        + " VALUES (?,?,?,?,?,?,?)",
                UUID.randomUUID(),
                assignment,
                previous == null ? null : previous.name(),
                target.name(),
                actor,
                reason,
                Timestamp.from(now));
    }
}
