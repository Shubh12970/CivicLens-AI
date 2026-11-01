package com.civiclens.reward;

import com.civiclens.common.PageResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Append-only reward ledger. There is no endpoint that grants points; grants are made by workflow services inside
 * their transactions. {@code source_key} is UNIQUE and inserts use ON CONFLICT DO NOTHING, so a qualifying event can
 * pay out at most once even under retries or concurrent requests.
 */
@Service
public class RewardService {

    private static final Logger log = LoggerFactory.getLogger(RewardService.class);

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public RewardService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public record LedgerEntry(UUID id, RewardReason reason, int points, UUID incidentId, Instant createdAt) {
    }

    public record RewardSummary(UUID userId, long balance, PageResponse<LedgerEntry> ledger) {
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean grantToUser(UUID userId, RewardReason reason, UUID incidentId) {
        return insert(userId, null, reason, incidentId, reason.name() + ":user:" + userId + ":incident:" + incidentId);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean grantToDepartment(UUID departmentId, RewardReason reason, UUID incidentId) {
        return insert(null, departmentId, reason, incidentId,
                reason.name() + ":department:" + departmentId + ":incident:" + incidentId);
    }

    private boolean insert(UUID userId, UUID departmentId, RewardReason reason, UUID incidentId, String sourceKey) {
        int rows = jdbc.update("""
                INSERT INTO reward_transactions (id, user_id, department_id, reason, points, incident_id, source_key, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (source_key) DO NOTHING
                """, UUID.randomUUID(), userId, departmentId, reason.name(), reason.points(), incidentId, sourceKey,
                Timestamp.from(clock.instant()));
        if (rows == 1) {
            log.info("reward-granted reason={} points={} userId={} departmentId={} incidentId={}", reason,
                    reason.points(), userId, departmentId, incidentId);
        }
        return rows == 1;
    }

    @Transactional(readOnly = true)
    public long userBalance(UUID userId) {
        Long sum = jdbc.queryForObject("SELECT COALESCE(SUM(points), 0) FROM reward_transactions WHERE user_id = ?",
                Long.class, userId);
        return sum == null ? 0 : sum;
    }

    @Transactional(readOnly = true)
    public long departmentPoints(UUID departmentId) {
        Long sum = jdbc.queryForObject("SELECT COALESCE(SUM(points), 0) FROM reward_transactions WHERE department_id = ?",
                Long.class, departmentId);
        return sum == null ? 0 : sum;
    }

    @Transactional(readOnly = true)
    public RewardSummary summary(UUID userId, int page, int size) {
        long total = jdbc.queryForObject("SELECT count(*) FROM reward_transactions WHERE user_id = ?", Long.class, userId);
        List<LedgerEntry> items = jdbc.query("""
                SELECT id, reason, points, incident_id, created_at FROM reward_transactions
                WHERE user_id = ? ORDER BY created_at DESC, id LIMIT ? OFFSET ?
                """, (rs, i) -> new LedgerEntry(rs.getObject("id", UUID.class), RewardReason.valueOf(rs.getString("reason")),
                rs.getInt("points"), rs.getObject("incident_id", UUID.class), rs.getTimestamp("created_at").toInstant()),
                userId, size, (long) page * size);
        return new RewardSummary(userId, userBalance(userId), PageResponse.of(items, page, size, total));
    }
}
