package com.tayyar.branch;

import static com.tayyar.branch.BranchDtos.*;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.*;
import java.time.*;
import java.util.*;

@Repository
public class BranchJournal {
    private final JdbcTemplate jdbc;

    public BranchJournal(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void validateTimezone(String zone) {
        if (!Boolean.TRUE.equals(
                jdbc.queryForObject(
                        "SELECT EXISTS (SELECT 1 FROM pg_timezone_names WHERE name=?)",
                        Boolean.class,
                        zone)))
            throw BranchException.invalid("Timezone is not supported by the database");
    }

    public List<Weekly> weekly(UUID branch) {
        return jdbc.query(
                "SELECT weekday,opens_at,closes_at FROM branch_opening_hours WHERE branch_id=?"
                        + " ORDER BY weekday",
                (r, n) ->
                        new Weekly(
                                r.getInt(1),
                                r.getObject(2, LocalTime.class),
                                r.getObject(3, LocalTime.class)),
                branch);
    }

    public List<Special> special(UUID branch) {
        return jdbc.query(
                "SELECT service_date,opens_at,closes_at FROM branch_special_hours WHERE branch_id=?"
                        + " ORDER BY service_date LIMIT 366",
                (r, n) ->
                        new Special(
                                r.getObject(1, LocalDate.class),
                                r.getObject(2, LocalTime.class),
                                r.getObject(3, LocalTime.class)),
                branch);
    }

    public void replaceHours(UUID branch, Hours input) {
        jdbc.update("DELETE FROM branch_opening_hours WHERE branch_id=?", branch);
        jdbc.update("DELETE FROM branch_special_hours WHERE branch_id=?", branch);
        if (!input.weekly().isEmpty())
            jdbc.batchUpdate(
                    "INSERT INTO branch_opening_hours(branch_id,weekday,opens_at,closes_at) VALUES"
                            + " (?,?,?,?)",
                    input.weekly().stream()
                            .map(
                                    row ->
                                            new Object[] {
                                                branch, row.weekday(), row.opensAt(), row.closesAt()
                                            })
                            .toList());
        if (!input.special().isEmpty())
            jdbc.batchUpdate(
                    "INSERT INTO branch_special_hours(branch_id,service_date,opens_at,closes_at)"
                            + " VALUES (?,?,?,?)",
                    input.special().stream()
                            .map(
                                    row ->
                                            new Object[] {
                                                branch, row.date(), row.opensAt(), row.closesAt()
                                            })
                            .toList());
    }

    public boolean activeUser(UUID user) {
        var result =
                jdbc.queryForList(
                        "SELECT status FROM users WHERE id=? FOR UPDATE", String.class, user);
        return result.size() == 1 && result.getFirst().equals("ACTIVE");
    }

    public boolean member(UUID restaurant, UUID user) {
        return !jdbc.queryForList(
                        "SELECT user_id FROM restaurant_memberships WHERE restaurant_id=? AND"
                                + " user_id=? FOR KEY SHARE",
                        UUID.class,
                        restaurant,
                        user)
                .isEmpty();
    }

    public void assign(UUID branch, UUID restaurant, UUID user, UUID actor, Instant now) {
        int inserted =
                jdbc.update(
                        "INSERT INTO"
                            + " branch_staff_assignments(branch_id,restaurant_id,user_id,assigned_by,created_at)"
                            + " VALUES (?,?,?,?,?) ON CONFLICT (branch_id,user_id) DO NOTHING",
                        branch,
                        restaurant,
                        user,
                        actor,
                        Timestamp.from(now));
        if (inserted == 0) throw BranchException.conflict();
    }

    public void unassign(UUID branch, UUID user) {
        if (jdbc.update(
                        "DELETE FROM branch_staff_assignments WHERE branch_id=? AND user_id=?",
                        branch,
                        user)
                == 0) throw BranchException.missing();
    }

    public Page<Staff> staff(UUID branch, Window window) {
        long count =
                jdbc.queryForObject(
                        "SELECT count(*) FROM branch_staff_assignments WHERE branch_id=?",
                        Long.class,
                        branch);
        var rows =
                jdbc.query(
                        "SELECT user_id,assigned_by,created_at FROM branch_staff_assignments WHERE"
                                + " branch_id=? ORDER BY user_id LIMIT ? OFFSET ?",
                        (r, n) ->
                                new Staff(
                                        r.getObject(1, UUID.class),
                                        r.getObject(2, UUID.class),
                                        r.getTimestamp(3).toInstant()),
                        branch,
                        window.size(),
                        window.offset());
        return new Page<>(rows, window.page(), window.size(), count);
    }
}
