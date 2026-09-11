package com.tayyar.cart;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.util.LinkedMultiValueMap;

import java.util.Set;
import java.util.UUID;

class CartRulesTests {
    @Test
    void quantityBoundaries() {
        for (int value : new int[] {1, 99})
            assertThatCode(() -> CartRules.quantity(value)).doesNotThrowAnyException();
        for (int value : new int[] {Integer.MIN_VALUE, 0, 100, Integer.MAX_VALUE})
            assertThatThrownBy(() -> CartRules.quantity(value)).isInstanceOf(CartException.class);
    }

    @Test
    void optimisticVersionsMustMatch() {
        assertThatCode(() -> CartRules.version(7, 7L, "Cart")).doesNotThrowAnyException();
        assertThatThrownBy(() -> CartRules.version(7, null, "Cart"))
                .isInstanceOf(CartException.class);
        assertThatThrownBy(() -> CartRules.version(7, 6L, "Cart"))
                .isInstanceOf(CartException.class);
    }

    @Test
    void deleteVersionsAreStrictAndBounded() {
        var values = new LinkedMultiValueMap<String, String>();
        UUID cart = UUID.randomUUID();
        values.add("cartId", cart.toString());
        values.add("cartVersion", "0");
        values.add("itemVersion", Long.toString(Long.MAX_VALUE));
        assertThat(
                        new CartParameters(values, Set.of("cartId", "cartVersion", "itemVersion"))
                                .versions())
                .isEqualTo(new CartDtos.Versions(cart, 0, Long.MAX_VALUE));
        values.set("cartVersion", "01");
        assertThatThrownBy(
                        () ->
                                new CartParameters(
                                                values,
                                                Set.of("cartId", "cartVersion", "itemVersion"))
                                        .versions())
                .isInstanceOf(CartException.class);
    }

    @Test
    void unknownAndRepeatedDeleteParametersAreRejected() {
        var values = new LinkedMultiValueMap<String, String>();
        values.add("version", "0");
        values.add("version", "1");
        assertThatThrownBy(() -> new CartParameters(values, Set.of("version")))
                .isInstanceOf(CartException.class);
        values.clear();
        values.add("customerId", "1");
        assertThatThrownBy(() -> new CartParameters(values, Set.of("version")))
                .isInstanceOf(CartException.class);
    }
}
