package com.omnisync.ubid.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * Core domain object representing a service request propagated
 * between SWS and department systems via OmniSync-K.
 */
@Entity
@Table(name = "service_requests")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    /** Unique Business Identifier — the only reliable join key across systems */
    @Column(name = "ubid", nullable = false, length = 64)
    private String ubid;

    /** Type of service request, e.g., ADDRESS_UPDATE, SIGNATORY_CHANGE */
    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    /** Source system: SWS or department ID (e.g., factories, shops_establishment) */
    @Column(name = "source_system", nullable = false, length = 64)
    private String sourceSystem;

    /** JSON payload of the actual change data */
    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    /** SHA-256 based idempotency key to prevent duplicate writes */
    @Column(name = "idempotency_key", unique = true, length = 128)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private RequestStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "retry_count")
    private int retryCount;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    public enum RequestStatus {
        RECEIVED, PROPAGATING, SUCCESS, FAILED, CONFLICT_DETECTED, CONFLICT_RESOLVED, RETRYING
    }
}
