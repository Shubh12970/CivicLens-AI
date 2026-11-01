package com.civiclens.incident;

import com.civiclens.common.ApiException;
import com.civiclens.incident.IncidentDtos.FeedFilter;
import com.civiclens.incident.IncidentDtos.FeedSort;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Feed / search SQL. All filters are bind parameters; sort keys come from a fixed enum, never from client strings.
 * Vote and comment totals are computed per row with LATERAL subqueries (no stored counters to drift).
 */
@Service
public class IncidentQueryService {

    public static final double MAX_RADIUS_METERS = 50_000;

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    public IncidentQueryService(NamedParameterJdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public record Stats(long up, long down, long comments, double priority, double trending, Double distanceMeters) {
    }

    public record SearchResult(List<UUID> ids, Map<UUID, Stats> stats, long total) {
    }

    private static final String STATS_JOINS = """
             LEFT JOIN LATERAL (SELECT COUNT(*) FILTER (WHERE v.value = 1) AS up, COUNT(*) FILTER (WHERE v.value = -1) AS down
                                FROM votes v WHERE v.incident_id = i.id) s ON TRUE
             LEFT JOIN LATERAL (SELECT COUNT(*) AS cnt FROM comments cm
                                WHERE cm.incident_id = i.id AND cm.moderation_status = 'VISIBLE') c ON TRUE
            """;

    private static final String DISTANCE_SQL = "(2 * 6371008.8 * ASIN(LEAST(1, SQRT(POWER(SIN(RADIANS(i.latitude - :nearLat) / 2), 2)"
            + " + COS(RADIANS(:nearLat)) * COS(RADIANS(i.latitude)) * POWER(SIN(RADIANS(i.longitude - :nearLng) / 2), 2)))))";

    @Transactional(readOnly = true)
    public SearchResult search(FeedFilter f, UUID viewerId, int page, int size) {
        MapSqlParameterSource p = new MapSqlParameterSource().addValue("now", Timestamp.from(clock.instant()));
        List<String> where = new ArrayList<>();
        boolean near = f.nearLat() != null || f.nearLng() != null;
        if (near) {
            if (f.nearLat() == null || f.nearLng() == null) {
                throw ApiException.validation("nearLat", "nearLat and nearLng must be provided together.");
            }
            if (Math.abs(f.nearLat()) > 90 || Math.abs(f.nearLng()) > 180) {
                throw ApiException.validation("nearLat", "Coordinates are out of range.");
            }
            double radius = f.radiusMeters() == null ? 2_000 : f.radiusMeters();
            if (radius <= 0 || radius > MAX_RADIUS_METERS) {
                throw ApiException.validation("radiusMeters", "radiusMeters must be between 1 and " + (int) MAX_RADIUS_METERS + ".");
            }
            p.addValue("nearLat", f.nearLat()).addValue("nearLng", f.nearLng()).addValue("radius", radius);
            where.add("i.latitude IS NOT NULL AND " + DISTANCE_SQL + " <= :radius");
        } else if (f.sort() == FeedSort.NEAREST) {
            throw ApiException.validation("sort", "sort=NEAREST requires nearLat and nearLng.");
        }
        if (f.cityId() != null) {
            where.add("i.city_id = :cityId");
            p.addValue("cityId", f.cityId());
        }
        if (f.locality() != null && !f.locality().isBlank()) {
            where.add("i.locality ILIKE :locality ESCAPE '\\'");
            p.addValue("locality", "%" + escapeLike(f.locality().strip()) + "%");
        }
        if (f.category() != null) {
            where.add("i.category = :category");
            p.addValue("category", f.category().name());
        }
        if (f.statuses() != null && !f.statuses().isEmpty()) {
            where.add("i.status IN (:statuses)");
            p.addValue("statuses", f.statuses().stream().map(Enum::name).toList());
        }
        if (Boolean.TRUE.equals(f.openOnly())) {
            where.add("i.status NOT IN ('RESOLVED', 'REJECTED')");
        }
        if (f.severity() != null) {
            where.add("i.severity = :severity");
            p.addValue("severity", f.severity().name());
        }
        if (f.departmentId() != null) {
            where.add("i.department_id = :departmentId");
            p.addValue("departmentId", f.departmentId());
        }
        if (Boolean.TRUE.equals(f.unassigned())) {
            where.add("i.department_id IS NULL");
        }
        if (f.safetyCritical() != null) {
            where.add("i.safety_critical = :safety");
            p.addValue("safety", f.safetyCritical());
        }
        if (Boolean.TRUE.equals(f.mine())) {
            if (viewerId == null) {
                throw new ApiException(com.civiclens.common.ErrorCode.UNAUTHORIZED, "mine=true requires authentication.");
            }
            where.add("i.reporter_id = :viewer");
            p.addValue("viewer", viewerId);
        }
        if (f.q() != null && !f.q().isBlank()) {
            if (f.q().length() > 100) {
                throw ApiException.validation("q", "q must be at most 100 characters.");
            }
            where.add("(i.title ILIKE :q ESCAPE '\\' OR i.description ILIKE :q ESCAPE '\\')");
            p.addValue("q", "%" + escapeLike(f.q().strip()) + "%");
        }
        String whereSql = where.isEmpty() ? "" : " WHERE " + String.join(" AND ", where);

        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM incidents i" + whereSql, p, Long.class);

        String closedFirst = "(i.status IN ('RESOLVED', 'REJECTED'))";
        String order = switch (f.sort() == null ? FeedSort.PRIORITY : f.sort()) {
            case PRIORITY -> closedFirst + " ASC, priority DESC, i.created_at DESC";
            case NEWEST -> "i.created_at DESC";
            case OLDEST -> "i.created_at ASC";
            case TRENDING -> closedFirst + " ASC, trending DESC, i.created_at DESC";
            case MOST_SUPPORTED -> "COALESCE(s.up, 0) DESC, i.created_at DESC";
            case RECENTLY_UPDATED -> "i.updated_at DESC";
            case NEAREST -> "distance ASC";
        };
        p.addValue("limit", size).addValue("offset", (long) page * size);
        String sql = "SELECT i.id, " + PriorityPolicy.prioritySql() + " AS priority, " + PriorityPolicy.trendingSql()
                + " AS trending, COALESCE(s.up, 0) AS up, COALESCE(s.down, 0) AS down, COALESCE(c.cnt, 0) AS cnt, "
                + (near ? DISTANCE_SQL : "CAST(NULL AS DOUBLE PRECISION)") + " AS distance"
                + " FROM incidents i" + STATS_JOINS + whereSql
                + " ORDER BY " + order + ", i.id LIMIT :limit OFFSET :offset";
        Map<UUID, Stats> stats = new LinkedHashMap<>();
        jdbc.query(sql, p, rs -> {
            stats.put(rs.getObject("id", UUID.class), new Stats(rs.getLong("up"), rs.getLong("down"), rs.getLong("cnt"),
                    round(rs.getDouble("priority")), round(rs.getDouble("trending")),
                    rs.getObject("distance") == null ? null : (double) Math.round(rs.getDouble("distance"))));
        });
        return new SearchResult(new ArrayList<>(stats.keySet()), stats, total == null ? 0 : total);
    }

    @Transactional(readOnly = true)
    public Map<UUID, Stats> statsFor(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        MapSqlParameterSource p = new MapSqlParameterSource().addValue("now", Timestamp.from(clock.instant()))
                .addValue("ids", ids);
        String sql = "SELECT i.id, " + PriorityPolicy.prioritySql() + " AS priority, " + PriorityPolicy.trendingSql()
                + " AS trending, COALESCE(s.up, 0) AS up, COALESCE(s.down, 0) AS down, COALESCE(c.cnt, 0) AS cnt"
                + " FROM incidents i" + STATS_JOINS + " WHERE i.id IN (:ids)";
        Map<UUID, Stats> stats = new LinkedHashMap<>();
        jdbc.query(sql, p, rs -> {
            stats.put(rs.getObject("id", UUID.class), new Stats(rs.getLong("up"), rs.getLong("down"), rs.getLong("cnt"),
                    round(rs.getDouble("priority")), round(rs.getDouble("trending")), null));
        });
        return stats;
    }

    @Transactional(readOnly = true)
    public Map<UUID, Integer> votesBy(UUID viewerId, Collection<UUID> ids) {
        if (viewerId == null || ids.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Integer> votes = new LinkedHashMap<>();
        jdbc.query("SELECT incident_id, value FROM votes WHERE user_id = :viewer AND incident_id IN (:ids)",
                new MapSqlParameterSource("viewer", viewerId).addValue("ids", ids),
                rs -> {
                    votes.put(rs.getObject("incident_id", UUID.class), rs.getInt("value"));
                });
        return votes;
    }

    private static double round(double v) {
        return Math.round(v * 1000) / 1000.0;
    }

    static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
