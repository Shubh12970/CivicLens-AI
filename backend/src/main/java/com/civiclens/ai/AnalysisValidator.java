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
    public static final int MAX_IMPACT_SUMMARY = 700;
    public static final int MAX_RISK_RATIONALE = 700;
    public static final int MAX_CITIZEN_GUIDANCE = 600;
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

        String impactSummary = validateRequiredText(raw.impactSummary(), "impactSummary", MAX_IMPACT_SUMMARY, errors);
        String riskRationale = validateRequiredText(raw.riskRationale(), "riskRationale", MAX_RISK_RATIONALE, errors);
        String citizenGuidance = validateRequiredText(raw.citizenGuidance(), "citizenGuidance", MAX_CITIZEN_GUIDANCE, errors);
        if (impactSummary != null && ACTION_CLAIM.matcher(impactSummary).find()) {
            errors.add("impactSummary claims an action was taken");
        }
        if (riskRationale != null && ACTION_CLAIM.matcher(riskRationale).find()) {
            errors.add("riskRationale claims an action was taken");
        }
        if (citizenGuidance != null && ACTION_CLAIM.matcher(citizenGuidance).find()) {
            errors.add("citizenGuidance claims an action was taken");
        }

        if (!errors.isEmpty()) {
            throw new ProviderException(FailureType.INVALID_OUTPUT, "Model output rejected: " + String.join("; ", errors));
        }

        List<String> concerns = cleanList(raw.safetyConcerns(), "safetyConcerns", warnings, 5, MAX_LIST_ITEM_LENGTH);
        List<String> missing = cleanList(raw.missingInformation(), "missingInformation", warnings, 5, MAX_LIST_ITEM_LENGTH);
        List<String> actions = cleanList(raw.recommendedActions(), "recommendedActions", warnings, 4, MAX_LIST_ITEM_LENGTH);
        List<String> questions = cleanList(raw.clarifyingQuestions(), "clarifyingQuestions", warnings, 4, MAX_LIST_ITEM_LENGTH);
        List<String> evidence = cleanList(raw.evidenceSuggestions(), "evidenceSuggestions", warnings, 4, MAX_LIST_ITEM_LENGTH);
        concerns = removeActionClaims(concerns, "safetyConcerns", warnings);
        missing = removeActionClaims(missing, "missingInformation", warnings);
        actions = removeActionClaims(actions, "recommendedActions", warnings);
        questions = removeActionClaims(questions, "clarifyingQuestions", warnings);
        evidence = removeActionClaims(evidence, "evidenceSuggestions", warnings);

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

        if (actions.isEmpty()) {
            actions = List.of("An authorized official should review the report and verify the reported location.");
            warnings.add("recommendedActions was empty; a safe review step was added");
        }

        return new ValidatedAnalysis(category, severity, confidence, safety, explanation, concerns, department,
                missing, nextAction, warnings, impactSummary, riskRationale, actions, questions,
                citizenGuidance, evidence);
    }

    private static List<String> removeActionClaims(List<String> items, String field, List<String> warnings) {
        List<String> safe = items.stream().filter(item -> {
            boolean hasClaim = ACTION_CLAIM.matcher(item).find();
            if (hasClaim) warnings.add(field + " contained an unsupported claim that an action was taken; item removed");
            return !hasClaim;
        }).toList();
        return safe;
    }

    private static String validateRequiredText(String value, String field, int maxLength, List<String> errors) {
        String clean = trimToNull(value);
        if (clean == null) {
            errors.add(field + " missing");
            return null;
        }
        if (clean.length() > maxLength) {
            errors.add(field + " too long");
            return null;
        }
        return clean;
    }

    private static List<String> cleanList(List<String> items, String field, List<String> warnings,
                                          int maxItems, int maxItemLength) {
        if (items == null) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String item : items) {
            String t = trimToNull(item);
            if (t == null) {
                continue;
            }
            if (t.length() > maxItemLength) {
                t = t.substring(0, maxItemLength);
                warnings.add(field + " item truncated");
            }
            if (out.size() == maxItems) {
                warnings.add(field + " limited to " + maxItems + " items");
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
