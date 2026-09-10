package com.tayyar.menu;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "restaurant_menus")
public class Menu {
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

    @Column(name = "currency", length = 3)
    private String currency;

    @Version
    @Column(name = "version")
    private long version;

    protected Menu() {}

    public UUID id() {
        return id;
    }

    public UUID restaurantId() {
        return restaurantId;
    }

    public long version() {
        return version;
    }

    public void touch(long expected, Instant now) {
        if (version != expected) throw MenuException.conflict();
        updatedAt = now.isAfter(updatedAt) ? now : updatedAt.plusNanos(1000);
    }

    public void edit(MenuDtos.EditMenu input) {
        name = input.name().strip();
        active = input.active();
    }

    public MenuDtos.MenuView view() {
        return new MenuDtos.MenuView(
                id, restaurantId, name, active, currency, version, createdAt, updatedAt);
    }
}
