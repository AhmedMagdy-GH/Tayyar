package com.tayyar.menu;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.*;

class MenuRulesTests {
    @Test
    void acceptsCompleteSiblingPermutation() {
        var a = UUID.randomUUID();
        var b = UUID.randomUUID();
        assertThatCode(() -> MenuRules.reorder(List.of(a, b), List.of(b, a)))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsDuplicatesOmissionsAndForeignIds() {
        var a = UUID.randomUUID();
        var b = UUID.randomUUID();
        for (var ids : List.of(List.of(a, a), List.of(a), List.of(a, UUID.randomUUID())))
            assertThatThrownBy(() -> MenuRules.reorder(List.of(a, b), ids))
                    .isInstanceOf(MenuException.class);
    }

    @Test
    void emptyOrderingIsValidOnlyForEmptyParent() {
        assertThatCode(() -> MenuRules.reorder(List.of(), List.of())).doesNotThrowAnyException();
        assertThatThrownBy(() -> MenuRules.reorder(List.of(UUID.randomUUID()), List.of()))
                .isInstanceOf(MenuException.class);
    }

    @Test
    void paginationIsBounded() {
        assertThatThrownBy(() -> new MenuDtos.Window(0, 101)).isInstanceOf(MenuException.class);
        assertThatThrownBy(() -> new MenuDtos.Window(-1, 20)).isInstanceOf(MenuException.class);
    }
}
