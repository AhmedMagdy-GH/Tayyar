package com.tayyar.order;

import org.springframework.http.HttpStatus;

public class OrderException extends RuntimeException {
    private final HttpStatus status;

    private OrderException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }

    public static OrderException invalid(String message) {
        return new OrderException(HttpStatus.BAD_REQUEST, message);
    }

    public static OrderException missing() {
        return new OrderException(HttpStatus.NOT_FOUND, "Order resource not found");
    }

    public static OrderException conflict() {
        return new OrderException(HttpStatus.CONFLICT, "Order changed; reload and retry");
    }
}
