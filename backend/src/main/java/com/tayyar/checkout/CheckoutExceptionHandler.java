package com.tayyar.checkout;

import com.tayyar.common.api.*;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CheckoutExceptionHandler {
    @ExceptionHandler(CheckoutException.class)
    ResponseEntity<ApiError> handle(CheckoutException ex, HttpServletRequest request) {
        return ResponseEntity.status(ex.status())
                .header("Cache-Control", "no-store")
                .body(
                        new ApiError(
                                ex.code(),
                                ex.getMessage(),
                                Instant.now(),
                                request.getRequestURI(),
                                (String) request.getAttribute(RequestCorrelationFilter.ATTRIBUTE),
                                List.of()));
    }

    @ExceptionHandler(ConcurrencyFailureException.class)
    ResponseEntity<ApiError> concurrent(
            ConcurrencyFailureException ex, HttpServletRequest request) {
        return handle(
                CheckoutException.conflict(
                        "CONCURRENT_CHANGE",
                        "Data changed concurrently; retry with the same idempotency key"),
                request);
    }
}
