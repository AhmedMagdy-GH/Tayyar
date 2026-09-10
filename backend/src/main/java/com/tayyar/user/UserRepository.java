package com.tayyar.user;

import org.springframework.data.jpa.repository.*;

import java.util.*;

public interface UserRepository extends JpaRepository<User, UUID> {
    @EntityGraph(attributePaths = "roles")
    Optional<User> findByEmail(String email);

    @EntityGraph(attributePaths = "roles")
    Optional<User> findWithRolesById(UUID id);

    @Query("select u.status as status, u.authVersion as authVersion from User u where u.id = :id")
    Optional<SecurityState> findSecurityStateById(UUID id);

    interface SecurityState {
        AccountStatus getStatus();

        long getAuthVersion();
    }
}
