package com.tayyar.restaurant;

import static com.tayyar.restaurant.RestaurantOperationsDtos.*;

import com.tayyar.auth.EmailAddress;
import com.tayyar.auth.SessionPrincipal;
import com.tayyar.user.AccountStatus;
import com.tayyar.user.RestaurantStaffRoleService;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
@PreAuthorize("hasAnyRole('RESTAURANT_OWNER','ADMIN')")
public class RestaurantStaffService {
    private final RestaurantService restaurants;
    private final RestaurantStaffStore staff;
    private final RestaurantStaffRoleService roles;
    private final Clock clock;

    public RestaurantStaffService(
            RestaurantService restaurants,
            RestaurantStaffStore staff,
            RestaurantStaffRoleService roles,
            Clock clock) {
        this.restaurants = restaurants;
        this.staff = staff;
        this.roles = roles;
        this.clock = clock;
    }

    public StaffPage list(
            UUID restaurant, SessionPrincipal actor, RestaurantDtos.Window window) {
        restaurants.details(restaurant, actor);
        return staff.list(restaurant, window);
    }

    @Transactional
    public StaffView create(UUID restaurant, SessionPrincipal actor, CreateStaff input) {
        restaurants.details(restaurant, actor);
        var target =
                staff.lockUserByEmail(EmailAddress.normalize(input.email()))
                        .orElseThrow(RestaurantException::missing);
        if (target.status() != AccountStatus.ACTIVE)
            throw new RestaurantException(
                    HttpStatus.CONFLICT,
                    "STAFF_ACCOUNT_UNAVAILABLE",
                    "Staff account is not active");
        if (!staff.create(restaurant, target.id(), clock.instant()))
            throw new RestaurantException(
                    HttpStatus.CONFLICT,
                    "STAFF_MEMBERSHIP_CONFLICT",
                    "Staff membership already exists or is incompatible");
        roles.grant(target.id());
        return staff.get(restaurant, target.id());
    }

    @Transactional
    public void remove(UUID restaurant, UUID user, SessionPrincipal actor) {
        restaurants.details(restaurant, actor);
        if (!staff.lockUser(user) || !staff.lockStaffMembership(restaurant, user))
            throw RestaurantException.missing();
        staff.remove(restaurant, user);
        roles.revokeWhenNoMembershipsRemain(user);
    }
}
