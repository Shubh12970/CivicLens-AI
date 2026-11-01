package com.civiclens.dashboard;

import com.civiclens.common.ApiException;
import com.civiclens.common.PageParams;
import com.civiclens.common.PageResponse;
import com.civiclens.incident.IncidentCategory;
import com.civiclens.incident.IncidentDtos.FeedFilter;
import com.civiclens.incident.IncidentDtos.FeedSort;
import com.civiclens.incident.IncidentDtos.IncidentView;
import com.civiclens.incident.IncidentService;
import com.civiclens.incident.IncidentStatus;
import com.civiclens.incident.Severity;
import com.civiclens.user.Authz;
import com.civiclens.user.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@Tag(name = "Dashboard (officials)")
public class DashboardController {

    private final DashboardService dashboard;
    private final IncidentService incidents;

    public DashboardController(DashboardService dashboard, IncidentService incidents) {
        this.dashboard = dashboard;
        this.incidents = incidents;
    }

    @Operation(summary = "Summary metrics computed from stored incidents")
    @GetMapping("/api/v1/dashboard/summary")
    public DashboardService.Summary summary(@RequestParam(required = false) UUID cityId,
                                            @RequestParam(required = false) UUID departmentId) {
        CurrentUser user = Authz.require();
        return dashboard.summary(dashboard.scopeCity(user, cityId), departmentId);
    }

    @Operation(summary = "Open incidents in safety-first priority order (paginated)")
    @GetMapping("/api/v1/dashboard/triage-queue")
    public PageResponse<IncidentView> triageQueue(@RequestParam(required = false) UUID cityId,
                                                  @RequestParam(required = false) UUID departmentId,
                                                  @RequestParam(required = false) Boolean unassigned,
                                                  @RequestParam(required = false) IncidentCategory category,
                                                  @RequestParam(required = false) Severity severity,
                                                  @RequestParam(required = false) List<IncidentStatus> status,
                                                  @RequestParam(required = false) Integer page,
                                                  @RequestParam(required = false) Integer size) {
        CurrentUser user = Authz.require();
        if (status != null && status.stream().anyMatch(IncidentStatus::isClosed)) {
            throw ApiException.validation("status", "The triage queue only contains open incidents.");
        }
        FeedFilter filter = new FeedFilter(dashboard.scopeCity(user, cityId), null, category, status, severity,
                departmentId, unassigned, null, true, null, null, null, null, null, FeedSort.PRIORITY);
        return incidents.search(filter, user, PageParams.page(page), PageParams.size(size, 20));
    }

    @Operation(summary = "Per-department workload, resolution time, ratings and points")
    @GetMapping("/api/v1/dashboard/department-performance")
    public List<DashboardService.DepartmentPerformance> performance(@RequestParam(required = false) UUID cityId) {
        CurrentUser user = Authz.require();
        return dashboard.departmentPerformance(dashboard.scopeCity(user, cityId));
    }
}
