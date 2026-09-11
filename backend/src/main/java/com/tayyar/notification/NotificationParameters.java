package com.tayyar.notification;

import static com.tayyar.notification.NotificationDtos.Window;

import org.springframework.util.MultiValueMap;

import java.util.Set;

final class NotificationParameters {
    private final MultiValueMap<String, String> values;

    NotificationParameters(MultiValueMap<String, String> values) {
        this.values = values;
        values.forEach((key, entries) -> {
            if (!Set.of("page", "size", "read").contains(key) || entries.size() != 1)
                throw NotificationException.invalid(
                        "INVALID_PARAMETERS", "Query parameters are invalid");
        });
    }

    Window window() {
        return new Window(integer("page", 0), integer("size", 20));
    }

    Boolean read() {
        String value = values.getFirst("read");
        if (value == null) return null;
        if (value.equals("true")) return true;
        if (value.equals("false")) return false;
        throw NotificationException.invalid("INVALID_READ_FILTER", "read must be true or false");
    }

    private int integer(String name, int fallback) {
        String value = values.getFirst(name);
        if (value == null) return fallback;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw NotificationException.invalid("INVALID_PARAMETERS", name + " must be an integer");
        }
    }
}
