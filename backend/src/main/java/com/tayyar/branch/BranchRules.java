package com.tayyar.branch;

import static com.tayyar.branch.BranchDtos.*;

import java.time.*;
import java.util.*;

public final class BranchRules {
    private BranchRules() {}

    public static void profile(Profile input) {
        if ((input.latitude() == null) != (input.longitude() == null))
            throw BranchException.invalid("Coordinates must be supplied together");
        if (!ZoneId.getAvailableZoneIds().contains(input.timezone()))
            throw BranchException.invalid("Timezone must be an IANA zone identifier");
    }

    public static void hours(Hours input) {
        Set<Integer> days = new HashSet<>();
        Set<LocalDate> dates = new HashSet<>();
        for (var row : input.weekly()) {
            if (!days.add(row.weekday())) throw BranchException.invalid("Duplicate weekly day");
            interval(row.opensAt(), row.closesAt(), false);
        }
        for (var row : input.special()) {
            if (!dates.add(row.date())) throw BranchException.invalid("Duplicate special date");
            interval(row.opensAt(), row.closesAt(), true);
        }
    }

    private static void interval(LocalTime opens, LocalTime closes, boolean allowClosed) {
        if (allowClosed && opens == null && closes == null) return;
        if (opens == null
                || closes == null
                || !opens.isBefore(closes)
                || opens.getSecond() != 0
                || closes.getSecond() != 0
                || opens.getNano() != 0
                || closes.getNano() != 0)
            throw BranchException.invalid(
                    "Hours require minute-precision opening before closing on the same day");
    }

    public static boolean open(
            Instant now, String timezone, List<Weekly> weekly, List<Special> special) {
        var local = now.atZone(ZoneId.of(timezone));
        for (var row : special)
            if (row.date().equals(local.toLocalDate()))
                return contains(local.toLocalTime(), row.opensAt(), row.closesAt());
        for (var row : weekly)
            if (row.weekday() == local.getDayOfWeek().getValue())
                return contains(local.toLocalTime(), row.opensAt(), row.closesAt());
        return false;
    }

    private static boolean contains(LocalTime now, LocalTime opens, LocalTime closes) {
        return opens != null && closes != null && !now.isBefore(opens) && now.isBefore(closes);
    }
}
