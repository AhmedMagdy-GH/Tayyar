package com.tayyar.notification;

import static com.tayyar.notification.NotificationDtos.*;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.*;
import java.time.Instant;
import java.util.*;

@Repository
public class NotificationStore {
    private final JdbcTemplate jdbc;

    public NotificationStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    record OrderContext(UUID customerId, String restaurantName) {}

    public OrderContext orderContext(UUID orderId) {
        return jdbc.query(
                        "SELECT o.customer_id,r.name FROM orders o JOIN restaurants r"
                                + " ON r.id=o.restaurant_id WHERE o.id=?",
                        (row, number) -> new OrderContext(
                                row.getObject(1, UUID.class), row.getString(2)), orderId)
                .stream().findFirst().orElseThrow(NotificationException::missing);
    }

    public void insert(
            UUID recipient,
            NotificationType type,
            String title,
            String body,
            RelatedEntityType relatedType,
            UUID relatedId,
            String deduplicationKey,
            Instant now) {
        jdbc.update(
                "INSERT INTO notifications(id,recipient_user_id,type,channel,title,body,"
                        + "related_entity_type,related_entity_id,created_at,deduplication_key)"
                        + " VALUES (?,?,?,'IN_APP',?,?,?,?,?,?)"
                        + " ON CONFLICT (deduplication_key) DO NOTHING",
                UUID.randomUUID(), recipient, type.name(), title, body, relatedType.name(), relatedId,
                Timestamp.from(now), deduplicationKey);
    }

    public Page list(UUID recipient, Boolean read, Window window) {
        String filter = read == null ? "" : read ? " AND read_at IS NOT NULL" : " AND read_at IS NULL";
        long total = jdbc.queryForObject(
                "SELECT count(*) FROM notifications WHERE recipient_user_id=?" + filter,
                Long.class, recipient);
        List<View> items = jdbc.query(
                "SELECT id,type,channel,title,body,related_entity_type,related_entity_id,read_at,created_at"
                        + " FROM notifications WHERE recipient_user_id=?" + filter
                        + " ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?",
                this::map, recipient, window.size(), window.offset());
        return new Page(items, window.page(), window.size(), total);
    }

    public View owned(UUID recipient, UUID id) {
        return jdbc.query(
                        "SELECT id,type,channel,title,body,related_entity_type,related_entity_id,read_at,created_at"
                                + " FROM notifications WHERE id=? AND recipient_user_id=?",
                        this::map, id, recipient)
                .stream().findFirst().orElseThrow(NotificationException::missing);
    }

    public View markRead(UUID recipient, UUID id, Instant now) {
        int updated = jdbc.update(
                "UPDATE notifications SET read_at=COALESCE(read_at,?)"
                        + " WHERE id=? AND recipient_user_id=?",
                Timestamp.from(now), id, recipient);
        if (updated != 1) throw NotificationException.missing();
        return owned(recipient, id);
    }

    public int markAllRead(UUID recipient, Instant now) {
        return jdbc.update(
                "UPDATE notifications SET read_at=? WHERE recipient_user_id=? AND read_at IS NULL",
                Timestamp.from(now), recipient);
    }

    private View map(ResultSet row, int number) throws SQLException {
        Timestamp readAt = row.getTimestamp("read_at");
        String related = row.getString("related_entity_type");
        return new View(
                row.getObject("id", UUID.class),
                NotificationType.valueOf(row.getString("type")),
                row.getString("channel"),
                row.getString("title"),
                row.getString("body"),
                related == null ? null : RelatedEntityType.valueOf(related),
                row.getObject("related_entity_id", UUID.class),
                readAt != null,
                row.getTimestamp("created_at").toInstant(),
                readAt == null ? null : readAt.toInstant());
    }
}
