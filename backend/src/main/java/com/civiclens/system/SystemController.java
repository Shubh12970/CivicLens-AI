package com.civiclens.system;

import com.civiclens.ai.AiStatusTracker;
import com.civiclens.ai.AnalysisPrompt;
import com.civiclens.ai.IntelligenceProvider;
import com.civiclens.ai.ProviderException;
import com.civiclens.community.VoteType;
import com.civiclens.config.AppProperties;
import com.civiclens.incident.IncidentCategory;
import com.civiclens.incident.IncidentDtos.FeedSort;
import com.civiclens.incident.IncidentStatus;
import com.civiclens.incident.Severity;
import com.civiclens.user.Role;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@RestController
@Tag(name = "System")
public class SystemController {

    private final IntelligenceProvider provider;
    private final AiStatusTracker tracker;
    private final AppProperties props;

    public SystemController(IntelligenceProvider provider, AiStatusTracker tracker, AppProperties props) {
        this.provider = provider;
        this.tracker = tracker;
        this.props = props;
    }

    public record Info(String name, String apiVersion, String buildVersion, Instant serverTime) {
    }

    public record AiStatus(String provider, String model, boolean configured, boolean fallbackEnabled,
                           String promptVersion, Instant lastSuccessAt, Instant lastFailureAt, String lastFailureType,
                           String note) {
    }

    @GetMapping("/api/v1/system/info")
    public Info info() {
        return new Info("CivicLens AI", "v1", "1.0.0", Instant.now());
    }

    @Operation(summary = "AI provider configuration and recent outcomes on this instance. Does not call the provider.")
    @GetMapping("/api/v1/system/ai-status")
    public AiStatus aiStatus() {
        return new AiStatus(provider.name(), provider.model(), provider.isConfigured(), props.ai().fallbackEnabled(),
                AnalysisPrompt.VERSION, tracker.lastSuccess(), tracker.lastFailure(), tracker.lastFailureType(),
                "AI availability is separate from application readiness; reports are accepted even when AI is down.");
    }

    @Operation(summary = "All enum values used by the API, for building frontend dropdowns")
    @GetMapping("/api/v1/system/enums")
    public Map<String, List<String>> enums() {
        return Map.of(
                "incidentCategory", names(IncidentCategory.values()),
                "severity", names(Severity.values()),
                "incidentStatus", names(IncidentStatus.values()),
                "role", names(Role.values()),
                "voteType", names(VoteType.values()),
                "analysisStatus", names(com.civiclens.ai.AnalysisStatus.values()),
                "analysisSource", names(com.civiclens.ai.AnalysisSource.values()),
                "fieldSource", names(com.civiclens.incident.FieldSource.values()),
                "feedSort", names(FeedSort.values()),
                "analysisErrorCode", names(ProviderException.FailureType.values()));
    }

    private static List<String> names(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).toList();
    }
}
