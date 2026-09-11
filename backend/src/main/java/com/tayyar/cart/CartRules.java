package com.tayyar.cart;

import java.util.UUID;

final class CartRules {
    private CartRules() {}

    static void quantity(int quantity) {
        if (quantity < 1 || quantity > CartDtos.MAX_QUANTITY)
            throw CartException.invalid("Quantity must be between 1 and 99");
    }

    static void version(long actual, Long expected, String resource) {
        if (expected == null || actual != expected)
            throw CartException.conflict(resource + " changed; reload and retry");
    }

    static void cart(UUID actual, UUID expected, long actualVersion, Long expectedVersion) {
        if (expected == null || !actual.equals(expected))
            throw CartException.conflict("Cart changed; reload and retry");
        version(actualVersion, expectedVersion, "Cart");
    }
}
