package com.civiclens.config;

import com.civiclens.ai.GeminiIntelligenceProvider;
import com.civiclens.ai.IntelligenceProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.Clock;
import java.time.Duration;

@Configuration
@EnableScheduling
public class CoreConfig {

    private static final Logger log = LoggerFactory.getLogger(CoreConfig.class);

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public com.civiclens.workflow.StatusTransitionPolicy statusTransitionPolicy(AppProperties props) {
        return new com.civiclens.workflow.StatusTransitionPolicy(
                Duration.ofDays(props.workflow().reopenWindowDays()));
    }

    @Bean
    public IntelligenceProvider intelligenceProvider(
            AppProperties props, ObjectMapper mapper, Environment environment) {

        String providerName = props.ai() == null ? "gemini" : props.ai().provider();
        if (!"gemini".equalsIgnoreCase(providerName)) {
            throw new IllegalStateException("Unsupported AI_PROVIDER '" + providerName + "'. Supported: gemini");
        }

        AppProperties.Gemini gemini = props.ai() == null ? null : props.ai().gemini();
        if (gemini == null) {
            // Defensive fallback for environments where nested record binding omitted the Gemini object.
            // Values are read from Spring's resolved properties/environment; no secrets are logged.
            String apiKey = environment.getProperty("GEMINI_API_KEY",
                    environment.getProperty("civiclens.ai.gemini.api-key", ""));
            String model = environment.getProperty("GEMINI_MODEL",
                    environment.getProperty("civiclens.ai.gemini.model", "gemini-3.8-flash"));
            String baseUrl = environment.getProperty("GEMINI_BASE_URL",
                    environment.getProperty("civiclens.ai.gemini.base-url",
                            "https://generativelanguage.googleapis.com/v1beta"));
            Duration connectTimeout = parseDuration(environment.getProperty(
                    "civiclens.ai.gemini.connect-timeout", "PT5S"), Duration.ofSeconds(5));
            String timeoutValue = environment.getProperty("GEMINI_TIMEOUT",
                    environment.getProperty("civiclens.ai.gemini.request-timeout", "PT90S"));
            Duration requestTimeout = parseDuration(timeoutValue, Duration.ofSeconds(90));
            String thinkingLevel = environment.getProperty("GEMINI_THINKING_LEVEL",
                    environment.getProperty("civiclens.ai.gemini.thinking-level", "HIGH"));
            int maxOutputTokens = parseInt(environment.getProperty("GEMINI_MAX_OUTPUT_TOKENS",
                    environment.getProperty("civiclens.ai.gemini.max-output-tokens", "8192")), 8192);

            gemini = new AppProperties.Gemini(apiKey, model, baseUrl, connectTimeout,
                    requestTimeout, thinkingLevel, maxOutputTokens);
            log.warn("Gemini settings were not bound as a nested configuration object; loaded resolved properties as a fallback.");
        }

        GeminiIntelligenceProvider provider = new GeminiIntelligenceProvider(gemini, mapper);
        boolean fallbackEnabled = props.ai() != null && props.ai().fallbackEnabled();
        if (provider.isConfigured()) {
            log.info("AI provider: gemini model={}", provider.model());
        } else {
            log.warn("GEMINI_API_KEY not set: reports will use the rule-based fallback (fallbackEnabled={})",
                    fallbackEnabled);
        }
        return provider;
    }

    private static Duration parseDuration(String value, Duration fallback) {
        try {
            return value == null || value.isBlank() ? fallback : Duration.parse(value.trim());
        } catch (RuntimeException ex) {
            return fallback;
        }
    }

    private static int parseInt(String value, int fallback) {
        try {
            return value == null || value.isBlank() ? fallback : Integer.parseInt(value.trim());
        } catch (RuntimeException ex) {
            return fallback;
        }
    }

    /** Bounded pool for analysis work. Rejected tasks stay PENDING and are retried by the sweep. */
    @Bean(name = "analysisExecutor")
    public TaskExecutor analysisExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("analysis-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(25);
        executor.initialize();
        return executor;
    }
}
