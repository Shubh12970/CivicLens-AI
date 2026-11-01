package com.civiclens.ai;

/**
 * PENDING    – queued, or waiting for an automatic retry ({@code nextAttemptAt}).
 * PROCESSING – a worker holds the lease and is calling the provider.
 * COMPLETED  – a validated result exists ({@code source} says whether it came from Gemini or the rule-based fallback).
 * FAILED     – terminal failure with no result; a new run can be requested.
 */
public enum AnalysisStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED;

    public boolean isActive() {
        return this == PENDING || this == PROCESSING;
    }
}
