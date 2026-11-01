package com.civiclens.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/** All CivicLens settings under the {@code civiclens.*} prefix. Values come from application.yml / environment variables. */
@ConfigurationProperties(prefix = "civiclens")
public record AppProperties(
        Security security,
        Cors cors,
        Bootstrap bootstrap,
        Demo demo,
        RateLimit rateLimit,
        Ai ai,
        Duplicates duplicates,
        Workflow workflow) {

    public record Security(String jwtSecret, Duration jwtTtl, String jwtIssuer) {
    }

    public record Cors(List<String> allowedOrigins) {
    }

    public record Bootstrap(String adminEmail, String adminPassword, String adminDisplayName) {
    }

    public record Demo(boolean seedEnabled, String seedPassword) {
    }

    public record RateLimit(boolean enabled, Duration window, int authRequestsPerWindow, int writeRequestsPerWindow) {
    }

    public record Ai(
            String provider,
            Gemini gemini,
            boolean fallbackEnabled,
            int maxAttempts,
            Duration retryBackoff,
            Duration leaseDuration,
            String processingMode,
            boolean schedulerEnabled,
            Duration schedulerInterval) {

        public boolean asyncProcessing() {
            return !"sync".equalsIgnoreCase(processingMode);
        }
    }

    public record Gemini(String apiKey, String model, String baseUrl, Duration connectTimeout, Duration requestTimeout,
                         String thinkingLevel, int maxOutputTokens) {

        public Gemini(String apiKey, String model, String baseUrl, Duration connectTimeout, Duration requestTimeout) {
            this(apiKey, model, baseUrl, connectTimeout, requestTimeout, "HIGH", 8192);
        }

        public Gemini {
            thinkingLevel = thinkingLevel == null || thinkingLevel.isBlank() ? "HIGH" : thinkingLevel.strip().toUpperCase(java.util.Locale.ROOT);
            if (!List.of("LOW", "MEDIUM", "HIGH").contains(thinkingLevel)) {
                throw new IllegalArgumentException("civiclens.ai.gemini.thinking-level must be LOW, MEDIUM or HIGH");
            }
            if (maxOutputTokens < 1024 || maxOutputTokens > 32768) {
                maxOutputTokens = 8192;
            }
        }

        public boolean configured() {
            return apiKey != null && !apiKey.isBlank();
        }
    }

    public record Duplicates(
            int windowDays,
            double maxDistanceMeters,
            int candidateScanLimit,
            double minScore,
            double strongMatchScore,
            int maxResults) {
    }

    public record Workflow(int agingThresholdDays, int reopenWindowDays) {
    }
}
