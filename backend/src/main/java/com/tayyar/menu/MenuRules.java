package com.tayyar.menu;

import java.util.*;

public final class MenuRules {
    private MenuRules() {}

    public static void reorder(List<UUID> current, List<UUID> requested) {
        if (requested.size() != current.size()
                || new HashSet<>(requested).size() != requested.size()
                || !new HashSet<>(requested).equals(new HashSet<>(current)))
            throw MenuException.invalid("Reordering requires every sibling ID exactly once");
    }
}
