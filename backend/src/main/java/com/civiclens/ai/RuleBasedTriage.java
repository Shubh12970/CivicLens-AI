package com.civiclens.ai;

import com.civiclens.ai.AnalysisModels.AnalysisInput;
import com.civiclens.ai.AnalysisModels.ValidatedAnalysis;
import com.civiclens.duplicate.TextSimilarity;
import com.civiclens.incident.IncidentCategory;
import com.civiclens.incident.Severity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Deterministic keyword fallback used only when Gemini is unavailable or not configured. Results are stored with
 * source {@code RULE_BASED_FALLBACK} and are never labelled as AI output.
 * Safety rule: the fallback never assigns LOW. Unknown reports get MEDIUM; safety keywords or a citizen hazard flag
 * give HIGH or CRITICAL, so a failed AI call can never bury a dangerous report.
 */
public final class RuleBasedTriage {

    private static final Map<IncidentCategory, List<String>> KEYWORDS = new LinkedHashMap<>();
    private static final List<String> CRITICAL_TERMS = List.of(
            "live wire", "exposed wire", "electrocut", "sparking", "spark", "open manhole", "manhole open",
            "collapse", "caved in", "gas leak", "gas smell", "fire", "drown", "sinkhole", "transformer blast");
    private static final List<String> HIGH_TERMS = List.of(
            "accident", "injur", "danger", "hazard", "swerv", "fell", "falling", "flood", "waterlog", "overflow",
            "sewage", "children", "school", "hospital", "blocked road", "fallen tree", "uprooted", "dark", "deep");

    static {
        KEYWORDS.put(IncidentCategory.ELECTRICAL_HAZARD, List.of("wire", "electric", "shock", "spark", "transformer", "pole current"));
        KEYWORDS.put(IncidentCategory.STREETLIGHT, List.of("streetlight", "street light", "lamp", "light not working", "dark street", "lights off"));
        KEYWORDS.put(IncidentCategory.TRAFFIC_SIGNAL, List.of("traffic signal", "signal not working", "traffic light"));
        KEYWORDS.put(IncidentCategory.ROAD_DAMAGE, List.of("pothole", "road damage", "crack", "broken road", "speed breaker", "footpath", "asphalt", "road"));
        KEYWORDS.put(IncidentCategory.GARBAGE, List.of("garbage", "trash", "waste", "dump", "litter", "rubbish", "bin"));
        KEYWORDS.put(IncidentCategory.WATER_LEAKAGE, List.of("pipe", "leak", "water supply", "burst", "tap", "pipeline"));
        KEYWORDS.put(IncidentCategory.SEWAGE, List.of("sewage", "sewer", "manhole", "stink"));
        KEYWORDS.put(IncidentCategory.FLOODING, List.of("flood", "waterlog", "submerged", "inundat"));
        KEYWORDS.put(IncidentCategory.DRAINAGE, List.of("drain", "gutter", "clogged", "stagnant water"));
        KEYWORDS.put(IncidentCategory.TREE_FALL, List.of("tree", "branch", "uprooted"));
        KEYWORDS.put(IncidentCategory.PUBLIC_PROPERTY_DAMAGE, List.of("bench", "park", "bus stop", "railing", "wall", "public toilet", "graffiti"));
    }

    private RuleBasedTriage() {
    }

    public static ValidatedAnalysis classify(AnalysisInput input, String reason) {
        String text = (" " + DuplicateText.of(input) + " ").toLowerCase();

        IncidentCategory category = input.citizenCategory() != null && input.citizenCategory() != IncidentCategory.OTHER
                ? input.citizenCategory() : null;
        if (category == null) {
            int best = 0;
            for (var entry : KEYWORDS.entrySet()) {
                int hits = 0;
                for (String kw : entry.getValue()) {
                    if (text.contains(kw)) {
                        hits++;
                    }
                }
                if (hits > best) {
                    best = hits;
                    category = entry.getKey();
                }
            }
        }
        if (category == null) {
            category = IncidentCategory.OTHER;
        }

        List<String> concerns = new ArrayList<>();
        boolean critical = CRITICAL_TERMS.stream().filter(text::contains).peek(t -> concerns.add("Mentions: " + t))
                .findAny().isPresent();
        boolean high = HIGH_TERMS.stream().anyMatch(text::contains);

        Severity severity;
        if (critical) {
            severity = Severity.CRITICAL;
        } else if (high || input.hazardReported() || category == IncidentCategory.ELECTRICAL_HAZARD) {
            severity = Severity.HIGH;
        } else {
            severity = Severity.MEDIUM;
        }
        boolean safety = critical || category == IncidentCategory.ELECTRICAL_HAZARD;

        List<String> missing = new ArrayList<>();
        if (input.locality() == null || input.locality().isBlank()) {
            missing.add("Locality or nearest landmark");
        }
        String explanation = "Rule-based fallback (not AI): category from keyword matching, severity is a conservative "
                + "default (" + severity + ") that an official must verify. Reason for fallback: " + reason + ".";
        String impact = "The reported infrastructure issue may disrupt normal use of the location. The report does not provide a verified count of affected people.";
        String riskRationale = "Fallback assessment uses conservative keyword rules and the citizen hazard flag. An authorized official must verify the actual conditions.";
        List<String> actions = List.of(
                "An authorized official should review the report and verify the exact location.",
                "Check whether the reported condition creates an immediate risk to people.",
                "Confirm the appropriate internal department queue before assignment.");
        List<String> questions = missing.isEmpty() ? List.of() : List.of("Can the reporter provide a nearby landmark or more precise location?");
        String guidance = safety
                ? "Keep a safe distance from the reported hazard. Do not touch or attempt to repair it; contact local emergency services if there is immediate danger."
                : "Avoid interfering with the reported infrastructure. Add a nearby landmark or safe photograph if it is easy to provide.";
        List<String> evidence = List.of("A photograph taken from a safe public location", "A nearby landmark or street name");
        return new ValidatedAnalysis(category, severity, null, safety, explanation, concerns.stream().limit(5).toList(),
                null, missing, "Official review required: verify category, severity and location.", List.of(),
                impact, riskRationale, actions, questions, guidance, evidence);
    }

    /** Small helper so the fallback reads the same text the duplicate detector sees. */
    private static final class DuplicateText {
        static String of(AnalysisInput input) {
            String raw = (input.title() == null ? "" : input.title()) + " " + input.description();
            // Keep the raw lower-cased text (multi-word phrases) plus normalized stems for single-word matches.
            return raw + " " + TextSimilarity.normalize(raw);
        }
    }
}
