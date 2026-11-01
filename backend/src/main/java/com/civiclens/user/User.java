package com.civiclens.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {

    public enum Status { ACTIVE, DELETED }

    @Id
    private UUID id;

    @Column(nullable = false)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(name = "department_id")
    private UUID departmentId;

    @Column(name = "home_city_id")
    private UUID homeCityId;

    @Column(name = "token_version", nullable = false)
    private int tokenVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.ACTIVE;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected User() {
    }

    public User(String email, String passwordHash, String displayName, Role role, UUID departmentId, UUID homeCityId,
                Instant now) {
        this.id = UUID.randomUUID();
        this.email = email;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.role = role;
        this.departmentId = departmentId;
        this.homeCityId = homeCityId;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** Invalidates every token issued so far (logout from all sessions). */
    public void revokeTokens(Instant now) {
        this.tokenVersion++;
        this.updatedAt = now;
    }

    /** Removes personal data while keeping the row so audit history and foreign keys stay valid. */
    public void anonymize(String randomPasswordHash, Instant now) {
        this.email = "deleted-" + id + "@deleted.invalid";
        this.displayName = "Deleted user";
        this.passwordHash = randomPasswordHash;
        this.homeCityId = null;
        this.status = Status.DELETED;
        this.tokenVersion++;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getDisplayName() { return displayName; }
    public Role getRole() { return role; }
    public UUID getDepartmentId() { return departmentId; }
    public UUID getHomeCityId() { return homeCityId; }
    public int getTokenVersion() { return tokenVersion; }
    public Status getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public boolean isActive() { return status == Status.ACTIVE; }
}
