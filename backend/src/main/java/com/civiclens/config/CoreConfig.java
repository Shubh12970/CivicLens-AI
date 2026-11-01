package com.civiclens.config;

import com.civiclens.ai.GeminiIntelligenceProvider;
import com.civiclens.ai.IntelligenceProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.Clock;

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
                java.time.Duration.ofDays(props.workflow().reopenWindowDays()));
    }

    @Bean
    public IntelligenceProvider intelligenceProvider(AppProperties props, ObjectMapper mapper) {
        if (!"gemini".equalsIgnoreCase(props.ai().provider())) {
            throw new IllegalStateException("Unsupported AI_PROVIDER '" + props.ai().provider() + "'. Supported: gemini");
        }
        GeminiIntelligenceProvider provider = new GeminiIntelligenceProvider(props.ai().gemini(), mapper);
        if (provider.isConfigured()) {
            log.info("AI provider: gemini model={}", provider.model());
        } else {
            log.warn("GEMINI_API_KEY not set: reports will be analysed by the rule-based fallback (fallbackEnabled={})",
                    props.ai().fallbackEnabled());
        }
        return provider;
    }

    /** Bounded pool for analysis work. Rejected tasks are fine: the run stays PENDING and the sweep retries it. */
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
