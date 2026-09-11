package com.tayyar.common.api;

import java.time.Instant;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<ApiError.FieldViolation> fields = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new ApiError.FieldViolation(error.getField(), error.getDefaultMessage()))
                .toList();
        return response("VALIDATION_FAILED", "Request validation failed", status, headers, request, fields);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        HttpStatus resolved = HttpStatus.resolve(status.value());
        String code = resolved == null ? "HTTP_ERROR" : resolved.name();
        String message = resolved == null ? "Request failed" : resolved.getReasonPhrase();
        return response(code, message, status, headers, request, List.of());
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        if (rootCause(ex) instanceof RequestBodyTooLargeException) {
            return response("PAYLOAD_TOO_LARGE", "Request body exceeds the configured limit",
                    HttpStatus.PAYLOAD_TOO_LARGE, headers, request, List.of());
        }
        return response("BAD_REQUEST", "Bad Request", status, headers, request, List.of());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> unexpected(Exception ex, ServletWebRequest request) {
        // Exception messages can contain SQL, submitted secrets, or provider payloads.
        StackTraceElement origin = ex.getStackTrace().length == 0 ? null : ex.getStackTrace()[0];
        LOG.error("Unexpected API failure; correlationId={}, exceptionType={}, failureAt={}",
                request.getRequest().getAttribute(RequestCorrelationFilter.ATTRIBUTE),
                ex.getClass().getName(), origin);
        return response("INTERNAL_ERROR", "An unexpected error occurred", HttpStatus.INTERNAL_SERVER_ERROR,
                HttpHeaders.EMPTY, request, List.of());
    }

    private ResponseEntity<Object> response(String code, String message, HttpStatusCode status,
            HttpHeaders headers, WebRequest request, List<ApiError.FieldViolation> fields) {
        HttpServletRequest servletRequest = ((ServletWebRequest) request).getRequest();
        ApiError error = new ApiError(code, message, Instant.now(), servletRequest.getRequestURI(),
                (String) servletRequest.getAttribute(RequestCorrelationFilter.ATTRIBUTE), fields);
        return new ResponseEntity<>(error, headers, status);
    }

    private Throwable rootCause(Throwable error) {
        Throwable result = error;
        while (result.getCause() != null && result.getCause() != result) result = result.getCause();
        return result;
    }
}
