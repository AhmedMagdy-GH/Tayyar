package com.tayyar.auth;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.HexFormat;

@Component
public class AuthRateLimiter {
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public AuthRateLimiter(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public void acquire(String key, int limit) {
        Instant now = clock.instant();
        Integer count =
                jdbc.queryForObject(
                        """
INSERT INTO auth_rate_limits(key_hash,window_started,attempts) VALUES (?, ?, 1)
ON CONFLICT (key_hash) DO UPDATE SET
  attempts=CASE WHEN auth_rate_limits.window_started <= ? THEN 1 ELSE LEAST(auth_rate_limits.attempts+1, 1000000) END,
  window_started=CASE WHEN auth_rate_limits.window_started <= ? THEN EXCLUDED.window_started ELSE auth_rate_limits.window_started END
RETURNING attempts
""",
                        Integer.class,
                        digest(key),
                        Timestamp.from(now),
                        Timestamp.from(now.minus(Duration.ofMinutes(15))),
                        Timestamp.from(now.minus(Duration.ofMinutes(15))));
        if (count != null && count > limit)
            throw new IdentityException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "RATE_LIMITED",
                    "Too many requests; try again later");
    }

    @Scheduled(cron = "0 0 * * * *")
    public void removeExpiredCounters() {
        jdbc.update(
                "DELETE FROM auth_rate_limits WHERE window_started < ?",
                Timestamp.from(clock.instant().minus(Duration.ofDays(1))));
    }

    private String digest(String value) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
