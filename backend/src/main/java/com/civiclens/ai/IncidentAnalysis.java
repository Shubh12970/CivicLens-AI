package com.civiclens.ai;

import com.civiclens.incident.IncidentCategory;
import com.civiclens.incident.Severity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** One analysis run for an incident. Rows are never overwritten by later runs, so changes stay traceable. */
@Entity
@Table(name = "incident_analyses")
public class IncidentAnalysis {

    @Id
    private UUID id;
    @Column(name = "incident_id", nullable = false)
    private UUID incidentId;
    @Column(name = "run_number", nullable = false)
    private int runNumber;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AnalysisStatus status;
    @Enumerated(EnumType.STRING)
    private AnalysisSource source;
    private String provider;
    private String model;
    @Column(name = "thinking_level", length = 10)
    private String thinkingLevel;
    @Column(name = "prompt_version", nullable = false)
    private String promptVersion;
    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;
    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts;
    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;
    @Column(name = "lease_until")
    private Instant leaseUntil;
    @Enumerated(EnumType.STRING)
    private IncidentCategory category;
    @Enumerated(EnumType.STRING)
    private Severity severity;
    private Double confidence;
    @Column(name = "safety_critical")
    private Boolean safetyCritical;
    private String explanation;
    @Column(name = "safety_concerns")
    private String safetyConcerns;
    @Column(name = "missing_information")
    private String missingInformation;
    @Column(name = "recommended_next_action")
    private String recommendedNextAction;
    @Column(name = "impact_summary", length = 700)
    private String impactSummary;
    @Column(name = "risk_rationale", length = 700)
    private String riskRationale;
    @Column(name = "recommended_actions")
    private String recommendedActions;
    @Column(name = "clarifying_questions")
    private String clarifyingQuestions;
    @Column(name = "citizen_guidance", length = 600)
    private String citizenGuidance;
    @Column(name = "evidence_suggestions")
    private String evidenceSuggestions;
    @Column(name = "recommended_department_id")
    private UUID recommendedDepartmentId;
    @Column(name = "validation_warnings")
    private String validationWarnings;
    @Column(name = "last_error_code")
    private String lastErrorCode;
    @Column(name = "last_error_message")
    private String lastErrorMessage;
    @Column(name = "requested_by_id")
    private UUID requestedById;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Column(name = "completed_at")
    private Instant completedAt;

    protected IncidentAnalysis() {
    }

    public IncidentAnalysis(UUID incidentId, int runNumber, int maxAttempts, UUID requestedById, Instant now) {
        this.id = UUID.randomUUID();
        this.incidentId = incidentId;
        this.runNumber = runNumber;
        this.status = AnalysisStatus.PENDING;
        this.promptVersion = AnalysisPrompt.VERSION;
        this.maxAttempts = maxAttempts;
        this.requestedById = requestedById;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void complete(AnalysisSource source, String provider, String model, String thinkingLevel,
                         IncidentCategory category, Severity severity, Double confidence, boolean safetyCritical, String explanation,
                         String safetyConcernsJson, String missingInfoJson, String nextAction, UUID department,
                         String warningsJson, String impactSummary, String riskRationale,
                         String recommendedActionsJson, String clarifyingQuestionsJson,
                         String citizenGuidance, String evidenceSuggestionsJson, Instant now) {
        this.status = AnalysisStatus.COMPLETED;
        this.source = source;
        this.provider = provider;
        this.model = model;
        this.thinkingLevel = thinkingLevel;
        this.category = category;
        this.severity = severity;
        this.confidence = confidence;
        this.safetyCritical = safetyCritical;
        this.explanation = explanation;
        this.safetyConcerns = safetyConcernsJson;
        this.missingInformation = missingInfoJson;
        this.recommendedNextAction = nextAction;
        this.recommendedDepartmentId = department;
        this.validationWarnings = warningsJson;
        this.impactSummary = impactSummary;
        this.riskRationale = riskRationale;
        this.recommendedActions = recommendedActionsJson;
        this.clarifyingQuestions = clarifyingQuestionsJson;
        this.citizenGuidance = citizenGuidance;
        this.evidenceSuggestions = evidenceSuggestionsJson;
        this.leaseUntil = null;
        this.nextAttemptAt = null;
        this.completedAt = now;
        this.updatedAt = now;
    }

    public void scheduleRetry(String errorCode, String errorMessage, Instant nextAttemptAt, Instant now) {
        this.status = AnalysisStatus.PENDING;
        this.lastErrorCode = errorCode;
        this.lastErrorMessage = truncate(errorMessage);
        this.nextAttemptAt = nextAttemptAt;
        this.leaseUntil = null;
        this.updatedAt = now;
    }

    public void fail(String provider, String model, String errorCode, String errorMessage, Instant now) {
        this.status = AnalysisStatus.FAILED;
        this.provider = provider;
        this.model = model;
        this.lastErrorCode = errorCode;
        this.lastErrorMessage = truncate(errorMessage);
        this.nextAttemptAt = null;
        this.leaseUntil = null;
        this.completedAt = now;
        this.updatedAt = now;
    }

    public void recordError(String errorCode, String errorMessage) {
        this.lastErrorCode = errorCode;
        this.lastErrorMessage = truncate(errorMessage);
    }

    private static String truncate(String s) {
        return s == null || s.length() <= 500 ? s : s.substring(0, 500);
    }

    public UUID getId() { return id; }
    public UUID getIncidentId() { return incidentId; }
    public int getRunNumber() { return runNumber; }
    public AnalysisStatus getStatus() { return status; }
    public AnalysisSource getSource() { return source; }
    public String getProvider() { return provider; }
    public String getModel() { return model; }
    public String getThinkingLevel() { return thinkingLevel; }
    public String getPromptVersion() { return promptVersion; }
    public int getAttemptCount() { return attemptCount; }
    public int getMaxAttempts() { return maxAttempts; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public Instant getLeaseUntil() { return leaseUntil; }
    public IncidentCategory getCategory() { return category; }
    public Severity getSeverity() { return severity; }
    public Double getConfidence() { return confidence; }
    public Boolean getSafetyCritical() { return safetyCritical; }
    public String getExplanation() { return explanation; }
    public String getSafetyConcerns() { return safetyConcerns; }
    public String getMissingInformation() { return missingInformation; }
    public String getRecommendedNextAction() { return recommendedNextAction; }
    public String getImpactSummary() { return impactSummary; }
    public String getRiskRationale() { return riskRationale; }
    public String getRecommendedActions() { return recommendedActions; }
    public String getClarifyingQuestions() { return clarifyingQuestions; }
    public String getCitizenGuidance() { return citizenGuidance; }
    public String getEvidenceSuggestions() { return evidenceSuggestions; }
    public UUID getRecommendedDepartmentId() { return recommendedDepartmentId; }
    public String getValidationWarnings() { return validationWarnings; }
    public String getLastErrorCode() { return lastErrorCode; }
    public String getLastErrorMessage() { return lastErrorMessage; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getCompletedAt() { return completedAt; }
}
