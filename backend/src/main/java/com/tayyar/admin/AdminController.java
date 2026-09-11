package com.tayyar.admin;

import static com.tayyar.admin.AdminDtos.*;

import com.tayyar.auth.SessionPrincipal;
import com.tayyar.delivery.DriverState;
import com.tayyar.order.OrderStatus;
import com.tayyar.user.AccountStatus;
import com.tayyar.user.Role;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
class AdminController {
    private final AdminService admin;

    AdminController(AdminService admin) { this.admin = admin; }

    @GetMapping("/users")
    Page<UserSummary> users(@RequestParam(required=false) String email,
                            @RequestParam(required=false) AccountStatus status,
                            @RequestParam(required=false) Role role,
                            @RequestParam(defaultValue="0") int page,
                            @RequestParam(defaultValue="20") int size) {
        return admin.users(email, status, role, new Window(page, size));
    }

    @GetMapping("/users/{id}")
    UserSummary user(@PathVariable UUID id) { return admin.user(id); }

    @PostMapping("/users/{id}/suspension")
    UserSummary suspend(@PathVariable UUID id, @AuthenticationPrincipal SessionPrincipal actor,
                        @Valid @RequestBody AccountCommand command) {
        return admin.suspend(id, actor, command);
    }

    @PostMapping("/users/{id}/reactivation")
    UserSummary reactivate(@PathVariable UUID id, @AuthenticationPrincipal SessionPrincipal actor,
                           @Valid @RequestBody AccountCommand command) {
        return admin.reactivate(id, actor, command);
    }

    @GetMapping("/restaurants/{id}")
    RestaurantOverview restaurant(@PathVariable UUID id) { return admin.restaurant(id); }

    @GetMapping("/orders")
    Page<OrderSummary> orders(@RequestParam(required=false) OrderStatus status,
                              @RequestParam(required=false) UUID restaurantId,
                              @RequestParam(required=false) UUID branchId,
                              @RequestParam(required=false) UUID customerId,
                              @RequestParam(defaultValue="0") int page,
                              @RequestParam(defaultValue="20") int size) {
        return admin.orders(status, restaurantId, branchId, customerId, new Window(page, size));
    }

    @GetMapping("/orders/{id}")
    OrderDetails order(@PathVariable UUID id) { return admin.order(id); }

    @GetMapping("/drivers")
    Page<DriverSummary> drivers(@RequestParam(required=false) AccountStatus accountStatus,
                                @RequestParam(required=false) DriverState state,
                                @RequestParam(defaultValue="0") int page,
                                @RequestParam(defaultValue="20") int size) {
        return admin.drivers(accountStatus, state, new Window(page, size));
    }

    @GetMapping("/audit-log")
    Page<AuditRecord> audit(@RequestParam(required=false) AdminAction action,
                            @RequestParam(required=false) AdminTarget targetType,
                            @RequestParam(required=false) UUID targetId,
                            @RequestParam(required=false) UUID actorId,
                            @RequestParam(defaultValue="0") int page,
                            @RequestParam(defaultValue="20") int size) {
        return admin.audit(action, targetType, targetId, actorId, new Window(page, size));
    }
}
