
package com.civiclens.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AnalysisPromptTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void responseSchemaRequiresExpandedDecisionSupportFields() {
        var schema = AnalysisPrompt.responseSchema(mapper);

        assertThat(schema.path("type").asText()).isEqualTo("object");
        assertThat(schema.path("additionalProperties").asBoolean()).isFalse();

        assertThat(schema.path("properties").has("impactSummary")).isTrue();
        assertThat(schema.path("properties").has("riskRationale")).isTrue();
        assertThat(schema.path("properties").has("recommendedActions")).isTrue();
        assertThat(schema.path("properties").has("clarifyingQuestions")).isTrue();
        assertThat(schema.path("properties").has("citizenGuidance")).isTrue();
        assertThat(schema.path("properties").has("evidenceSuggestions")).isTrue();

        assertThat(schema.path("required").toString())
                .contains("impactSummary", "riskRationale", "citizenGuidance");
    }

    @Test
    void promptSeparatesFactsFromUncertaintyAndRestrictsDepartmentIds() {
        var input = new AnalysisModels.AnalysisInput(
                "Open manhole",
                "A manhole is open on the road.",
                "Patna, Bihar, India",
                "Kankarbagh",
                "Near the crossing",
                null,
                true,
                java.util.List.of()
        );

        String prompt = AnalysisPrompt.userPrompt(input);

        // These details belong to the user prompt.
        assertThat(prompt).contains(
                "Patna, Bihar, India",
                "ALLOWED INTERNAL DEPARTMENT QUEUES",
                "A manhole is open",
                "complete set"
        );

        // These rules belong to the system instruction.
        assertThat(AnalysisPrompt.SYSTEM_INSTRUCTION).contains(
                "Do not invent casualty counts",
                "never recommend entering unsafe areas"
        );
    }
}
