package com.tayyar.favorite;

import org.springframework.http.HttpStatus;

final class FavoriteException extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    private FavoriteException(HttpStatus status, String code, String message) {
        super(message); this.status = status; this.code = code;
    }
    HttpStatus status() { return status; }
    String code() { return code; }
    static FavoriteException invalid(String message) {
        return new FavoriteException(HttpStatus.BAD_REQUEST, "INVALID_FAVORITES_REQUEST", message);
    }
    static FavoriteException restaurantMissing() {
        return new FavoriteException(HttpStatus.NOT_FOUND, "RESTAURANT_NOT_FOUND", "Restaurant resource not found");
    }
}
