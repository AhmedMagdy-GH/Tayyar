package com.tayyar.admin;

import org.springframework.http.HttpStatus;

public class AdminException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    AdminException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    HttpStatus status() { return status; }
    String code() { return code; }

    static AdminException invalid(String code, String message) {
        return new AdminException(HttpStatus.BAD_REQUEST, code, message);
    }
    static AdminException missing(String resource) {
        return new AdminException(HttpStatus.NOT_FOUND, resource + "_NOT_FOUND", resource + " resource not found");
    }
    static AdminException conflict(String message) {
        return new AdminException(HttpStatus.CONFLICT, "ADMIN_OPERATION_CONFLICT", message);
    }
}
