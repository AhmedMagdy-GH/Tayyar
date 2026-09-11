package com.tayyar.cart;

import org.springframework.http.HttpStatus;

public class CartException extends RuntimeException {
    private final HttpStatus status;

    public CartException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }

    public static CartException invalid(String message) {
        return new CartException(HttpStatus.BAD_REQUEST, message);
    }

    public static CartException missing() {
        return new CartException(HttpStatus.NOT_FOUND, "Cart resource not found");
    }

    public static CartException conflict(String message) {
        return new CartException(HttpStatus.CONFLICT, message);
    }
}
