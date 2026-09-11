package com.tayyar.delivery;

import static com.tayyar.delivery.DriverOperationsDtos.*;

import com.tayyar.auth.SessionPrincipal;

import jakarta.validation.Valid;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/delivery-assignments")
public class AdminDeliveryAssignmentController {
    private final DeliveryAssignmentService assignments;

    public AdminDeliveryAssignmentController(DeliveryAssignmentService assignments) {
        this.assignments = assignments;
    }

    @PostMapping
    public Assignment assign(
            @AuthenticationPrincipal SessionPrincipal actor,
            @Valid @RequestBody AssignmentRequest input) {
        return assignments.assign(actor, input);
    }
}
