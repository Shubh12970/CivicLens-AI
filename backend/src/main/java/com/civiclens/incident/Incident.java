package com.civiclens.incident;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "incidents")
public class Incident {

    @Id
    private UUID id;
    @Column(name = "city_id", nullable = false)
    private UUID cityId;
    @Column(name = "reporter_id", nullable = false)
    private UUID reporterId;
    @Column(nullable = false)
    private String title;
    @Column(nullable = false)
    private String description;
    @Column(name = "normalized_text", nullable = false)
    private String normalizedText;
    private String locality;
    @Column(name = "address_text")
    private String addressText;
    private Double latitude;
    private Double longitude;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IncidentCategory category;
    @Enumerated(EnumType.STRING)
    @Column(name = "category_source", nullable = false)
    private FieldSource categorySource;

    @Enumerated(EnumType.STRING)
    private Severity severity;
    @Enumerated(EnumType.STRING)
    @Column(name = "severity_source")
    private FieldSource severitySource;

    @Column(name = "safety_critical", nullable = false)
    private boolean safetyCritical;
    @Column(name = "hazard_reported", nullable = false)
    private boolean hazardReported;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IncidentStatus status;

    @Column(name = "department_id")
    private UUID departmentId;
    @Column(name = "recommended_department_id")
    private UUID recommendedDepartmentId;
    @Enumerated(EnumType.STRING)
    @Column(name = "recommended_department_source")
    private FieldSource recommendedDepartmentSource;

    @Column(name = "duplicate_of_id")
    private UUID duplicateOfId;
    @Column(name = "resolution_note")
    private String resolutionNote;
    @Column(name = "demo_data", nullable = false)
    private boolean demoData;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Column(name = "status_changed_at", nullable = false)
    private Instant statusChangedAt;
    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Version
    private Long version;

    protected Incident() {
    }

    public Incident(UUID cityId, UUID reporterId, String title, String description, String normalizedText,
                    String locality, String addressText, Double latitude, Double longitude,
                    IncidentCategory category, boolean hazardReported, boolean demoData, Instant now) {
        this.id = UUID.randomUUID();
        this.cityId = cityId;
        this.reporterId = reporterId;
        this.title = title;
        this.description = description;
        this.normalizedText = normalizedText;
        this.locality = locality;
        this.addressText = addressText;
        this.latitude = latitude;
        this.longitude = longitude;
        this.category = category == null ? IncidentCategory.OTHER : category;
        this.categorySource = FieldSource.CITIZEN;
        this.hazardReported = hazardReported;
        this.demoData = demoData;
        this.status = IncidentStatus.SUBMITTED;
        this.createdAt = now;
        this.updatedAt = now;
        this.statusChangedAt = now;
    }

    // ---- State changes. Callers (services) are responsible for authorization and audit events. ----

    public void changeStatus(IncidentStatus newStatus, Instant now) {
        this.status = newStatus;
        this.statusChangedAt = now;
        this.updatedAt = now;
        if (newStatus == IncidentStatus.RESOLVED) {
            this.resolvedAt = now;
        } else if (newStatus == IncidentStatus.REOPENED) {
            this.resolvedAt = null;
        }
    }

    public void assignTo(UUID departmentId, Instant now) {
        this.departmentId = departmentId;
        this.updatedAt = now;
    }

    public void setResolutionNote(String note, Instant now) {
        this.resolutionNote = note;
        this.updatedAt = now;
    }

    public void markDuplicateOf(UUID canonicalId, Instant now) {
        this.duplicateOfId = canonicalId;
        this.updatedAt = now;
    }

    /**
     * Applies an analysis suggestion without overwriting anything an official has verified or a specific
     * category the citizen chose.
     */
    public void applySuggestion(IncidentCategory suggestedCategory, Severity suggestedSeverity, boolean suggestedSafety,
                                UUID suggestedDepartment, FieldSource source, Instant now) {
        if (suggestedCategory != null && categorySource != FieldSource.OFFICIAL
                && (category == IncidentCategory.OTHER || categorySource != FieldSource.CITIZEN)) {
            this.category = suggestedCategory;
            this.categorySource = source;
        }
        if (suggestedSeverity != null && severitySource != FieldSource.OFFICIAL) {
            this.severity = suggestedSeverity;
            this.severitySource = source;
        }
        if (severitySource != FieldSource.OFFICIAL) {
            // A safety flag raised by any assessment is never silently cleared by a later automated one.
            this.safetyCritical = this.safetyCritical || suggestedSafety;
        }
        if (suggestedDepartment != null) {
            this.recommendedDepartmentId = suggestedDepartment;
            this.recommendedDepartmentSource = FieldSource.AI_SUGGESTED;
        }
        this.updatedAt = now;
    }

    public void recommendFromMapping(UUID departmentId, Instant now) {
        if (recommendedDepartmentSource != FieldSource.AI_SUGGESTED) {
            this.recommendedDepartmentId = departmentId;
            this.recommendedDepartmentSource = departmentId == null ? null : FieldSource.CATEGORY_MAPPING;
            this.updatedAt = now;
        }
    }

    public void officialTriage(IncidentCategory category, Severity severity, Boolean safetyCritical, Instant now) {
        if (category != null) {
            this.category = category;
            this.categorySource = FieldSource.OFFICIAL;
        }
        if (severity != null) {
            this.severity = severity;
            this.severitySource = FieldSource.OFFICIAL;
        }
        if (safetyCritical != null) {
            this.safetyCritical = safetyCritical;
        }
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getCityId() { return cityId; }
    public UUID getReporterId() { return reporterId; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getNormalizedText() { return normalizedText; }
    public String getLocality() { return locality; }
    public String getAddressText() { return addressText; }
    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }
    public IncidentCategory getCategory() { return category; }
    public FieldSource getCategorySource() { return categorySource; }
    public Severity getSeverity() { return severity; }
    public FieldSource getSeveritySource() { return severitySource; }
    public boolean isSafetyCritical() { return safetyCritical; }
    public boolean isHazardReported() { return hazardReported; }
    public IncidentStatus getStatus() { return status; }
    public UUID getDepartmentId() { return departmentId; }
    public UUID getRecommendedDepartmentId() { return recommendedDepartmentId; }
    public FieldSource getRecommendedDepartmentSource() { return recommendedDepartmentSource; }
    public UUID getDuplicateOfId() { return duplicateOfId; }
    public String getResolutionNote() { return resolutionNote; }
    public boolean isDemoData() { return demoData; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getStatusChangedAt() { return statusChangedAt; }
    public Instant getResolvedAt() { return resolvedAt; }
    public long getVersion() { return version == null ? 0 : version; }
}
