package com.omnisync.ubid.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Immutable append-only audit ledger entry.
 * Records every propagation attempt, conflict resolution, and retry.
 * Never updated — only inserted.
 */
@Entity
@Table(name = "audit_log")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    /** Correlation ID linking all events for a single service request */
    @Column(name = "correlation_id", nullable = false, length = 64)
    private String correlationId;

    /** UBID of the business whose record was changed */
    @Column(name = "ubid", nullable = false, length = 64)
    private String ubid;

    /** What type of event this log entry records */
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 64)
    private AuditEventType eventType;

    /** System that originated the change */
    @Column(name = "source_system", length = 64)
    private String sourceSystem;

    /** System that received the write */
    @Column(name = "target_system", length = 64)
    private String targetSystem;

    /** Human-readable description of what happened */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /** Outcome of the propagation attempt */
    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", length = 32)
    private Outcome outcome;

    /** Conflict resolution policy applied, if any */
    @Column(name = "conflict_policy", length = 64)
    private String conflictPolicy;

    /** HTTP status or system response code */
    @Column(name = "response_code", length = 16)
    private String responseCode;

    @Column(name = "idempotency_key", length = 128)
    private String idempotencyKey;

    @Column(name = "timestamp", nullable = false)
    private Instant timestamp;

    public enum AuditEventType {
        REQUEST_RECEIVED,
        TRANSLATION_STARTED,
        TRANSLATION_COMPLETED,
        PROPAGATION_SENT,
        PROPAGATION_SUCCESS,
        PROPAGATION_FAILED,
        RETRY_ATTEMPTED,
        CONFLICT_DETECTED,
        CONFLICT_RESOLVED,
        POLLING_CHANGE_DETECTED,
        IDEMPOTENCY_SKIP
    }

    public enum Outcome {
        SUCCESS, FAILED, SKIPPED, CONFLICT_RESOLVED, RETRYING
    }
}
