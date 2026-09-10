package com.tayyar.restaurant;

import static com.tayyar.restaurant.RestaurantDtos.*;

import com.tayyar.auth.SessionPrincipal;
import com.tayyar.user.Role;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
@PreAuthorize("hasAnyRole('RESTAURANT_OWNER','ADMIN')")
public class RestaurantService {
    private final RestaurantRepository restaurants;
    private final RestaurantJournal journal;
    private final Clock clock;

    public RestaurantService(
            RestaurantRepository restaurants, RestaurantJournal journal, Clock clock) {
        this.restaurants = restaurants;
        this.journal = journal;
        this.clock = clock;
    }

    public RestaurantView details(UUID id, SessionPrincipal actor) {
        checkAccess(id, actor);
        return RestaurantView.from(
                restaurants.findById(id).orElseThrow(RestaurantException::missing));
    }

    public Page<RestaurantView> list(SessionPrincipal actor, Window window) {
        return journal.restaurants(actor.roles().contains(Role.ADMIN) ? null : actor.id(), window);
    }

    @Transactional
    public RestaurantView edit(UUID id, SessionPrincipal actor, Edit input) {
        var restaurant = restaurants.lockById(id).orElseThrow(RestaurantException::missing);
        checkAccess(id, actor);
        restaurant.edit(input.name(), input.description(), input.version(), clock.instant());
        restaurants.flush();
        return RestaurantView.from(restaurant);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public RestaurantView changeStatus(UUID id, SessionPrincipal actor, StatusChange input) {
        var restaurant = restaurants.lockById(id).orElseThrow(RestaurantException::missing);
        var previous = restaurant.getStatus();
        restaurant.changeStatus(input.status(), input.version(), clock.instant());
        journal.recordStatus(
                id, actor.id(), previous, input.status(), input.reason(), clock.instant());
        restaurants.flush();
        return RestaurantView.from(restaurant);
    }

    public Page<Membership> memberships(UUID id, SessionPrincipal actor, Window window) {
        checkAccess(id, actor);
        return journal.memberships(id, window);
    }

    @PreAuthorize("hasRole('ADMIN')")
    public Page<StatusHistory> statusHistory(UUID id, Window window) {
        if (!restaurants.existsById(id)) throw RestaurantException.missing();
        return journal.statusHistory(id, window);
    }

    private void checkAccess(UUID id, SessionPrincipal actor) {
        if (!actor.roles().contains(Role.ADMIN) && !journal.isOwner(id, actor.id()))
            throw RestaurantException.missing();
        if (!restaurants.existsById(id)) throw RestaurantException.missing();
    }
}
