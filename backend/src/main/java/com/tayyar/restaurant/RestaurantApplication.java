package com.tayyar.restaurant;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "restaurant_applications")
public class RestaurantApplication {
    @Id private UUID id;

    @Column(name = "applicant_id", nullable = false)
    private UUID applicantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ApplicationStatus status;

    @Column(name = "current_revision", nullable = false)
    private int currentRevision;

    @Version private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected RestaurantApplication() {}

    public RestaurantApplication(UUID applicant, Instant now) {
        id = UUID.randomUUID();
        applicantId = applicant;
        status = ApplicationStatus.PENDING;
        currentRevision = 1;
        createdAt = now;
        updatedAt = now;
    }

    public void review(ReviewOutcome outcome, long expectedVersion, Instant now) {
        if (version != expectedVersion || status != ApplicationStatus.PENDING)
            throw RestaurantException.conflict();
        status = ApplicationStatus.valueOf(outcome.name());
        updatedAt = now;
    }

    public void resubmit(long expectedVersion, Instant now) {
        if (version != expectedVersion || status != ApplicationStatus.REJECTED)
            throw RestaurantException.conflict();
        currentRevision++;
        status = ApplicationStatus.PENDING;
        updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getApplicantId() {
        return applicantId;
    }

    public ApplicationStatus getStatus() {
        return status;
    }

    public int getCurrentRevision() {
        return currentRevision;
    }

    public long getVersion() {
        return version;
    }
}
