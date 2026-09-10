package com.tayyar.restaurant;

import org.springframework.http.HttpStatus;

public class RestaurantException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public RestaurantException(HttpStatus status, String code, String message) {
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

    public static RestaurantException missing() {
        return new RestaurantException(
                HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Resource was not found");
    }

    public static RestaurantException conflict() {
        return new RestaurantException(
                HttpStatus.CONFLICT,
                "STATE_CONFLICT",
                "Resource has changed or the transition is not allowed");
    }
}
