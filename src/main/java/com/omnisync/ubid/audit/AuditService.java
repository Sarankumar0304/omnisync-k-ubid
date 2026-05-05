package com.omnisync.ubid.audit;

import com.omnisync.ubid.model.AuditLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Immutable append-only audit ledger service.
 * Uses REQUIRES_NEW so audit entries are never rolled back,
 * even when the outer transaction fails.
 *
 * Every propagation leaves a trail answering:
 *   WHAT changed | WHERE it came from | WHERE it went | ANY conflict resolved?
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    /**
     * Write a single audit entry — always in a new transaction so it
     * survives failures in the calling transaction.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuditLog log(String correlationId,
                        String ubid,
                        AuditLog.AuditEventType eventType,
                        String sourceSystem,
                        String targetSystem,
                        String description,
                        AuditLog.Outcome outcome) {

        AuditLog entry = AuditLog.builder()
                .correlationId(correlationId)
                .ubid(ubid)
                .eventType(eventType)
                .sourceSystem(sourceSystem)
                .targetSystem(targetSystem)
                .description(description)
                .outcome(outcome)
                .timestamp(Instant.now())
                .build();

        AuditLog saved = auditLogRepository.save(entry);
        log.debug("[AUDIT] corr={} ubid={} event={} outcome={}",
                correlationId, ubid, eventType, outcome);
        return saved;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuditLog logConflict(String correlationId,
                                String ubid,
                                String conflictPolicy,
                                String description) {
        AuditLog entry = AuditLog.builder()
                .correlationId(correlationId)
                .ubid(ubid)
                .eventType(AuditLog.AuditEventType.CONFLICT_RESOLVED)
                .description(description)
                .outcome(AuditLog.Outcome.CONFLICT_RESOLVED)
                .conflictPolicy(conflictPolicy)
                .timestamp(Instant.now())
                .build();
        return auditLogRepository.save(entry);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuditLog logIdempotencySkip(String correlationId, String ubid, String idempotencyKey) {
        AuditLog entry = AuditLog.builder()
                .correlationId(correlationId)
                .ubid(ubid)
                .eventType(AuditLog.AuditEventType.IDEMPOTENCY_SKIP)
                .idempotencyKey(idempotencyKey)
                .description("Duplicate event skipped — idempotency key already seen: " + idempotencyKey)
                .outcome(AuditLog.Outcome.SKIPPED)
                .timestamp(Instant.now())
                .build();
        return auditLogRepository.save(entry);
    }

    public List<AuditLog> getAuditTrailForUbid(String ubid) {
        return auditLogRepository.findByUbidOrderByTimestampDesc(ubid);
    }

    public List<AuditLog> getAuditTrailForCorrelation(String correlationId) {
        return auditLogRepository.findByCorrelationIdOrderByTimestamp(correlationId);
    }

    public List<AuditLog> getAllEntries() {
        return auditLogRepository.findAll();
    }

    public long countConflictsResolved() {
        return auditLogRepository.countConflictsResolved();
    }

    public long countSuccessful() {
        return auditLogRepository.countSuccessful();
    }

    public long countAll() {
        return auditLogRepository.count();
    }
}
