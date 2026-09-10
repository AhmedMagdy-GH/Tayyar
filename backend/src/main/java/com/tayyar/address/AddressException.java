package com.tayyar.address;

import org.springframework.http.HttpStatus;

public class AddressException extends RuntimeException {
    private final HttpStatus status;

    public AddressException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }

    public static AddressException missing() {
        return new AddressException(HttpStatus.NOT_FOUND, "Address not found");
    }

    public static AddressException conflict() {
        return new AddressException(HttpStatus.CONFLICT, "Address changed; reload and retry");
    }

    public static AddressException invalid(String message) {
        return new AddressException(HttpStatus.BAD_REQUEST, message);
    }
}
