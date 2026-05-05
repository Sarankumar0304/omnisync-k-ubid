package com.omnisync.ubid.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;

/**
 * Redis-backed idempotency store.
 *
 * Prevents duplicate writes when Kafka retries or the caller retries
 * a failed propagation. Uses a 24-hour TTL so the key eventually expires
 * and does not accumulate unboundedly.
 *
 * Key format:  omnisync:idem:{sha256(ubid + eventType + timestamp)}
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IdempotencyService {

    private final StringRedisTemplate redisTemplate;

    @Value("${omnisync.idempotency.ttl-hours:24}")
    private int ttlHours;

    private static final String KEY_PREFIX = "omnisync:idem:";

    /**
     * Generate a SHA-256 idempotency key from the unique event properties.
     */
    public String generateKey(String ubid, String eventType, String timestamp) {
        String raw = ubid + "|" + eventType + "|" + timestamp;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // Fallback — should never happen
            return Integer.toHexString(raw.hashCode());
        }
    }

    /**
     * Atomically check-and-set the idempotency key.
     * Returns true if this is a NEW event (first time we see this key).
     * Returns false if we have already processed this event (duplicate).
     */
    public boolean tryAcquire(String idempotencyKey) {
        String redisKey = KEY_PREFIX + idempotencyKey;
        Boolean isNew = redisTemplate.opsForValue()
                .setIfAbsent(redisKey, "1", Duration.ofHours(ttlHours));
        boolean acquired = Boolean.TRUE.equals(isNew);
        if (!acquired) {
            log.warn("[IDEMPOTENCY] Duplicate detected — key={}", idempotencyKey);
        }
        return acquired;
    }

    /**
     * Check if key exists without setting (read-only check).
     */
    public boolean isDuplicate(String idempotencyKey) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + idempotencyKey));
    }

    /**
     * Manually invalidate a key (e.g., after a rollback that needs replay).
     */
    public void release(String idempotencyKey) {
        redisTemplate.delete(KEY_PREFIX + idempotencyKey);
        log.info("[IDEMPOTENCY] Key released — key={}", idempotencyKey);
    }
}
