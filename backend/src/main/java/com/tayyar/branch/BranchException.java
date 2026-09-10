package com.tayyar.branch;

import org.springframework.http.HttpStatus;

public class BranchException extends RuntimeException {
    private final HttpStatus status;

    public BranchException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }

    public static BranchException missing() {
        return new BranchException(HttpStatus.NOT_FOUND, "Branch resource not found");
    }

    public static BranchException conflict() {
        return new BranchException(HttpStatus.CONFLICT, "Branch state changed; reload and retry");
    }

    public static BranchException invalid(String message) {
        return new BranchException(HttpStatus.BAD_REQUEST, message);
    }
}
