package com.civiclens.community;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "comments")
public class Comment {

    /** VISIBLE, DELETED (by author/admin), HIDDEN (reserved for a future moderation workflow). */
    public enum Moderation { VISIBLE, DELETED, HIDDEN }

    @Id
    private UUID id;
    @Column(name = "incident_id", nullable = false)
    private UUID incidentId;
    @Column(name = "author_id", nullable = false)
    private UUID authorId;
    @Column(nullable = false)
    private String body;
    @Enumerated(EnumType.STRING)
    @Column(name = "moderation_status", nullable = false)
    private Moderation moderationStatus;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Comment() {
    }

    public Comment(UUID incidentId, UUID authorId, String body, Instant now) {
        this.id = UUID.randomUUID();
        this.incidentId = incidentId;
        this.authorId = authorId;
        this.body = body;
        this.moderationStatus = Moderation.VISIBLE;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** Soft delete: the text is erased, the row stays so thread order and audit remain intact. */
    public void delete(Instant now) {
        this.body = "[deleted]";
        this.moderationStatus = Moderation.DELETED;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getIncidentId() { return incidentId; }
    public UUID getAuthorId() { return authorId; }
    public String getBody() { return body; }
    public Moderation getModerationStatus() { return moderationStatus; }
    public Instant getCreatedAt() { return createdAt; }
}
