package com.tayyar.menu;

import org.springframework.http.HttpStatus;

public class MenuException extends RuntimeException {
    private final HttpStatus status;

    public MenuException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }

    public static MenuException missing() {
        return new MenuException(HttpStatus.NOT_FOUND, "Menu resource not found");
    }

    public static MenuException conflict() {
        return new MenuException(HttpStatus.CONFLICT, "Menu state changed; reload and retry");
    }

    public static MenuException invalid(String message) {
        return new MenuException(HttpStatus.BAD_REQUEST, message);
    }
}
