package com.tayyar.auth;

import com.tayyar.user.UserResponse;

import jakarta.servlet.http.*;
import jakarta.validation.Valid;

import org.springframework.http.*;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final RegistrationService registrations;
    private final SessionLoginService sessions;
    private final AuthRateLimiter limiter;
    private final IdentityProperties properties;

    public AuthController(
            RegistrationService registrations,
            SessionLoginService sessions,
            AuthRateLimiter limiter,
            IdentityProperties properties) {
        this.registrations = registrations;
        this.sessions = sessions;
        this.limiter = limiter;
        this.properties = properties;
    }

    @GetMapping("/csrf")
    public ResponseEntity<CsrfResponse> csrf(CsrfToken token, HttpServletRequest request) {
        limiter.acquire("csrf:" + request.getRemoteAddr(), properties.csrfLimit());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new CsrfResponse(token.getHeaderName(), token.getToken()));
    }

    @PostMapping("/registrations")
    public ResponseEntity<UserResponse> register(
            @Valid @RequestBody RegistrationRequest input, HttpServletRequest request) {
        limiter.acquire("registration:" + request.getRemoteAddr(), properties.registrationLimit());
        return ResponseEntity.status(201)
                .cacheControl(CacheControl.noStore())
                .body(registrations.register(input));
    }

    @PostMapping("/session")
    public ResponseEntity<Void> login(
            @Valid @RequestBody LoginRequest input,
            HttpServletRequest request,
            HttpServletResponse response) {
        limiter.acquire("login-ip:" + request.getRemoteAddr(), properties.loginLimit());
        limiter.acquire("login-email:" + input.email(), properties.loginLimit());
        sessions.login(input, request, response);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    public record CsrfResponse(String headerName, String token) {}
}
