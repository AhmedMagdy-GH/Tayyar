package com.tayyar.order;

import com.tayyar.auth.SessionPrincipal;
import com.tayyar.user.Role;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.*;

@Repository
public class OrderOperationsAccess {
    private final JdbcTemplate jdbc;

    public OrderOperationsAccess(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    record Context(UUID restaurant, UUID branch) {}

    public void customer(UUID actor, UUID order) {
        if (jdbc.query(
                        "SELECT id FROM orders WHERE id=? AND customer_id=?",
                        (row, number) -> row.getObject(1, UUID.class),
                        order,
                        actor)
                .isEmpty()) throw OrderOperationsException.missing();
    }

    public boolean restaurantScope(SessionPrincipal actor, UUID restaurant, UUID branch) {
        if (restaurant == null) throw OrderOperationsException.missing();
        if (actor.roles().contains(Role.RESTAURANT_OWNER)
                && member(actor.id(), restaurant, false)) {
            if (branch != null && !branchBelongs(restaurant, branch))
                throw OrderOperationsException.missing();
            return true;
        }
        if (actor.roles().contains(Role.RESTAURANT_STAFF)) {
            if (branch != null) {
                if (!assigned(actor.id(), restaurant, branch, false))
                    throw OrderOperationsException.missing();
            } else if (!assignedToRestaurant(actor.id(), restaurant)) {
                throw OrderOperationsException.missing();
            }
            return false;
        }
        throw OrderOperationsException.missing();
    }

    public void order(SessionPrincipal actor, UUID order, boolean lockAuthorization) {
        Context context =
                jdbc
                        .query(
                                "SELECT restaurant_id,branch_id FROM orders WHERE id=?",
                                (row, number) ->
                                        new Context(
                                                row.getObject(1, UUID.class),
                                                row.getObject(2, UUID.class)),
                                order)
                        .stream()
                        .findFirst()
                        .orElseThrow(OrderOperationsException::missing);
        if (actor.roles().contains(Role.RESTAURANT_OWNER)
                && member(actor.id(), context.restaurant(), lockAuthorization)) return;
        if (actor.roles().contains(Role.RESTAURANT_STAFF)
                && assigned(actor.id(), context.restaurant(), context.branch(), lockAuthorization))
            return;
        throw OrderOperationsException.missing();
    }

    private boolean member(UUID actor, UUID restaurant, boolean lock) {
        String suffix = lock ? " FOR KEY SHARE" : "";
        return !jdbc.query(
                        "SELECT user_id FROM restaurant_memberships WHERE restaurant_id=? AND"
                                + " user_id=?"
                                + suffix,
                        (row, number) -> row.getObject(1, UUID.class),
                        restaurant,
                        actor)
                .isEmpty();
    }

    private boolean assigned(UUID actor, UUID restaurant, UUID branch, boolean lock) {
        String suffix = lock ? " FOR KEY SHARE" : "";
        return !jdbc.query(
                        "SELECT user_id FROM branch_staff_assignments WHERE restaurant_id=? AND"
                                + " branch_id=? AND user_id=?"
                                + suffix,
                        (row, number) -> row.getObject(1, UUID.class),
                        restaurant,
                        branch,
                        actor)
                .isEmpty();
    }

    private boolean assignedToRestaurant(UUID actor, UUID restaurant) {
        return Boolean.TRUE.equals(
                jdbc.queryForObject(
                        "SELECT EXISTS(SELECT 1 FROM branch_staff_assignments WHERE"
                                + " restaurant_id=? AND user_id=?)",
                        Boolean.class,
                        restaurant,
                        actor));
    }

    private boolean branchBelongs(UUID restaurant, UUID branch) {
        return Boolean.TRUE.equals(
                jdbc.queryForObject(
                        "SELECT EXISTS(SELECT 1 FROM branches WHERE restaurant_id=? AND id=?)",
                        Boolean.class,
                        restaurant,
                        branch));
    }
}
