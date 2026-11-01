package com.civiclens.dashboard;

import com.civiclens.common.ApiException;
import com.civiclens.config.AppProperties;
import com.civiclens.incident.IncidentCategory;
import com.civiclens.incident.IncidentStatus;
import com.civiclens.incident.Severity;
import com.civiclens.user.CurrentUser;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Dashboard figures, all computed by aggregate SQL over stored records at request time. Nothing is hardcoded or cached.
 * Department admins are scoped to their department's city; system admins may view any city or all cities.
 */
@Service
public class DashboardService {

    private final NamedParameterJdbcTemplate jdbc;
    private final AppProperties.Workflow workflow;
    private final Clock clock;

    public DashboardService(NamedParameterJdbcTemplate jdbc, AppProperties props, Clock clock) {
        this.jdbc = jdbc;
        this.workflow = props.workflow();
        this.clock = clock;
    }

    public record DepartmentLoad(UUID departmentId, String departmentName, long open, long resolved, long total) {
    }

    public record RecentIncident(UUID id, String title, IncidentStatus status, Severity severity, Instant updatedAt) {
    }

    public record Summary(
            UUID cityId,
            UUID departmentId,
            long totalIncidents,
            long openIncidents,
            Map<String, Long> byStatus,
            Map<String, Long> byCategory,
            Map<String, Long> bySeverity,
            List<DepartmentLoad> byDepartment,
            long unassignedOpen,
            long agingOpen,
            int agingThresholdDays,
            long safetyCriticalOpen,
            Double averageResolutionHours,
            List<RecentIncident> recentlyUpdated,
            Instant generatedAt) {
    }

    public record DepartmentPerformance(UUID departmentId, String departmentName, long assignedTotal, long open,
                                        long resolved, long rejected, Double averageResolutionHours,
                                        double averageRating, long reviewCount, long rewardPoints) {
    }

    /** Applies role scoping and returns the effective city filter (null = all cities, system admin only). */
    public UUID scopeCity(CurrentUser user, UUID requestedCity) {
        if (user.isSystemAdmin()) {
            return requestedCity;
        }
        if (requestedCity != null && !requestedCity.equals(user.departmentCityId())) {
            throw ApiException.forbidden("Department officials can only view their own city's dashboard.");
        }
        return user.departmentCityId();
    }

    @Transactional(readOnly = true)
    public Summary summary(UUID cityId, UUID departmentId) {
        Instant now = clock.instant();
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("cityId", cityId)
                .addValue("departmentId", departmentId)
                .addValue("agingBefore", Timestamp.from(now.minus(Duration.ofDays(workflow.agingThresholdDays()))));
        String scope = " WHERE (CAST(:cityId AS uuid) IS NULL OR i.city_id = :cityId)"
                + " AND (CAST(:departmentId AS uuid) IS NULL OR i.department_id = :departmentId)";
        String open = " AND i.status NOT IN ('RESOLVED', 'REJECTED')";

        Map<String, Object> totals = jdbc.queryForMap("""
                SELECT COUNT(*) AS total,
                       COUNT(*) FILTER (WHERE i.status NOT IN ('RESOLVED','REJECTED')) AS open,
                       COUNT(*) FILTER (WHERE i.status NOT IN ('RESOLVED','REJECTED') AND i.department_id IS NULL) AS unassigned,
                       COUNT(*) FILTER (WHERE i.status NOT IN ('RESOLVED','REJECTED') AND i.created_at < :agingBefore) AS aging,
                       COUNT(*) FILTER (WHERE i.status NOT IN ('RESOLVED','REJECTED') AND i.safety_critical) AS safety,
                       AVG(EXTRACT(EPOCH FROM (i.resolved_at - i.created_at)) / 3600.0) FILTER (WHERE i.status = 'RESOLVED') AS avg_hours
                FROM incidents i""" + scope, p);

        Map<String, Long> byStatus = zeroed(Arrays.stream(IncidentStatus.values()).map(Enum::name).toList());
        byStatus.putAll(groupCount("i.status", scope, p));
        Map<String, Long> byCategory = zeroed(Arrays.stream(IncidentCategory.values()).map(Enum::name).toList());
        byCategory.putAll(groupCount("i.category", scope, p));
        List<String> severities = new java.util.ArrayList<>(Arrays.stream(Severity.values()).map(Enum::name).toList());
        severities.add("UNASSESSED");
        Map<String, Long> bySeverity = zeroed(severities);
        bySeverity.putAll(groupCount("COALESCE(i.severity, 'UNASSESSED')", scope, p));

        List<DepartmentLoad> byDepartment = jdbc.query("""
                SELECT d.id, d.name,
                       COUNT(i.id) FILTER (WHERE i.status NOT IN ('RESOLVED','REJECTED')) AS open,
                       COUNT(i.id) FILTER (WHERE i.status = 'RESOLVED') AS resolved,
                       COUNT(i.id) AS total
                FROM incidents i JOIN departments d ON d.id = i.department_id""" + scope
                + " GROUP BY d.id, d.name ORDER BY open DESC, d.name", p,
                (rs, n) -> new DepartmentLoad(rs.getObject("id", UUID.class), rs.getString("name"), rs.getLong("open"),
                        rs.getLong("resolved"), rs.getLong("total")));

        List<RecentIncident> recent = jdbc.query("SELECT i.id, i.title, i.status, i.severity, i.updated_at FROM incidents i"
                        + scope + " ORDER BY i.updated_at DESC, i.id LIMIT 10", p,
                (rs, n) -> new RecentIncident(rs.getObject("id", UUID.class), rs.getString("title"),
                        IncidentStatus.valueOf(rs.getString("status")),
                        rs.getString("severity") == null ? null : Severity.valueOf(rs.getString("severity")),
                        rs.getTimestamp("updated_at").toInstant()));

        Object avg = totals.get("avg_hours");
        return new Summary(cityId, departmentId, num(totals.get("total")), num(totals.get("open")), byStatus, byCategory,
                bySeverity, byDepartment, num(totals.get("unassigned")), num(totals.get("aging")),
                workflow.agingThresholdDays(), num(totals.get("safety")),
                avg == null ? null : Math.round(((Number) avg).doubleValue() * 10) / 10.0, recent, now);
    }

