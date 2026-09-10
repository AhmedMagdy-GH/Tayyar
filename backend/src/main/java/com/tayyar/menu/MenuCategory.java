package com.tayyar.menu;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "menu_categories")
public class MenuCategory {
    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "restaurant_id")
    private UUID restaurantId;

    @Column(name = "name", length = 120)
    private String name;

    @Column(name = "active")
    private boolean active;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "menu_id")
    private UUID menuId;

    @Column(name = "description", length = 2000)
    private String description;

    @Column(name = "position")
    private int position;

    protected MenuCategory() {}
}
