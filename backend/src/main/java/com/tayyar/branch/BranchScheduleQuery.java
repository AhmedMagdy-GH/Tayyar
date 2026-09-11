package com.tayyar.branch;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@Repository
public class BranchScheduleQuery {
    private final NamedParameterJdbcTemplate jdbc;

    public BranchScheduleQuery(JdbcTemplate jdbc) {
        this.jdbc = new NamedParameterJdbcTemplate(jdbc);
    }

    public boolean open(UUID branch, Instant now) {
        return Boolean.TRUE.equals(
                jdbc.queryForObject(
                        "SELECT "
                                + BranchReadSql.OPEN
                                + " FROM branches b "
                                + BranchReadSql.HOURS_JOIN
                                + " WHERE b.id=:branch",
                        Map.of("branch", branch, "now", Timestamp.from(now)),
                        Boolean.class));
    }
}
