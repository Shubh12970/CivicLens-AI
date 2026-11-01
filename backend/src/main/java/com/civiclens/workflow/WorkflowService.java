package com.civiclens.workflow;

import com.civiclens.city.CityDirectory;
import com.civiclens.city.Department;
import com.civiclens.common.ApiError;
import com.civiclens.common.ApiException;
import com.civiclens.common.ErrorCode;
import com.civiclens.incident.Incident;
import com.civiclens.incident.IncidentDtos.IncidentView;
import com.civiclens.incident.IncidentEvent;
import com.civiclens.incident.IncidentEventRepository;
import com.civiclens.incident.IncidentRepository;
import com.civiclens.incident.IncidentService;
import com.civiclens.incident.IncidentStatus;
import com.civiclens.reward.RewardReason;
import com.civiclens.reward.RewardService;
import com.civiclens.user.CurrentUser;
import com.civiclens.workflow.StatusTransitionPolicy.Denied;
import com.civiclens.workflow.WorkflowDtos.AssignmentRequest;
import com.civiclens.workflow.WorkflowDtos.MarkDuplicateRequest;
import com.civiclens.workflow.WorkflowDtos.StatusChangeRequest;
import com.civiclens.workflow.WorkflowDtos.TriageRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Module C — deterministic civic workflow. Every change locks the incident row (SELECT ... FOR UPDATE), validates
 * permission and transition, then updates the incident, appends the audit event and issues rewards in the same
 * transaction: either all of it commits or none of it does. AI output never reaches this service directly.
 */
