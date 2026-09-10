package com.tayyar.user;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "users")
public class User {
    @Id private UUID id;

    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(length = 16)
    private String phone;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AccountStatus status;

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @Column(name = "auth_version", insertable = false, updatable = false, nullable = false)
    private long authVersion;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @ElementCollection
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "role_name", length = 32)
    private Set<Role> roles = new HashSet<>();

    protected User() {}

    public static User customer(String name, String email, String phone, String hash, Instant now) {
        User user = new User();
        user.id = UUID.randomUUID();
        user.fullName = name.strip();
        user.email = email;
        user.phone = phone;
        user.passwordHash = hash;
        user.status = AccountStatus.ACTIVE;
        user.createdAt = now;
        user.updatedAt = now;
        user.roles.add(Role.CUSTOMER);
        return user;
    }

    public UUID getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public Instant getEmailVerifiedAt() {
        return emailVerifiedAt;
    }

    public long getAuthVersion() {
        return authVersion;
    }

    public Set<Role> getRoles() {
        return Set.copyOf(roles);
    }
}
