package com.tayyar.promotion;

import org.springframework.http.HttpStatus;

public class PromotionException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public PromotionException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() { return status; }
    public String code() { return code; }

    public static PromotionException invalid(String message) {
        return new PromotionException(HttpStatus.BAD_REQUEST, "INVALID_PROMOTION", message);
    }

    public static PromotionException missing() {
        return new PromotionException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Promotion not found");
    }

    public static PromotionException conflict(String code, String message) {
        return new PromotionException(HttpStatus.CONFLICT, code, message);
    }
}
