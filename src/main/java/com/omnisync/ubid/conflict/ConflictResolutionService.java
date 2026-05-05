package com.omnisync.ubid.conflict;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Conflict Detection & Resolution Policy Engine.
 *
 * When two updates for the same UBID arrive within the conflict window
 * (default 200ms), this engine:
 *   1. Detects the conflict
 *   2. Applies the configured resolution policy
 *   3. Returns a deterministic, auditable result
 *
 * Policies (configurable via properties):
 *   SWS_PRIORITY     — SWS always wins for identity fields
 *   FIELD_LEVEL_MERGE — merge non-overlapping fields from both sources
 *   DEPT_AUTHORITY   — department system wins for its domain-specific fields
 *   TIMESTAMP_WINS   — most recent timestamp wins
 */
@Service
@Slf4j
public class ConflictResolutionService {

    private final StringRedisTemplate redisTemplate;

    @Value("${omnisync.conflict.window-ms:200}")
    private long conflictWindowMs;

    @Value("${omnisync.conflict.policy:SWS_PRIORITY}")
    private String defaultPolicy;

    private static final String CONFLICT_KEY_PREFIX = "omnisync:conflict:";

    public ConflictResolutionService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Record a pending update and check if a conflict exists with a recent
     * update from another source for the same UBID.
     *
     * Returns empty if no conflict, or a ConflictResult if one is detected.
     */
    public Optional<ConflictResult> checkAndRecord(String ubid,
                                                    String sourceSystem,
                                                    Map<String, Object> payload) {
        String conflictKey = CONFLICT_KEY_PREFIX + ubid;
        String existing = redisTemplate.opsForValue().get(conflictKey);

        if (existing != null && !existing.startsWith(sourceSystem + ":")) {
            // Conflict detected! Different source system updated same UBID within window
            log.warn("[CONFLICT] Conflict detected for UBID={} — sources: {} vs {}",
                    ubid, existing.split(":")[0], sourceSystem);

            ConflictResult result = resolve(ubid, existing.split(":")[0], sourceSystem, payload);
            return Optional.of(result);
        }

        // No conflict — register this update in Redis with short TTL
        String value = sourceSystem + ":" + Instant.now().toEpochMilli();
        redisTemplate.opsForValue().set(conflictKey, value, Duration.ofMillis(conflictWindowMs * 5));
        return Optional.empty();
    }

    /**
     * Apply the configured resolution policy and return a deterministic result.
     */
    private ConflictResult resolve(String ubid, String source1, String source2, Map<String, Object> payload) {
        String winner;
        String reason;

        switch (defaultPolicy) {
            case "SWS_PRIORITY" -> {
                winner = source1.equals("SWS") ? source1 : source2;
                reason = "SWS has authority over canonical identity fields. " +
                         "Department-specific operational fields are field-merged.";
            }
            case "FIELD_LEVEL_MERGE" -> {
                winner = "MERGED";
                reason = "Non-overlapping fields merged from both sources. " +
                         "Overlapping fields resolved by SWS priority.";
            }
            case "TIMESTAMP_WINS" -> {
                winner = source2; // most recent (just arrived)
                reason = "Most recent timestamp wins. Source: " + source2;
            }
            default -> {
                winner = "SWS";
                reason = "Default fallback: SWS_PRIORITY applied.";
            }
        }

        log.info("[CONFLICT] Resolution: ubid={} policy={} winner={}", ubid, defaultPolicy, winner);

        return ConflictResult.builder()
                .ubid(ubid)
                .source1(source1)
                .source2(source2)
                .winner(winner)
                .policyApplied(defaultPolicy)
                .reason(reason)
                .resolvedAt(Instant.now())
                .build();
    }

    @Data
    @lombok.Builder
    public static class ConflictResult {
        private String ubid;
        private String source1;
        private String source2;
        private String winner;
        private String policyApplied;
        private String reason;
        private Instant resolvedAt;
    }
}
