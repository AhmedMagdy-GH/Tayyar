package com.tayyar.favorite;

import java.time.Instant;
import java.util.*;

final class FavoriteDtos {
    private FavoriteDtos() {}
    record Favorite(UUID restaurantId, String name, String description, Instant favoritedAt) {}
    record Page(List<Favorite> items, int page, int size, long total) {}
}
