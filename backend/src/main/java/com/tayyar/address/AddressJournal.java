package com.tayyar.address;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

@Repository
public class AddressJournal {
    private final JdbcTemplate jdbc;

    public AddressJournal(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void lockUser(UUID user) {
        if (jdbc.queryForList("SELECT id FROM users WHERE id=? FOR UPDATE", UUID.class, user)
                .isEmpty()) throw AddressException.missing();
    }

    public void clearDefault(UUID user, UUID except, Instant now) {
        jdbc.update(
                "UPDATE customer_addresses SET is_default=FALSE,version=version+1,updated_at=?"
                    + " WHERE user_id=? AND is_default AND id<>?",
                Timestamp.from(now),
                user,
                except);
    }
}
