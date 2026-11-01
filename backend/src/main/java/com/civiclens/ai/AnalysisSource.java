package com.civiclens.ai;

public enum AnalysisSource {
    /** Produced by the Gemini model and accepted by {@link AnalysisValidator}. */
    GEMINI,
    /** Produced by {@link RuleBasedTriage} keyword rules. Never presented as AI output. */
    RULE_BASED_FALLBACK
}
