package com.tayyar.discovery;

import java.math.BigDecimal;
import java.util.*;

public final class DiscoveryDtos {
    private DiscoveryDtos() {}

    public record Page<T>(List<T> items, int page, int size, long total) {}

    public record Window(int page, int size) {
        public Window {
            if (page < 0 || page > 10000 || size < 1 || size > 100)
                throw DiscoveryException.invalid("Page must be 0–10000 and size 1–100");
        }

        public long offset() {
            return (long) page * size;
        }
    }

    public enum Sort {
        NAME,
        DELIVERY_FEE,
        MINIMUM_ORDER,
        ETA
    }

    public record Filter(
            String query,
            UUID zoneId,
            UUID addressId,
            Boolean openNow,
            BigDecimal maxDeliveryFee,
            BigDecimal maxMinimumOrder,
            UUID categoryId,
            boolean availableOnly,
            Sort sort) {
        public Filter {
            if (query != null && (query.length() > 200 || query.indexOf('\0') >= 0))
                throw DiscoveryException.invalid(
                        "Query must contain at most 200 characters and no NUL");
            query = query == null ? "" : query.strip();
            if (zoneId != null && addressId != null)
                throw DiscoveryException.invalid("Supply zoneId or addressId, not both");
            for (BigDecimal value : new BigDecimal[] {maxDeliveryFee, maxMinimumOrder})
                if (value != null
                        && (value.signum() < 0
                                || value.scale() > 2
                                || value.compareTo(new BigDecimal("10000000000")) >= 0))
                    throw DiscoveryException.invalid(
                            "Money filters must be nonnegative EGP amounts below 10000000000 with"
                                + " at most two decimals");
            if (sort == null) sort = Sort.NAME;
            if (zoneId == null
                    && addressId == null
                    && (maxDeliveryFee != null || maxMinimumOrder != null || sort != Sort.NAME))
                throw DiscoveryException.invalid("Delivery filters and sorts require a location");
        }

        public String pattern() {
            return "%" + query.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        }
    }

    public record Restaurant(
            UUID id,
            String name,
            String description,
            long branchCount,
            boolean openNow,
            Boolean serviceable,
            BigDecimal minimumDeliveryFee,
            BigDecimal minimumOrder,
            Integer minimumEtaMinutes,
            String currency) {}

    public record Branch(
            UUID id,
            String name,
            String addressLine1,
            String city,
            String timezone,
            boolean openNow,
            String state,
            Boolean serviceable,
            BigDecimal deliveryFee,
            BigDecimal minimumOrder,
            Integer etaMinMinutes,
            Integer etaMaxMinutes,
            String currency) {}

    public record Item(
            UUID id,
            String name,
            String description,
            BigDecimal effectivePrice,
            boolean effectiveAvailability) {}

    public record Category(UUID id, String name, String description, Page<Item> items) {}

    public record Menu(
            UUID id, UUID branchId, String name, String currency, Page<Category> categories) {}

    public record Zone(UUID id, String name, UUID cityId, String cityName) {}
}
