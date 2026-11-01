package com.civiclens.idempotency;

import com.civiclens.common.ApiException;
import com.civiclens.common.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Optional {@code Idempotency-Key} support for retry-prone writes. The business operation and the stored response
 * commit in one transaction. A retry with the same key and body replays the stored response; a concurrent duplicate
 * blocks on the primary key, its own transaction rolls back, and it then replays the winner's response.
 * Records are kept for 24 hours.
 */
@Service
public class IdempotencyService {

    public static final String HEADER = "Idempotency-Key";
    private static final Pattern KEY_PATTERN = Pattern.compile("[A-Za-z0-9._:-]{8,100}");
    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final ObjectMapper mapper;
    private final Clock clock;

    public IdempotencyService(JdbcTemplate jdbc, PlatformTransactionManager txManager, ObjectMapper mapper, Clock clock) {
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(txManager);
        this.mapper = mapper;
        this.clock = clock;
    }

    private record Stored(String scope, String hash, int status, String body) {
    }

    public <T> ResponseEntity<?> execute(UUID userId, String key, String scope, Object request, HttpStatus successStatus,
                                         Supplier<T> operation) {
        if (key == null || key.isBlank()) {
            return ResponseEntity.status(successStatus).body(tx.execute(s -> operation.get()));
        }
        if (!KEY_PATTERN.matcher(key).matches()) {
            throw ApiException.validation(HEADER, "Idempotency-Key must be 8-100 characters of A-Z a-z 0-9 . _ : -");
        }
        String hash = hash(scope, request);
        ResponseEntity<?> replay = replayIfPresent(userId, key, scope, hash);
        if (replay != null) {
            return replay;
        }
        try {
            T result = tx.execute(s -> {
                T value = operation.get();
                jdbc.update("""
                        INSERT INTO idempotency_records (user_id, idempotency_key, scope, request_hash, response_status, response_body, created_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """, userId, key, scope, hash, successStatus.value(), toJson(value), Timestamp.from(clock.instant()));
                return value;
            });
            return ResponseEntity.status(successStatus).body(result);
        } catch (DataIntegrityViolationException e) {
            ResponseEntity<?> winner = replayIfPresent(userId, key, scope, hash);
            if (winner != null) {
                log.info("idempotency-concurrent-replay scope={}", scope);
                return winner;
            }
            throw e;
        }
    }

    private ResponseEntity<?> replayIfPresent(UUID userId, String key, String scope, String hash) {
        List<Stored> found = jdbc.query("""
                SELECT scope, request_hash, response_status, response_body FROM idempotency_records
                WHERE user_id = ? AND idempotency_key = ?
                """, (rs, i) -> new Stored(rs.getString(1), rs.getString(2), rs.getInt(3), rs.getString(4)), userId, key);
        if (found.isEmpty()) {
            return null;
        }
        Stored s = found.get(0);
        if (!s.scope().equals(scope) || !s.hash().equals(hash)) {
            throw new ApiException(ErrorCode.IDEMPOTENCY_KEY_REUSED,
                    "This Idempotency-Key was already used for a different request.");
        }
        return ResponseEntity.status(s.status()).contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Replayed", "true").body(s.body());
    }

    private String hash(String scope, Object request) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((scope + "|" + toJson(request)).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private String toJson(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize idempotent response", e);
        }
    }

    @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT5M")
    public void purgeExpired() {
        int removed = jdbc.update("DELETE FROM idempotency_records WHERE created_at < ?",
                Timestamp.from(clock.instant().minus(Duration.ofHours(24))));
        if (removed > 0) {
            log.info("idempotency-purged count={}", removed);
        }
    }
}
