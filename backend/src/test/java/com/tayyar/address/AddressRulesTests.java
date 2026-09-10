package com.tayyar.address;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

class AddressRulesTests {
    private AddressDtos.Profile profile(BigDecimal latitude, BigDecimal longitude) {
        return new AddressDtos.Profile(
                " Home ",
                " Street ",
                " 12 ",
                null,
                null,
                null,
                "Ring bell",
                " Cairo ",
                null,
                null,
                "EG",
                latitude,
                longitude);
    }

    @Test
    void coordinatesArePaired() {
        assertThatThrownBy(
                        () ->
                                new CustomerAddress(
                                        UUID.randomUUID(),
                                        profile(BigDecimal.ONE, null),
                                        true,
                                        Instant.now()))
                .isInstanceOf(AddressException.class);
    }

    @Test
    void trimsRequiredFieldsAndPreservesInstructions() {
        var row = new CustomerAddress(UUID.randomUUID(), profile(null, null), true, Instant.now());
        assertThat(row.view().profile().label()).isEqualTo("Home");
        assertThat(row.view().profile().instructions()).isEqualTo("Ring bell");
        assertThat(row.view().isDefault()).isTrue();
    }

    @Test
    void rejectsStaleVersion() {
        var row = new CustomerAddress(UUID.randomUUID(), profile(null, null), true, Instant.now());
        assertThatThrownBy(() -> row.checkVersion(1)).isInstanceOf(AddressException.class);
    }

    @Test
    void paginationBounds() {
        assertThatThrownBy(() -> new AddressDtos.Window(0, 101))
                .isInstanceOf(AddressException.class);
        assertThatThrownBy(() -> new AddressDtos.Window(-1, 20))
                .isInstanceOf(AddressException.class);
    }
}
