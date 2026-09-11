package com.tayyar.discovery;

import static com.tayyar.discovery.DiscoveryDtos.*;

import org.springframework.util.MultiValueMap;

import java.math.BigDecimal;
import java.util.*;

/** Strict query contract also rejects duplicate and client-supplied quote parameters. */
final class DiscoveryParameters {
    static final Set<String> LIST =
            Set.of(
                    "query",
                    "zoneId",
                    "addressId",
                    "openNow",
                    "maxDeliveryFee",
                    "maxMinimumOrder",
                    "categoryId",
                    "availableOnly",
                    "sort",
                    "page",
                    "size");
    private final MultiValueMap<String, String> values;

    DiscoveryParameters(MultiValueMap<String, String> values, Set<String> allowed) {
        this.values = values;
        values.forEach(
                (k, v) -> {
                    if (!allowed.contains(k) || v.size() != 1)
                        throw DiscoveryException.invalid("Unknown or repeated query parameter");
                    if (v.getFirst() == null || v.getFirst().length() > 200)
                        throw DiscoveryException.invalid("Query parameter exceeds 200 characters");
                });
    }

    String get(String name, String fallback) {
        return values.containsKey(name) ? values.getFirst(name) : fallback;
    }

    UUID uuid(String name) {
        if (!values.containsKey(name)) return null;
        try {
            String raw = get(name, "");
            UUID value = UUID.fromString(raw);
            if (!value.toString().equalsIgnoreCase(raw)) throw new IllegalArgumentException();
            return value;
        } catch (IllegalArgumentException ex) {
            throw DiscoveryException.invalid("Invalid UUID parameter");
        }
    }

    Boolean bool(String name, Boolean fallback) {
        if (!values.containsKey(name)) return fallback;
        return switch (get(name, "")) {
            case "true" -> true;
            case "false" -> false;
            default -> throw DiscoveryException.invalid("Boolean parameters must be true or false");
        };
    }

    int integer(String name, int fallback) {
        try {
            return Integer.parseInt(get(name, Integer.toString(fallback)));
        } catch (NumberFormatException ex) {
            throw DiscoveryException.invalid("Invalid pagination parameter");
        }
    }

    BigDecimal money(String name) {
        if (!values.containsKey(name)) return null;
        String raw = get(name, "");
        if (!raw.matches("[0-9]{1,10}(\\.[0-9]{1,2})?"))
            throw DiscoveryException.invalid("Invalid money filter");
        return new BigDecimal(raw);
    }

    Window window() {
        return new Window(integer("page", 0), integer("size", 20));
    }

    Filter filter() {
        Sort sort;
        try {
            sort = Sort.valueOf(get("sort", "NAME"));
        } catch (IllegalArgumentException ex) {
            throw DiscoveryException.invalid("Unknown sort option");
        }
        return new Filter(
                get("query", ""),
                uuid("zoneId"),
                uuid("addressId"),
                bool("openNow", null),
                money("maxDeliveryFee"),
                money("maxMinimumOrder"),
                uuid("categoryId"),
                bool("availableOnly", false),
                sort);
    }
}
