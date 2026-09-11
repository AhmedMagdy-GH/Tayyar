package com.tayyar.review;

import org.springframework.http.HttpStatus;

final class ReviewException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    private ReviewException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    HttpStatus status() { return status; }
    String code() { return code; }

    static ReviewException invalid(String message) {
        return new ReviewException(HttpStatus.BAD_REQUEST, "INVALID_REVIEW", message);
    }

    static ReviewException missing() {
        return new ReviewException(HttpStatus.NOT_FOUND, "REVIEW_NOT_FOUND", "Review resource not found");
    }

    static ReviewException restaurantMissing() {
        return new ReviewException(HttpStatus.NOT_FOUND, "RESTAURANT_NOT_FOUND", "Restaurant resource not found");
    }

    static ReviewException conflict(String message) {
        return new ReviewException(HttpStatus.CONFLICT, "REVIEW_CONFLICT", message);
    }
}
