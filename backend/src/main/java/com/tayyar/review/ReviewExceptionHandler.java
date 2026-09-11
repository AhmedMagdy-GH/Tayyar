package com.tayyar.review;

import com.tayyar.common.api.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.List;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class ReviewExceptionHandler {
    @ExceptionHandler(ReviewException.class)
    ResponseEntity<ApiError> handle(ReviewException error, HttpServletRequest request) {
        return ResponseEntity.status(error.status()).cacheControl(CacheControl.noStore()).body(
                new ApiError(error.code(), error.getMessage(), Instant.now(), request.getRequestURI(),
                        (String) request.getAttribute(RequestCorrelationFilter.ATTRIBUTE), List.of()));
    }
}
