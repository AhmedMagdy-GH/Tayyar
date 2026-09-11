package com.tayyar.favorite;

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
class FavoriteExceptionHandler {
    @ExceptionHandler(FavoriteException.class)
    ResponseEntity<ApiError> handle(FavoriteException error, HttpServletRequest request) {
        return ResponseEntity.status(error.status()).cacheControl(CacheControl.noStore()).body(
                new ApiError(error.code(), error.getMessage(), Instant.now(), request.getRequestURI(),
                        (String) request.getAttribute(RequestCorrelationFilter.ATTRIBUTE), List.of()));
    }
}
