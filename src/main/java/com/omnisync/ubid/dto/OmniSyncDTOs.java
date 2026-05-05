package com.omnisync.ubid.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Data Transfer Objects for OmniSync-K UBID REST API.
 */
public class OmniSyncDTOs {

    // ── Inbound from SWS ─────────────────────────────────────────
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class SwsEventRequest {
        @NotBlank(message = "UBID is required")
        @Pattern(regexp = "KA-BIZ-\\d{5}", message = "UBID must match pattern KA-BIZ-XXXXX")
        private String ubid;

        @NotBlank(message = "eventType is required")
        private String eventType;

        private Map<String, Object> payload;
        private Instant timestamp;
    }

    // ── Inbound from Department System ───────────────────────────
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class DeptEventRequest {
        @NotBlank
        private String ubid;

        @NotBlank
        private String departmentId;

        @NotBlank
        private String eventType;

        private Map<String, Object> payload;
        private Instant timestamp;
    }

    // ── Propagation Result ───────────────────────────────────────
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PropagationResponse {
        private String correlationId;
        private String ubid;
        private String status;
        private String message;
        private List<DeptResult> departmentResults;
        private ConflictInfo conflict;
        private Instant processedAt;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class DeptResult {
        private String departmentId;
        private String departmentName;
        private String status;
        private String idempotencyKey;
        private String responseCode;
        private String message;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ConflictInfo {
        private boolean detected;
        private String policyApplied;
        private String winner;
        private String reason;
        private long windowMs;
    }

    // ── Audit Query Response ─────────────────────────────────────
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class AuditEntry {
        private String id;
        private String correlationId;
        private String ubid;
        private String eventType;
        private String sourceSystem;
        private String targetSystem;
        private String description;
        private String outcome;
        private String conflictPolicy;
        private Instant timestamp;
    }

    // ── Dashboard Stats ──────────────────────────────────────────
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class DashboardStats {
        private long totalPropagations;
        private long successfulPropagations;
        private long conflictsResolved;
        private long activeRetries;
        private long auditEntries;
        private double successRate;
        private Instant lastUpdated;
    }
}
