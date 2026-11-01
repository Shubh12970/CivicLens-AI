package com.civiclens.duplicate;

import com.civiclens.city.CityDirectory;
import com.civiclens.config.AppProperties;
import com.civiclens.duplicate.DuplicateDtos.DuplicateCandidate;
import com.civiclens.duplicate.DuplicateDtos.DuplicateCheckRequest;
import com.civiclens.duplicate.DuplicateDtos.DuplicateCheckResponse;
import com.civiclens.incident.IncidentCategory;
import com.civiclens.incident.IncidentStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Module B — deterministic duplicate detection. Finds plausible existing incidents in the same city and explains
 * why each matched. It never merges, hides or discards reports: the citizen (or an official) decides.
 */
@Service
public class DuplicateDetectionService {

    private static final Logger log = LoggerFactory.getLogger(DuplicateDetectionService.class);

    private final NamedParameterJdbcTemplate jdbc;
    private final CityDirectory cityDirectory;
    private final AppProperties.Duplicates config;
    private final DuplicateScorer scorer;
    private final Clock clock;

    public DuplicateDetectionService(NamedParameterJdbcTemplate jdbc, CityDirectory cityDirectory, AppProperties props,
                                     Clock clock) {
        this.jdbc = jdbc;
        this.cityDirectory = cityDirectory;
        this.config = props.duplicates();
        this.scorer = new DuplicateScorer(config.maxDistanceMeters());
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public DuplicateCheckResponse check(DuplicateCheckRequest request) {
        return check(request, null);
    }

    @Transactional(readOnly = true)
    public DuplicateCheckResponse check(DuplicateCheckRequest request, UUID excludeIncidentId) {
        cityDirectory.requireActiveCity(request.cityId());
        if ((request.latitude() == null) != (request.longitude() == null)) {
            throw com.civiclens.common.ApiException.validation("latitude",
                    "latitude and longitude must be provided together.");
        }
        String probeText = TextSimilarity.normalize(joinText(request.title(), request.description()));
        var probe = new DuplicateScorer.Probe(probeText, request.locality(), request.latitude(), request.longitude(),
                request.category());

        List<Row> rows = loadCandidates(request, excludeIncidentId);
        List<Scored> scored = new ArrayList<>();
        for (Row row : rows) {
            var result = scorer.score(probe, new DuplicateScorer.Existing(row.normalizedText, row.locality,
                    row.latitude, row.longitude, row.category, row.status));
            if (result != null && result.score() >= config.minScore()) {
                scored.add(new Scored(row, result));
            }
        }
        scored.sort(Comparator.comparingDouble((Scored s) -> s.result.score()).reversed());
        List<Scored> top = scored.stream().limit(config.maxResults()).toList();

        Map<UUID, Long> support = supportCounts(top.stream().map(s -> s.row.id).toList());
        List<DuplicateCandidate> candidates = top.stream().map(s -> new DuplicateCandidate(
                s.row.id, s.row.title, excerpt(s.row.description), s.row.locality, s.row.category, s.row.status,
                s.row.createdAt, s.result.distanceMeters() == null ? null : (double) Math.round(s.result.distanceMeters()),
                s.result.score(), s.result.score() >= config.strongMatchScore(),
                support.getOrDefault(s.row.id, 0L), s.result.reasons())).toList();

        boolean strong = candidates.stream().anyMatch(DuplicateCandidate::strongMatch);
        log.info("duplicate-check city={} scanned={} matched={} strong={}", request.cityId(), rows.size(),
                candidates.size(), strong);
        return new DuplicateCheckResponse(candidates, strong, config.minScore(), config.strongMatchScore(), clock.instant());
    }

    public static String joinText(String title, String description) {
        return ((title == null ? "" : title) + " " + (description == null ? "" : description)).trim();
    }

    private List<Row> loadCandidates(DuplicateCheckRequest request, UUID excludeId) {
        Instant since = clock.instant().minus(Duration.ofDays(config.windowDays()));
        StringBuilder sql = new StringBuilder("""
                SELECT id, title, description, normalized_text, locality, latitude, longitude, category, status, created_at
                FROM incidents
                WHERE city_id = :cityId AND created_at >= :since AND status <> 'REJECTED' AND duplicate_of_id IS NULL
                """);
        var params = new MapSqlParameterSource()
                .addValue("cityId", request.cityId())
                .addValue("since", Timestamp.from(since))
                .addValue("limit", config.candidateScanLimit());
        if (excludeId != null) {
            sql.append(" AND id <> :excludeId");
            params.addValue("excludeId", excludeId);
        }
        // Prefilter to the neighbourhood when we have location signals; reports without coordinates stay eligible.
        List<String> nearby = new ArrayList<>();
        if (request.latitude() != null) {
            double radius = 3 * config.maxDistanceMeters();
            nearby.add("(latitude BETWEEN :minLat AND :maxLat AND longitude BETWEEN :minLon AND :maxLon)");
            nearby.add("latitude IS NULL");
            double dLat = GeoDistance.latDelta(radius);
            double dLon = GeoDistance.lonDelta(radius, request.latitude());
            params.addValue("minLat", request.latitude() - dLat).addValue("maxLat", request.latitude() + dLat)
                    .addValue("minLon", request.longitude() - dLon).addValue("maxLon", request.longitude() + dLon);
        }
        if (request.locality() != null && !request.locality().isBlank()) {
            nearby.add("lower(locality) = lower(:locality)");
            params.addValue("locality", request.locality().trim());
        }
        if (!nearby.isEmpty()) {
            sql.append(" AND (").append(String.join(" OR ", nearby)).append(")");
        }
        sql.append(" ORDER BY created_at DESC LIMIT :limit");
        return jdbc.query(sql.toString(), params, (rs, i) -> new Row(
                rs.getObject("id", UUID.class), rs.getString("title"), rs.getString("description"),
                rs.getString("normalized_text"), rs.getString("locality"),
                (Double) rs.getObject("latitude"), (Double) rs.getObject("longitude"),
                IncidentCategory.valueOf(rs.getString("category")), IncidentStatus.valueOf(rs.getString("status")),
                rs.getTimestamp("created_at").toInstant()));
    }

    private Map<UUID, Long> supportCounts(List<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return jdbc.query("SELECT incident_id, count(*) AS c FROM votes WHERE value = 1 AND incident_id IN (:ids) GROUP BY incident_id",
                        new MapSqlParameterSource("ids", ids),
                        (rs, i) -> Map.entry(rs.getObject("incident_id", UUID.class), rs.getLong("c")))
                .stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private static String excerpt(String description) {
        return description.length() <= 200 ? description : description.substring(0, 197) + "...";
    }

    private record Row(UUID id, String title, String description, String normalizedText, String locality,
                       Double latitude, Double longitude, IncidentCategory category, IncidentStatus status,
                       Instant createdAt) {
    }

    private record Scored(Row row, DuplicateScorer.Result result) {
    }
}