@Service
public class WorkflowService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowService.class);

    private final IncidentRepository incidents;
    private final IncidentEventRepository events;
    private final CityDirectory cityDirectory;
    private final RewardService rewards;
    private final IncidentService incidentService;
    private final StatusTransitionPolicy policy;
    private final Clock clock;

    public WorkflowService(IncidentRepository incidents, IncidentEventRepository events, CityDirectory cityDirectory,
                           RewardService rewards, IncidentService incidentService, StatusTransitionPolicy policy,
                           Clock clock) {
        this.incidents = incidents;
        this.events = events;
        this.cityDirectory = cityDirectory;
        this.rewards = rewards;
        this.incidentService = incidentService;
        this.policy = policy;
        this.clock = clock;
    }

    public StatusTransitionPolicy policy() {
        return policy;
    }

    @Transactional
    public IncidentView changeStatus(CurrentUser actor, UUID incidentId, StatusChangeRequest request) {
        Instant now = clock.instant();
        Incident incident = lock(incidentId);
        IncidentStatus from = incident.getStatus();
        String note = trimToNull(request.note());
        enforce(policy.checkStatusChange(actor, incident, request.status(), note, now), from);

        incident.changeStatus(request.status(), now);
        if (request.status() == IncidentStatus.RESOLVED) {
            incident.setResolutionNote(note, now);
        }
        events.save(new IncidentEvent(incident.getId(), IncidentEvent.Type.STATUS_CHANGED, from, request.status(),
                incident.getDepartmentId(), actor.id(), actor.role(), note, now));

        if (request.status() == IncidentStatus.RESOLVED) {
            rewards.grantToUser(incident.getReporterId(), RewardReason.REPORT_RESOLVED, incident.getId());
            if (incident.getDepartmentId() != null) {
                rewards.grantToDepartment(incident.getDepartmentId(), RewardReason.DEPARTMENT_RESOLUTION, incident.getId());
            }
        }
        incidents.flush();
        log.info("status-changed incidentId={} from={} to={} actorId={} role={}", incidentId, from, request.status(),
                actor.id(), actor.role());
        return incidentService.view(incident, actor);
    }

    @Transactional
    public IncidentView assign(CurrentUser actor, UUID incidentId, AssignmentRequest request) {
        Instant now = clock.instant();
        Incident incident = lock(incidentId);
        Department department = cityDirectory.requireDepartment(request.departmentId());
        IncidentStatus from = incident.getStatus();
        enforce(policy.checkAssignment(actor, incident, department.getCityId()), from);
        if (department.getId().equals(incident.getDepartmentId()) && from == IncidentStatus.ASSIGNED) {
            throw new ApiException(ErrorCode.CONFLICT, "The incident is already assigned to this department.");
        }

        incident.assignTo(department.getId(), now);
        if (from != IncidentStatus.ASSIGNED) {
            incident.changeStatus(IncidentStatus.ASSIGNED, now);
        }
        events.save(new IncidentEvent(incident.getId(), IncidentEvent.Type.ASSIGNED, from, IncidentStatus.ASSIGNED,
                department.getId(), actor.id(), actor.role(), trimToNull(request.note()), now));
        rewards.grantToUser(incident.getReporterId(), RewardReason.REPORT_ACCEPTED, incident.getId());
        incidents.flush();
        log.info("incident-assigned incidentId={} departmentId={} from={} actorId={}", incidentId, department.getId(),
                from, actor.id());
        return incidentService.view(incident, actor);
    }

    @Transactional
    public IncidentView triage(CurrentUser actor, UUID incidentId, TriageRequest request) {
        if (request.category() == null && request.severity() == null && request.safetyCritical() == null) {
            throw ApiException.validation("category", "Provide at least one of category, severity or safetyCritical.");
        }
        Instant now = clock.instant();
        Incident incident = lock(incidentId);
        requireOfficialAccess(actor, incident);
        incident.officialTriage(request.category(), request.severity(), request.safetyCritical(), now);
        incident.recommendFromMapping(cityDirectory.departmentFor(incident.getCityId(), incident.getCategory()).orElse(null), now);
        String summary = "Verified by official:"
                + (request.category() != null ? " category=" + request.category() : "")
                + (request.severity() != null ? " severity=" + request.severity() : "")
                + (request.safetyCritical() != null ? " safetyCritical=" + request.safetyCritical() : "")
                + (trimToNull(request.note()) != null ? ". " + request.note().strip() : "");
        events.save(new IncidentEvent(incident.getId(), IncidentEvent.Type.TRIAGE_UPDATED, null, null,
                incident.getDepartmentId(), actor.id(), actor.role(), summary, now));
        incidents.flush();
        log.info("incident-triaged incidentId={} actorId={}", incidentId, actor.id());
        return incidentService.view(incident, actor);
    }

    /** Officials only. Links the report to the canonical one and rejects it; nothing is deleted or merged. */
    @Transactional
    public IncidentView markDuplicate(CurrentUser actor, UUID incidentId, MarkDuplicateRequest request) {
        Instant now = clock.instant();
        if (incidentId.equals(request.duplicateOfId())) {
            throw ApiException.validation("duplicateOfId", "An incident cannot be a duplicate of itself.");
        }
        Incident incident = lock(incidentId);
        requireOfficialAccess(actor, incident);
        Incident canonical = incidents.findById(request.duplicateOfId())
                .orElseThrow(() -> ApiException.notFound("Canonical incident"));
        if (!canonical.getCityId().equals(incident.getCityId())) {
            throw ApiException.validation("duplicateOfId", "The canonical incident must be in the same city.");
        }
        if (canonical.getDuplicateOfId() != null) {
            throw ApiException.validation("duplicateOfId", "The canonical incident is itself marked as a duplicate.");
        }
        IncidentStatus from = incident.getStatus();
        if (!StatusTransitionPolicy.isValidTransition(from, IncidentStatus.REJECTED)) {
            throw new ApiException(ErrorCode.INVALID_STATUS_TRANSITION,
                    "An incident in status " + from + " cannot be marked as a duplicate.");
        }
        String note = "Duplicate of " + canonical.getId() + (trimToNull(request.note()) != null ? ". " + request.note().strip() : "");
        incident.markDuplicateOf(canonical.getId(), now);
        incident.changeStatus(IncidentStatus.REJECTED, now);
        events.save(new IncidentEvent(incident.getId(), IncidentEvent.Type.MARKED_DUPLICATE, null, null, null,
                actor.id(), actor.role(), note, now));
        events.save(new IncidentEvent(incident.getId(), IncidentEvent.Type.STATUS_CHANGED, from, IncidentStatus.REJECTED,
                incident.getDepartmentId(), actor.id(), actor.role(), note, now));
        incidents.flush();
        log.info("incident-marked-duplicate incidentId={} canonicalId={} actorId={}", incidentId, canonical.getId(), actor.id());
        return incidentService.view(incident, actor);
    }

    private void requireOfficialAccess(CurrentUser actor, Incident incident) {
        if (actor.isSystemAdmin()) {
            return;
        }
        if (!actor.isDepartmentAdmin()) {
            throw ApiException.forbidden("Only department officials can do this.");
        }
        if (!incident.getCityId().equals(actor.departmentCityId())) {
            throw ApiException.forbidden("This incident is outside your department's city.");
        }
        if (incident.getDepartmentId() != null && !incident.getDepartmentId().equals(actor.departmentId())) {
            throw ApiException.forbidden("This incident is assigned to another department.");
        }
    }

    private Incident lock(UUID incidentId) {
        return incidents.findByIdForUpdate(incidentId).orElseThrow(() -> ApiException.notFound("Incident"));
    }

    private static void enforce(StatusTransitionPolicy.Decision decision, IncidentStatus current) {
        if (decision instanceof Denied d) {
            if (d.forbidden()) {
                throw ApiException.forbidden(d.reason());
            }
            if (d.invalidTransition()) {
                throw new ApiException(ErrorCode.INVALID_STATUS_TRANSITION, d.reason(),
                        List.of(new ApiError.Detail("currentStatus", current.name())), null);
            }
            throw ApiException.validation(d.field(), d.reason());
        }
    }

    private static String trimToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
