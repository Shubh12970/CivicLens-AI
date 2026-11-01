package com.civiclens.city;

import com.civiclens.incident.IncidentCategory;
import com.civiclens.review.ReviewService;
import com.civiclens.reward.RewardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@Tag(name = "Cities & departments")
public class CityController {

    /** Shown with every department so no client mistakes an internal queue for a live government integration. */
    public static final String INTEGRATION_STATUS = "INTERNAL_TRIAGE_QUEUE";

    private final CityDirectory directory;
    private final ReviewService reviews;
    private final RewardService rewards;
    private final JdbcTemplate jdbc;

    public CityController(CityDirectory directory, ReviewService reviews, RewardService rewards, JdbcTemplate jdbc) {
        this.directory = directory;
        this.reviews = reviews;
        this.rewards = rewards;
        this.jdbc = jdbc;
    }

    public record CityView(UUID id, String code, String name, String state, String country, Double centerLatitude,
                           Double centerLongitude, int departmentCount, List<IncidentCategory> unmappedCategories) {
    }

    public record DepartmentView(UUID id, UUID cityId, String code, String name, String description,
                                 List<IncidentCategory> categories, String integrationStatus) {
    }

    public record DepartmentDetail(UUID id, UUID cityId, String cityName, String code, String name, String description,
                                   List<IncidentCategory> categories, String integrationStatus, double averageRating,
                                   long reviewCount, long rewardPoints, long resolvedCount, long openAssignedCount) {
    }

    @Operation(summary = "Supported cities. unmappedCategories go to manual triage in that city.")
    @GetMapping("/api/v1/cities")
    @Transactional(readOnly = true)
    public List<CityView> cities() {
        return directory.activeCities().stream().map(this::toView).toList();
    }

    @GetMapping("/api/v1/cities/{cityId}")
    @Transactional(readOnly = true)
    public CityView city(@PathVariable UUID cityId) {
        return toView(directory.requireActiveCity(cityId));
    }

    @Operation(summary = "Departments (internal triage queues) of a city with the categories each handles")
    @GetMapping("/api/v1/cities/{cityId}/departments")
    @Transactional(readOnly = true)
    public List<DepartmentView> departments(@PathVariable UUID cityId) {
        directory.requireActiveCity(cityId);
        Map<UUID, List<IncidentCategory>> handled = directory.categoriesByDepartment(cityId);
        return directory.departmentsOf(cityId).stream()
                .map(d -> new DepartmentView(d.getId(), d.getCityId(), d.getCode(), d.getName(), d.getDescription(),
                        handled.getOrDefault(d.getId(), List.of()), INTEGRATION_STATUS))
                .toList();
    }

    @Operation(summary = "Department details with public performance figures computed from stored records")
    @GetMapping("/api/v1/departments/{departmentId}")
    @Transactional(readOnly = true)
    public DepartmentDetail department(@PathVariable UUID departmentId) {
        Department d = directory.requireDepartment(departmentId);
        String cityName = directory.findCity(d.getCityId()).map(City::getName).orElse(null);
        var rating = reviews.summary(departmentId);
        Map<String, Object> counts = jdbc.queryForMap("""
                SELECT COUNT(*) FILTER (WHERE status = 'RESOLVED') AS resolved,
                       COUNT(*) FILTER (WHERE status IN ('ASSIGNED', 'IN_PROGRESS')) AS open_assigned
                FROM incidents WHERE department_id = ?
                """, departmentId);
        return new DepartmentDetail(d.getId(), d.getCityId(), cityName, d.getCode(), d.getName(), d.getDescription(),
                directory.categoriesByDepartment(d.getCityId()).getOrDefault(d.getId(), List.of()), INTEGRATION_STATUS,
                rating.averageRating(), rating.reviewCount(), rewards.departmentPoints(departmentId),
                ((Number) counts.get("resolved")).longValue(), ((Number) counts.get("open_assigned")).longValue());
    }

    private CityView toView(City c) {
        var routing = directory.routingTable(c.getId());
        List<IncidentCategory> unmapped = Arrays.stream(IncidentCategory.values())
                .filter(cat -> !routing.containsKey(cat)).toList();
        return new CityView(c.getId(), c.getCode(), c.getName(), c.getState(), c.getCountry(), c.getCenterLatitude(),
                c.getCenterLongitude(), directory.departmentsOf(c.getId()).size(), unmapped);
    }
}
