package com.tayyar.favorite;

import org.springframework.util.MultiValueMap;
import java.util.Set;

record FavoriteParameters(int page, int size) {
    FavoriteParameters(MultiValueMap<String, String> values) {
        this(integer(values, "page", 0), integer(values, "size", 20));
        values.forEach((key, entries) -> {
            if (!Set.of("page", "size").contains(key) || entries.size() != 1)
                throw FavoriteException.invalid("Query parameters are invalid");
        });
        if (page < 0 || page > 10000 || size < 1 || size > 100)
            throw FavoriteException.invalid("Page must be 0–10000 and size 1–100");
    }
    long offset() { return (long) page * size; }
    private static int integer(MultiValueMap<String, String> values, String name, int fallback) {
        String value = values.getFirst(name);
        if (value == null) return fallback;
        try { return Integer.parseInt(value); }
        catch (NumberFormatException error) { throw FavoriteException.invalid(name + " must be an integer"); }
    }
}
