package com.tayyar.restaurant;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

class RestaurantRulesTests {
    @Test
    void onlyRejectedApplicationsCanBeResubmitted() {
        var application = new RestaurantApplication(UUID.randomUUID(), Instant.now());
        assertThatThrownBy(() -> application.resubmit(0, Instant.now()))
                .isInstanceOf(RestaurantException.class);
        application.review(ReviewOutcome.REJECTED, 0, Instant.now());
        application.resubmit(0, Instant.now());
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.PENDING);
        assertThat(application.getCurrentRevision()).isEqualTo(2);
    }

    @Test
    void terminalReviewAndStaleVersionAreRejected() {
        var application = new RestaurantApplication(UUID.randomUUID(), Instant.now());
        assertThatThrownBy(() -> application.review(ReviewOutcome.APPROVED, 1, Instant.now()))
                .isInstanceOf(RestaurantException.class);
        application.review(ReviewOutcome.APPROVED, 0, Instant.now());
        assertThatThrownBy(() -> application.review(ReviewOutcome.REJECTED, 0, Instant.now()))
                .isInstanceOf(RestaurantException.class);
    }

    @Test
    void profileAndStatusChangesRequireCurrentVersion() {
        var restaurant = new Restaurant(UUID.randomUUID(), "Name", "Description", Instant.now());
        assertThatThrownBy(() -> restaurant.edit("Changed", "", 2, Instant.now()))
                .isInstanceOf(RestaurantException.class);
        assertThatThrownBy(() -> restaurant.changeStatus(RestaurantStatus.ACTIVE, 0, Instant.now()))
                .isInstanceOf(RestaurantException.class);
        restaurant.changeStatus(RestaurantStatus.SUSPENDED, 0, Instant.now());
        assertThat(restaurant.getStatus()).isEqualTo(RestaurantStatus.SUSPENDED);
    }

    @Test
    void paginationHasExplicitBounds() {
        assertThat(new RestaurantDtos.Window(2, 20).offset()).isEqualTo(40);
        for (int size : new int[] {0, 101, -1})
            assertThatThrownBy(() -> new RestaurantDtos.Window(0, size))
                    .isInstanceOf(RestaurantException.class);
        assertThatThrownBy(() -> new RestaurantDtos.Window(-1, 20))
                .isInstanceOf(RestaurantException.class);
    }
}
