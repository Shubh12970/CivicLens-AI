package com.civiclens.ai;

import com.civiclens.incident.IncidentCategory;
import com.civiclens.incident.Severity;

import java.util.List;
import java.util.UUID;

/** Provider-neutral data passed into and out of the incident-intelligence agent. */
public final class AnalysisModels {

    private AnalysisModels() {
    }

    /** What the agent is allowed to see: report text and city routing context, no personal data about the reporter. */
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

    /** Untrusted, unvalidated fields exactly as returned by a provider. */
    public record RawAnalysis(
            String category,
            String severity,
            Double confidence,
            Boolean safetyCritical,
            String explanation,
            List<String> safetyConcerns,
            String recommendedDepartmentId,
            List<String> missingInformation,
            String recommendedNextAction) {
    }

    /** Output that passed {@link AnalysisValidator}; every field is within the documented bounds. */
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
            List<String> warnings) {
    }

    public record ProviderResult(RawAnalysis raw, String provider, String model) {
    }
}
