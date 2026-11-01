package com.civiclens.incident;

import com.civiclens.user.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Append-only audit entry on an incident's public timeline. */
@Entity
@Table(name = "incident_events")
public class IncidentEvent {

    public enum Type { CREATED, STATUS_CHANGED, ASSIGNED, TRIAGE_UPDATED, ANALYSIS_COMPLETED, ANALYSIS_FAILED, MARKED_DUPLICATE }

    @Id
    private UUID id;
    @Column(name = "incident_id", nullable = false)
    private UUID incidentId;
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private Type eventType;
    @Enumerated(EnumType.STRING)
    @Column(name = "from_status")
    private IncidentStatus fromStatus;
    @Enumerated(EnumType.STRING)
    @Column(name = "to_status")
    private IncidentStatus toStatus;
    @Column(name = "department_id")
    private UUID departmentId;
    @Column(name = "actor_id")
    private UUID actorId;
    @Enumerated(EnumType.STRING)
    @Column(name = "actor_role")
    private Role actorRole;
    private String note;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected IncidentEvent() {
    }

    public IncidentEvent(UUID incidentId, Type eventType, IncidentStatus fromStatus, IncidentStatus toStatus,
                         UUID departmentId, UUID actorId, Role actorRole, String note, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.incidentId = incidentId;
        this.eventType = eventType;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.departmentId = departmentId;
        this.actorId = actorId;
        this.actorRole = actorRole;
        this.note = note;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getIncidentId() { return incidentId; }
    public Type getEventType() { return eventType; }
    public IncidentStatus getFromStatus() { return fromStatus; }
    public IncidentStatus getToStatus() { return toStatus; }
    public UUID getDepartmentId() { return departmentId; }
    public UUID getActorId() { return actorId; }
    public Role getActorRole() { return actorRole; }
    public String getNote() { return note; }
    public Instant getCreatedAt() { return createdAt; }
}
