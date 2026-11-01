package com.civiclens.ai;

import com.civiclens.ai.AnalysisModels.AnalysisInput;
import com.civiclens.config.AppProperties;
import com.civiclens.incident.IncidentCategory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GeminiIntelligenceProviderTest {

    @Test
    void requestUsesConfiguredHighThinkingAndStrictJsonOutput() throws Exception {
        var config = new AppProperties.Gemini("test-key", "gemini-3.8-flash",
                "https://generativelanguage.googleapis.com/v1beta", Duration.ofSeconds(5),
                Duration.ofSeconds(90), "HIGH", 8192);
        var mapper = new ObjectMapper();
        var provider = new GeminiIntelligenceProvider(config, mapper);
        var input = new AnalysisInput("Open manhole", "An uncovered manhole is in the road.",
                "Patna, Bihar, India", "Kankarbagh", "Near the crossing", IncidentCategory.SEWAGE,
                true, List.of());

        var body = mapper.readTree(provider.buildBody(input));
        assertThat(body.at("/generationConfig/thinkingConfig/thinkingLevel").asText()).isEqualTo("HIGH");
        assertThat(body.at("/generationConfig/maxOutputTokens").asInt()).isEqualTo(8192);
        assertThat(body.at("/generationConfig/responseFormat/text/mimeType").asText()).isEqualTo("APPLICATION_JSON");
        assertThat(body.at("/generationConfig/responseFormat/text/schema/properties/recommendedActions/type").asText())
                .isEqualTo("array");
        assertThat(body.at("/generationConfig/responseSchema").isMissingNode()).isTrue();
        assertThat(body.toString()).doesNotContain("test-key");
    }

    @Test
    void oldGeminiConfigurationDefaultsToHighThinking() {
        var config = new AppProperties.Gemini("test-key", "gemini-3.8-flash",
                "https://generativelanguage.googleapis.com/v1beta", Duration.ofSeconds(5), Duration.ofSeconds(90));
        assertThat(config.thinkingLevel()).isEqualTo("HIGH");
        assertThat(config.maxOutputTokens()).isEqualTo(8192);
    }
}
