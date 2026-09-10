package com.tayyar.auth;

import com.tayyar.common.api.*;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class IdentityExceptionHandler {
    @ExceptionHandler(IdentityException.class)
    ResponseEntity<ApiError> identity(IdentityException error, HttpServletRequest request) {
        var response = ResponseEntity.status(error.status()).cacheControl(CacheControl.noStore());
        if (error.status() == HttpStatus.TOO_MANY_REQUESTS) response.header("Retry-After", "900");
        return response.body(body(error.code(), error.getMessage(), request));
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ApiError> authentication(
            AuthenticationException error, HttpServletRequest request) {
        return ResponseEntity.status(401)
                .cacheControl(CacheControl.noStore())
                .body(body("INVALID_CREDENTIALS", "Invalid email or password", request));
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiError> forbidden(AccessDeniedException error, HttpServletRequest request) {
        return ResponseEntity.status(403).body(body("ACCESS_DENIED", "Access denied", request));
    }

    private ApiError body(String code, String message, HttpServletRequest request) {
        return new ApiError(
                code,
                message,
                Instant.now(),
                request.getRequestURI(),
                (String) request.getAttribute(RequestCorrelationFilter.ATTRIBUTE),
                List.of());
    }
}
