package com.omnisync.ubid.controller;

import com.omnisync.ubid.audit.AuditService;
import com.omnisync.ubid.dto.OmniSyncDTOs.*;
import com.omnisync.ubid.model.AuditLog;
import com.omnisync.ubid.service.PropagationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Primary REST API for OmniSync-K UBID.
 *
 * Exposes endpoints for:
 *   - SWS → Department propagation (Direction 1)
 *   - Department → SWS propagation (Direction 2)
 *   - Webhook receiver for department events
 *   - Audit trail queries
 *   - Dashboard statistics
 */
@RestController
@RequestMapping("/api/v1/omnisync")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "OmniSync-K UBID", description = "Two-Way Interoperability API — Karnataka SWS")
public class OmniSyncController {

    private final PropagationService propagationService;
    private final AuditService auditService;

    // ── Direction 1: SWS → Departments ──────────────────────────────────────

    @PostMapping("/sws/event")
    @Operation(
        summary = "Receive and propagate SWS service request",
        description = "Accepts a service request raised in SWS (e.g., address change) " +
                      "and propagates it to all matching department systems via UBID join."
    )
    public ResponseEntity<PropagationResponse> receiveSwsEvent(
            @Valid @RequestBody SwsEventRequest request) {
        log.info("[API] POST /sws/event ubid={} eventType={}", request.getUbid(), request.getEventType());
        PropagationResponse response = propagationService.propagateFromSws(request);
        return ResponseEntity.ok(response);
    }

    // ── Direction 2: Department → SWS ────────────────────────────────────────

    @PostMapping("/dept/event")
    @Operation(
        summary = "Receive and propagate department system event to SWS",
        description = "Accepts a service request raised directly in a department system " +
                      "and propagates it back to SWS, translated via the UBID join key."
    )
    public ResponseEntity<PropagationResponse> receiveDeptEvent(
            @Valid @RequestBody DeptEventRequest request) {
        log.info("[API] POST /dept/event ubid={} dept={} eventType={}",
                request.getUbid(), request.getDepartmentId(), request.getEventType());
        PropagationResponse response = propagationService.propagateFromDept(request);
        return ResponseEntity.ok(response);
    }

    // ── Webhook Receiver ─────────────────────────────────────────────────────

    @PostMapping("/webhook/{departmentId}")
    @Operation(
        summary = "Webhook endpoint for department systems",
        description = "Receives webhook payloads from department systems that support push notifications. " +
                      "Automatically translates and propagates to SWS."
    )
    public ResponseEntity<Map<String, String>> receiveWebhook(
            @Parameter(description = "Department ID, e.g. gst, shops_establishment")
            @PathVariable String departmentId,
            @RequestBody Map<String, Object> rawPayload) {

        log.info("[WEBHOOK] Received from dept={} payload-keys={}", departmentId, rawPayload.keySet());

        String ubid = (String) rawPayload.get("ubid");
        if (ubid == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "ubid is required in webhook payload"));
        }

        DeptEventRequest request = DeptEventRequest.builder()
                .ubid(ubid)
                .departmentId(departmentId)
                .eventType((String) rawPayload.getOrDefault("event_type", "GENERIC_UPDATE"))
                .payload(rawPayload)
                .timestamp(Instant.now())
                .build();

        propagationService.propagateFromDept(request);
        return ResponseEntity.ok(Map.of("status", "accepted", "ubid", ubid));
    }

    // ── Audit Trail ──────────────────────────────────────────────────────────

    @GetMapping("/audit/ubid/{ubid}")
    @Operation(
        summary = "Get full audit trail for a UBID",
        description = "Returns every propagation event, conflict resolution, and retry " +
                      "recorded for a given Unique Business Identifier."
    )
    public ResponseEntity<List<AuditLog>> getAuditByUbid(
            @Parameter(description = "Unique Business Identifier, e.g. KA-BIZ-00192")
            @PathVariable String ubid) {
        return ResponseEntity.ok(auditService.getAuditTrailForUbid(ubid));
    }

    @GetMapping("/audit/correlation/{correlationId}")
    @Operation(summary = "Get audit trail for a specific propagation event")
    public ResponseEntity<List<AuditLog>> getAuditByCorrelation(
            @PathVariable String correlationId) {
        return ResponseEntity.ok(auditService.getAuditTrailForCorrelation(correlationId));
    }

    @GetMapping("/audit/all")
    @Operation(summary = "Get all audit entries (paginated — demo returns all)")
    public ResponseEntity<List<AuditLog>> getAllAudit() {
        return ResponseEntity.ok(auditService.getAllEntries());
    }

    // ── Dashboard Stats ──────────────────────────────────────────────────────

    @GetMapping("/dashboard/stats")
    @Operation(summary = "Get live dashboard statistics")
    public ResponseEntity<DashboardStats> getDashboardStats() {
        long total = auditService.countAll();
        long successful = auditService.countSuccessful();
        long conflicts = auditService.countConflictsResolved();

        DashboardStats stats = DashboardStats.builder()
                .totalPropagations(total)
                .successfulPropagations(successful)
                .conflictsResolved(conflicts)
                .activeRetries(0L)
                .auditEntries(total)
                .successRate(total > 0 ? (double) successful / total * 100 : 0)
                .lastUpdated(Instant.now())
                .build();

        return ResponseEntity.ok(stats);
    }

    // ── Health / Info ─────────────────────────────────────────────────────────

    @GetMapping("/info")
    @Operation(summary = "Get OmniSync middleware info and available endpoints")
    public ResponseEntity<Map<String, Object>> getInfo() {
        return ResponseEntity.ok(Map.of(
            "name", "OmniSync-K UBID",
            "version", "1.0.0",
            "description", "Two-Way Interoperability Middleware — Karnataka SWS",
            "endpoints", Map.of(
                "SWS→Dept",  "POST /api/v1/omnisync/sws/event",
                "Dept→SWS",  "POST /api/v1/omnisync/dept/event",
                "Webhook",   "POST /api/v1/omnisync/webhook/{departmentId}",
                "Audit",     "GET  /api/v1/omnisync/audit/ubid/{ubid}",
                "Stats",     "GET  /api/v1/omnisync/dashboard/stats",
                "SwaggerUI", "http://localhost:8080/swagger-ui.html",
                "H2Console", "http://localhost:8080/h2-console"
            ),
            "departments", List.of("shops_establishment", "factories", "gst", "labour"),
            "conflictPolicy", "SWS_PRIORITY",
            "idempotencyTTL", "24h"
        ));
    }
}
