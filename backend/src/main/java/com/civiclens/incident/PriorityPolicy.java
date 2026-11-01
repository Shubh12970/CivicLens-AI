package com.civiclens.incident;

/**
 * Deterministic ranking formulas. The SQL fragments used for sorting are generated from the same constants as the
 * Java methods, so the documented formula, the tests and the database ordering cannot drift apart.
 *
 * <h3>Priority (safety-first triage order)</h3>
 * <pre>
 * priority = severityWeight            CRITICAL 150, HIGH 90, unassessed 45, MEDIUM 30, LOW 10
 *          + 40 if safetyCritical
 *          + 15 if the citizen flagged a hazard (ignored once an official has set severity)
 *          + min(2 * ageDays, 40)       unresolved age
 *          + min(10 * ln(1 + supportVotes), 30)   community support, capped
 *          + 15 if REOPENED
 * </pre>
 * A LOW item can reach at most 10 + 15 + 40 + 30 = 95, below any safety-critical item (>= 130) and any CRITICAL
 * item (>= 150), however many votes it gets.
 *
 * <h3>Trending (community engagement, NOT safety)</h3>
 * <pre>trending = (supportVotes - opposeVotes + 0.5 * comments) / (ageHours + 2)^1.5</pre>
 */
public final class PriorityPolicy {

    public static final int W_CRITICAL = 150;
    public static final int W_HIGH = 90;
    public static final int W_UNASSESSED = 45;
    public static final int W_MEDIUM = 30;
    public static final int W_LOW = 10;
    public static final int SAFETY_BONUS = 40;
    public static final int HAZARD_FLAG_BONUS = 15;
    public static final double AGE_PER_DAY = 2.0;
    public static final double AGE_CAP = 40.0;
    public static final double SUPPORT_FACTOR = 10.0;
    public static final double SUPPORT_CAP = 30.0;
    public static final int REOPENED_BONUS = 15;
    public static final double TRENDING_GRAVITY = 1.5;

    private PriorityPolicy() {
    }

    public static int severityWeight(Severity severity) {
        if (severity == null) {
            return W_UNASSESSED;
        }
        return switch (severity) {
            case CRITICAL -> W_CRITICAL;
            case HIGH -> W_HIGH;
            case MEDIUM -> W_MEDIUM;
            case LOW -> W_LOW;
        };
    }

    public static double priority(Severity severity, boolean officialSeverity, boolean safetyCritical,
                                  boolean hazardReported, double ageDays, long supportVotes, IncidentStatus status) {
        double score = severityWeight(severity);
        if (safetyCritical) {
            score += SAFETY_BONUS;
        }
        if (hazardReported && !officialSeverity) {
            score += HAZARD_FLAG_BONUS;
        }
        score += Math.min(AGE_PER_DAY * Math.max(ageDays, 0), AGE_CAP);
        score += Math.min(SUPPORT_FACTOR * Math.log(1 + Math.max(supportVotes, 0)), SUPPORT_CAP);
        if (status == IncidentStatus.REOPENED) {
            score += REOPENED_BONUS;
        }
        return score;
    }

    public static double trending(long supportVotes, long opposeVotes, long comments, double ageHours) {
        double engagement = supportVotes - opposeVotes + 0.5 * comments;
        return engagement / Math.pow(Math.max(ageHours, 0) + 2, TRENDING_GRAVITY);
    }

    /**
     * SQL for priority. Expects alias {@code i} for incidents, {@code s.up} for support votes and the bind
     * parameter {@code :now} (timestamptz).
     */
    public static String prioritySql() {
        return "(CASE i.severity WHEN 'CRITICAL' THEN " + W_CRITICAL + " WHEN 'HIGH' THEN " + W_HIGH
                + " WHEN 'MEDIUM' THEN " + W_MEDIUM + " WHEN 'LOW' THEN " + W_LOW + " ELSE " + W_UNASSESSED + " END"
                + " + CASE WHEN i.safety_critical THEN " + SAFETY_BONUS + " ELSE 0 END"
                + " + CASE WHEN i.hazard_reported AND i.severity_source IS DISTINCT FROM 'OFFICIAL' THEN "
                + HAZARD_FLAG_BONUS + " ELSE 0 END"
                + " + LEAST(" + AGE_PER_DAY + " * GREATEST(EXTRACT(EPOCH FROM (:now - i.created_at)) / 86400.0, 0), " + AGE_CAP + ")"
                + " + LEAST(" + SUPPORT_FACTOR + " * LN(1 + COALESCE(s.up, 0)), " + SUPPORT_CAP + ")"
                + " + CASE WHEN i.status = 'REOPENED' THEN " + REOPENED_BONUS + " ELSE 0 END)";
    }

    /** SQL for trending. Expects {@code s.up}, {@code s.down}, {@code c.cnt} and {@code :now}. */
    public static String trendingSql() {
        return "((COALESCE(s.up, 0) - COALESCE(s.down, 0) + 0.5 * COALESCE(c.cnt, 0))"
                + " / POWER(GREATEST(EXTRACT(EPOCH FROM (:now - i.created_at)) / 3600.0, 0) + 2, " + TRENDING_GRAVITY + "))";
    }
}
