package com.civiclens.ai;

import com.civiclens.city.CityDirectory;
import com.civiclens.city.Department;
import com.civiclens.common.ApiException;
import com.civiclens.common.ErrorCode;
import com.civiclens.config.AppProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Creates analysis runs and exposes their results. Processing happens in {@link AnalysisProcessor} after the
 * creating transaction commits, so a slow or failing provider never blocks or rolls back report persistence.
 */
@Service
public class AnalysisService {

    private final IncidentAnalysisRepository analyses;
    private final CityDirectory cityDirectory;
    private final ApplicationEventPublisher events;
    private final AppProperties.Ai config;
    private final ObjectMapper mapper;
    private final Clock clock;

    public AnalysisService(IncidentAnalysisRepository analyses, CityDirectory cityDirectory,
                           ApplicationEventPublisher events, AppProperties props, ObjectMapper mapper, Clock clock) {
        this.analyses = analyses;
        this.cityDirectory = cityDirectory;
        this.events = events;
        this.config = props.ai();
        this.mapper = mapper;
        this.clock = clock;
    }

    /** Event published inside the transaction; {@link AnalysisDispatcher} acts on it only after commit. */
    public record AnalysisRequested(UUID analysisId) {
    }

    /**
     * Queues a new run. Joins the caller's transaction (incident creation) or starts one. The partial unique index
     * {@code uq_analysis_one_active} guarantees at most one PENDING/PROCESSING run per incident, even under races.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public IncidentAnalysis queue(UUID incidentId, UUID requestedBy) {
        Optional<IncidentAnalysis> latest = analyses.findFirstByIncidentIdOrderByRunNumberDesc(incidentId);
        if (latest.isPresent() && latest.get().getStatus().isActive()) {
            throw new ApiException(ErrorCode.ANALYSIS_IN_PROGRESS, "An analysis for this incident is already queued or running.");
        }
        int run = latest.map(IncidentAnalysis::getRunNumber).orElse(0) + 1;
        IncidentAnalysis analysis = new IncidentAnalysis(incidentId, run, Math.max(1, config.maxAttempts()), requestedBy,
                clock.instant());
        try {
            analyses.saveAndFlush(analysis);
        } catch (DataIntegrityViolationException e) {
            throw new ApiException(ErrorCode.ANALYSIS_IN_PROGRESS, "An analysis for this incident is already queued or running.");
        }
        events.publishEvent(new AnalysisRequested(analysis.getId()));
        return analysis;
    }

    @Transactional(readOnly = true)
    public Optional<AnalysisView> latest(UUID incidentId) {
        return analyses.findFirstByIncidentIdOrderByRunNumberDesc(incidentId).map(this::toView);
    }

    @Transactional(readOnly = true)
    public List<AnalysisView> history(UUID incidentId) {
        return analyses.findByIncidentIdOrderByRunNumberDesc(incidentId).stream().map(this::toView).toList();
    }

    @Transactional(readOnly = true)
    public Map<UUID, IncidentAnalysis> latestFor(Collection<UUID> incidentIds) {
        if (incidentIds.isEmpty()) {
            return Map.of();
        }
        return analyses.findLatestFor(incidentIds).stream()
                .collect(Collectors.toMap(IncidentAnalysis::getIncidentId, Function.identity(), (a, b) -> a));
    }

    public AnalysisView toView(IncidentAnalysis a) {
        String deptName = cityDirectory.findDepartment(a.getRecommendedDepartmentId()).map(Department::getName).orElse(null);
        return new AnalysisView(a.getId(), a.getRunNumber(), a.getStatus(), a.getSource(),
                a.getSource() == AnalysisSource.GEMINI, a.getProvider(), a.getModel(), a.getThinkingLevel(), a.getPromptVersion(),
                a.getAttemptCount(), a.getMaxAttempts(), a.getNextAttemptAt(), a.getCategory(), a.getSeverity(),
                a.getConfidence(), a.getSafetyCritical(), a.getExplanation(), readList(a.getSafetyConcerns()),
                readList(a.getMissingInformation()), a.getRecommendedNextAction(), a.getRecommendedDepartmentId(),
                deptName, readList(a.getValidationWarnings()), a.getLastErrorCode(), a.getLastErrorMessage(),
                a.getImpactSummary(), a.getRiskRationale(), readList(a.getRecommendedActions()),
                readList(a.getClarifyingQuestions()), a.getCitizenGuidance(), readList(a.getEvidenceSuggestions()),
                a.getCreatedAt(), a.getCompletedAt(), AnalysisView.DISCLAIMER);
    }

    private List<String> readList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return mapper.readValue(json, new TypeReference<List<String>>() { });
        } catch (Exception e) {
            return List.of();
        }
    }
}
