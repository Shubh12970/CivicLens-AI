package com.civiclens.workflow;

import com.civiclens.incident.Incident;
import com.civiclens.incident.IncidentCategory;
import com.civiclens.incident.IncidentStatus;
import com.civiclens.user.CurrentUser;
import com.civiclens.user.Role;
import com.civiclens.workflow.StatusTransitionPolicy.Allowed;
import com.civiclens.workflow.StatusTransitionPolicy.Denied;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static com.civiclens.incident.IncidentStatus.*;
import static org.assertj.core.api.Assertions.assertThat;

class StatusTransitionPolicyTest {

    static final UUID CITY = UUID.randomUUID();
    static final UUID OTHER_CITY = UUID.randomUUID();
    static final UUID DEPT = UUID.randomUUID();
    static final UUID OTHER_DEPT = UUID.randomUUID();
    static final Instant NOW = Instant.parse("2026-11-01T10:00:00Z");

    final StatusTransitionPolicy policy = new StatusTransitionPolicy(Duration.ofDays(30));
    final UUID reporterId = UUID.randomUUID();
    final CurrentUser reporter = new CurrentUser(reporterId, "r", Role.CITIZEN, null, null);
    final CurrentUser otherCitizen = new CurrentUser(UUID.randomUUID(), "c", Role.CITIZEN, null, null);
    final CurrentUser deptAdmin = new CurrentUser(UUID.randomUUID(), "d", Role.DEPARTMENT_ADMIN, DEPT, CITY);
    final CurrentUser otherCityAdmin = new CurrentUser(UUID.randomUUID(), "o", Role.DEPARTMENT_ADMIN, DEPT, OTHER_CITY);
    final CurrentUser sysAdmin = new CurrentUser(UUID.randomUUID(), "s", Role.SYSTEM_ADMIN, null, null);

    Incident incident(IncidentStatus status, UUID department) {
        Incident i = new Incident(CITY, reporterId, "Pothole", "A deep pothole on the road", "deep pothole road",
                null, null, null, null, IncidentCategory.ROAD_DAMAGE, false, false, NOW.minusSeconds(3600));
        if (department != null) {
            i.assignTo(department, NOW);
        }
        if (status != SUBMITTED) {
            i.changeStatus(status, NOW.minusSeconds(60));
        }
        return i;
    }

