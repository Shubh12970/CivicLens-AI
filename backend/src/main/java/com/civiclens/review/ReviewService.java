package com.civiclens.review;

import com.civiclens.common.ApiException;
import com.civiclens.common.ErrorCode;
import com.civiclens.common.PageResponse;
import com.civiclens.incident.Incident;
import com.civiclens.incident.IncidentRepository;
import com.civiclens.incident.IncidentStatus;
import com.civiclens.reward.RewardReason;
import com.civiclens.reward.RewardService;
import com.civiclens.user.CurrentUser;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Department reviews. A review is attached to one incident AND the department that resolved it.
 * Eligibility: the caller reported the incident, the incident is RESOLVED, it has an assigned department,
 * and it has not been reviewed yet (UNIQUE incident_id).
 */
@Service
public class ReviewService {

    private static final Logger log = LoggerFactory.getLogger(ReviewService.class);

    private final JdbcTemplate jdbc;
    private final IncidentRepository incidents;
    private final RewardService rewards;
    private final Clock clock;

    public ReviewService(JdbcTemplate jdbc, IncidentRepository incidents, RewardService rewards, Clock clock) {
        this.jdbc = jdbc;
        this.incidents = incidents;
        this.rewards = rewards;
        this.clock = clock;
    }

    public record ReviewRequest(
            @NotNull(message = "rating is required.") @Min(value = 1, message = "rating must be 1-5.")
            @Max(value = 5, message = "rating must be 1-5.") Integer rating,
            @Size(max = 1000, message = "text must be at most 1000 characters.") String text) {
    }

    public record ReviewView(UUID id, UUID incidentId, UUID departmentId, int rating, String text, Instant createdAt) {
    }

    public record RatingSummary(double averageRating, long reviewCount) {
    }

    @Transactional
    public ReviewView create(CurrentUser user, UUID incidentId, ReviewRequest req) {
        Incident incident = incidents.findByIdForUpdate(incidentId).orElseThrow(() -> ApiException.notFound("Incident"));
        if (!incident.getReporterId().equals(user.id())) {
            throw new ApiException(ErrorCode.REVIEW_NOT_ELIGIBLE, "Only the citizen who reported this incident can review it.");
        }
        if (incident.getStatus() != IncidentStatus.RESOLVED) {
            throw new ApiException(ErrorCode.REVIEW_NOT_ELIGIBLE, "Reviews are accepted only after the incident is resolved.");
        }
        if (incident.getDepartmentId() == null) {
            throw new ApiException(ErrorCode.REVIEW_NOT_ELIGIBLE, "This incident was not handled by a department.");
        }
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        String text = req.text() == null || req.text().isBlank() ? null : req.text().strip();
        try {
            jdbc.update("""
                    INSERT INTO department_reviews (id, incident_id, department_id, reviewer_id, rating, review_text, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """, id, incidentId, incident.getDepartmentId(), user.id(), req.rating(), text, Timestamp.from(now));
        } catch (DuplicateKeyException e) {
            throw new ApiException(ErrorCode.ALREADY_REVIEWED, "This incident has already been reviewed.");
        }
        rewards.grantToUser(user.id(), RewardReason.REVIEW_SUBMITTED, incidentId);
        if (req.rating() >= 4) {
            rewards.grantToDepartment(incident.getDepartmentId(), RewardReason.DEPARTMENT_POSITIVE_REVIEW, incidentId);
        }
        log.info("review-created incidentId={} departmentId={} rating={}", incidentId, incident.getDepartmentId(), req.rating());
        return new ReviewView(id, incidentId, incident.getDepartmentId(), req.rating(), text, now);
    }

    @Transactional(readOnly = true)
    public PageResponse<ReviewView> forDepartment(UUID departmentId, int page, int size) {
        Long total = jdbc.queryForObject("SELECT count(*) FROM department_reviews WHERE department_id = ?", Long.class, departmentId);
        List<ReviewView> items = jdbc.query("""
                SELECT id, incident_id, department_id, rating, review_text, created_at FROM department_reviews
                WHERE department_id = ? ORDER BY created_at DESC, id LIMIT ? OFFSET ?
                """, (rs, i) -> new ReviewView(rs.getObject("id", UUID.class), rs.getObject("incident_id", UUID.class),
                rs.getObject("department_id", UUID.class), rs.getInt("rating"), rs.getString("review_text"),
                rs.getTimestamp("created_at").toInstant()), departmentId, size, (long) page * size);
        return PageResponse.of(items, page, size, total == null ? 0 : total);
    }

    @Transactional(readOnly = true)
    public RatingSummary summary(UUID departmentId) {
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT COALESCE(AVG(rating), 0) AS avg, COUNT(*) AS cnt FROM department_reviews WHERE department_id = ?",
                departmentId);
        double avg = ((Number) row.get("avg")).doubleValue();
        return new RatingSummary(Math.round(avg * 100) / 100.0, ((Number) row.get("cnt")).longValue());
    }

    @Transactional(readOnly = true)
    public ReviewView forIncident(UUID incidentId) {
        List<ReviewView> found = jdbc.query("""
                SELECT id, incident_id, department_id, rating, review_text, created_at FROM department_reviews WHERE incident_id = ?
                """, (rs, i) -> new ReviewView(rs.getObject("id", UUID.class), rs.getObject("incident_id", UUID.class),
                rs.getObject("department_id", UUID.class), rs.getInt("rating"), rs.getString("review_text"),
                rs.getTimestamp("created_at").toInstant()), incidentId);
        return found.isEmpty() ? null : found.get(0);
    }
}
