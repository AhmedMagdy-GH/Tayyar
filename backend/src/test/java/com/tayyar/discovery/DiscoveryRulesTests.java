package com.tayyar.discovery;

import static com.tayyar.discovery.DiscoveryDtos.*;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.util.LinkedMultiValueMap;

import java.math.BigDecimal;

class DiscoveryRulesTests {
    @Test
    void paginationBoundaries() {
        assertThat(new Window(10000, 100).offset()).isEqualTo(1000000);
        for (int[] v : new int[][] {{-1, 20}, {10001, 20}, {0, 0}, {0, 101}})
            assertThatThrownBy(() -> new Window(v[0], v[1])).isInstanceOf(DiscoveryException.class);
    }

    @Test
    void normalizationAndLiteralWildcards() {
        var f = new Filter("  A_%!  ", null, null, null, null, null, null, false, Sort.NAME);
        assertThat(f.query()).isEqualTo("A_%!");
        assertThat(f.pattern()).isEqualTo("%A!_!%!!%");
        assertThat(new Filter(" \t ", null, null, null, null, null, null, false, null).query())
                .isEmpty();
        assertThatThrownBy(
                        () ->
                                new Filter(
                                        "x".repeat(201),
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        false,
                                        null))
                .isInstanceOf(DiscoveryException.class);
        assertThatThrownBy(() -> new Filter("\0", null, null, null, null, null, null, false, null))
                .isInstanceOf(DiscoveryException.class);
    }

    @Test
    void deliveryFiltersRequireLocation() {
        assertThatThrownBy(
                        () ->
                                new Filter(
                                        null,
                                        null,
                                        null,
                                        null,
                                        BigDecimal.ONE,
                                        null,
                                        null,
                                        false,
                                        null))
                .isInstanceOf(DiscoveryException.class);
        assertThatThrownBy(
                        () -> new Filter(null, null, null, null, null, null, null, false, Sort.ETA))
                .isInstanceOf(DiscoveryException.class);
    }

    @Test
    void duplicatesAndUnsupportedParametersAreRejected() {
        var p = new LinkedMultiValueMap<String, String>();
        p.add("query", "a");
        p.add("query", "b");
        assertThatThrownBy(() -> new DiscoveryParameters(p, DiscoveryParameters.LIST))
                .isInstanceOf(DiscoveryException.class);
        p.clear();
        p.add("deliveryFee", "0");
        assertThatThrownBy(() -> new DiscoveryParameters(p, DiscoveryParameters.LIST))
                .isInstanceOf(DiscoveryException.class);
    }
}
