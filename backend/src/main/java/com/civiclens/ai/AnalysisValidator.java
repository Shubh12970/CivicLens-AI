package com.civiclens.ai;

import com.civiclens.ai.AnalysisModels.RawAnalysis;
import com.civiclens.ai.AnalysisModels.ValidatedAnalysis;
import com.civiclens.ai.ProviderException.FailureType;
import com.civiclens.incident.IncidentCategory;
import com.civiclens.incident.Severity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Accepts model output only when it fits the contract. Hard violations (unknown enum, missing required field,
 * oversize text, claims that actions were taken) reject the whole result; soft issues (unknown department id,
 * overlong lists) are corrected and recorded as warnings.
 */
public final class AnalysisValidator {

    public static final int MAX_EXPLANATION = 1000;
    public static final int MAX_NEXT_ACTION = 400;
    public static final int MAX_LIST_ITEMS = 5;
    public static final int MAX_LIST_ITEM_LENGTH = 200;

    /** Phrases asserting that the system or someone already acted. The agent may only suggest. */
    private static final Pattern ACTION_CLAIM = Pattern.compile(
            "\\b(has|have) been (assigned|dispatched|reported|forwarded|escalated|fixed|resolved|scheduled)\\b"
                    + "|\\bi (have|ve) (assigned|notified|reported|contacted|dispatched|escalated)\\b"
                    + "|\\b(crew|team) (is|are) on (the|their) way\\b"
                    + "|\\bwill be (fixed|resolved|repaired) (by|within)\\b",
            Pattern.CASE_INSENSITIVE);

    private AnalysisValidator() {
    }

    public static ValidatedAnalysis validate(RawAnalysis raw, Set<UUID> allowedDepartments) throws ProviderException {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        IncidentCategory category = null;
        if (raw.category() == null) {
            errors.add("category missing");
        } else {
            category = IncidentCategory.parseOrNull(raw.category());
            if (category == null) {
                errors.add("category not an allowed value");
            }
        }

        Severity severity = null;
        if (raw.severity() == null) {
            errors.add("severity missing");
        } else {
            severity = Severity.parseOrNull(raw.severity());
            if (severity == null) {
                errors.add("severity not an allowed value");
            }
        }

        Double confidence = raw.confidence();
        if (confidence != null && (confidence.isNaN() || confidence < 0 || confidence > 1)) {
            errors.add("confidence outside 0..1");
        }

        String explanation = trimToNull(raw.explanation());
        if (explanation == null) {
            errors.add("explanation missing");
        } else if (explanation.length() > MAX_EXPLANATION) {
            errors.add("explanation too long");
        } else if (ACTION_CLAIM.matcher(explanation).find()) {
            errors.add("explanation claims an action was taken");
        }

        String nextAction = trimToNull(raw.recommendedNextAction());
        if (nextAction != null && nextAction.length() > MAX_NEXT_ACTION) {
            errors.add("recommendedNextAction too long");
        } else if (nextAction != null && ACTION_CLAIM.matcher(nextAction).find()) {
            warnings.add("recommendedNextAction claimed an action was taken; removed");
            nextAction = null;
        }

        if (raw.safetyCritical() == null) {
            errors.add("safetyCritical missing");
        }

        if (!errors.isEmpty()) {
            throw new ProviderException(FailureType.INVALID_OUTPUT, "Model output rejected: " + String.join("; ", errors));
        }

        List<String> concerns = cleanList(raw.safetyConcerns(), "safetyConcerns", warnings);
        List<String> missing = cleanList(raw.missingInformation(), "missingInformation", warnings);

        UUID department = null;
        String deptRaw = trimToNull(raw.recommendedDepartmentId());
        if (deptRaw != null && !"null".equalsIgnoreCase(deptRaw)) {
            try {
                UUID parsed = UUID.fromString(deptRaw);
                if (allowedDepartments.contains(parsed)) {
                    department = parsed;
                } else {
                    warnings.add("recommendedDepartmentId is not a department of this city; ignored");
                }
            } catch (IllegalArgumentException e) {
                warnings.add("recommendedDepartmentId is not a valid id; ignored");
            }
        }

        boolean safety = raw.safetyCritical();
        if (safety && (severity == Severity.LOW || severity == Severity.MEDIUM)) {
            warnings.add("safetyCritical with " + severity + " severity; raised to HIGH");
            severity = Severity.HIGH;
        }

        return new ValidatedAnalysis(category, severity, confidence, safety, explanation, concerns, department,
                missing, nextAction, warnings);
    }

    private static List<String> cleanList(List<String> items, String field, List<String> warnings) {
        if (items == null) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String item : items) {
            String t = trimToNull(item);
            if (t == null) {
                continue;
            }
            if (t.length() > MAX_LIST_ITEM_LENGTH) {
                t = t.substring(0, MAX_LIST_ITEM_LENGTH);
                warnings.add(field + " item truncated");
            }
            if (out.size() == MAX_LIST_ITEMS) {
                warnings.add(field + " limited to " + MAX_LIST_ITEMS + " items");
                break;
            }
            out.add(t);
        }
        return out;
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.strip();
        return t.isEmpty() ? null : t;
    }

    static boolean containsActionClaim(String text) {
        return text != null && ACTION_CLAIM.matcher(text.toLowerCase(Locale.ROOT)).find();
    }
}
