package com.civiclens.incident;

public enum IncidentStatus {
    SUBMITTED,
    UNDER_REVIEW,
    ASSIGNED,
    IN_PROGRESS,
    RESOLVED,
    REJECTED,
    REOPENED;

    public boolean isClosed() {
        return this == RESOLVED || this == REJECTED;
    }
}
