package com.civiclens.reward;

/** Every way points can be earned, with fixed values. Points are only granted by server-side workflow events. */
public enum RewardReason {
    /** Citizen: an official accepted the report by assigning it to a department (rejected reports earn nothing). */
    REPORT_ACCEPTED(10),
    /** Citizen: the report was resolved. Paid once per incident even if it is reopened and resolved again. */
    REPORT_RESOLVED(20),
    /** Citizen: submitted an eligible review of a resolved incident. */
    REVIEW_SUBMITTED(5),
    /** Department: resolved an incident (once per incident). */
    DEPARTMENT_RESOLUTION(15),
    /** Department: received a review rated 4 or 5. */
    DEPARTMENT_POSITIVE_REVIEW(5);

    private final int points;

    RewardReason(int points) {
        this.points = points;
    }

    public int points() {
        return points;
    }
}
