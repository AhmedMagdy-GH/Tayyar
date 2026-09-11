package com.tayyar.discovery;

import org.springframework.http.HttpStatus;

public class DiscoveryException extends RuntimeException {
    private final HttpStatus status;

    public DiscoveryException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }

    public static DiscoveryException invalid(String message) {
        return new DiscoveryException(HttpStatus.BAD_REQUEST, message);
    }

    public static DiscoveryException missing() {
        return new DiscoveryException(HttpStatus.NOT_FOUND, "Discovery resource not found");
    }
}
