package com.tayyar.restaurant;

import static com.tayyar.restaurant.RestaurantOperationsDtos.*;

import com.tayyar.auth.SessionPrincipal;
import com.tayyar.branch.BranchReadSql;
import com.tayyar.branch.BranchStatus;
import com.tayyar.user.Role;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class RestaurantOperationsQuery {
    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    public RestaurantOperationsQuery(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = new NamedParameterJdbcTemplate(jdbc);
        this.clock = clock;
    }

    public List<RestaurantContext> contexts(SessionPrincipal actor) {
        var parameters =
                Map.<String, Object>of(
                        "actor",
                        actor.id(),
                        "owner",
                        actor.roles().contains(Role.RESTAURANT_OWNER),
                        "staff",
                        actor.roles().contains(Role.RESTAURANT_STAFF),
                        "now",
                        Timestamp.from(clock.instant()));
        var builders = new LinkedHashMap<UUID, ContextBuilder>();
        jdbc.query(
                """
SELECT r.id AS restaurant_id,r.name AS restaurant_name,r.status AS restaurant_status,
       m.membership_type,b.id AS branch_id,b.name AS branch_name,b.status AS branch_status,
       b.paused,
       """
                        + BranchReadSql.OPEN
                        + """ 
                         AS schedule_open
                FROM restaurant_memberships m
                JOIN restaurants r ON r.id=m.restaurant_id
                LEFT JOIN branches b ON b.restaurant_id=r.id AND
                  (m.membership_type='OWNER' OR EXISTS(
                    SELECT 1 FROM branch_staff_assignments a
                    WHERE a.restaurant_id=m.restaurant_id AND a.branch_id=b.id AND a.user_id=m.user_id))
                """
                        + BranchReadSql.HOURS_JOIN
                        + """
                WHERE m.user_id=:actor AND
                  ((m.membership_type='OWNER' AND :owner) OR
                   (m.membership_type='STAFF' AND :staff))
                ORDER BY r.name,r.id,b.name,b.id
                """,
                parameters,
                (RowCallbackHandler) row -> add(row, builders));
        return builders.values().stream().map(ContextBuilder::view).toList();
    }

    private void add(ResultSet row, Map<UUID, ContextBuilder> builders) throws SQLException {
        UUID restaurantId = row.getObject("restaurant_id", UUID.class);
        var builder =
                builders.computeIfAbsent(
                        restaurantId,
                        ignored ->
                                new ContextBuilder(
                                        restaurantId,
                                        get(row, "restaurant_name"),
                                        RestaurantStatus.valueOf(get(row, "restaurant_status")),
                                        get(row, "membership_type")));
        UUID branchId = row.getObject("branch_id", UUID.class);
        if (branchId == null) return;
        BranchStatus branchStatus = BranchStatus.valueOf(get(row, "branch_status"));
        String state =
                builder.status != RestaurantStatus.ACTIVE
                        ? "RESTAURANT_SUSPENDED"
                        : branchStatus != BranchStatus.ACTIVE
                                ? "INACTIVE"
                                : row.getBoolean("paused")
                                        ? "PAUSED"
                                        : row.getBoolean("schedule_open") ? "OPEN" : "CLOSED";
        builder.branches.add(
                new BranchContext(branchId, get(row, "branch_name"), branchStatus, state));
    }

    private String get(ResultSet row, String column) {
        try {
            return row.getString(column);
        } catch (SQLException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static final class ContextBuilder {
        private final UUID id;
        private final String name;
        private final RestaurantStatus status;
        private final String role;
        private final List<BranchContext> branches = new ArrayList<>();

        private ContextBuilder(UUID id, String name, RestaurantStatus status, String role) {
            this.id = id;
            this.name = name;
            this.status = status;
            this.role = role;
        }

        private RestaurantContext view() {
            return new RestaurantContext(id, name, status, role, List.copyOf(branches));
        }
    }
}
