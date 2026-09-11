package com.tayyar.admin;

import static com.tayyar.admin.AdminDtos.*;

import com.tayyar.auth.EmailAddress;
import com.tayyar.auth.SessionPrincipal;
import com.tayyar.delivery.DriverState;
import com.tayyar.order.OrderStatus;
import com.tayyar.user.AccountStatus;
import com.tayyar.user.Role;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
@PreAuthorize("hasRole('ADMIN')")
class AdminService {
    private final JdbcTemplate jdbc;
    private final AdminQuery query;
    private final Clock clock;

    AdminService(JdbcTemplate jdbc, AdminQuery query, Clock clock) {
        this.jdbc = jdbc;
        this.query = query;
        this.clock = clock;
    }

    Page<UserSummary> users(String email, AccountStatus status, Role role, Window window) {
        String normalized = email == null ? null : EmailAddress.normalize(email);
        if (normalized != null && (normalized.isBlank() || normalized.length() > 254))
            throw AdminException.invalid("INVALID_EMAIL_FILTER", "Email filter is invalid");
        return query.users(normalized, status, role, window);
    }

    UserSummary user(UUID id) { return query.user(id); }

    @Transactional
    UserSummary suspend(UUID id, SessionPrincipal actor, AccountCommand command) {
        if (id.equals(actor.id())) throw AdminException.conflict("An administrator cannot suspend their own account");
        AccountStatus current = lockStatus(id);
        if (current != AccountStatus.ACTIVE) throw AdminException.conflict("Only an active account can be suspended");
        if (busyDriver(id)) throw AdminException.conflict("A BUSY Driver with an active assignment requires an explicit recovery workflow");
        if (hasLiveOrder(id)) throw AdminException.conflict("A Customer with an active Order cannot be suspended by the simple account-state operation");
        if (isSoleActiveOwner(id)) throw AdminException.conflict("A sole active Restaurant owner cannot be suspended without ownership recovery");
        change(id, actor.id(), current, AccountStatus.SUSPENDED, AdminAction.ACCOUNT_SUSPENDED, command.reason());
        return query.user(id);
    }

    @Transactional
    UserSummary reactivate(UUID id, SessionPrincipal actor, AccountCommand command) {
        AccountStatus current = lockStatus(id);
        if (current != AccountStatus.SUSPENDED) throw AdminException.conflict("Only a suspended account can be reactivated");
        change(id, actor.id(), current, AccountStatus.ACTIVE, AdminAction.ACCOUNT_REACTIVATED, command.reason());
        return query.user(id);
    }

    RestaurantOverview restaurant(UUID id) { return query.restaurant(id); }
    Page<OrderSummary> orders(OrderStatus status, UUID restaurant, UUID branch, UUID customer, Window window) {
        return query.orders(status, restaurant, branch, customer, window);
    }
    OrderDetails order(UUID id) { return query.order(id); }
    Page<DriverSummary> drivers(AccountStatus accountStatus, DriverState state, Window window) {
        return query.drivers(accountStatus, state, window);
    }
    Page<AuditRecord> audit(AdminAction action, AdminTarget target, UUID targetId, UUID actorId, Window window) {
        return query.audit(action, target, targetId, actorId, window);
    }

    private AccountStatus lockStatus(UUID id) {
        return jdbc.query("SELECT status FROM users WHERE id=? FOR UPDATE",
                        (r,n) -> AccountStatus.valueOf(r.getString(1)), id).stream().findFirst()
                .orElseThrow(() -> AdminException.missing("USER"));
    }

    private boolean busyDriver(UUID id) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM driver_profiles d" +
                " JOIN delivery_assignments a ON a.driver_id=d.user_id AND a.status='ACTIVE'" +
                " WHERE d.user_id=? AND d.state='BUSY')", Boolean.class, id));
    }

    private boolean hasLiveOrder(UUID id) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM orders WHERE customer_id=?" +
                " AND status NOT IN ('DELIVERED','PAYMENT_FAILED','REJECTED','CANCELLED'))", Boolean.class, id));
    }

    private boolean isSoleActiveOwner(UUID id) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM restaurant_memberships mine" +
                " WHERE mine.user_id=? AND mine.membership_type='OWNER' AND NOT EXISTS(" +
                " SELECT 1 FROM restaurant_memberships other JOIN users u ON u.id=other.user_id" +
                " WHERE other.restaurant_id=mine.restaurant_id AND other.membership_type='OWNER'" +
                " AND other.user_id<>mine.user_id AND u.status='ACTIVE'))", Boolean.class, id));
    }

    private void change(UUID target, UUID actor, AccountStatus before, AccountStatus after,
                        AdminAction action, String reason) {
        var now = clock.instant();
        int changed = jdbc.update("UPDATE users SET status=? WHERE id=? AND status=?", after.name(), target, before.name());
        if (changed != 1) throw AdminException.conflict("Account state changed concurrently");
        jdbc.update("INSERT INTO admin_audit_log(id,actor_id,action_type,target_entity_type,target_entity_id," +
                        "reason,before_state,after_state,occurred_at) VALUES (?,?,?,?,?,?,?,?,?)",
                UUID.randomUUID(), actor, action.name(), AdminTarget.USER.name(), target, reason.strip(),
                before.name(), after.name(), Timestamp.from(now));
    }
}
