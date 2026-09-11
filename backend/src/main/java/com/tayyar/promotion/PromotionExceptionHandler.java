package com.tayyar.promotion;

import com.tayyar.common.api.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PromotionExceptionHandler {
    @ExceptionHandler(PromotionException.class)
    ResponseEntity<ApiError> handle(PromotionException ex, HttpServletRequest request) {
        return ResponseEntity.status(ex.status()).header("Cache-Control", "no-store")
                .body(new ApiError(ex.code(), ex.getMessage(), Instant.now(), request.getRequestURI(),
                        (String) request.getAttribute(RequestCorrelationFilter.ATTRIBUTE), List.of()));
    }
}
