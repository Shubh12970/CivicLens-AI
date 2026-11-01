package com.civiclens.ai;

import com.civiclens.ai.AnalysisModels.RawAnalysis;
import com.civiclens.ai.ProviderException.FailureType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Turns a Gemini generateContent response body into {@link RawAnalysis}, classifying every failure mode. */
public final class GeminiResponseParser {

    private static final Set<String> BLOCKED_FINISH = Set.of("SAFETY", "RECITATION", "BLOCKLIST", "PROHIBITED_CONTENT",
            "SPII", "LANGUAGE");

    private GeminiResponseParser() {
    }

    public static RawAnalysis parse(String body, ObjectMapper mapper) throws ProviderException {
        JsonNode root;
        try {
            root = mapper.readTree(body);
        } catch (JsonProcessingException e) {
            throw new ProviderException(FailureType.MALFORMED_OUTPUT, "Provider response was not JSON");
        }
        if (root == null || !root.isObject()) {
            throw new ProviderException(FailureType.MALFORMED_OUTPUT, "Provider response was not a JSON object");
        }
        String blockReason = root.path("promptFeedback").path("blockReason").asText("");
        if (!blockReason.isEmpty()) {
            throw new ProviderException(FailureType.BLOCKED, "Provider blocked the prompt: " + blockReason);
        }
        JsonNode candidates = root.path("candidates");
        if (!candidates.isArray() || candidates.isEmpty()) {
            throw new ProviderException(FailureType.EMPTY_RESPONSE, "Provider returned no candidates");
        }
        JsonNode candidate = candidates.get(0);
        String finish = candidate.path("finishReason").asText("");
        if (BLOCKED_FINISH.contains(finish)) {
            throw new ProviderException(FailureType.BLOCKED, "Provider stopped generation: " + finish);
        }
        StringBuilder text = new StringBuilder();
        for (JsonNode part : candidate.path("content").path("parts")) {
            if (part.path("thought").asBoolean(false)) {
                continue; // reasoning summaries are not part of the answer
            }
            text.append(part.path("text").asText(""));
        }
        if (text.isEmpty() || text.toString().isBlank()) {
            if ("MAX_TOKENS".equals(finish)) {
                throw new ProviderException(FailureType.MALFORMED_OUTPUT, "Provider output was truncated (MAX_TOKENS)");
            }
            throw new ProviderException(FailureType.EMPTY_RESPONSE, "Provider returned an empty answer");
        }
        return parseAnalysisJson(text.toString(), mapper);
    }

    /** Parses the model's JSON answer. Types are checked strictly; values are validated later. */
    public static RawAnalysis parseAnalysisJson(String json, ObjectMapper mapper) throws ProviderException {
        String cleaned = stripCodeFence(json.trim());
        JsonNode node;
        try {
            node = mapper.readTree(cleaned);
        } catch (JsonProcessingException e) {
            throw new ProviderException(FailureType.MALFORMED_OUTPUT, "Model answer was not valid JSON");
        }
        if (node == null || !node.isObject()) {
            throw new ProviderException(FailureType.MALFORMED_OUTPUT, "Model answer was not a JSON object");
        }
        return new RawAnalysis(
                text(node, "category"),
                text(node, "severity"),
                number(node, "confidence"),
                bool(node, "safetyCritical"),
                text(node, "explanation"),
                strings(node, "safetyConcerns"),
                text(node, "recommendedDepartmentId"),
                strings(node, "missingInformation"),
                text(node, "recommendedNextAction"),
                text(node, "impactSummary"),
                text(node, "riskRationale"),
                strings(node, "recommendedActions"),
                strings(node, "clarifyingQuestions"),
                text(node, "citizenGuidance"),
                strings(node, "evidenceSuggestions"));
    }

    private static String stripCodeFence(String s) {
        if (s.startsWith("```")) {
            int firstNewline = s.indexOf('\n');
            int lastFence = s.lastIndexOf("```");
            if (firstNewline > 0 && lastFence > firstNewline) {
                return s.substring(firstNewline + 1, lastFence).trim();
            }
        }
        return s;
    }

    private static String text(JsonNode node, String field) throws ProviderException {
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) {
            return null;
        }
        if (!v.isTextual()) {
            throw new ProviderException(FailureType.MALFORMED_OUTPUT, "Field '" + field + "' must be a string");
        }
        return v.asText();
    }

    private static Double number(JsonNode node, String field) throws ProviderException {
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) {
            return null;
        }
        if (!v.isNumber()) {
            throw new ProviderException(FailureType.MALFORMED_OUTPUT, "Field '" + field + "' must be a number");
        }
        return v.asDouble();
    }

    private static Boolean bool(JsonNode node, String field) throws ProviderException {
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) {
            return null;
        }
        if (!v.isBoolean()) {
            throw new ProviderException(FailureType.MALFORMED_OUTPUT, "Field '" + field + "' must be a boolean");
        }
        return v.asBoolean();
    }

    private static List<String> strings(JsonNode node, String field) throws ProviderException {
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) {
            return null;
        }
        if (!v.isArray()) {
            throw new ProviderException(FailureType.MALFORMED_OUTPUT, "Field '" + field + "' must be an array");
        }
        List<String> out = new ArrayList<>();
        for (JsonNode item : v) {
            if (!item.isTextual()) {
                throw new ProviderException(FailureType.MALFORMED_OUTPUT, "Field '" + field + "' must contain strings");
            }
            out.add(item.asText());
        }
        return out;
    }
}
