package com.civiclens.duplicate;

import com.civiclens.incident.IncidentCategory;
import com.civiclens.incident.IncidentStatus;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class DuplicateDtos {

    private DuplicateDtos() {
    }

    public record DuplicateCheckRequest(
            @NotNull(message = "cityId is required.") UUID cityId,
            @Size(max = 120, message = "title must be at most 120 characters.") String title,
            @NotBlank(message = "description is required.")
            @Size(min = 3, max = 2000, message = "description must be 3-2000 characters.") String description,
            @Size(max = 120, message = "locality must be at most 120 characters.") String locality,
            @DecimalMin(value = "-90.0", message = "latitude must be between -90 and 90.")
            @DecimalMax(value = "90.0", message = "latitude must be between -90 and 90.") Double latitude,
            @DecimalMin(value = "-180.0", message = "longitude must be between -180 and 180.")
            @DecimalMax(value = "180.0", message = "longitude must be between -180 and 180.") Double longitude,
            IncidentCategory category) {
    }

    /** Public view of a possible duplicate. Contains no information about who reported it. */
    public record DuplicateCandidate(
            UUID incidentId,
            String title,
            String descriptionExcerpt,
            String locality,
            IncidentCategory category,
            IncidentStatus status,
            Instant createdAt,
            Double distanceMeters,
            double score,
            boolean strongMatch,
            long supportCount,
            List<DuplicateScorer.Reason> reasons) {
    }

    public record DuplicateCheckResponse(
            List<DuplicateCandidate> candidates,
            boolean strongMatchFound,
            double minScore,
            double strongMatchScore,
            Instant checkedAt) {
    }
}
