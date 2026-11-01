package com.civiclens.incident;

/**
 * Where an incident's category / severity / recommendation came from. Keeps model suggestions distinguishable
 * from facts verified by an official.
 */
public enum FieldSource {
    /** Supplied by the reporting citizen. */
    CITIZEN,
    /** Suggested by the Gemini incident-intelligence agent (not verified). */
    AI_SUGGESTED,
    /** Produced by the documented deterministic keyword fallback (not verified, not AI). */
    RULE_BASED,
    /** Set or confirmed by an authorized official. */
    OFFICIAL,
    /** Department recommendation taken from the city's category routing table. */
    CATEGORY_MAPPING
}
