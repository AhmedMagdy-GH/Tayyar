package com.tayyar.discovery;

import static com.tayyar.discovery.DiscoveryDtos.*;

import com.tayyar.auth.SessionPrincipal;
import com.tayyar.user.Role;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.time.Clock;
import java.util.UUID;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class DiscoveryService {
    private final DiscoveryQuery query;
    private final Clock clock;

    public DiscoveryService(DiscoveryQuery query, Clock clock) {
        this.query = query;
        this.clock = clock;
    }

    private UUID zone(Filter f, SessionPrincipal actor) {
        if (f.addressId() == null) return f.zoneId();
        if (actor == null)
            throw new DiscoveryException(
                    HttpStatus.UNAUTHORIZED, "Authentication is required for saved addresses");
        if (!actor.roles().contains(Role.CUSTOMER))
            throw new DiscoveryException(HttpStatus.FORBIDDEN, "A customer account is required");
        return query.ownedZone(actor.id(), f.addressId());
    }

    public Page<Restaurant> restaurants(Filter f, Window w, SessionPrincipal actor) {
        return query.restaurants(f, zone(f, actor), w, clock.instant(), null);
    }

    public Restaurant restaurant(UUID id) {
        var f = new Filter(null, null, null, null, null, null, null, false, Sort.NAME);
        var rows = query.restaurants(f, null, new Window(0, 1), clock.instant(), id).items();
        if (rows.isEmpty()) throw DiscoveryException.missing();
        return rows.getFirst();
    }

    public Page<Branch> branches(UUID restaurant, Filter f, Window w, SessionPrincipal actor) {
        UUID zone = zone(f, actor);
        return query.branches(restaurant, f, zone, w, clock.instant());
    }

    public Menu menu(
            UUID restaurant, UUID branch, Window categories, Window items, boolean availableOnly) {
        return query.menu(restaurant, branch, categories, items, availableOnly);
    }

    public Page<Zone> zones(UUID city, Window w) {
        return query.zones(city, w);
    }
}
