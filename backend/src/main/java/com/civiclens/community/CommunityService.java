package com.civiclens.community;

import com.civiclens.common.ApiException;
import com.civiclens.common.ErrorCode;
import com.civiclens.common.PageResponse;
import com.civiclens.incident.Incident;
import com.civiclens.incident.IncidentRepository;
import com.civiclens.incident.IncidentStatus;
import com.civiclens.user.CurrentUser;
import com.civiclens.user.Role;
import com.civiclens.user.User;
import com.civiclens.user.UserRepository;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Votes and comments. Vote totals are always derived from the votes table; clients never send totals. */
@Service
public class CommunityService {

    private static final Logger log = LoggerFactory.getLogger(CommunityService.class);

    private final JdbcTemplate jdbc;
    private final IncidentRepository incidents;
    private final CommentRepository comments;
    private final UserRepository users;
    private final Clock clock;

    public CommunityService(JdbcTemplate jdbc, IncidentRepository incidents, CommentRepository comments,
                            UserRepository users, Clock clock) {
        this.jdbc = jdbc;
        this.incidents = incidents;
        this.comments = comments;
        this.users = users;
        this.clock = clock;
    }

    public record VoteRequest(@NotNull(message = "type is required (UP or DOWN).") VoteType type) {
    }

    public record VoteSummary(UUID incidentId, long supportCount, long opposeCount, long score, VoteType myVote) {
    }

    public record CommentRequest(
            @NotBlank(message = "body is required.")
            @Size(min = 1, max = 2000, message = "body must be 1-2000 characters.") String body) {
    }

    public record CommentView(UUID id, UUID incidentId, String authorDisplayName, Role authorRole, boolean official,
                              String body, boolean deleted, boolean mine, Instant createdAt) {
    }

    /**
     * Idempotent upsert: one row per (incident, user) enforced by the primary key. Repeating the request, or sending
     * it concurrently, leaves exactly one vote.
     */
    @Transactional
    public VoteSummary vote(CurrentUser user, UUID incidentId, VoteType type) {
        Incident incident = requireIncident(incidentId);
        if (incident.getReporterId().equals(user.id())) {
            throw ApiException.forbidden("You cannot vote on your own report.");
        }
        if (incident.getStatus() == IncidentStatus.REJECTED) {
            throw new ApiException(ErrorCode.CONFLICT, "Rejected reports cannot be voted on.");
        }
        Timestamp now = Timestamp.from(clock.instant());
        jdbc.update("""
                INSERT INTO votes (incident_id, user_id, value, created_at, updated_at) VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (incident_id, user_id) DO UPDATE SET value = EXCLUDED.value, updated_at = EXCLUDED.updated_at
                """, incidentId, user.id(), type.value(), now, now);
        log.info("vote-recorded incidentId={} type={}", incidentId, type);
        return summary(incidentId, user.id());
    }

    @Transactional
    public VoteSummary removeVote(CurrentUser user, UUID incidentId) {
        requireIncident(incidentId);
        jdbc.update("DELETE FROM votes WHERE incident_id = ? AND user_id = ?", incidentId, user.id());
        return summary(incidentId, user.id());
    }

    @Transactional(readOnly = true)
    public VoteSummary summary(UUID incidentId, UUID viewerId) {
        requireIncident(incidentId);
        Map<String, Object> row = jdbc.queryForMap("""
                SELECT COALESCE(SUM(CASE WHEN value = 1 THEN 1 ELSE 0 END), 0) AS up,
                       COALESCE(SUM(CASE WHEN value = -1 THEN 1 ELSE 0 END), 0) AS down
                FROM votes WHERE incident_id = ?
                """, incidentId);
        long up = ((Number) row.get("up")).longValue();
        long down = ((Number) row.get("down")).longValue();
        VoteType mine = null;
        if (viewerId != null) {
            List<Integer> v = jdbc.queryForList("SELECT value FROM votes WHERE incident_id = ? AND user_id = ?",
                    Integer.class, incidentId, viewerId);
            mine = v.isEmpty() ? null : VoteType.fromValue(v.get(0));
        }
        return new VoteSummary(incidentId, up, down, up - down, mine);
    }

    @Transactional
    public CommentView addComment(CurrentUser user, UUID incidentId, String body) {
        requireIncident(incidentId);
        String text = body.strip();
        if (text.isEmpty()) {
            throw ApiException.validation("body", "body is required.");
        }
        Comment comment = comments.save(new Comment(incidentId, user.id(), text, clock.instant()));
        log.info("comment-added incidentId={} commentId={}", incidentId, comment.getId());
        User author = users.findById(user.id()).orElseThrow();
        return toView(comment, author, user.id());
    }

    @Transactional(readOnly = true)
    public PageResponse<CommentView> listComments(UUID incidentId, int page, int size, UUID viewerId) {
        requireIncident(incidentId);
        Page<Comment> result = comments.findByIncidentIdOrderByCreatedAtAscIdAsc(incidentId, PageRequest.of(page, size));
        List<UUID> authorIds = result.getContent().stream().map(Comment::getAuthorId).distinct().toList();
        Map<UUID, User> authors = users.findAllById(authorIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        return PageResponse.of(result, c -> toView(c, authors.get(c.getAuthorId()), viewerId));
    }

    /** Author or SYSTEM_ADMIN only. Soft delete. */
    @Transactional
    public void deleteComment(CurrentUser user, UUID commentId) {
        Comment comment = comments.findById(commentId).orElseThrow(() -> ApiException.notFound("Comment"));
        if (!comment.getAuthorId().equals(user.id()) && !user.isSystemAdmin()) {
            throw ApiException.forbidden("You can only delete your own comments.");
        }
        if (comment.getModerationStatus() == Comment.Moderation.VISIBLE) {
            comment.delete(clock.instant());
            log.info("comment-deleted commentId={} byAdmin={}", commentId, !comment.getAuthorId().equals(user.id()));
        }
    }

    private CommentView toView(Comment c, User author, UUID viewerId) {
        boolean visible = c.getModerationStatus() == Comment.Moderation.VISIBLE;
        String name = author == null ? "Unknown" : author.getDisplayName();
        Role role = author == null ? Role.CITIZEN : author.getRole();
        return new CommentView(c.getId(), c.getIncidentId(), name, role, role.isOfficial(),
                visible ? c.getBody() : "[deleted]", !visible, c.getAuthorId().equals(viewerId), c.getCreatedAt());
    }

    private Incident requireIncident(UUID id) {
        return incidents.findById(id).orElseThrow(() -> ApiException.notFound("Incident"));
    }
}
