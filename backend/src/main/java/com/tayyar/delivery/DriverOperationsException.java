package com.tayyar.delivery;

import org.springframework.http.HttpStatus;

public class DriverOperationsException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    private DriverOperationsException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    static DriverOperationsException invalid(String message) {
        return new DriverOperationsException(HttpStatus.BAD_REQUEST, "DELIVERY_INVALID", message);
    }

    static DriverOperationsException missing() {
        return new DriverOperationsException(
                HttpStatus.NOT_FOUND, "DELIVERY_NOT_FOUND", "Delivery resource not found");
    }

    static DriverOperationsException conflict(String message) {
        return new DriverOperationsException(HttpStatus.CONFLICT, "DELIVERY_CONFLICT", message);
    }
}
