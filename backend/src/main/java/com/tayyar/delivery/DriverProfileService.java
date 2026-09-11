package com.tayyar.delivery;

import static com.tayyar.delivery.DriverOperationsDtos.*;

import com.tayyar.auth.SessionPrincipal;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.time.*;

@Service
@Transactional(readOnly = true)
public class DriverProfileService {
    private final DriverOperationsStore store;
    private final Clock clock;

    public DriverProfileService(DriverOperationsStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public Profile provision(SessionPrincipal actor, Provision input) {
        store.provision(input.userId(), actor.id(), clock.instant());
        return store.profile(input.userId());
    }

    @PreAuthorize("hasRole('DRIVER')")
    public Profile profile(SessionPrincipal actor) {
        return store.profile(actor.id());
    }

    @PreAuthorize("hasRole('DRIVER')")
    @Transactional
    public Profile available(SessionPrincipal actor, Version input) {
        return transition(actor, input.version(), DriverState.OFFLINE, DriverState.AVAILABLE);
    }

    @PreAuthorize("hasRole('DRIVER')")
    @Transactional
    public Profile offline(SessionPrincipal actor, Version input) {
        return transition(actor, input.version(), DriverState.AVAILABLE, DriverState.OFFLINE);
    }

    private Profile transition(
            SessionPrincipal actor, long version, DriverState expected, DriverState target) {
        var driver = store.lockDriver(actor.id());
        if (driver.version() != version)
            throw DriverOperationsException.conflict("Driver state changed; reload and retry");
        if (driver.state() != expected)
            throw DriverOperationsException.conflict("Driver state transition is not allowed");
        if (target == DriverState.OFFLINE && store.activeAssignmentForDriver(actor.id()).isPresent())
            throw DriverOperationsException.conflict(
                    "A Driver with an active assignment cannot go offline");
        store.changeDriverState(
                driver,
                target,
                actor.id(),
                target == DriverState.AVAILABLE ? "Driver available" : "Driver offline",
                clock.instant());
        return store.profile(actor.id());
    }
}
