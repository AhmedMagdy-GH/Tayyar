package com.tayyar.checkout;

import org.springframework.http.HttpStatus;

public class CheckoutException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public CheckoutException(HttpStatus status, String code, String message) {
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

    public static CheckoutException conflict(String code, String message) {
        return new CheckoutException(HttpStatus.CONFLICT, code, message);
    }

    public static CheckoutException missing() {
        return new CheckoutException(
                HttpStatus.NOT_FOUND, "NOT_FOUND", "Checkout resource not found");
    }
}
