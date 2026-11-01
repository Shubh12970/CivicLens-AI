package com.civiclens.ai;

import com.civiclens.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.UUID;

/**
 * Starts processing after the creating transaction commits, and periodically sweeps runs that are due
 * (retries, runs whose worker crashed, runs dropped because the executor queue was full).
 * Durability comes from the database state, not from this in-memory executor.
 */
@Component
public class AnalysisDispatcher {

    private static final Logger log = LoggerFactory.getLogger(AnalysisDispatcher.class);

    private final AnalysisProcessor processor;
    private final AnalysisRecorder recorder;
    private final TaskExecutor executor;
    private final AppProperties.Ai config;

    public AnalysisDispatcher(AnalysisProcessor processor, AnalysisRecorder recorder,
                              @Qualifier("analysisExecutor") TaskExecutor executor, AppProperties props) {
        this.processor = processor;
        this.recorder = recorder;
        this.executor = executor;
        this.config = props.ai();
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRequested(AnalysisService.AnalysisRequested event) {
        dispatch(event.analysisId());
    }

    public void dispatch(UUID analysisId) {
        if (!config.asyncProcessing()) {
            processor.process(analysisId);
            return;
        }
        try {
            executor.execute(() -> processor.process(analysisId));
        } catch (TaskRejectedException e) {
            log.warn("analysis-executor-full analysisId={}; the scheduler will pick it up", analysisId);
        }
    }

    @Scheduled(fixedDelayString = "${civiclens.ai.scheduler-interval:PT15S}", initialDelayString = "PT10S")
    public void sweep() {
        if (!config.schedulerEnabled()) {
            return;
        }
        try {
            for (UUID id : recorder.dueRuns(10)) {
                processor.process(id);
            }
        } catch (RuntimeException e) {
            log.error("analysis-sweep-failed", e);
        }
    }
}
