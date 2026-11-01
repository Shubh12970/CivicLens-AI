package com.civiclens.ai;

import com.civiclens.incident.IncidentCategory;
import com.civiclens.incident.Severity;

import java.util.List;
import java.util.UUID;

/** Provider-neutral data passed into and out of the incident-intelligence agent. */
public final class AnalysisModels {

    private AnalysisModels() {
    }

    /** What the agent may see: report and location/routing context, never reporter account or contact data. */
    public record AnalysisInput(
            String title,
            String description,
            String cityName,
            String locality,
            String addressText,
            IncidentCategory citizenCategory,
            boolean hazardReported,
            List<DepartmentOption> departments) {
    }

    public record DepartmentOption(UUID id, String name, List<IncidentCategory> categories) {
    }

    /** Untrusted, unvalidated fields returned by the provider. */
    public record RawAnalysis(
            String category,
            String severity,
            Double confidence,
            Boolean safetyCritical,
            String explanation,
            List<String> safetyConcerns,
            String recommendedDepartmentId,
            List<String> missingInformation,
            String recommendedNextAction,
            String impactSummary,
            String riskRationale,
            List<String> recommendedActions,
            List<String> clarifyingQuestions,
            String citizenGuidance,
            List<String> evidenceSuggestions) {

        /** Backward-compatible constructor for existing test providers and fixtures. */
        public RawAnalysis(String category, String severity, Double confidence, Boolean safetyCritical,
                           String explanation, List<String> safetyConcerns, String recommendedDepartmentId,
                           List<String> missingInformation, String recommendedNextAction) {
            this(category, severity, confidence, safetyCritical, explanation, safetyConcerns,
                    recommendedDepartmentId, missingInformation, recommendedNextAction,
                    "The report describes a public-infrastructure concern. The number of affected people is unknown.",
                    "This is a preliminary assessment based only on the submitted description; an official must verify the site.",
                    List.of("An authorized official should verify the location and reported condition."),
                    List.of(),
                    "Keep a safe distance from any suspected hazard. Do not attempt repairs yourself.",
                    List.of());
        }
    }

    /** Only fields that passed {@link AnalysisValidator}. */
    public record ValidatedAnalysis(
            IncidentCategory category,
            Severity severity,
            Double confidence,
            boolean safetyCritical,
            String explanation,
            List<String> safetyConcerns,
            UUID recommendedDepartmentId,
            List<String> missingInformation,
            String recommendedNextAction,
            List<String> warnings,
            String impactSummary,
            String riskRationale,
            List<String> recommendedActions,
            List<String> clarifyingQuestions,
            String citizenGuidance,
            List<String> evidenceSuggestions) {

        /** Backward-compatible constructor for older deterministic test fixtures. */
        public ValidatedAnalysis(IncidentCategory category, Severity severity, Double confidence,
                                 boolean safetyCritical, String explanation, List<String> safetyConcerns,
                                 UUID recommendedDepartmentId, List<String> missingInformation,
                                 String recommendedNextAction, List<String> warnings) {
            this(category, severity, confidence, safetyCritical, explanation, safetyConcerns,
                    recommendedDepartmentId, missingInformation, recommendedNextAction, warnings,
                    "The report describes a public-infrastructure concern. The number of affected people is unknown.",
                    "Preliminary assessment only. An authorized official must verify the reported conditions.",
                    List.of("An authorized official should verify the report and location."), List.of(),
                    "Keep a safe distance from suspected hazards. Do not attempt repairs yourself.", List.of());
        }
    }

    public record ProviderResult(RawAnalysis raw, String provider, String model) {
    }
}
