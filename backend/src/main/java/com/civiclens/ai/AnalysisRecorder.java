package com.civiclens.ai;

import com.civiclens.ai.AnalysisModels.AnalysisInput;
import com.civiclens.ai.AnalysisModels.DepartmentOption;
import com.civiclens.ai.AnalysisModels.ValidatedAnalysis;
import com.civiclens.city.City;
import com.civiclens.city.CityDirectory;
import com.civiclens.config.AppProperties;
import com.civiclens.incident.FieldSource;
import com.civiclens.incident.Incident;
import com.civiclens.incident.IncidentCategory;
import com.civiclens.incident.IncidentEvent;
import com.civiclens.incident.IncidentEventRepository;
import com.civiclens.incident.IncidentRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Transactional steps of analysis processing. The provider call itself happens outside any transaction. */
@Component
public class AnalysisRecorder {

    private static final Logger log = LoggerFactory.getLogger(AnalysisRecorder.class);

    private final IncidentAnalysisRepository analyses;
    private final IncidentRepository incidents;
    private final IncidentEventRepository incidentEvents;
    private final CityDirectory cityDirectory;
    private final JdbcTemplate jdbc;
    private final AppProperties.Ai config;
    private final ObjectMapper mapper;
    private final Clock clock;

    public AnalysisRecorder(IncidentAnalysisRepository analyses, IncidentRepository incidents,
                            IncidentEventRepository incidentEvents, CityDirectory cityDirectory, JdbcTemplate jdbc,
                            AppProperties props, ObjectMapper mapper, Clock clock) {
        this.analyses = analyses;
        this.incidents = incidents;
        this.incidentEvents = incidentEvents;
        this.cityDirectory = cityDirectory;
        this.jdbc = jdbc;
        this.config = props.ai();
        this.mapper = mapper;
        this.clock = clock;
    }

    /**
     * Atomically takes the lease on a due run (PENDING and due, or PROCESSING with an expired lease).
     * Exactly one concurrent caller can win; the others get 0 rows and skip.
     */
    @Transactional
    public boolean claim(UUID analysisId) {
        Instant now = clock.instant();
        Duration lease = config.leaseDuration() == null ? Duration.ofMinutes(2) : config.leaseDuration();
        int rows = jdbc.update("""
                UPDATE incident_analyses
                   SET status = 'PROCESSING', lease_until = ?, attempt_count = attempt_count + 1, updated_at = ?
                 WHERE id = ?
                   AND ((status = 'PENDING' AND (next_attempt_at IS NULL OR next_attempt_at <= ?))
                     OR (status = 'PROCESSING' AND lease_until < ?))
                """, Timestamp.from(now.plus(lease)), Timestamp.from(now), analysisId, Timestamp.from(now),
                Timestamp.from(now));
        return rows == 1;
    }

    @Transactional(readOnly = true)
    public List<UUID> dueRuns(int limit) {
        Timestamp now = Timestamp.from(clock.instant());
        return jdbc.queryForList("""
                SELECT id FROM incident_analyses
                 WHERE (status = 'PENDING' AND (next_attempt_at IS NULL OR next_attempt_at <= ?))
                    OR (status = 'PROCESSING' AND lease_until < ?)
                 ORDER BY created_at LIMIT ?
                """, UUID.class, now, now, limit);
    }

    @Transactional(readOnly = true)
    public Optional<AnalysisInput> loadInput(UUID analysisId) {
        return analyses.findById(analysisId)
                .flatMap(a -> incidents.findById(a.getIncidentId()))
                .map(this::inputFor);
    }

    public AnalysisInput inputFor(Incident incident) {
        String cityName = cityDirectory.findCity(incident.getCityId()).map(City::getName).orElse(null);
        Map<UUID, List<IncidentCategory>> handled = cityDirectory.categoriesByDepartment(incident.getCityId());
        List<DepartmentOption> departments = cityDirectory.departmentsOf(incident.getCityId()).stream()
                .map(d -> new DepartmentOption(d.getId(), d.getName(), handled.getOrDefault(d.getId(), List.of())))
                .toList();
        IncidentCategory citizenCategory = incident.getCategorySource() == FieldSource.CITIZEN
                && incident.getCategory() != IncidentCategory.OTHER ? incident.getCategory() : null;
        return new AnalysisInput(incident.getTitle(), incident.getDescription(), cityName, incident.getLocality(),
                incident.getAddressText(), citizenCategory, incident.isHazardReported(), departments);
    }

