package com.tayyar.delivery;

import static com.tayyar.delivery.DeliveryDtos.*;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

class DeliveryRulesTests {
    RuleInput input(String fee, String minimum, int min, int max) {
        return new RuleInput(new BigDecimal(fee), new BigDecimal(minimum), min, max, true);
    }

    @Test
    void exactMoneyBoundaries() {
        DeliveryRules.rule(input("0", "9999999999.99", 1, 1));
        for (String invalid : new String[] {"-0.01", "10000000000", "0.001", "1E+10"}) {
            assertThatThrownBy(() -> DeliveryRules.rule(input(invalid, "0", 30, 45)))
                    .isInstanceOf(DeliveryException.class);
            assertThatThrownBy(() -> DeliveryRules.rule(input("0", invalid, 30, 45)))
                    .isInstanceOf(DeliveryException.class);
        }
    }

    @Test
    void etaBoundaries() {
        assertThatThrownBy(() -> DeliveryRules.rule(input("25", "100", 0, 45)))
                .isInstanceOf(DeliveryException.class);
        assertThatThrownBy(() -> DeliveryRules.rule(input("25", "100", 45, 30)))
                .isInstanceOf(DeliveryException.class);
    }

    @Test
    void geographyAndPageBounds() {
        for (String value : new String[] {"", " ", "---", " - - "})
            assertThatThrownBy(() -> DeliveryRules.name(value))
                    .isInstanceOf(DeliveryException.class);
        assertThat(DeliveryRules.name(" Cairo ")).isEqualTo("Cairo");
        assertThatThrownBy(() -> new Window(-1, 20)).isInstanceOf(DeliveryException.class);
        assertThatThrownBy(() -> new Window(0, 101)).isInstanceOf(DeliveryException.class);
    }

    @Test
    void staleVersionCannotMutateRecord() {
        var city = new City(new GeographyInput("Cairo", true), Instant.EPOCH);
        assertThatThrownBy(() -> city.touch(1, Instant.now()))
                .isInstanceOf(DeliveryException.class);
        assertThat(city.version).isZero();
        assertThat(city.updatedAt).isEqualTo(Instant.EPOCH);
    }
}
