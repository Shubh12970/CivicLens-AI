package com.civiclens.workflow;

import com.civiclens.incident.IncidentCategory;
import com.civiclens.incident.IncidentStatus;
import com.civiclens.incident.Severity;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public final class WorkflowDtos {

    private WorkflowDtos() {
    }

    public record StatusChangeRequest(
            @NotNull(message = "status is required.") IncidentStatus status,
            @Size(max = 2000, message = "note must be at most 2000 characters.") String note) {
    }

    public record AssignmentRequest(
            @NotNull(message = "departmentId is required.") UUID departmentId,
            @Size(max = 2000, message = "note must be at most 2000 characters.") String note) {
    }

    /** Official verification of category/severity. At least one field must be set. */
    public record TriageRequest(
            IncidentCategory category,
            Severity severity,
            Boolean safetyCritical,
            @Size(max = 2000, message = "note must be at most 2000 characters.") String note) {
    }

    public record MarkDuplicateRequest(
            @NotNull(message = "duplicateOfId is required.") UUID duplicateOfId,
            @Size(max = 2000, message = "note must be at most 2000 characters.") String note) {
    }
}
