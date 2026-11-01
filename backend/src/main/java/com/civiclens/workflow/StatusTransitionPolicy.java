package com.civiclens.workflow;

import com.civiclens.incident.Incident;
import com.civiclens.incident.IncidentStatus;
import com.civiclens.user.CurrentUser;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.civiclens.incident.IncidentStatus.ASSIGNED;
import static com.civiclens.incident.IncidentStatus.IN_PROGRESS;
import static com.civiclens.incident.IncidentStatus.REJECTED;
import static com.civiclens.incident.IncidentStatus.REOPENED;
import static com.civiclens.incident.IncidentStatus.RESOLVED;
import static com.civiclens.incident.IncidentStatus.SUBMITTED;
import static com.civiclens.incident.IncidentStatus.UNDER_REVIEW;

/**
 * The complete, explicit status workflow. Pure logic (no I/O), unit-tested exhaustively.
 *
 * <pre>
 * Status changes (POST /incidents/{id}/status):
 *   SUBMITTED    -> UNDER_REVIEW | REJECTED
 *   UNDER_REVIEW -> REJECTED
 *   ASSIGNED     -> IN_PROGRESS | REJECTED
 *   IN_PROGRESS  -> RESOLVED
 *   RESOLVED     -> REOPENED
 *   REJECTED     -> REOPENED
 *   REOPENED     -> UNDER_REVIEW | REJECTED
 * Assignment (POST /incidents/{id}/assignment) moves to ASSIGNED from
 *   SUBMITTED | UNDER_REVIEW | REOPENED, and reassigns from ASSIGNED | IN_PROGRESS.
 * Required note: REJECTED (reason), RESOLVED (resolution note), REOPENED (reason).
 * </pre>
 */
public final class StatusTransitionPolicy {

    public static final int MIN_NOTE_LENGTH = 5;

    private static final Map<IncidentStatus, Set<IncidentStatus>> TRANSITIONS = new EnumMap<>(IncidentStatus.class);
    private static final Set<IncidentStatus> ASSIGNABLE_FROM = EnumSet.of(SUBMITTED, UNDER_REVIEW, REOPENED, ASSIGNED, IN_PROGRESS);
    private static final Set<IncidentStatus> NOTE_REQUIRED = EnumSet.of(REJECTED, RESOLVED, REOPENED);

    static {
        TRANSITIONS.put(SUBMITTED, EnumSet.of(UNDER_REVIEW, REJECTED));
        TRANSITIONS.put(UNDER_REVIEW, EnumSet.of(REJECTED));
        TRANSITIONS.put(ASSIGNED, EnumSet.of(IN_PROGRESS, REJECTED));
        TRANSITIONS.put(IN_PROGRESS, EnumSet.of(RESOLVED));
        TRANSITIONS.put(RESOLVED, EnumSet.of(REOPENED));
        TRANSITIONS.put(REJECTED, EnumSet.of(REOPENED));
        TRANSITIONS.put(REOPENED, EnumSet.of(UNDER_REVIEW, REJECTED));
    }

    private final Duration reopenWindow;

    public StatusTransitionPolicy(Duration reopenWindow) {
        this.reopenWindow = reopenWindow;
    }

    public sealed interface Decision permits Allowed, Denied {
    }

    public record Allowed() implements Decision {
    }

    /** {@code forbidden} = actor lacks permission (403); otherwise the transition itself is invalid (409/400). */
    public record Denied(boolean forbidden, boolean invalidTransition, String field, String reason) implements Decision {
    }

    public static boolean isValidTransition(IncidentStatus from, IncidentStatus to) {
        return TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }

    public static boolean canAssignFrom(IncidentStatus from) {
        return ASSIGNABLE_FROM.contains(from);
    }

    public static boolean noteRequired(IncidentStatus to) {
        return NOTE_REQUIRED.contains(to);
    }

