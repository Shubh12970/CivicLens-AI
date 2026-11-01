package com.civiclens.duplicate;

import com.civiclens.incident.IncidentCategory;
import com.civiclens.incident.IncidentStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Pure, explainable scoring of one existing incident against a new report. No I/O, so it is fully unit-tested.
 *
 * <pre>
 * score = 0.45 * text + 0.40 * location + 0.15 * category      (then x0.8 if the candidate is already RESOLVED)
 *   text     = TextSimilarity (word overlap + trigram), reduced for very short descriptions
 *   location = 1 - distance/maxDistance when both have coordinates (0 beyond maxDistance),
 *              otherwise 0.8 for the same locality name, else 0
 *   category = 1 same, 0.5 related (e.g. DRAINAGE/FLOODING), 0 otherwise or unknown
 * Gates (candidate excluded): both have coordinates and are > 3 x maxDistance apart;
 *                              or neither text (< 0.2) nor category (same/related) supports the match.
 * </pre>
 */
public final class DuplicateScorer {

    public static final double W_TEXT = 0.45;
    public static final double W_LOCATION = 0.40;
    public static final double W_CATEGORY = 0.15;

    private final double maxDistanceMeters;

    public DuplicateScorer(double maxDistanceMeters) {
        this.maxDistanceMeters = maxDistanceMeters;
    }

    public record Probe(String normalizedText, String locality, Double latitude, Double longitude, IncidentCategory category) {
    }

    public record Existing(String normalizedText, String locality, Double latitude, Double longitude,
                           IncidentCategory category, IncidentStatus status) {
    }

    public record Reason(String code, String message) {
    }

    public record Result(double score, Double distanceMeters, List<Reason> reasons) {
    }

    /** Returns null when the candidate is excluded by a gate. */
    public Result score(Probe probe, Existing existing) {
        List<Reason> reasons = new ArrayList<>();

        double text = TextSimilarity.similarity(probe.normalizedText(), existing.normalizedText());
        int tokens = Math.min(TextSimilarity.tokenCount(probe.normalizedText()),
                TextSimilarity.tokenCount(existing.normalizedText()));
        if (tokens < 3) {
            text *= 0.6; // very short descriptions are weak evidence
        }
        if (text >= 0.75) {
            reasons.add(new Reason("SIMILAR_DESCRIPTION", "Description is nearly identical"));
        } else if (text >= 0.35) {
            reasons.add(new Reason("SIMILAR_DESCRIPTION", "Description is similar"));
        }

        Double distance = null;
        double location = 0.0;
        if (probe.latitude() != null && probe.longitude() != null
                && existing.latitude() != null && existing.longitude() != null) {
            distance = GeoDistance.meters(probe.latitude(), probe.longitude(), existing.latitude(), existing.longitude());
            if (distance > 3 * maxDistanceMeters) {
                return null;
            }
            location = Math.max(0.0, 1.0 - distance / maxDistanceMeters);
            if (distance <= maxDistanceMeters) {
                reasons.add(new Reason("NEARBY", "Within " + Math.round(distance) + " m"));
            }
        }
        boolean sameLocality = sameLocality(probe.locality(), existing.locality());
        if (sameLocality) {
            reasons.add(new Reason("SAME_LOCALITY", "Same locality: " + existing.locality().trim()));
            if (distance == null) {
                location = 0.8;
            }
        }

        double category = 0.0;
        if (probe.category() != null && probe.category() != IncidentCategory.OTHER) {
            if (probe.category() == existing.category()) {
                category = 1.0;
                reasons.add(new Reason("SAME_CATEGORY", "Same category: " + existing.category()));
            } else if (probe.category().relatedTo(existing.category())) {
                category = 0.5;
                reasons.add(new Reason("RELATED_CATEGORY", "Related category: " + existing.category()));
            }
        }

        if (text < 0.2 && category == 0.0) {
            return null;
        }

        double score = W_TEXT * text + W_LOCATION * location + W_CATEGORY * category;
        if (existing.status() == IncidentStatus.RESOLVED) {
            score *= 0.8;
            reasons.add(new Reason("PREVIOUSLY_RESOLVED", "Marked resolved earlier; the problem may have recurred"));
        }
        return new Result(Math.round(score * 1000) / 1000.0, distance, reasons);
    }

    static boolean sameLocality(String a, String b) {
        if (a == null || b == null || a.isBlank() || b.isBlank()) {
            return false;
        }
        return normalizeLocality(a).equals(normalizeLocality(b));
    }

    static String normalizeLocality(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", "");
    }
}
