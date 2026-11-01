package com.civiclens.ai;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/** In-memory, per-instance record of recent provider outcomes, for the AI status endpoint only (not readiness). */
@Component
public class AiStatusTracker {

    private final Clock clock;
    private final AtomicReference<Instant> lastSuccess = new AtomicReference<>();
    private final AtomicReference<Instant> lastFailure = new AtomicReference<>();
    private final AtomicReference<String> lastFailureType = new AtomicReference<>();

    public AiStatusTracker(Clock clock) {
        this.clock = clock;
    }

    public void success() {
        lastSuccess.set(clock.instant());
    }

    public void failure(ProviderException.FailureType type) {
        lastFailure.set(clock.instant());
        lastFailureType.set(type.name());
    }

    public Instant lastSuccess() {
        return lastSuccess.get();
    }

    public Instant lastFailure() {
        return lastFailure.get();
    }

    public String lastFailureType() {
        return lastFailureType.get();
    }
}
