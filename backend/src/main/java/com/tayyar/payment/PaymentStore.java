package com.tayyar.payment;

import static com.tayyar.payment.PaymentDtos.*;

import com.tayyar.order.TransitionActor;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@Repository
public class PaymentStore {
    private final JdbcTemplate jdbc;

    public PaymentStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    record Terms(BigDecimal amount, String currency) {}

    record State(UUID id, PaymentMethod method, PaymentStatus status, long version) {}

    public Terms terms(UUID order) {
        return jdbc
                .query(
                        "SELECT final_total,currency FROM orders WHERE id=?",
                        (row, number) ->
                                new Terms(
                                        row.getBigDecimal("final_total"),
                                        row.getString("currency")),
                        order)
                .stream()
                .findFirst()
                .orElseThrow(PaymentException::missing);
    }

    public void create(UUID id, Create input, Terms terms, Instant now) {
        jdbc.update(
                "INSERT INTO payments(id,order_id,method,status,amount,currency,provider,"
                        + "provider_payment_reference,created_at,updated_at)"
                        + " VALUES (?,?,?,'PENDING',?,?,?,?,?,?)",
                id,
                input.orderId(),
                input.method().name(),
                terms.amount(),
                terms.currency(),
                input.provider(),
                input.providerReference(),
                Timestamp.from(now),
                Timestamp.from(now));
    }

    public State state(UUID id) {
        return jdbc
                .query(
                        "SELECT id,method,status,version FROM payments WHERE id=?",
                        (row, number) ->
                                new State(
                                        row.getObject("id", UUID.class),
                                        PaymentMethod.valueOf(row.getString("method")),
                                        PaymentStatus.valueOf(row.getString("status")),
                                        row.getLong("version")),
                        id)
                .stream()
                .findFirst()
                .orElseThrow(PaymentException::missing);
    }

    public void transition(State state, PaymentStatus target, Instant now) {
        if (jdbc.update(
                        "UPDATE payments SET status=?,version=version+1,updated_at=? WHERE id=? AND"
                                + " version=?",
                        target.name(),
                        Timestamp.from(now),
                        state.id(),
                        state.version())
                != 1) throw PaymentException.conflict();
    }

    public void history(
            UUID payment,
            PaymentStatus previous,
            PaymentStatus next,
            TransitionActor actor,
            String reason,
            Instant now) {
        jdbc.update(
                "INSERT INTO payment_status_history(id,payment_id,previous_status,new_status,"
                        + "actor_kind,actor_id,reason,occurred_at) VALUES (?,?,?,?,?,?,?,?)",
                UUID.randomUUID(),
                payment,
                previous == null ? null : previous.name(),
                next.name(),
                actor.kind().name(),
                actor.userId(),
                reason,
                Timestamp.from(now));
    }

    public View details(UUID id) {
        return jdbc
                .query(
                        "SELECT * FROM payments WHERE id=?",
                        (row, number) ->
                                new View(
                                        row.getObject("id", UUID.class),
                                        row.getObject("order_id", UUID.class),
                                        PaymentMethod.valueOf(row.getString("method")),
                                        PaymentStatus.valueOf(row.getString("status")),
                                        row.getBigDecimal("amount"),
                                        row.getString("currency"),
                                        row.getString("provider"),
                                        row.getString("provider_payment_reference"),
                                        row.getLong("version"),
                                        row.getTimestamp("created_at").toInstant(),
                                        row.getTimestamp("updated_at").toInstant()),
                        id)
                .stream()
                .findFirst()
                .orElseThrow(PaymentException::missing);
    }

    public List<History> history(UUID payment) {
        return jdbc.query(
                "SELECT * FROM payment_status_history WHERE payment_id=? ORDER BY occurred_at,id"
                        + " LIMIT 101",
                (row, number) ->
                        new History(
                                row.getObject("id", UUID.class),
                                row.getString("previous_status") == null
                                        ? null
                                        : PaymentStatus.valueOf(row.getString("previous_status")),
                                PaymentStatus.valueOf(row.getString("new_status")),
                                TransitionActor.Kind.valueOf(row.getString("actor_kind")),
                                row.getObject("actor_id", UUID.class),
                                row.getString("reason"),
                                row.getTimestamp("occurred_at").toInstant()),
                payment);
    }
}
