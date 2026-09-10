package com.tayyar.auth;

import com.tayyar.user.*;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;

@Service
public class RegistrationService {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public RegistrationService(
            UserRepository users,
            PasswordEncoder encoder,
            Clock clock,
            PlatformTransactionManager manager) {
        this.users = users;
        this.encoder = encoder;
        this.clock = clock;
        this.transaction = new TransactionTemplate(manager);
    }

    public UserResponse register(RegistrationRequest request) {
        String hash = encoder.encode(request.password());
        try {
            return transaction.execute(
                    status ->
                            UserResponse.from(
                                    users.saveAndFlush(
                                            User.customer(
                                                    request.fullName(),
                                                    request.email(),
                                                    request.phone(),
                                                    hash,
                                                    clock.instant()))));
        } catch (DataIntegrityViolationException ex) {
            throw new IdentityException(
                    HttpStatus.CONFLICT,
                    "REGISTRATION_CONFLICT",
                    "Registration could not be completed");
        }
    }
}
