package com.tayyar.user;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class RestaurantStaffRoleService {
    private final JdbcTemplate jdbc;

    public RestaurantStaffRoleService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void grant(UUID user) {
        jdbc.update(
                "INSERT INTO user_roles(user_id,role_name) VALUES (?,'RESTAURANT_STAFF') ON"
                        + " CONFLICT DO NOTHING",
                user);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void revokeWhenNoMembershipsRemain(UUID user) {
        jdbc.update(
                "DELETE FROM user_roles WHERE user_id=? AND role_name='RESTAURANT_STAFF' AND NOT"
                        + " EXISTS(SELECT 1 FROM restaurant_memberships WHERE user_id=? AND"
                        + " membership_type='STAFF')",
                user,
                user);
    }
}
