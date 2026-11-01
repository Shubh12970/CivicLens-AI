package com.civiclens.ai;

import com.civiclens.ai.AnalysisModels.RawAnalysis;
import com.civiclens.incident.IncidentCategory;
import com.civiclens.incident.Severity;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnalysisValidatorTest {

    static final UUID DEPT = UUID.randomUUID();

    RawAnalysis raw(String category, String severity, Double confidence, Boolean safety, String explanation,
                    String dept, String next) {
        return new RawAnalysis(category, severity, confidence, safety, explanation, List.of("risk"), dept,
                List.of("Exact location"), next);
    }

    @Test
    void acceptsValidOutput() throws Exception {
        var v = AnalysisValidator.validate(raw("ROAD_DAMAGE", "HIGH", 0.8, false, "Road defect.", DEPT.toString(),
                "Verify location."), Set.of(DEPT));
        assertThat(v.category()).isEqualTo(IncidentCategory.ROAD_DAMAGE);
        assertThat(v.severity()).isEqualTo(Severity.HIGH);
        assertThat(v.recommendedDepartmentId()).isEqualTo(DEPT);
        assertThat(v.warnings()).isEmpty();
    }

    @Test
    void lowercaseEnumsAreAccepted() throws Exception {
        var v = AnalysisValidator.validate(raw("road_damage", "medium", null, false, "ok", null, null), Set.of());
        assertThat(v.category()).isEqualTo(IncidentCategory.ROAD_DAMAGE);
    }

    @Test
    void rejectsUnknownCategory() {
        assertThatThrownBy(() -> AnalysisValidator.validate(raw("POTHOLES", "HIGH", 0.5, false, "x", null, null), Set.of()))
                .isInstanceOfSatisfying(ProviderException.class,
                        e -> assertThat(e.type()).isEqualTo(ProviderException.FailureType.INVALID_OUTPUT));
    }

    @Test
    void rejectsUnknownSeverityMissingFieldsAndBadConfidence() {
        assertThatThrownBy(() -> AnalysisValidator.validate(raw("GARBAGE", "URGENT", 0.5, false, "x", null, null), Set.of()))
                .isInstanceOf(ProviderException.class);
        assertThatThrownBy(() -> AnalysisValidator.validate(raw("GARBAGE", "LOW", 0.5, false, "  ", null, null), Set.of()))
                .hasMessageContaining("explanation missing");
        assertThatThrownBy(() -> AnalysisValidator.validate(raw("GARBAGE", "LOW", 1.7, false, "x", null, null), Set.of()))
                .hasMessageContaining("confidence");
        assertThatThrownBy(() -> AnalysisValidator.validate(raw("GARBAGE", "LOW", 0.5, null, "x", null, null), Set.of()))
                .hasMessageContaining("safetyCritical");
        assertThatThrownBy(() -> AnalysisValidator.validate(raw(null, "LOW", 0.5, false, "x", null, null), Set.of()))
                .hasMessageContaining("category missing");
    }

    @Test
    void rejectsOversizeExplanation() {
        assertThatThrownBy(() -> AnalysisValidator.validate(raw("GARBAGE", "LOW", 0.5, false, "x".repeat(1001), null, null), Set.of()))
                .hasMessageContaining("too long");
    }

    @Test
    void rejectsClaimsThatActionsWereTaken() {
        assertThatThrownBy(() -> AnalysisValidator.validate(raw("GARBAGE", "LOW", 0.5, false,
                "The issue has been assigned to the sanitation team.", null, null), Set.of()))
                .hasMessageContaining("claims an action");
    }

    @Test
    void unsafeNextActionIsDroppedWithWarning() throws Exception {
        var v = AnalysisValidator.validate(raw("GARBAGE", "LOW", 0.5, false, "Garbage pile.", null,
                "A crew is on the way."), Set.of());
        assertThat(v.recommendedNextAction()).isNull();
        assertThat(v.warnings()).anyMatch(w -> w.contains("recommendedNextAction"));
    }

    @Test
    void unknownOrForeignDepartmentIsIgnoredWithWarning() throws Exception {
        var foreign = AnalysisValidator.validate(raw("GARBAGE", "LOW", 0.5, false, "x", UUID.randomUUID().toString(), null), Set.of(DEPT));
        assertThat(foreign.recommendedDepartmentId()).isNull();
        assertThat(foreign.warnings()).hasSize(1);
        var garbage = AnalysisValidator.validate(raw("GARBAGE", "LOW", 0.5, false, "x", "Roads Department", null), Set.of(DEPT));
        assertThat(garbage.recommendedDepartmentId()).isNull();
    }

    @Test
    void safetyCriticalLowSeverityIsRaised() throws Exception {
        var v = AnalysisValidator.validate(raw("ELECTRICAL_HAZARD", "LOW", 0.5, true, "Exposed wire.", null, null), Set.of());
        assertThat(v.severity()).isEqualTo(Severity.HIGH);
        assertThat(v.warnings()).anyMatch(w -> w.contains("raised to HIGH"));
    }

    @Test
    void acceptsExpandedDecisionSupportAndKeepsItBounded() throws Exception {
        var v = AnalysisValidator.validate(raw("ROAD_DAMAGE", "HIGH", 0.82, false,
                "Vehicles are swerving around a reported pothole.", null, "Verify the location."), Set.of());
        assertThat(v.impactSummary()).contains("public-infrastructure");
        assertThat(v.riskRationale()).contains("official");
        assertThat(v.recommendedActions()).isNotEmpty();
        assertThat(v.citizenGuidance()).contains("safe distance");
    }

    @Test
    void rejectsActionClaimsInCitizenGuidance() {
        RawAnalysis value = new RawAnalysis("GARBAGE", "MEDIUM", 0.7, false, "Waste has accumulated.",
                List.of(), null, List.of(), "Verify location.",
                "The waste may affect sanitation.", "No immediate danger was described.",
                List.of("Verify the site"), List.of(), "A team has been dispatched.", List.of());
        assertThatThrownBy(() -> AnalysisValidator.validate(value, Set.of()))
                .hasMessageContaining("citizenGuidance claims an action was taken");
    }

    @Test
    void removesActionClaimsFromRecommendedActionList() throws Exception {
        RawAnalysis value = new RawAnalysis("GARBAGE", "MEDIUM", 0.7, false, "Waste has accumulated.",
                List.of(), null, List.of(), "Verify location.",
                "The waste may affect sanitation.", "No immediate danger was described.",
                List.of("A team is on the way", "An official should verify the site"), List.of(),
                "Keep away from waste and report immediate hazards to local services.", List.of());
        var result = AnalysisValidator.validate(value, Set.of());
        assertThat(result.recommendedActions()).containsExactly("An official should verify the site");
        assertThat(result.warnings()).anyMatch(w -> w.contains("recommendedActions"));
    }

    @Test
    void listsAreTrimmedAndCapped() throws Exception {
        var r = new RawAnalysis("GARBAGE", "LOW", 0.5, false, "x", List.of("a", " ", "b", "c", "d", "e", "f", "y".repeat(300)),
                null, null, null);
        var v = AnalysisValidator.validate(r, Set.of());
        assertThat(v.safetyConcerns()).hasSize(5).doesNotContain(" ");
        assertThat(v.missingInformation()).isEmpty();
    }
}
