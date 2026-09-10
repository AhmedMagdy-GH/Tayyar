package com.tayyar.user;

import com.tayyar.auth.SessionPrincipal;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
public class CurrentUserController {
    private final UserRepository users;

    public CurrentUserController(UserRepository users) {
        this.users = users;
    }

    @GetMapping("/api/v1/users/me")
    @PreAuthorize("isAuthenticated()")
    public UserResponse current(@AuthenticationPrincipal SessionPrincipal principal) {
        return UserResponse.from(
                users.findWithRolesById(principal.id())
                        .orElseThrow(() -> new BadCredentialsException("Account unavailable")));
    }
}
