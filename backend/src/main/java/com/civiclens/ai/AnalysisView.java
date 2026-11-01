package com.civiclens.ai;

import com.civiclens.incident.IncidentCategory;
import com.civiclens.incident.Severity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** API representation of an analysis run. {@code aiGenerated} is true only for validated Gemini output. */
public record AnalysisView(
        UUID analysisId,
        int runNumber,
        AnalysisStatus status,
        AnalysisSource source,
        boolean aiGenerated,
        String provider,
        String model,
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
        Instant createdAt,
        Instant completedAt,
        String disclaimer) {

    public static final String DISCLAIMER = "Suggestions only. Category and severity are not verified until an "
            + "authorized official confirms them.";
}