    @Test
    void documentedTransitionsAreExactlyTheAllowedOnes() {
        assertThat(StatusTransitionPolicy.isValidTransition(SUBMITTED, UNDER_REVIEW)).isTrue();
        assertThat(StatusTransitionPolicy.isValidTransition(SUBMITTED, REJECTED)).isTrue();
        assertThat(StatusTransitionPolicy.isValidTransition(UNDER_REVIEW, REJECTED)).isTrue();
        assertThat(StatusTransitionPolicy.isValidTransition(ASSIGNED, IN_PROGRESS)).isTrue();
        assertThat(StatusTransitionPolicy.isValidTransition(IN_PROGRESS, RESOLVED)).isTrue();
        assertThat(StatusTransitionPolicy.isValidTransition(RESOLVED, REOPENED)).isTrue();
        assertThat(StatusTransitionPolicy.isValidTransition(REJECTED, REOPENED)).isTrue();
        assertThat(StatusTransitionPolicy.isValidTransition(REOPENED, UNDER_REVIEW)).isTrue();

        assertThat(StatusTransitionPolicy.isValidTransition(SUBMITTED, RESOLVED)).isFalse();
        assertThat(StatusTransitionPolicy.isValidTransition(SUBMITTED, IN_PROGRESS)).isFalse();
        assertThat(StatusTransitionPolicy.isValidTransition(RESOLVED, IN_PROGRESS)).isFalse();
        assertThat(StatusTransitionPolicy.isValidTransition(REJECTED, RESOLVED)).isFalse();
        assertThat(StatusTransitionPolicy.isValidTransition(IN_PROGRESS, SUBMITTED)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(IncidentStatus.class)
    void noStatusCanTransitionToItself(IncidentStatus s) {
        assertThat(StatusTransitionPolicy.isValidTransition(s, s)).isFalse();
    }

    @Test
    void assignedCannotBeReachedThroughStatusEndpoint() {
        var d = policy.checkStatusChange(sysAdmin, incident(SUBMITTED, null), ASSIGNED, null, NOW);
        assertThat(d).isInstanceOfSatisfying(Denied.class, x -> assertThat(x.invalidTransition()).isTrue());
    }

    @Test
    void citizenCannotChangeOfficialStatus() {
        var d = policy.checkStatusChange(reporter, incident(SUBMITTED, null), UNDER_REVIEW, null, NOW);
        assertThat(d).isInstanceOfSatisfying(Denied.class, x -> assertThat(x.forbidden()).isTrue());
    }

    @Test
    void reporterCanReopenResolvedWithinWindowWithReason() {
        Incident i = incident(IN_PROGRESS, DEPT);
        i.changeStatus(RESOLVED, NOW.minus(Duration.ofDays(2)));
        assertThat(policy.checkStatusChange(reporter, i, REOPENED, "Still broken after rain", NOW)).isInstanceOf(Allowed.class);
        assertThat(policy.checkStatusChange(reporter, i, REOPENED, null, NOW))
                .isInstanceOfSatisfying(Denied.class, x -> assertThat(x.field()).isEqualTo("note"));
        assertThat(policy.checkStatusChange(otherCitizen, i, REOPENED, "Still broken", NOW))
                .isInstanceOfSatisfying(Denied.class, x -> assertThat(x.forbidden()).isTrue());
    }

    @Test
    void reporterCannotReopenAfterWindow() {
        Incident i = incident(IN_PROGRESS, DEPT);
        i.changeStatus(RESOLVED, NOW.minus(Duration.ofDays(31)));
        assertThat(policy.checkStatusChange(reporter, i, REOPENED, "Still broken", NOW))
                .isInstanceOfSatisfying(Denied.class, x -> assertThat(x.forbidden()).isTrue());
    }

    @Test
    void reporterCannotReopenRejected() {
        assertThat(policy.checkStatusChange(reporter, incident(REJECTED, null), REOPENED, "Please look again", NOW))
                .isInstanceOfSatisfying(Denied.class, x -> assertThat(x.forbidden()).isTrue());
    }

    @Test
    void deptAdminScopedToCityAndDepartment() {
        assertThat(policy.checkStatusChange(deptAdmin, incident(SUBMITTED, null), UNDER_REVIEW, null, NOW)).isInstanceOf(Allowed.class);
        assertThat(policy.checkStatusChange(otherCityAdmin, incident(SUBMITTED, null), UNDER_REVIEW, null, NOW))
                .isInstanceOfSatisfying(Denied.class, x -> assertThat(x.forbidden()).isTrue());
        assertThat(policy.checkStatusChange(deptAdmin, incident(ASSIGNED, DEPT), IN_PROGRESS, null, NOW)).isInstanceOf(Allowed.class);
        assertThat(policy.checkStatusChange(deptAdmin, incident(ASSIGNED, OTHER_DEPT), IN_PROGRESS, null, NOW))
                .isInstanceOfSatisfying(Denied.class, x -> assertThat(x.forbidden()).isTrue());
    }

    @Test
    void requiredNotes() {
        assertThat(policy.checkStatusChange(sysAdmin, incident(SUBMITTED, null), REJECTED, "", NOW))
                .isInstanceOfSatisfying(Denied.class, x -> assertThat(x.field()).isEqualTo("note"));
        assertThat(policy.checkStatusChange(sysAdmin, incident(IN_PROGRESS, DEPT), RESOLVED, "ok", NOW))
                .isInstanceOf(Denied.class);
        assertThat(policy.checkStatusChange(sysAdmin, incident(IN_PROGRESS, DEPT), RESOLVED, "Pothole filled and levelled", NOW))
                .isInstanceOf(Allowed.class);
    }

    @Test
    void invalidTransitionReportedBeforePermission() {
        var d = policy.checkStatusChange(reporter, incident(SUBMITTED, null), RESOLVED, "done done", NOW);
        assertThat(d).isInstanceOfSatisfying(Denied.class, x -> assertThat(x.invalidTransition()).isTrue());
    }

    @Test
    void assignmentRules() {
        assertThat(policy.checkAssignment(deptAdmin, incident(SUBMITTED, null), CITY)).isInstanceOf(Allowed.class);
        assertThat(policy.checkAssignment(deptAdmin, incident(SUBMITTED, null), OTHER_CITY))
                .isInstanceOfSatisfying(Denied.class, x -> assertThat(x.field()).isEqualTo("departmentId"));
        assertThat(policy.checkAssignment(deptAdmin, incident(ASSIGNED, OTHER_DEPT), CITY))
                .isInstanceOfSatisfying(Denied.class, x -> assertThat(x.forbidden()).isTrue());
        assertThat(policy.checkAssignment(reporter, incident(SUBMITTED, null), CITY))
                .isInstanceOfSatisfying(Denied.class, x -> assertThat(x.forbidden()).isTrue());
        assertThat(policy.checkAssignment(sysAdmin, incident(RESOLVED, DEPT), CITY))
                .isInstanceOfSatisfying(Denied.class, x -> assertThat(x.invalidTransition()).isTrue());
    }

    @Test
    void availableActionsReflectRole() {
        assertThat(policy.availableActions(null, incident(SUBMITTED, null), NOW)).isEmpty();
        assertThat(policy.availableActions(otherCitizen, incident(SUBMITTED, null), NOW)).isEmpty();
        assertThat(policy.availableActions(deptAdmin, incident(SUBMITTED, null), NOW))
                .contains("STATUS:UNDER_REVIEW", "STATUS:REJECTED", "ASSIGN", "TRIAGE");
    }
}
