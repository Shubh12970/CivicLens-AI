package com.civiclens.ai;

import com.civiclens.ai.ProviderException.FailureType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeminiResponseParserTest {

    final ObjectMapper mapper = new ObjectMapper();

    static String envelope(String answerJson) {
        String escaped = answerJson.replace("\\", "\\\\").replace("\"", "\\\"");
        return "{\"candidates\":[{\"content\":{\"role\":\"model\",\"parts\":[{\"text\":\"" + escaped
                + "\"}]},\"finishReason\":\"STOP\"}]}";
    }

    static final String GOOD = "{\"category\":\"ROAD_DAMAGE\",\"severity\":\"HIGH\",\"confidence\":0.9,"
            + "\"safetyCritical\":false,\"explanation\":\"Road defect\",\"safetyConcerns\":[\"swerving\"],"
            + "\"recommendedDepartmentId\":null,\"missingInformation\":[],\"recommendedNextAction\":\"Verify\"}";

    @Test
    void parsesStructuredAnswer() throws Exception {
        var raw = GeminiResponseParser.parse(envelope(GOOD), mapper);
        assertThat(raw.category()).isEqualTo("ROAD_DAMAGE");
        assertThat(raw.confidence()).isEqualTo(0.9);
        assertThat(raw.safetyConcerns()).containsExactly("swerving");
        assertThat(raw.recommendedDepartmentId()).isNull();
    }

    @Test
    void skipsThoughtPartsAndStripsCodeFences() throws Exception {
        String body = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"thinking...\",\"thought\":true},"
                + "{\"text\":\"```json\\n" + GOOD.replace("\"", "\\\"") + "\\n```\"}]},\"finishReason\":\"STOP\"}]}";
        assertThat(GeminiResponseParser.parse(body, mapper).severity()).isEqualTo("HIGH");
    }

    @Test
    void classifiesFailures() {
        assertType("not json", FailureType.MALFORMED_OUTPUT);
        assertType("{\"candidates\":[]}", FailureType.EMPTY_RESPONSE);
        assertType("{}", FailureType.EMPTY_RESPONSE);
        assertType("{\"promptFeedback\":{\"blockReason\":\"SAFETY\"}}", FailureType.BLOCKED);
        assertType("{\"candidates\":[{\"finishReason\":\"SAFETY\"}]}", FailureType.BLOCKED);
        assertType("{\"candidates\":[{\"content\":{\"parts\":[]},\"finishReason\":\"MAX_TOKENS\"}]}", FailureType.MALFORMED_OUTPUT);
        assertType("{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"\"}]}}]}", FailureType.EMPTY_RESPONSE);
        assertType(envelope("{\"category\": 5}"), FailureType.MALFORMED_OUTPUT);
        assertType(envelope("{\"safetyConcerns\": \"one\"}"), FailureType.MALFORMED_OUTPUT);
        assertType(envelope("[1,2]"), FailureType.MALFORMED_OUTPUT);
        assertType(envelope("{\"category\":\"ROAD_DAMAGE\""), FailureType.MALFORMED_OUTPUT);
    }

    @Test
    void classifiesHttpErrors() {
        assertThat(GeminiIntelligenceProvider.classifyHttpError(400, "{\"error\":{\"message\":\"API key not valid. Please pass a valid API key.\"}}").type())
                .isEqualTo(FailureType.AUTH_FAILED);
        assertThat(GeminiIntelligenceProvider.classifyHttpError(403, "").type()).isEqualTo(FailureType.AUTH_FAILED);
        assertThat(GeminiIntelligenceProvider.classifyHttpError(404, "").type()).isEqualTo(FailureType.MODEL_NOT_FOUND);
        assertThat(GeminiIntelligenceProvider.classifyHttpError(429, "").retryable()).isTrue();
        assertThat(GeminiIntelligenceProvider.classifyHttpError(503, "").type()).isEqualTo(FailureType.PROVIDER_ERROR);
        assertThat(GeminiIntelligenceProvider.classifyHttpError(400, "bad").type()).isEqualTo(FailureType.BAD_REQUEST);
        assertThat(GeminiIntelligenceProvider.classifyHttpError(400, "bad").retryable()).isFalse();
    }

    @Test
    void retryDecisionsPerFailureType() {
        assertThat(FailureType.TIMEOUT.retryable()).isTrue();
        assertThat(FailureType.NETWORK.retryable()).isTrue();
        assertThat(FailureType.MALFORMED_OUTPUT.retryable()).isTrue();
        assertThat(FailureType.INVALID_OUTPUT.retryable()).isTrue();
        assertThat(FailureType.NOT_CONFIGURED.retryable()).isFalse();
        assertThat(FailureType.AUTH_FAILED.retryable()).isFalse();
        assertThat(FailureType.BLOCKED.retryable()).isFalse();
    }

    private void assertType(String body, FailureType expected) {
        assertThatThrownBy(() -> GeminiResponseParser.parse(body, mapper))
                .isInstanceOfSatisfying(ProviderException.class, e -> assertThat(e.type()).isEqualTo(expected));
    }
}
