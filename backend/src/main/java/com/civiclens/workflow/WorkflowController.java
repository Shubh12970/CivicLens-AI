package com.civiclens.workflow;

import com.civiclens.idempotency.IdempotencyService;
import com.civiclens.incident.IncidentDtos.IncidentView;
import com.civiclens.user.Authz;
import com.civiclens.user.CurrentUser;
import com.civiclens.workflow.WorkflowDtos.AssignmentRequest;
import com.civiclens.workflow.WorkflowDtos.MarkDuplicateRequest;
import com.civiclens.workflow.WorkflowDtos.StatusChangeRequest;
import com.civiclens.workflow.WorkflowDtos.TriageRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@Tag(name = "Department workflow")
public class WorkflowController {

    private final WorkflowService workflow;
    private final IdempotencyService idempotency;

    public WorkflowController(WorkflowService workflow, IdempotencyService idempotency) {
        this.workflow = workflow;
        this.idempotency = idempotency;
    }

    @Operation(summary = "Change incident status (officials; reporter may reopen a resolved report). Supports Idempotency-Key.")
    @PostMapping("/api/v1/incidents/{incidentId}/status")
    public ResponseEntity<?> changeStatus(@PathVariable UUID incidentId, @Valid @RequestBody StatusChangeRequest request,
                                          @RequestHeader(value = IdempotencyService.HEADER, required = false) String key) {
        CurrentUser actor = Authz.require();
        return idempotency.execute(actor.id(), key, "status:" + incidentId, request, HttpStatus.OK,
                () -> workflow.changeStatus(actor, incidentId, request));
    }

    @Operation(summary = "Assign or reassign to a department (officials). Supports Idempotency-Key.")
    @PostMapping("/api/v1/incidents/{incidentId}/assignment")
    public ResponseEntity<?> assign(@PathVariable UUID incidentId, @Valid @RequestBody AssignmentRequest request,
                                    @RequestHeader(value = IdempotencyService.HEADER, required = false) String key) {
        CurrentUser actor = Authz.require();
        return idempotency.execute(actor.id(), key, "assign:" + incidentId, request, HttpStatus.OK,
                () -> workflow.assign(actor, incidentId, request));
    }

    @Operation(summary = "Verify category / severity / safety flag (officials)")
    @PatchMapping("/api/v1/incidents/{incidentId}/triage")
    public IncidentView triage(@PathVariable UUID incidentId, @Valid @RequestBody TriageRequest request) {
        return workflow.triage(Authz.require(), incidentId, request);
    }

    @Operation(summary = "Mark as duplicate of another incident and reject it (officials; nothing is deleted)")
    @PostMapping("/api/v1/incidents/{incidentId}/mark-duplicate")
    public IncidentView markDuplicate(@PathVariable UUID incidentId, @Valid @RequestBody MarkDuplicateRequest request) {
        return workflow.markDuplicate(Authz.require(), incidentId, request);
    }
}
