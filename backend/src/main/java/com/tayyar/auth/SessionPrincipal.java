package com.tayyar.auth;

import com.tayyar.user.Role;

import java.io.Serializable;
import java.security.Principal;
import java.time.Instant;
import java.util.*;

public record SessionPrincipal(UUID id, Set<Role> roles, long authVersion, Instant authenticatedAt)
        implements Principal, Serializable {
    public SessionPrincipal {
        roles = Set.copyOf(roles);
    }

    @Override
    public String getName() {
        return id.toString();
    }
}
