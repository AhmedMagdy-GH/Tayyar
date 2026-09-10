package com.tayyar.branch;

import static com.tayyar.branch.BranchDtos.*;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.*;

class BranchRulesTests {
    private final List<Weekly> monday =
            List.of(new Weekly(1, LocalTime.of(9, 0), LocalTime.of(23, 0)));

    @Test
    void usesTimezoneAndExclusiveClosingBoundary() {
        assertThat(
                        BranchRules.open(
                                Instant.parse("2026-01-05T07:00:00Z"),
                                "Africa/Cairo",
                                monday,
                                List.of()))
                .isTrue();
        assertThat(
                        BranchRules.open(
                                Instant.parse("2026-01-05T21:00:00Z"),
                                "Africa/Cairo",
                                monday,
                                List.of()))
                .isFalse();
    }

    @Test
    void absentDayIsClosed() {
        assertThat(
                        BranchRules.open(
                                Instant.parse("2026-01-06T10:00:00Z"),
                                "Africa/Cairo",
                                monday,
                                List.of()))
                .isFalse();
    }

    @Test
    void dateOverrideReplacesWeeklyAndAllowsFullClosure() {
        Instant now = Instant.parse("2026-01-05T10:00:00Z");
        assertThat(
                        BranchRules.open(
                                now,
                                "Africa/Cairo",
                                monday,
                                List.of(new Special(LocalDate.of(2026, 1, 5), null, null))))
                .isFalse();
        assertThat(
                        BranchRules.open(
                                now,
                                "Africa/Cairo",
                                List.of(),
                                List.of(
                                        new Special(
                                                LocalDate.of(2026, 1, 5),
                                                LocalTime.of(10, 0),
                                                LocalTime.of(14, 0)))))
                .isTrue();
    }

    @Test
    void handlesDaylightSavingOverlapAsLocalWallTime() {
        var sunday = List.of(new Weekly(7, LocalTime.of(1, 0), LocalTime.of(2, 0)));
        assertThat(
                        BranchRules.open(
                                Instant.parse("2026-11-01T05:30:00Z"),
                                "America/New_York",
                                sunday,
                                List.of()))
                .isTrue();
        assertThat(
                        BranchRules.open(
                                Instant.parse("2026-11-01T06:30:00Z"),
                                "America/New_York",
                                sunday,
                                List.of()))
                .isTrue();
    }

    @Test
    void rejectsOvernightSubminuteAndDuplicateHours() {
        for (var interval :
                List.of(
                        new Weekly(1, LocalTime.of(23, 0), LocalTime.of(9, 0)),
                        new Weekly(1, LocalTime.of(9, 0, 1), LocalTime.of(23, 0))))
            assertThatThrownBy(() -> BranchRules.hours(new Hours(List.of(interval), List.of(), 0L)))
                    .isInstanceOf(BranchException.class);
        assertThatThrownBy(
                        () ->
                                BranchRules.hours(
                                        new Hours(
                                                List.of(monday.getFirst(), monday.getFirst()),
                                                List.of(),
                                                0L)))
                .isInstanceOf(BranchException.class);
    }

    @Test
    void rejectsHalfClosedAndDuplicateExceptions() {
        var date = LocalDate.of(2026, 1, 5);
        assertThatThrownBy(
                        () ->
                                BranchRules.hours(
                                        new Hours(
                                                List.of(),
                                                List.of(new Special(date, null, LocalTime.NOON)),
                                                0L)))
                .isInstanceOf(BranchException.class);
        var closed = new Special(date, null, null);
        assertThatThrownBy(
                        () -> BranchRules.hours(new Hours(List.of(), List.of(closed, closed), 0L)))
                .isInstanceOf(BranchException.class);
    }

    @Test
    void paginationIsBounded() {
        assertThatThrownBy(() -> new Window(0, 101)).isInstanceOf(BranchException.class);
        assertThatThrownBy(() -> new Window(-1, 20)).isInstanceOf(BranchException.class);
    }
}
