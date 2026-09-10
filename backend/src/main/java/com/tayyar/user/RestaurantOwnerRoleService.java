package com.tayyar.user;

import com.tayyar.auth.IdentityException;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.util.UUID;

@Service
public class RestaurantOwnerRoleService {
    private final JdbcTemplate jdbc;

    public RestaurantOwnerRoleService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(propagation = Propagation.MANDATORY)
    public void grantForApprovedOwnership(UUID applicant) {
        String status =
                jdbc.queryForObject(
                        "SELECT status FROM users WHERE id=? FOR UPDATE", String.class, applicant);
        if (!AccountStatus.ACTIVE.name().equals(status))
            throw new IdentityException(
                    HttpStatus.CONFLICT, "ACCOUNT_UNAVAILABLE", "Applicant account is not active");
        jdbc.update(
                "INSERT INTO user_roles(user_id,role_name) VALUES (?,'RESTAURANT_OWNER') ON"
                    + " CONFLICT DO NOTHING",
                applicant);
    }
}
