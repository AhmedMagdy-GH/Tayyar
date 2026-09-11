package com.tayyar.admin;

import com.tayyar.common.api.ApiError;
import com.tayyar.common.api.RequestCorrelationFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.List;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class AdminExceptionHandler {
    @ExceptionHandler(AdminException.class)
    ResponseEntity<ApiError> handle(AdminException error, HttpServletRequest request) {
        return ResponseEntity.status(error.status()).cacheControl(CacheControl.noStore()).body(
                new ApiError(error.code(), error.getMessage(), Instant.now(), request.getRequestURI(),
                        (String) request.getAttribute(RequestCorrelationFilter.ATTRIBUTE), List.of()));
    }
}
