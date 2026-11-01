package com.civiclens.incident;

import com.civiclens.ai.AnalysisStatus;
import com.civiclens.ai.AnalysisView;
import com.civiclens.community.VoteType;
import com.civiclens.user.Role;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class IncidentDtos {

    private IncidentDtos() {
    }

    public record CreateIncidentRequest(
            @NotNull(message = "cityId is required.") UUID cityId,
            @NotBlank(message = "title is required.")
            @Size(min = 5, max = 120, message = "title must be 5-120 characters.") String title,
            @NotBlank(message = "description is required.")
            @Size(min = 10, max = 2000, message = "description must be 10-2000 characters.") String description,
            @Size(max = 120, message = "locality must be at most 120 characters.") String locality,
            @Size(max = 250, message = "addressText must be at most 250 characters.") String addressText,
            @DecimalMin(value = "-90.0", message = "latitude must be between -90 and 90.")
            @DecimalMax(value = "90.0", message = "latitude must be between -90 and 90.") Double latitude,
            @DecimalMin(value = "-180.0", message = "longitude must be between -180 and 180.")
            @DecimalMax(value = "180.0", message = "longitude must be between -180 and 180.") Double longitude,
            IncidentCategory category,
            Boolean hazardReported,
            @Valid DuplicateAcknowledgement duplicateCheck) {
    }

    /**
     * Sent after the citizen has seen duplicate candidates and decided this is a different problem.
     * Creation succeeds only if every strong candidate found at submit time is in {@code reviewedCandidateIds}.
     */
    public record DuplicateAcknowledgement(
            @Size(max = 20, message = "reviewedCandidateIds may contain at most 20 ids.") List<UUID> reviewedCandidateIds,
            boolean confirmedNotDuplicate) {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record IncidentView(
            UUID id,
            String title,
            String description,
            UUID cityId,
            String cityName,
            String locality,
            String addressText,
            Double latitude,
            Double longitude,
            IncidentCategory category,
            FieldSource categorySource,
            Severity severity,
            FieldSource severitySource,
            boolean safetyCritical,
            boolean hazardReported,
            IncidentStatus status,
            UUID departmentId,
            String departmentName,
            UUID recommendedDepartmentId,
            String recommendedDepartmentName,
            FieldSource recommendedDepartmentSource,
            AnalysisStatus analysisStatus,
            long supportCount,
            long opposeCount,
            long voteScore,
            long commentCount,
            Double priorityScore,
            Double trendingScore,
            Double distanceMeters,
            UUID duplicateOfId,
            String resolutionNote,
            boolean demoData,
            boolean reportedByMe,
            VoteType myVote,
            Instant createdAt,
            Instant updatedAt,
            Instant statusChangedAt,
            Instant resolvedAt,
            long version,
            /* Detail-only fields (null in lists). */
            AnalysisView analysis,
            List<String> availableActions) {
    }

    public record TimelineEntry(
            UUID id,
            IncidentEvent.Type type,
            IncidentStatus fromStatus,
            IncidentStatus toStatus,
            UUID departmentId,
            String departmentName,
            Role actorRole,
            String note,
            Instant createdAt) {
    }

    public enum FeedSort { PRIORITY, NEWEST, OLDEST, TRENDING, MOST_SUPPORTED, RECENTLY_UPDATED, NEAREST }

    public record FeedFilter(
            UUID cityId,
            String locality,
            IncidentCategory category,
            List<IncidentStatus> statuses,
            Severity severity,
            UUID departmentId,
            Boolean unassigned,
            Boolean safetyCritical,
            Boolean openOnly,
            Boolean mine,
            String q,
            Double nearLat,
            Double nearLng,
            Double radiusMeters,
            FeedSort sort) {
    }
}
