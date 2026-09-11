package com.tayyar.order;

import org.springframework.http.HttpStatus;

public class OrderOperationsException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public OrderOperationsException(HttpStatus status, String code, String message) {
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

    public static OrderOperationsException invalid(String code, String message) {
        return new OrderOperationsException(HttpStatus.BAD_REQUEST, code, message);
    }

    public static OrderOperationsException missing() {
        return new OrderOperationsException(
                HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "Order resource not found");
    }

    public static OrderOperationsException conflict(String message) {
        return new OrderOperationsException(HttpStatus.CONFLICT, "ORDER_CONFLICT", message);
    }
}
