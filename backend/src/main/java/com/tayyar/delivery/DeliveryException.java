package com.tayyar.delivery;

import org.springframework.http.HttpStatus;

public class DeliveryException extends RuntimeException {
    private final HttpStatus status;

    private DeliveryException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }

    public static DeliveryException missing() {
        return new DeliveryException(HttpStatus.NOT_FOUND, "Delivery resource not found");
    }

    public static DeliveryException conflict() {
        return new DeliveryException(
                HttpStatus.CONFLICT, "Delivery resource exists or changed; reload and retry");
    }

    public static DeliveryException invalid(String message) {
        return new DeliveryException(HttpStatus.BAD_REQUEST, message);
    }
}
