package com.tayyar.support.fixture;

import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "foundation_probe")
public class MigrationProbe {
    @Id private UUID id;
    @Column(nullable = false, length = 80) private String label;

    protected MigrationProbe() {}
}
