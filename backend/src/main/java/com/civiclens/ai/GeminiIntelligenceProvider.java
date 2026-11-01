package com.civiclens.ai;

import com.civiclens.ai.AnalysisModels.AnalysisInput;
import com.civiclens.ai.AnalysisModels.ProviderResult;
import com.civiclens.ai.ProviderException.FailureType;
import com.civiclens.config.AppProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;

/**
 * Gemini REST client for {@code models/{model}:generateContent} with structured JSON output
 * and configurable Gemini 3 thinking depth.
 * The API key is sent in the {@code x-goog-api-key} header, never in the URL, and is never logged.
 * One call per attempt: retries and back-off are handled by {@link AnalysisService} so they are persistent and bounded.
 */
public class GeminiIntelligenceProvider implements IntelligenceProvider {

    private final AppProperties.Gemini config;
    private final ObjectMapper mapper;
    private final HttpClient http;

    public GeminiIntelligenceProvider(AppProperties.Gemini config, ObjectMapper mapper) {
        this.config = config;
        this.mapper = mapper;
        this.http = HttpClient.newBuilder()
                .connectTimeout(config.connectTimeout() == null ? Duration.ofSeconds(5) : config.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Override
    public String name() {
        return "gemini";
    }

    @Override
    public String model() {
        return config.model();
    }

    @Override
    public boolean isConfigured() {
        return config.configured() && config.model() != null && !config.model().isBlank();
    }

    @Override
    public ProviderResult analyze(AnalysisInput input) throws ProviderException {
        if (!isConfigured()) {
            throw new ProviderException(FailureType.NOT_CONFIGURED, "GEMINI_API_KEY is not set");
        }
        String url = stripTrailingSlash(config.baseUrl()) + "/models/"
                + URLEncoder.encode(config.model(), StandardCharsets.UTF_8) + ":generateContent";
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(config.requestTimeout() == null ? Duration.ofSeconds(20) : config.requestTimeout())
                    .header("Content-Type", "application/json")
                    .header("x-goog-api-key", config.apiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(buildBody(input)))
                    .build();
        } catch (IllegalArgumentException e) {
            throw new ProviderException(FailureType.BAD_REQUEST, "Invalid Gemini base URL configuration");
        }

        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (HttpTimeoutException e) {
            throw new ProviderException(FailureType.TIMEOUT, "Gemini request timed out");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ProviderException(FailureType.NETWORK, "Gemini request interrupted");
        } catch (IOException e) {
            throw new ProviderException(FailureType.NETWORK, "Network error calling Gemini: " + e.getClass().getSimpleName());
        }

        int status = response.statusCode();
        if (status != 200) {
            throw classifyHttpError(status, response.body());
        }
        return new ProviderResult(GeminiResponseParser.parse(response.body(), mapper), name(), config.model());
    }

    static ProviderException classifyHttpError(int status, String body) {
        String lower = body == null ? "" : body.toLowerCase(Locale.ROOT);
        if (status == 401 || status == 403 || lower.contains("api_key_invalid") || lower.contains("api key not valid")) {
            return new ProviderException(FailureType.AUTH_FAILED, "Gemini rejected the API key (HTTP " + status + ")");
        }
        if (status == 404) {
            return new ProviderException(FailureType.MODEL_NOT_FOUND, "Gemini model not found; check GEMINI_MODEL");
        }
        if (status == 429) {
            return new ProviderException(FailureType.RATE_LIMITED, "Gemini rate limit or quota exceeded");
        }
        if (status >= 500) {
            return new ProviderException(FailureType.PROVIDER_ERROR, "Gemini server error (HTTP " + status + ")");
        }
        return new ProviderException(FailureType.BAD_REQUEST, "Gemini rejected the request (HTTP " + status + ")");
    }

    String buildBody(AnalysisInput input) {
        ObjectNode body = mapper.createObjectNode();
        body.putObject("systemInstruction").putArray("parts").addObject().put("text", AnalysisPrompt.SYSTEM_INSTRUCTION);
        ObjectNode content = body.putArray("contents").addObject();
        content.put("role", "user");
        content.putArray("parts").addObject().put("text", AnalysisPrompt.userPrompt(input));
        ObjectNode generation = body.putObject("generationConfig");
        // Gemini 3.8 Flash supports explicit reasoning depth. Private thoughts are not requested,
        // logged, stored, or exposed; only the validated structured result is returned to CivicLens.
        generation.putObject("thinkingConfig").put("thinkingLevel", config.thinkingLevel());
        generation.put("maxOutputTokens", config.maxOutputTokens());
        ObjectNode textFormat = generation.putObject("responseFormat").putObject("text");
        textFormat.put("mimeType", "APPLICATION_JSON");
        textFormat.set("schema", AnalysisPrompt.responseSchema(mapper));
        try {
            return mapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String stripTrailingSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