    public Decision checkStatusChange(CurrentUser actor, Incident incident, IncidentStatus target, String note, Instant now) {
        IncidentStatus from = incident.getStatus();
        if (target == ASSIGNED) {
            return new Denied(false, true, "status", "Use the assignment endpoint to assign an incident.");
        }
        if (!isValidTransition(from, target)) {
            return new Denied(false, true, "status", "Transition " + from + " -> " + target + " is not allowed.");
        }
        Decision permission = checkPermission(actor, incident, target, now);
        if (permission instanceof Denied) {
            return permission;
        }
        if (noteRequired(target) && (note == null || note.strip().length() < MIN_NOTE_LENGTH)) {
            return new Denied(false, false, "note", "A note of at least " + MIN_NOTE_LENGTH
                    + " characters is required when moving to " + target + ".");
        }
        return new Allowed();
    }

    public Decision checkAssignment(CurrentUser actor, Incident incident, java.util.UUID targetDepartmentCity) {
        if (!canAssignFrom(incident.getStatus())) {
            return new Denied(false, true, "status", "An incident in status " + incident.getStatus() + " cannot be assigned.");
        }
        if (!incident.getCityId().equals(targetDepartmentCity)) {
            return new Denied(false, false, "departmentId", "The department does not serve this incident's city.");
        }
        if (actor.isSystemAdmin()) {
            return new Allowed();
        }
        if (!actor.isDepartmentAdmin()) {
            return new Denied(true, false, null, "Only department officials can assign incidents.");
        }
        if (!incident.getCityId().equals(actor.departmentCityId())) {
            return new Denied(true, false, null, "This incident is outside your department's city.");
        }
        if (incident.getDepartmentId() != null && !incident.getDepartmentId().equals(actor.departmentId())) {
            return new Denied(true, false, null, "This incident is assigned to another department.");
        }
        return new Allowed();
    }

    private Decision checkPermission(CurrentUser actor, Incident incident, IncidentStatus target, Instant now) {
        if (actor.isSystemAdmin()) {
            return new Allowed();
        }
        if (actor.isDepartmentAdmin()) {
            if (!incident.getCityId().equals(actor.departmentCityId())) {
                return new Denied(true, false, null, "This incident is outside your department's city.");
            }
            boolean ownDepartment = actor.departmentId().equals(incident.getDepartmentId());
            boolean unassigned = incident.getDepartmentId() == null;
            if (target == IN_PROGRESS || target == RESOLVED) {
                return ownDepartment ? new Allowed()
                        : new Denied(true, false, null, "Only the assigned department can do this.");
            }
            return ownDepartment || unassigned ? new Allowed()
                    : new Denied(true, false, null, "This incident is assigned to another department.");
        }
        // Citizens: only the reporter may reopen their own resolved report within the reopen window.
        if (target == REOPENED && incident.getStatus() == RESOLVED && incident.getReporterId().equals(actor.id())) {
            if (incident.getResolvedAt() != null && incident.getResolvedAt().plus(reopenWindow).isBefore(now)) {
                return new Denied(true, false, null, "The reopen window of " + reopenWindow.toDays() + " days has passed.");
            }
            return new Allowed();
        }
        return new Denied(true, false, null, "Only authorized officials can change the status of this incident.");
    }

    /** Actions the given user may currently take, for the frontend to show buttons (server still re-checks). */
    public List<String> availableActions(CurrentUser actor, Incident incident, Instant now) {
        List<String> actions = new ArrayList<>();
        if (actor == null) {
            return actions;
        }
        for (IncidentStatus target : TRANSITIONS.getOrDefault(incident.getStatus(), Set.of())) {
            if (checkPermission(actor, incident, target, now) instanceof Allowed) {
                actions.add("STATUS:" + target);
            }
        }
        if (actor.isOfficial() && canAssignFrom(incident.getStatus())
                && checkAssignment(actor, incident, incident.getCityId()) instanceof Allowed) {
            actions.add("ASSIGN");
            actions.add("TRIAGE");
        }
        return actions;
    }
}
