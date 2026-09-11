package com.tayyar.delivery;

import static com.tayyar.delivery.DriverOperationsDtos.Window;

import org.springframework.util.MultiValueMap;

import java.util.Set;

final class DriverOperationsParameters {
    private final MultiValueMap<String, String> values;

    DriverOperationsParameters(MultiValueMap<String, String> values, Set<String> allowed) {
        this.values = values;
        values.forEach(
                (key, entries) -> {
                    if (!allowed.contains(key) || entries.size() != 1)
                        throw DriverOperationsException.invalid("Query parameters are invalid");
                });
    }

    Window window() {
        return new Window(integer("page", 0), integer("size", 20));
    }

    private int integer(String name, int fallback) {
        String value = values.getFirst(name);
        if (value == null) return fallback;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw DriverOperationsException.invalid(name + " must be an integer");
        }
    }
}
