package com.civiclens.ai;

import com.civiclens.incident.IncidentCategory;
import com.civiclens.incident.Severity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** API representation of a validated analysis run. */
public record AnalysisView(
        UUID analysisId,
        int runNumber,
        AnalysisStatus status,
        AnalysisSource source,
        boolean aiGenerated,
        String provider,
        String model,
        String thinkingLevel,
        String promptVersion,
        int attemptCount,
        int maxAttempts,
        Instant nextAttemptAt,
        IncidentCategory category,
        Severity severity,
        Double confidence,
        Boolean safetyCritical,
        String explanation,
        List<String> safetyConcerns,
        List<String> missingInformation,
        String recommendedNextAction,
        UUID recommendedDepartmentId,
        String recommendedDepartmentName,
        List<String> validationWarnings,
        String lastErrorCode,
        String lastErrorMessage,
        String impactSummary,
        String riskRationale,
        List<String> recommendedActions,
        List<String> clarifyingQuestions,
        String citizenGuidance,
        List<String> evidenceSuggestions,
        Instant createdAt,
        Instant completedAt,
        String disclaimer) {

    public static final String DISCLAIMER = "AI-generated suggestions only. Impact and risk assessments use only "
            + "the citizen report and supplied CivicLens context. Category and severity are not verified until an "
            + "authorized official confirms them. No department action is executed by the AI.";
}