    @Transactional(readOnly = true)
    public List<DepartmentPerformance> departmentPerformance(UUID cityId) {
        MapSqlParameterSource p = new MapSqlParameterSource("cityId", cityId);
        return jdbc.query("""
                SELECT d.id, d.name,
                       COUNT(i.id) AS total,
                       COUNT(i.id) FILTER (WHERE i.status IN ('ASSIGNED','IN_PROGRESS','REOPENED','UNDER_REVIEW','SUBMITTED')) AS open,
                       COUNT(i.id) FILTER (WHERE i.status = 'RESOLVED') AS resolved,
                       COUNT(i.id) FILTER (WHERE i.status = 'REJECTED') AS rejected,
                       AVG(EXTRACT(EPOCH FROM (i.resolved_at - i.created_at)) / 3600.0) FILTER (WHERE i.status = 'RESOLVED') AS avg_hours,
                       (SELECT COALESCE(AVG(r.rating), 0) FROM department_reviews r WHERE r.department_id = d.id) AS rating,
                       (SELECT COUNT(*) FROM department_reviews r WHERE r.department_id = d.id) AS reviews,
                       (SELECT COALESCE(SUM(t.points), 0) FROM reward_transactions t WHERE t.department_id = d.id) AS points
                FROM departments d LEFT JOIN incidents i ON i.department_id = d.id
                WHERE d.active AND (CAST(:cityId AS uuid) IS NULL OR d.city_id = :cityId)
                GROUP BY d.id, d.name ORDER BY d.name
                """, p, (rs, n) -> {
            Object avg = rs.getObject("avg_hours");
            return new DepartmentPerformance(rs.getObject("id", UUID.class), rs.getString("name"), rs.getLong("total"),
                    rs.getLong("open"), rs.getLong("resolved"), rs.getLong("rejected"),
                    avg == null ? null : Math.round(((Number) avg).doubleValue() * 10) / 10.0,
                    Math.round(rs.getDouble("rating") * 100) / 100.0, rs.getLong("reviews"), rs.getLong("points"));
        });
    }

    private Map<String, Long> groupCount(String expr, String scope, MapSqlParameterSource p) {
        Map<String, Long> out = new LinkedHashMap<>();
        jdbc.query("SELECT " + expr + " AS k, COUNT(*) AS c FROM incidents i" + scope + " GROUP BY " + expr, p,
                rs -> {
                    out.put(rs.getString("k"), rs.getLong("c"));
                });
        return out;
    }

    private static Map<String, Long> zeroed(List<String> keys) {
        Map<String, Long> m = new LinkedHashMap<>();
        keys.forEach(k -> m.put(k, 0L));
        return m;
    }

    private static long num(Object o) {
        return o == null ? 0 : ((Number) o).longValue();
    }
}
