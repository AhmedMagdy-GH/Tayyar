package com.tayyar.restaurant;

import static com.tayyar.restaurant.RestaurantDtos.*;

import org.springframework.jdbc.core.*;
import org.springframework.stereotype.Repository;

import java.sql.*;
import java.time.Instant;
import java.util.*;

@Repository
public class RestaurantJournal {
    private final JdbcTemplate jdbc;
    private static final String APPLICATION_SELECT =
            """
SELECT a.id,a.applicant_id,a.status,a.current_revision,a.version,s.name,s.description,
       r.id AS restaurant_id,a.created_at,a.updated_at
FROM restaurant_applications a
JOIN application_submissions s ON s.application_id=a.id AND s.revision=a.current_revision
LEFT JOIN restaurants r ON r.application_id=a.id
""";

    public RestaurantJournal(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void submit(UUID id, int revision, String name, String description, Instant now) {
        jdbc.update(
                "INSERT INTO"
                    + " application_submissions(application_id,revision,name,description,submitted_at)"
                    + " VALUES (?,?,?,?,?)",
                id,
                revision,
                name.strip(),
                description,
                Timestamp.from(now));
    }

    public Submission submission(UUID id, int revision) {
        return jdbc.queryForObject(
                "SELECT revision,name,description,submitted_at FROM application_submissions WHERE"
                    + " application_id=? AND revision=?",
                (r, n) ->
                        new Submission(
                                r.getInt("revision"),
                                r.getString("name"),
                                r.getString("description"),
                                instant(r, "submitted_at")),
                id,
                revision);
    }

    public void recordDecision(
            UUID id,
            int revision,
            UUID reviewer,
            ReviewOutcome outcome,
            String reason,
            UUID restaurant,
            Instant now) {
        jdbc.update(
                "INSERT INTO"
                    + " application_decisions(application_id,revision,reviewer_id,outcome,reason,restaurant_id,decided_at)"
                    + " VALUES (?,?,?,?,?,?,?)",
                id,
                revision,
                reviewer,
                outcome.name(),
                reason,
                restaurant,
                Timestamp.from(now));
    }

    public void createOwner(UUID restaurant, UUID user, Instant now) {
        jdbc.update(
                "INSERT INTO"
                    + " restaurant_memberships(restaurant_id,user_id,membership_type,created_at)"
                    + " VALUES (?,?,'OWNER',?)",
                restaurant,
                user,
                Timestamp.from(now));
    }

    public boolean isOwner(UUID restaurant, UUID user) {
        return Boolean.TRUE.equals(
                jdbc.queryForObject(
                        "SELECT EXISTS(SELECT 1 FROM restaurant_memberships WHERE restaurant_id=?"
                            + " AND user_id=? AND membership_type='OWNER')",
                        Boolean.class,
                        restaurant,
                        user));
    }

    public ApplicationView application(UUID id) {
        return jdbc.queryForObject(APPLICATION_SELECT + " WHERE a.id=?", this::applicationRow, id);
    }

    public Page<ApplicationView> applications(
            UUID applicant, ApplicationStatus status, Window window) {
        String where =
                applicant != null
                        ? " WHERE a.applicant_id=?"
                        : status != null ? " WHERE a.status=?" : "";
        List<Object> parameters = new ArrayList<>();
        if (applicant != null) parameters.add(applicant);
        else if (status != null) parameters.add(status.name());
        Long count =
                jdbc.queryForObject(
                        "SELECT count(*) FROM restaurant_applications a" + where,
                        Long.class,
                        parameters.toArray());
        parameters.add(window.size());
        parameters.add(window.offset());
        var items =
                jdbc.query(
                        APPLICATION_SELECT
                                + where
                                + " ORDER BY a.created_at DESC,a.id DESC LIMIT ? OFFSET ?",
                        this::applicationRow,
                        parameters.toArray());
        return new Page<>(items, window.page(), window.size(), count);
    }

    public Page<Submission> submissions(UUID application, Window window) {
        return history(
                "application_submissions",
                "application_id",
                application,
                "revision DESC",
                window,
                "revision,name,description,submitted_at",
                (r, n) ->
                        new Submission(
                                r.getInt("revision"),
                                r.getString("name"),
                                r.getString("description"),
                                instant(r, "submitted_at")));
    }

    public Page<Decision> decisions(UUID application, Window window) {
        return history(
                "application_decisions",
                "application_id",
                application,
                "revision DESC",
                window,
                "revision,reviewer_id,outcome,reason,restaurant_id,decided_at",
                (r, n) ->
                        new Decision(
                                r.getInt("revision"),
                                r.getObject("reviewer_id", UUID.class),
                                ReviewOutcome.valueOf(r.getString("outcome")),
                                r.getString("reason"),
                                r.getObject("restaurant_id", UUID.class),
                                instant(r, "decided_at")));
    }

    public Page<Membership> memberships(UUID restaurant, Window window) {
        return history(
                "restaurant_memberships",
                "restaurant_id",
                restaurant,
                "user_id",
                window,
                "user_id,membership_type,created_at",
                (r, n) ->
                        new Membership(
                                r.getObject("user_id", UUID.class),
                                r.getString("membership_type"),
                                instant(r, "created_at")));
    }

    public void recordStatus(
            UUID restaurant,
            UUID actor,
            RestaurantStatus previous,
            RestaurantStatus next,
            String reason,
            Instant now) {
        jdbc.update(
                "INSERT INTO"
                    + " restaurant_status_history(id,restaurant_id,actor_id,previous_status,new_status,reason,changed_at)"
                    + " VALUES (?,?,?,?,?,?,?)",
                UUID.randomUUID(),
                restaurant,
                actor,
                previous.name(),
                next.name(),
                reason.strip(),
                Timestamp.from(now));
    }

    public Page<StatusHistory> statusHistory(UUID restaurant, Window window) {
        return history(
                "restaurant_status_history",
                "restaurant_id",
                restaurant,
                "changed_at DESC,id DESC",
                window,
                "id,actor_id,previous_status,new_status,reason,changed_at",
                (r, n) ->
                        new StatusHistory(
                                r.getObject("id", UUID.class),
                                r.getObject("actor_id", UUID.class),
                                RestaurantStatus.valueOf(r.getString("previous_status")),
                                RestaurantStatus.valueOf(r.getString("new_status")),
                                r.getString("reason"),
                                instant(r, "changed_at")));
    }

    public Page<RestaurantView> restaurants(UUID owner, Window window) {
        String from =
                " FROM restaurants r"
                        + (owner == null
                                ? ""
                                : " JOIN restaurant_memberships m ON m.restaurant_id=r.id WHERE"
                                      + " m.user_id=? AND m.membership_type='OWNER'");
        List<Object> params = new ArrayList<>();
        if (owner != null) params.add(owner);
        Long count = jdbc.queryForObject("SELECT count(*)" + from, Long.class, params.toArray());
        params.add(window.size());
        params.add(window.offset());
        var items =
                jdbc.query(
                        "SELECT"
                            + " r.id,r.name,r.description,r.status,r.version,r.created_at,r.updated_at"
                                + from
                                + " ORDER BY r.created_at DESC,r.id DESC LIMIT ? OFFSET ?",
                        (r, n) ->
                                new RestaurantView(
                                        r.getObject("id", UUID.class),
                                        r.getString("name"),
                                        r.getString("description"),
                                        RestaurantStatus.valueOf(r.getString("status")),
                                        r.getLong("version"),
                                        instant(r, "created_at"),
                                        instant(r, "updated_at")),
                        params.toArray());
        return new Page<>(items, window.page(), window.size(), count);
    }

    // Table/column/order fragments are private constants supplied only by the methods above.
    private <T> Page<T> history(
            String table,
            String key,
            UUID id,
            String order,
            Window window,
            String columns,
            RowMapper<T> mapper) {
        Long count =
                jdbc.queryForObject(
                        "SELECT count(*) FROM " + table + " WHERE " + key + "=?", Long.class, id);
        var items =
                jdbc.query(
                        "SELECT "
                                + columns
                                + " FROM "
                                + table
                                + " WHERE "
                                + key
                                + "=? ORDER BY "
                                + order
                                + " LIMIT ? OFFSET ?",
                        mapper,
                        id,
                        window.size(),
                        window.offset());
        return new Page<>(items, window.page(), window.size(), count);
    }

    private ApplicationView applicationRow(ResultSet r, int index) throws SQLException {
        return new ApplicationView(
                r.getObject("id", UUID.class),
                r.getObject("applicant_id", UUID.class),
                ApplicationStatus.valueOf(r.getString("status")),
                r.getInt("current_revision"),
                r.getLong("version"),
                r.getString("name"),
                r.getString("description"),
                r.getObject("restaurant_id", UUID.class),
                instant(r, "created_at"),
                instant(r, "updated_at"));
    }

    private static Instant instant(ResultSet r, String column) throws SQLException {
        return r.getTimestamp(column).toInstant();
    }
}
