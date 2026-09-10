package com.tayyar.restaurant;

import static com.tayyar.restaurant.RestaurantDtos.*;

import com.tayyar.auth.SessionPrincipal;
import com.tayyar.user.*;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class ApplicationService {
    private final ApplicationRepository applications;
    private final RestaurantRepository restaurants;
    private final RestaurantJournal journal;
    private final RestaurantOwnerRoleService roles;
    private final Clock clock;

    public ApplicationService(
            ApplicationRepository applications,
            RestaurantRepository restaurants,
            RestaurantJournal journal,
            RestaurantOwnerRoleService roles,
            Clock clock) {
        this.applications = applications;
        this.restaurants = restaurants;
        this.journal = journal;
        this.roles = roles;
        this.clock = clock;
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    @Transactional
    public ApplicationView apply(SessionPrincipal actor, Apply input) {
        var application = new RestaurantApplication(actor.id(), clock.instant());
        applications.saveAndFlush(application);
        journal.submit(application.getId(), 1, input.name(), input.description(), clock.instant());
        return journal.application(application.getId());
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    @Transactional
    public ApplicationView resubmit(UUID id, SessionPrincipal actor, Resubmit input) {
        var application = applications.lockById(id).orElseThrow(RestaurantException::missing);
        if (!application.getApplicantId().equals(actor.id())) throw RestaurantException.missing();
        application.resubmit(input.version(), clock.instant());
        journal.submit(
                id,
                application.getCurrentRevision(),
                input.name(),
                input.description(),
                clock.instant());
        applications.flush();
        return journal.application(id);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ApplicationView review(UUID id, SessionPrincipal actor, Review input) {
        var application = applications.lockById(id).orElseThrow(RestaurantException::missing);
        if (application.getApplicantId().equals(actor.id()))
            throw new AccessDeniedException("Self-review is prohibited");
        if (input.outcome() == ReviewOutcome.REJECTED
                && (input.reason() == null || input.reason().isBlank()))
            throw new RestaurantException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "REJECTION_REASON_REQUIRED",
                    "Rejection requires a reason");
        application.review(input.outcome(), input.version(), clock.instant());
        UUID restaurantId = null;
        if (input.outcome() == ReviewOutcome.APPROVED) {
            roles.grantForApprovedOwnership(application.getApplicantId());
            var submission = journal.submission(id, application.getCurrentRevision());
            var restaurant =
                    new Restaurant(
                            id, submission.name(), submission.description(), clock.instant());
            restaurants.saveAndFlush(restaurant);
            restaurantId = restaurant.getId();
            journal.createOwner(restaurantId, application.getApplicantId(), clock.instant());
        }
        journal.recordDecision(
                id,
                application.getCurrentRevision(),
                actor.id(),
                input.outcome(),
                input.reason(),
                restaurantId,
                clock.instant());
        applications.flush();
        return journal.application(id);
    }

    @PreAuthorize("hasAnyRole('CUSTOMER','ADMIN')")
    public ApplicationView details(UUID id, SessionPrincipal actor) {
        checkAccess(id, actor);
        return journal.application(id);
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    public Page<ApplicationView> mine(SessionPrincipal actor, Window window) {
        return journal.applications(actor.id(), null, window);
    }

    @PreAuthorize("hasRole('ADMIN')")
    public Page<ApplicationView> queue(ApplicationStatus status, Window window) {
        return journal.applications(null, status, window);
    }

    @PreAuthorize("hasAnyRole('CUSTOMER','ADMIN')")
    public Page<Submission> submissions(UUID id, SessionPrincipal actor, Window window) {
        checkAccess(id, actor);
        return journal.submissions(id, window);
    }

    @PreAuthorize("hasAnyRole('CUSTOMER','ADMIN')")
    public Page<Decision> decisions(UUID id, SessionPrincipal actor, Window window) {
        checkAccess(id, actor);
        return journal.decisions(id, window);
    }

    private void checkAccess(UUID id, SessionPrincipal actor) {
        var application = applications.findById(id).orElseThrow(RestaurantException::missing);
        if (!actor.roles().contains(Role.ADMIN) && !application.getApplicantId().equals(actor.id()))
            throw RestaurantException.missing();
    }
}
