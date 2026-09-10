package com.tayyar.common.api;

import java.time.Instant;
import java.util.List;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Keeps servlet error dispatches on the same JSON contract as MVC failures. */
@RestController
public class ApiErrorController implements ErrorController {
    @RequestMapping("${server.error.path:/error}")
    ResponseEntity<ApiError> error(HttpServletRequest request) {
        Object statusAttribute = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        HttpStatus status = statusAttribute instanceof Integer value ? HttpStatus.resolve(value) : null;
        if (status == null || !status.isError()) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        Object path = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        return ResponseEntity.status(status).body(new ApiError(
                status.is5xxServerError() ? "INTERNAL_ERROR" : status.name(),
                status.is5xxServerError() ? "An unexpected error occurred" : status.getReasonPhrase(),
                Instant.now(), path instanceof String value ? value : request.getRequestURI(),
                (String) request.getAttribute(RequestCorrelationFilter.ATTRIBUTE), List.of()));
    }
}
