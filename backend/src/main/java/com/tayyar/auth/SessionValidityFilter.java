package com.tayyar.auth;

import com.tayyar.user.*;

import jakarta.servlet.*;
import jakarta.servlet.http.*;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;

public class SessionValidityFilter extends OncePerRequestFilter {
    private final UserRepository users;
    private final IdentityProperties properties;
    private final Clock clock;
    private final SecurityErrorWriter errors;

    public SessionValidityFilter(
            UserRepository users,
            IdentityProperties properties,
            Clock clock,
            SecurityErrorWriter errors) {
        this.users = users;
        this.properties = properties;
        this.clock = clock;
        this.errors = errors;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null
                && authentication.getPrincipal() instanceof SessionPrincipal principal) {
            var state = users.findSecurityStateById(principal.id());
            boolean expired =
                    !clock.instant()
                            .isBefore(
                                    principal
                                            .authenticatedAt()
                                            .plus(properties.absoluteLifetime()));
            if (expired
                    || state.isEmpty()
                    || state.get().getStatus() != AccountStatus.ACTIVE
                    || state.get().getAuthVersion() != principal.authVersion()) {
                var session = request.getSession(false);
                if (session != null) session.invalidate();
                SecurityContextHolder.clearContext();
                errors.write(
                        request, response, 401, "SESSION_INVALID", "Authentication is required");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
