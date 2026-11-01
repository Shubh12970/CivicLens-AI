package com.civiclens.ai;

import com.civiclens.ai.AnalysisModels.AnalysisInput;
import com.civiclens.ai.AnalysisModels.DepartmentOption;
import com.civiclens.ai.AnalysisModels.ProviderResult;
import com.civiclens.ai.AnalysisModels.ValidatedAnalysis;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Module A orchestration: claim → call provider (no transaction held) → validate → record.
 * Safe to call concurrently or repeatedly for the same run; only the lease holder does work.
 */
@Component
public class AnalysisProcessor {

    private static final Logger log = LoggerFactory.getLogger(AnalysisProcessor.class);

    private final AnalysisRecorder recorder;
    private final IntelligenceProvider provider;
    private final AiStatusTracker tracker;

    public AnalysisProcessor(AnalysisRecorder recorder, IntelligenceProvider provider, AiStatusTracker tracker) {
        this.recorder = recorder;
        this.provider = provider;
        this.tracker = tracker;
    }

    public void process(UUID analysisId) {
        if (!recorder.claim(analysisId)) {
            return;
        }
        Optional<AnalysisInput> maybeInput = recorder.loadInput(analysisId);
        if (maybeInput.isEmpty()) {
            return;
        }
        AnalysisInput input = maybeInput.get();
        log.info("analysis-start analysisId={} provider={} model={}", analysisId, provider.name(), provider.model());
        try {
            ProviderResult result = provider.analyze(input);
            Set<UUID> allowed = input.departments().stream().map(DepartmentOption::id).collect(Collectors.toSet());
            ValidatedAnalysis validated = AnalysisValidator.validate(result.raw(), allowed);
            recorder.complete(analysisId, validated, AnalysisSource.GEMINI, result.provider(), result.model(), null);
            tracker.success();
        } catch (ProviderException e) {
            if (e.type() != ProviderException.FailureType.NOT_CONFIGURED) {
                tracker.failure(e.type());
            }
            log.warn("analysis-provider-failure analysisId={} type={} message={}", analysisId, e.type(), e.getMessage());
            recorder.handleFailure(analysisId, e, provider.name(), provider.model(), input);
        } catch (RuntimeException e) {
            // Unexpected bug: leave the run to the lease timeout + scheduler rather than losing it.
            log.error("analysis-unexpected-error analysisId={}", analysisId, e);
        }
    }
}
