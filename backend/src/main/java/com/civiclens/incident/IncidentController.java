package com.civiclens.incident;

import com.civiclens.ai.AnalysisService;
import com.civiclens.ai.AnalysisSource;
import com.civiclens.ai.AnalysisStatus;
import com.civiclens.ai.AnalysisView;
import com.civiclens.common.ApiException;
import com.civiclens.common.PageParams;
import com.civiclens.common.PageResponse;
import com.civiclens.duplicate.DuplicateDetectionService;
import com.civiclens.duplicate.DuplicateDtos.DuplicateCheckRequest;
import com.civiclens.duplicate.DuplicateDtos.DuplicateCheckResponse;
import com.civiclens.idempotency.IdempotencyService;
import com.civiclens.incident.IncidentDtos.CreateIncidentRequest;
import com.civiclens.incident.IncidentDtos.FeedFilter;
import com.civiclens.incident.IncidentDtos.FeedSort;
import com.civiclens.incident.IncidentDtos.IncidentView;
import com.civiclens.incident.IncidentDtos.TimelineEntry;
import com.civiclens.user.Authz;
import com.civiclens.user.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@Tag(name = "Incidents")
public class IncidentController {

    private final IncidentService incidents;
    private final DuplicateDetectionService duplicates;
    private final AnalysisService analysis;
    private final IdempotencyService idempotency;

    public IncidentController(IncidentService incidents, DuplicateDetectionService duplicates, AnalysisService analysis,
                              IdempotencyService idempotency) {
        this.incidents = incidents;
        this.duplicates = duplicates;
        this.analysis = analysis;
        this.idempotency = idempotency;
    }

    @Operation(summary = "Submit a report. Returns 409 POSSIBLE_DUPLICATE with candidates when strong matches exist. "
            + "AI analysis starts after the report is saved. Supports Idempotency-Key.")
    @PostMapping("/api/v1/incidents")
    public ResponseEntity<?> create(@Valid @RequestBody CreateIncidentRequest request,
                                    @RequestHeader(value = IdempotencyService.HEADER, required = false) String key) {
        CurrentUser reporter = Authz.require();
        return idempotency.execute(reporter.id(), key, "create-incident", request, HttpStatus.CREATED,
                () -> incidents.create(reporter, request, false));
    }

    @Operation(summary = "Feed / search with filters, sorting and pagination (public)")
    @GetMapping("/api/v1/incidents")
    public PageResponse<IncidentView> list(
            @RequestParam(required = false) UUID cityId,
            @RequestParam(required = false) String locality,
            @RequestParam(required = false) IncidentCategory category,
            @RequestParam(required = false) List<IncidentStatus> status,
            @RequestParam(required = false) Severity severity,
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) Boolean unassigned,
            @RequestParam(required = false) Boolean safetyCritical,
            @RequestParam(required = false) Boolean openOnly,
            @RequestParam(required = false) Boolean mine,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Double nearLat,
            @RequestParam(required = false) Double nearLng,
            @RequestParam(required = false) Double radiusMeters,
            @RequestParam(required = false, defaultValue = "PRIORITY") FeedSort sort,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        if (locality != null && locality.length() > 120) {
            throw ApiException.validation("locality", "locality must be at most 120 characters.");
        }
        FeedFilter filter = new FeedFilter(cityId, locality, category, status, severity, departmentId, unassigned,
                safetyCritical, openOnly, mine, q, nearLat, nearLng, radiusMeters, sort);
        return incidents.search(filter, Authz.current().orElse(null), PageParams.page(page), PageParams.size(size, 20));
    }

    @Operation(summary = "Incident details, including the latest analysis and the actions available to the caller")
    @GetMapping("/api/v1/incidents/{incidentId}")
    public IncidentView get(@PathVariable UUID incidentId) {
        return incidents.get(incidentId, Authz.current().orElse(null));
    }

    @Operation(summary = "Public audit timeline: creation, analysis, assignment, status changes")
    @GetMapping("/api/v1/incidents/{incidentId}/history")
    public List<TimelineEntry> history(@PathVariable UUID incidentId) {
        return incidents.timeline(incidentId);
    }

    @Operation(summary = "Find possible duplicates before submitting (no data is stored)")
    @PostMapping("/api/v1/incidents/duplicate-check")
    public DuplicateCheckResponse duplicateCheck(@Valid @RequestBody DuplicateCheckRequest request) {
        return duplicates.check(request);
    }

    @Operation(summary = "Possible duplicates of an existing incident (for officials reviewing reports)")
    @GetMapping("/api/v1/incidents/{incidentId}/duplicates")
    public DuplicateCheckResponse duplicatesOf(@PathVariable UUID incidentId) {
        Incident i = incidents.require(incidentId);
        return duplicates.check(new DuplicateCheckRequest(i.getCityId(), i.getTitle(), i.getDescription(),
                i.getLocality(), i.getLatitude(), i.getLongitude(), i.getCategory()), i.getId());
    }

    @Operation(summary = "Latest analysis run (404 if none)")
    @GetMapping("/api/v1/incidents/{incidentId}/analysis")
    public AnalysisView latestAnalysis(@PathVariable UUID incidentId) {
        incidents.require(incidentId);
        return analysis.latest(incidentId).orElseThrow(() -> ApiException.notFound("Analysis"));
    }

    @Operation(summary = "All analysis runs, newest first")
    @GetMapping("/api/v1/incidents/{incidentId}/analysis/history")
    public List<AnalysisView> analysisHistory(@PathVariable UUID incidentId) {
        incidents.require(incidentId);
        return analysis.history(incidentId);
    }

    @Operation(summary = "Request a new analysis run (reporter: only after failure or fallback; officials: any time). "
            + "Returns 202; poll GET .../analysis.")
    @PostMapping("/api/v1/incidents/{incidentId}/analysis")
    public ResponseEntity<AnalysisView> requestAnalysis(@PathVariable UUID incidentId) {
        CurrentUser user = Authz.require();
        Incident incident = incidents.require(incidentId);
        boolean reporter = incident.getReporterId().equals(user.id());
        boolean officialInScope = user.isSystemAdmin()
                || (user.isDepartmentAdmin() && incident.getCityId().equals(user.departmentCityId()));
        if (!reporter && !officialInScope) {
            throw ApiException.forbidden("Only the reporter or a department official can request analysis.");
        }
        if (!officialInScope) {
            analysis.latest(incidentId).ifPresent(latest -> {
                boolean retryable = latest.status() == AnalysisStatus.FAILED
                        || (latest.status() == AnalysisStatus.COMPLETED && latest.source() == AnalysisSource.RULE_BASED_FALLBACK);
                if (!retryable && !latest.status().isActive()) {
                    throw ApiException.forbidden("This report already has a completed AI analysis.");
                }
            });
        }
        var queued = analysis.queue(incidentId, user.id());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(analysis.toView(queued));
    }
}
