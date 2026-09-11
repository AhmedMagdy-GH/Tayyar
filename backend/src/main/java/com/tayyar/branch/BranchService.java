package com.tayyar.branch;

import static com.tayyar.branch.BranchDtos.*;

import com.tayyar.auth.SessionPrincipal;
import com.tayyar.restaurant.RestaurantService;
import com.tayyar.restaurant.RestaurantStatus;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
@PreAuthorize("hasAnyRole('RESTAURANT_OWNER','ADMIN')")
public class BranchService {
    private final BranchRepository branches;
    private final BranchJournal journal;
    private final RestaurantService restaurants;
    private final Clock clock;
    private final BranchScheduleQuery schedules;

    public BranchService(
            BranchRepository branches,
            BranchJournal journal,
            RestaurantService restaurants,
            Clock clock,
            BranchScheduleQuery schedules) {
        this.branches = branches;
        this.journal = journal;
        this.restaurants = restaurants;
        this.clock = clock;
        this.schedules = schedules;
    }

    @Transactional
    public View create(UUID restaurant, SessionPrincipal actor, Profile input) {
        restaurants.details(restaurant, actor);
        BranchRules.profile(input);
        journal.validateTimezone(input.timezone());
        return branches.saveAndFlush(new Branch(restaurant, input, clock.instant())).view();
    }

    public View details(UUID restaurant, UUID branch, SessionPrincipal actor) {
        return read(restaurant, branch, actor).view();
    }

    public Page<View> list(UUID restaurant, SessionPrincipal actor, Window window) {
        restaurants.details(restaurant, actor);
        var rows =
                branches.findByRestaurantId(
                        restaurant,
                        PageRequest.of(
                                window.page(),
                                window.size(),
                                Sort.by(Sort.Direction.DESC, "createdAt", "id")));
        return new Page<>(
                rows.map(Branch::view).getContent(),
                window.page(),
                window.size(),
                rows.getTotalElements());
    }

    @Transactional
    public View edit(UUID restaurant, UUID branch, SessionPrincipal actor, Edit input) {
        var row = lock(restaurant, branch, actor);
        BranchRules.profile(input.profile());
        journal.validateTimezone(input.profile().timezone());
        row.touch(input.version(), clock.instant());
        row.profile(input.profile());
        branches.flush();
        return row.view();
    }

    @Transactional
    public View operation(UUID restaurant, UUID branch, SessionPrincipal actor, Operation input) {
        var row = lock(restaurant, branch, actor);
        row.operation(input, clock.instant());
        branches.flush();
        return row.view();
    }

    public Schedule hours(UUID restaurant, UUID branch, SessionPrincipal actor) {
        var row = read(restaurant, branch, actor);
        return new Schedule(journal.weekly(branch), journal.special(branch), row.version());
    }

    @Transactional
    public Schedule replaceHours(
            UUID restaurant, UUID branch, SessionPrincipal actor, Hours input) {
        var row = lock(restaurant, branch, actor);
        BranchRules.hours(input);
        row.touch(input.version(), clock.instant());
        journal.replaceHours(branch, input);
        branches.flush();
        return new Schedule(journal.weekly(branch), journal.special(branch), row.version());
    }

    public Availability availability(UUID restaurant, UUID branch, SessionPrincipal actor) {
        var parent = restaurants.details(restaurant, actor);
        var row =
                branches.findByIdAndRestaurantId(branch, restaurant)
                        .orElseThrow(BranchException::missing);
        var now = clock.instant();
        boolean open = schedules.open(branch, now);
        String state =
                parent.status() != RestaurantStatus.ACTIVE
                        ? "RESTAURANT_SUSPENDED"
                        : row.status() != BranchStatus.ACTIVE
                                ? "INACTIVE"
                                : row.paused() ? "PAUSED" : open ? "OPEN" : "CLOSED";
        return new Availability(state, open, now, row.timezone());
    }

    public Page<Staff> staff(UUID restaurant, UUID branch, SessionPrincipal actor, Window window) {
        read(restaurant, branch, actor);
        return journal.staff(branch, window);
    }

    @Transactional
    public View assign(
            UUID restaurant, UUID branch, UUID user, SessionPrincipal actor, Version input) {
        var row = lock(restaurant, branch, actor);
        row.touch(input.version(), clock.instant());
        if (!journal.activeUser(user) || !journal.member(restaurant, user))
            throw BranchException.invalid("Staff must be an active member of this restaurant");
        journal.assign(branch, restaurant, user, actor.id(), clock.instant());
        branches.flush();
        return row.view();
    }

    @Transactional
    public View unassign(
            UUID restaurant, UUID branch, UUID user, SessionPrincipal actor, Version input) {
        var row = lock(restaurant, branch, actor);
        row.touch(input.version(), clock.instant());
        journal.unassign(branch, user);
        branches.flush();
        return row.view();
    }

    private Branch read(UUID restaurant, UUID branch, SessionPrincipal actor) {
        restaurants.details(restaurant, actor);
        return branches.findByIdAndRestaurantId(branch, restaurant)
                .orElseThrow(BranchException::missing);
    }

    private Branch lock(UUID restaurant, UUID branch, SessionPrincipal actor) {
        restaurants.details(restaurant, actor);
        return branches.lock(restaurant, branch).orElseThrow(BranchException::missing);
    }
}
