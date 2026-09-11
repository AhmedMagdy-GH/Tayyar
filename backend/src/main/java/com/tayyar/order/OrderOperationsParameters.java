package com.tayyar.order;

import static com.tayyar.order.OrderOperationsDtos.Window;

import org.springframework.util.MultiValueMap;

import java.util.*;

final class OrderOperationsParameters {
    private final MultiValueMap<String, String> values;

    OrderOperationsParameters(MultiValueMap<String, String> values, Set<String> allowed) {
        this.values = values;
        values.forEach(
                (key, entries) -> {
                    if (!allowed.contains(key) || entries.size() != 1)
                        throw OrderOperationsException.invalid(
                                "INVALID_PARAMETERS", "Query parameters are invalid");
                });
    }

    Window window() {
        return new Window(integer("page", 0), integer("size", 20));
    }

    OrderStatus status(boolean queue) {
        String value = values.getFirst("status");
        if (value == null) return null;
        try {
            OrderStatus status = OrderStatus.valueOf(value);
            if (queue
                    && !Set.of(
                                    OrderStatus.PLACED,
                                    OrderStatus.ACCEPTED,
                                    OrderStatus.PREPARING,
                                    OrderStatus.READY_FOR_PICKUP)
                            .contains(status)) throw new IllegalArgumentException();
            return status;
        } catch (IllegalArgumentException exception) {
            throw OrderOperationsException.invalid(
                    "INVALID_STATUS", "Status filter is not supported");
        }
    }

    UUID requiredUuid(String name) {
        UUID value = uuid(name);
        if (value == null)
            throw OrderOperationsException.invalid("INVALID_PARAMETERS", name + " is required");
        return value;
    }

    UUID uuid(String name) {
        String value = values.getFirst(name);
        if (value == null) return null;
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw OrderOperationsException.invalid("INVALID_PARAMETERS", name + " must be a UUID");
        }
    }

    private int integer(String name, int defaultValue) {
        String value = values.getFirst(name);
        if (value == null) return defaultValue;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw OrderOperationsException.invalid(
                    "INVALID_PARAMETERS", name + " must be an integer");
        }
    }
}
