package com.tayyar.cart;

import static com.tayyar.cart.CartDtos.Versions;

import org.springframework.util.MultiValueMap;

import java.util.*;

final class CartParameters {
    private final MultiValueMap<String, String> values;

    CartParameters(MultiValueMap<String, String> values, Set<String> allowed) {
        this.values = values;
        values.forEach(
                (key, entries) -> {
                    if (!allowed.contains(key) || entries.size() != 1)
                        throw CartException.invalid("Unknown or repeated query parameter");
                });
    }

    long requiredVersion(String name) {
        String raw = values.getFirst(name);
        if (raw == null || !raw.matches("0|[1-9][0-9]{0,18}"))
            throw CartException.invalid("A valid " + name + " is required");
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException exception) {
            throw CartException.invalid("A valid " + name + " is required");
        }
    }

    Versions versions() {
        return new Versions(
                requiredUuid("cartId"),
                requiredVersion("cartVersion"),
                requiredVersion("itemVersion"));
    }

    UUID requiredUuid(String name) {
        String raw = values.getFirst(name);
        try {
            UUID value = UUID.fromString(raw);
            if (!value.toString().equalsIgnoreCase(raw)) throw new IllegalArgumentException();
            return value;
        } catch (RuntimeException exception) {
            throw CartException.invalid("A valid " + name + " is required");
        }
    }
}
