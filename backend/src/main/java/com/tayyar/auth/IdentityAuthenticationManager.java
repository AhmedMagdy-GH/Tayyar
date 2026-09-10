package com.tayyar.auth;

import com.tayyar.user.*;

import org.springframework.security.authentication.*;
import org.springframework.security.core.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Clock;

@Component
public class IdentityAuthenticationManager implements AuthenticationManager {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final Clock clock;
    private final String dummyHash;

    public IdentityAuthenticationManager(
            UserRepository users, PasswordEncoder encoder, Clock clock) {
        this.users = users;
        this.encoder = encoder;
        this.clock = clock;
        this.dummyHash = encoder.encode(java.util.UUID.randomUUID().toString());
    }

    @Override
    public Authentication authenticate(Authentication request) {
        String password = (String) request.getCredentials();
        if (password == null || password.getBytes(StandardCharsets.UTF_8).length > 72)
            throw invalid();
        User user = users.findByEmail(EmailAddress.normalize(request.getName())).orElse(null);
        boolean matches =
                encoder.matches(password, user == null ? dummyHash : user.getPasswordHash());
        if (!matches || user == null || user.getStatus() != AccountStatus.ACTIVE) throw invalid();
        SessionPrincipal principal =
                new SessionPrincipal(
                        user.getId(), user.getRoles(), user.getAuthVersion(), clock.instant());
        return UsernamePasswordAuthenticationToken.authenticated(
                principal,
                null,
                principal.roles().stream()
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
                        .toList());
    }

    private BadCredentialsException invalid() {
        return new BadCredentialsException("Invalid email or password");
    }
}
