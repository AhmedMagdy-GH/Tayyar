package com.tayyar.notification;

import org.springframework.http.HttpStatus;

final class NotificationException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    private NotificationException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    HttpStatus status() { return status; }
    String code() { return code; }

    static NotificationException invalid(String code, String message) {
        return new NotificationException(HttpStatus.BAD_REQUEST, code, message);
    }

    static NotificationException missing() {
        return new NotificationException(
                HttpStatus.NOT_FOUND, "NOTIFICATION_NOT_FOUND", "Notification resource not found");
    }
}
