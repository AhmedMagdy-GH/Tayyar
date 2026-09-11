package com.tayyar.payment;

import org.springframework.http.HttpStatus;

public class PaymentException extends RuntimeException {
    private final HttpStatus status;

    private PaymentException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }

    public static PaymentException invalid(String message) {
        return new PaymentException(HttpStatus.BAD_REQUEST, message);
    }

    public static PaymentException missing() {
        return new PaymentException(HttpStatus.NOT_FOUND, "Payment resource not found");
    }

    public static PaymentException conflict() {
        return new PaymentException(HttpStatus.CONFLICT, "Payment changed; reload and retry");
    }
}