    @Transactional
    public void complete(UUID analysisId, ValidatedAnalysis result, AnalysisSource source, String provider,
                         String model, String fallbackReason) {
        IncidentAnalysis analysis = analyses.findById(analysisId).orElse(null);
        if (analysis == null || analysis.getStatus() != AnalysisStatus.PROCESSING) {
            log.warn("analysis-complete ignored analysisId={} (lease lost or already finished)", analysisId);
            return;
        }
        Instant now = clock.instant();
        analysis.complete(source, provider, model, result.category(), result.severity(), result.confidence(),
                result.safetyCritical(), result.explanation(), json(result.safetyConcerns()),
                json(result.missingInformation()), result.recommendedNextAction(), result.recommendedDepartmentId(),
                json(result.warnings()), now);
        if (fallbackReason != null) {
            analysis.recordError(fallbackReason, "Gemini analysis unavailable; rule-based fallback used");
        }

        Incident incident = incidents.findByIdForUpdate(analysis.getIncidentId()).orElseThrow();
        FieldSource fieldSource = source == AnalysisSource.GEMINI ? FieldSource.AI_SUGGESTED : FieldSource.RULE_BASED;
        incident.applySuggestion(result.category(), result.severity(), result.safetyCritical(),
                result.recommendedDepartmentId(), fieldSource, now);
        if (incident.getRecommendedDepartmentSource() != FieldSource.AI_SUGGESTED) {
            incident.recommendFromMapping(
                    cityDirectory.departmentFor(incident.getCityId(), incident.getCategory()).orElse(null), now);
        }
        String note = source == AnalysisSource.GEMINI
                ? "AI analysis completed (" + provider + "/" + model + "): suggested " + result.category() + ", " + result.severity()
                : "Rule-based fallback analysis (not AI): suggested " + result.category() + ", " + result.severity();
        incidentEvents.save(new IncidentEvent(incident.getId(), IncidentEvent.Type.ANALYSIS_COMPLETED, null, null,
                null, null, null, note, now));
        log.info("analysis-completed analysisId={} incidentId={} source={} category={} severity={} warnings={}",
                analysisId, incident.getId(), source, result.category(), result.severity(), result.warnings().size());
    }

    /**
     * Handles a provider/validation failure in one transaction: schedule a bounded retry, or (terminal) store the
     * labelled rule-based fallback if enabled, or mark the run FAILED. The report itself is never touched destructively.
     */
    @Transactional
    public void handleFailure(UUID analysisId, ProviderException failure, String provider, String model,
                              AnalysisInput input) {
        IncidentAnalysis analysis = analyses.findById(analysisId).orElse(null);
        if (analysis == null || analysis.getStatus() != AnalysisStatus.PROCESSING) {
            return;
        }
        Instant now = clock.instant();
        boolean willRetry = failure.retryable() && analysis.getAttemptCount() < analysis.getMaxAttempts();
        if (!willRetry && config.fallbackEnabled() && input != null) {
            log.warn("analysis-fallback analysisId={} attempts={} error={}", analysisId, analysis.getAttemptCount(),
                    failure.type());
            complete(analysisId, RuleBasedTriage.classify(input, failure.type().name()),
                    AnalysisSource.RULE_BASED_FALLBACK, "rules", null, failure.type().name());
            return;
        }
        if (willRetry) {
            Duration backoff = config.retryBackoff() == null ? Duration.ofSeconds(20) : config.retryBackoff();
            Instant next = now.plus(backoff.multipliedBy(analysis.getAttemptCount()));
            analysis.scheduleRetry(failure.type().name(), failure.getMessage(), next, now);
            log.warn("analysis-retry-scheduled analysisId={} attempt={}/{} error={} nextAttemptAt={}", analysisId,
                    analysis.getAttemptCount(), analysis.getMaxAttempts(), failure.type(), next);
            return;
        }
        analysis.fail(provider, model, failure.type().name(), failure.getMessage(), now);
        incidentEvents.save(new IncidentEvent(analysis.getIncidentId(), IncidentEvent.Type.ANALYSIS_FAILED, null, null,
                null, null, null, "Automated analysis failed (" + failure.type() + "). The report is saved and awaits manual triage.", now));
        log.warn("analysis-failed analysisId={} attempts={} error={}", analysisId, analysis.getAttemptCount(), failure.type());
    }

    private String json(List<String> list) {
        try {
            return mapper.writeValueAsString(list == null ? List.of() : list);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }
}
