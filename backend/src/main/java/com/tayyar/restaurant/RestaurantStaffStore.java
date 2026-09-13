package com.tayyar.restaurant;

import static com.tayyar.restaurant.RestaurantOperationsDtos.*;

import com.tayyar.branch.BranchStatus;
import com.tayyar.user.AccountStatus;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class RestaurantStaffStore {
    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;

    public RestaurantStaffStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.named = new NamedParameterJdbcTemplate(jdbc);
    }

    public Optional<TargetUser> lockUserByEmail(String email) {
        return jdbc
                .query(
                        "SELECT id,status FROM users WHERE email=? FOR UPDATE",
                        (row, number) ->
                                new TargetUser(
                                        row.getObject("id", UUID.class),
                                        AccountStatus.valueOf(row.getString("status"))),
                        email)
                .stream()
                .findFirst();
    }

    public boolean lockUser(UUID user) {
        return !jdbc.queryForList(
                                "SELECT id FROM users WHERE id=? FOR UPDATE", UUID.class, user)
                        .isEmpty();
    }

    public boolean lockStaffMembership(UUID restaurant, UUID user) {
        return !jdbc.queryForList(
                                "SELECT user_id FROM restaurant_memberships WHERE restaurant_id=?"
                                        + " AND user_id=? AND membership_type='STAFF' FOR UPDATE",
                                UUID.class,
                                restaurant,
                                user)
                        .isEmpty();
    }

    public boolean create(UUID restaurant, UUID user, Instant now) {
        return jdbc.update(
                                "INSERT INTO"
                                        + " restaurant_memberships(restaurant_id,user_id,membership_type,created_at)"
                                        + " VALUES (?,?,'STAFF',?) ON CONFLICT (restaurant_id,user_id)"
                                        + " DO NOTHING",
                                restaurant,
                                user,
                                Timestamp.from(now))
                        == 1;
    }

    public void remove(UUID restaurant, UUID user) {
        jdbc.update(
                "DELETE FROM branch_staff_assignments WHERE restaurant_id=? AND user_id=?",
                restaurant,
                user);
        if (jdbc.update(
                        "DELETE FROM restaurant_memberships WHERE restaurant_id=? AND user_id=? AND"
                                + " membership_type='STAFF'",
                        restaurant,
                        user)
                != 1) throw RestaurantException.missing();
    }

    public StaffPage list(UUID restaurant, RestaurantDtos.Window window) {
        long total =
                jdbc.queryForObject(
                        "SELECT count(*) FROM restaurant_memberships WHERE restaurant_id=? AND"
                                + " membership_type='STAFF'",
                        Long.class,
                        restaurant);
        var staff =
                jdbc.query(
                        "SELECT m.user_id,u.full_name,u.email,m.created_at FROM"
                                + " restaurant_memberships m JOIN users u ON u.id=m.user_id WHERE"
                                + " m.restaurant_id=? AND m.membership_type='STAFF' ORDER BY"
                                + " m.created_at,m.user_id LIMIT ? OFFSET ?",
                        (row, number) ->
                                new StaffRow(
                                        row.getObject("user_id", UUID.class),
                                        row.getString("full_name"),
                                        row.getString("email"),
                                        row.getTimestamp("created_at").toInstant()),
                        restaurant,
                        window.size(),
                        window.offset());
        return new StaffPage(
                views(staff, assignments(restaurant, staff)), window.page(), window.size(), total);
    }

    public StaffView get(UUID restaurant, UUID user) {
        var staff =
                jdbc.query(
                                "SELECT m.user_id,u.full_name,u.email,m.created_at FROM"
                                        + " restaurant_memberships m JOIN users u ON u.id=m.user_id"
                                        + " WHERE m.restaurant_id=? AND m.user_id=? AND"
                                        + " m.membership_type='STAFF'",
                                (row, number) ->
                                        new StaffRow(
                                                row.getObject("user_id", UUID.class),
                                                row.getString("full_name"),
                                                row.getString("email"),
                                                row.getTimestamp("created_at").toInstant()),
                                restaurant,
                                user)
                        .stream()
                        .findFirst()
                        .orElseThrow(RestaurantException::missing);
        return views(List.of(staff), assignments(restaurant, List.of(staff))).getFirst();
    }

    private Map<UUID, List<StaffBranch>> assignments(UUID restaurant, List<StaffRow> staff) {
        var result = new LinkedHashMap<UUID, List<StaffBranch>>();
        staff.forEach(row -> result.put(row.userId(), new ArrayList<>()));
        if (staff.isEmpty()) return result;
        named.query(
                "SELECT a.user_id,b.id,b.name,b.status FROM branch_staff_assignments a JOIN"
                        + " branches b ON b.id=a.branch_id AND b.restaurant_id=a.restaurant_id"
                        + " WHERE a.restaurant_id=:restaurant AND a.user_id IN (:users) ORDER BY"
                        + " b.name,b.id",
                Map.of(
                        "restaurant",
                        restaurant,
                        "users",
                        staff.stream().map(StaffRow::userId).toList()),
                (RowCallbackHandler)
                        row ->
                        result.get(row.getObject("user_id", UUID.class))
                                .add(
                                        new StaffBranch(
                                                row.getObject("id", UUID.class),
                                                row.getString("name"),
                                                BranchStatus.valueOf(row.getString("status")))));
        return result;
    }

    private List<StaffView> views(
            List<StaffRow> staff, Map<UUID, List<StaffBranch>> assignments) {
        return staff.stream()
                .map(
                        row ->
                                new StaffView(
                                        row.userId(),
                                        row.fullName(),
                                        row.email(),
                                        row.createdAt(),
                                        List.copyOf(assignments.get(row.userId()))))
                .toList();
    }

    public record TargetUser(UUID id, AccountStatus status) {}

    private record StaffRow(UUID userId, String fullName, String email, Instant createdAt) {}
}
