package com.omnisync.ubid.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omnisync.ubid.adapter.MockDepartmentAdapter;
import com.omnisync.ubid.model.AuditLog;
import com.omnisync.ubid.audit.AuditService;
import com.omnisync.ubid.conflict.ConflictResolutionService;
import com.omnisync.ubid.dto.OmniSyncDTOs.*;
import com.omnisync.ubid.model.ServiceRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

/**
 * Core Propagation Orchestrator — the heart of OmniSync-K UBID.
 *
 * Direction 1: SWS → All matching department systems
 * Direction 2: Department system → SWS
 *
 * For every propagation:
 *   1. Check idempotency (skip duplicates)
 *   2. Detect & resolve conflicts
 *   3. Translate schemas
 *   4. Write to target system(s)
 *   5. Handle failures with exponential backoff retry
 *   6. Write full audit trail
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PropagationService {

    private final IdempotencyService idempotencyService;
    private final SchemaTranslationService translationService;
    private final ConflictResolutionService conflictService;
    private final AuditService auditService;
    private final MockDepartmentAdapter departmentAdapter;
    private final ObjectMapper objectMapper;

    @Value("${omnisync.retry.max-attempts:5}")
    private int maxRetryAttempts;

    @Value("${omnisync.retry.backoff-ms:1000}")
    private long retryBackoffMs;

    // ── Direction 1: SWS → Departments ───────────────────────────────────────

    /**
     * Propagate a service request from SWS to all matching department systems.
     * Uses UBID as the join key to find records in each department.
     */
    public PropagationResponse propagateFromSws(SwsEventRequest request) {
        String correlationId = UUID.randomUUID().toString();
        String timestamp = request.getTimestamp() != null
                ? request.getTimestamp().toString()
                : Instant.now().toString();

        log.info("[PROPAGATION] SWS→Depts corr={} ubid={} event={}",
                correlationId, request.getUbid(), request.getEventType());

        // Step 1: Idempotency check
        String idempKey = idempotencyService.generateKey(
                request.getUbid(), request.getEventType(), timestamp);

        if (!idempotencyService.tryAcquire(idempKey)) {
            auditService.logIdempotencySkip(correlationId, request.getUbid(), idempKey);
            return PropagationResponse.builder()
                    .correlationId(correlationId)
                    .ubid(request.getUbid())
                    .status("SKIPPED")
                    .message("Duplicate event — idempotency key already processed")
                    .processedAt(Instant.now())
                    .build();
        }

        // Step 2: Audit receipt
        auditService.log(correlationId, request.getUbid(),
                AuditLog.AuditEventType.REQUEST_RECEIVED,
                "SWS", "MIDDLEWARE",
                "Received " + request.getEventType() + " from SWS",
                AuditLog.Outcome.SUCCESS);

        // Step 3: Conflict detection
        Map<String, Object> payloadWithUbid = new HashMap<>(request.getPayload());
        payloadWithUbid.put("ubid", request.getUbid());

        Optional<ConflictResolutionService.ConflictResult> conflict =
                conflictService.checkAndRecord(request.getUbid(), "SWS", payloadWithUbid);

        ConflictInfo conflictInfo = null;
        if (conflict.isPresent()) {
            var cr = conflict.get();
            conflictInfo = ConflictInfo.builder()
                    .detected(true)
                    .policyApplied(cr.getPolicyApplied())
                    .winner(cr.getWinner())
                    .reason(cr.getReason())
                    .windowMs(200)
                    .build();
            auditService.logConflict(correlationId, request.getUbid(),
                    cr.getPolicyApplied(),
                    "Conflict between SWS and " + cr.getSource1() +
                    " — resolved by policy: " + cr.getPolicyApplied() +
                    " — winner: " + cr.getWinner());
        }

        // Step 4: Propagate to each department
        List<DeptResult> results = new ArrayList<>();
        for (MockDepartmentAdapter.DepartmentInfo dept : MockDepartmentAdapter.DEPARTMENTS) {
            DeptResult result = propagateToDepartmentWithRetry(
                    correlationId, request.getUbid(),
                    request.getEventType(), payloadWithUbid,
                    dept.id(), dept.name(), idempKey);
            results.add(result);
        }

        long successCount = results.stream().filter(r -> "SUCCESS".equals(r.getStatus())).count();
        String overallStatus = successCount == results.size() ? "SUCCESS"
                : successCount == 0 ? "FAILED" : "PARTIAL";

        return PropagationResponse.builder()
                .correlationId(correlationId)
                .ubid(request.getUbid())
                .status(overallStatus)
                .message(successCount + "/" + results.size() + " departments updated successfully")
                .departmentResults(results)
                .conflict(conflictInfo)
                .processedAt(Instant.now())
                .build();
    }

    // ── Direction 2: Department → SWS ────────────────────────────────────────

    /**
     * Propagate a service request from a department system back to SWS.
     * This can be triggered by webhook, polling change detection, or direct call.
     */
    public PropagationResponse propagateFromDept(DeptEventRequest request) {
        String correlationId = UUID.randomUUID().toString();
        String timestamp = request.getTimestamp() != null
                ? request.getTimestamp().toString()
                : Instant.now().toString();

        log.info("[PROPAGATION] Dept→SWS corr={} ubid={} dept={} event={}",
                correlationId, request.getUbid(), request.getDepartmentId(), request.getEventType());

        // Step 1: Idempotency
        String idempKey = idempotencyService.generateKey(
                request.getUbid(), request.getDepartmentId() + ":" + request.getEventType(), timestamp);

        if (!idempotencyService.tryAcquire(idempKey)) {
            auditService.logIdempotencySkip(correlationId, request.getUbid(), idempKey);
            return PropagationResponse.builder()
                    .correlationId(correlationId).ubid(request.getUbid())
                    .status("SKIPPED").message("Duplicate — already processed")
                    .processedAt(Instant.now()).build();
        }

        auditService.log(correlationId, request.getUbid(),
                AuditLog.AuditEventType.REQUEST_RECEIVED,
                request.getDepartmentId(), "MIDDLEWARE",
                "Received " + request.getEventType() + " from " + request.getDepartmentId(),
                AuditLog.Outcome.SUCCESS);

        // Step 2: Conflict detection
        Optional<ConflictResolutionService.ConflictResult> conflict =
                conflictService.checkAndRecord(request.getUbid(), request.getDepartmentId(), request.getPayload());

        ConflictInfo conflictInfo = null;
        if (conflict.isPresent()) {
            var cr = conflict.get();
            conflictInfo = ConflictInfo.builder()
                    .detected(true).policyApplied(cr.getPolicyApplied())
                    .winner(cr.getWinner()).reason(cr.getReason()).windowMs(200).build();
            auditService.logConflict(correlationId, request.getUbid(), cr.getPolicyApplied(),
                    "Dept→SWS conflict resolved: " + cr.getReason());
        }

        // Step 3: Translate dept payload → SWS canonical
        Map<String, Object> canonical = translationService.translateToSwsFormat(
                request.getDepartmentId(), request.getEventType(), request.getPayload());
        canonical.put("ubid", request.getUbid());

        auditService.log(correlationId, request.getUbid(),
                AuditLog.AuditEventType.TRANSLATION_COMPLETED,
                request.getDepartmentId(), "SWS",
                "Translated to SWS canonical format", AuditLog.Outcome.SUCCESS);

        // Step 4: Write to SWS
        MockDepartmentAdapter.AdapterResult swsResult =
                departmentAdapter.pushToSws(request.getUbid(), canonical, correlationId);

        auditService.log(correlationId, request.getUbid(),
                swsResult.isSuccess()
                        ? AuditLog.AuditEventType.PROPAGATION_SUCCESS
                        : AuditLog.AuditEventType.PROPAGATION_FAILED,
                request.getDepartmentId(), "SWS",
                swsResult.getMessage(),
                swsResult.isSuccess() ? AuditLog.Outcome.SUCCESS : AuditLog.Outcome.FAILED);

        return PropagationResponse.builder()
                .correlationId(correlationId)
                .ubid(request.getUbid())
                .status(swsResult.isSuccess() ? "SUCCESS" : "FAILED")
                .message(swsResult.getMessage())
                .conflict(conflictInfo)
                .processedAt(Instant.now())
                .build();
    }

    // ── Internal Retry Logic ─────────────────────────────────────────────────

    private DeptResult propagateToDepartmentWithRetry(String correlationId,
                                                       String ubid,
                                                       String eventType,
                                                       Map<String, Object> payload,
                                                       String deptId,
                                                       String deptName,
                                                       String idempKey) {
        // Translate
        auditService.log(correlationId, ubid, AuditLog.AuditEventType.TRANSLATION_STARTED,
                "SWS", deptId, "Translating payload for " + deptName, AuditLog.Outcome.SUCCESS);

        String translated = translationService.translate(deptId, eventType, payload);

        auditService.log(correlationId, ubid, AuditLog.AuditEventType.TRANSLATION_COMPLETED,
                "SWS", deptId, "Translation complete for " + deptName, AuditLog.Outcome.SUCCESS);

        // Retry loop with exponential backoff
        int attempt = 0;
        while (attempt < maxRetryAttempts) {
            attempt++;
            auditService.log(correlationId, ubid, AuditLog.AuditEventType.PROPAGATION_SENT,
                    "SWS", deptId,
                    "Attempt " + attempt + "/" + maxRetryAttempts + " for " + deptName,
                    AuditLog.Outcome.SUCCESS);

            MockDepartmentAdapter.AdapterResult result =
                    departmentAdapter.pushToDepartment(deptId, ubid, translated, idempKey);

            if (result.isSuccess()) {
                auditService.log(correlationId, ubid, AuditLog.AuditEventType.PROPAGATION_SUCCESS,
                        "SWS", deptId,
                        deptName + " write confirmed on attempt " + attempt,
                        AuditLog.Outcome.SUCCESS);

                return DeptResult.builder()
                        .departmentId(deptId).departmentName(deptName)
                        .status("SUCCESS").responseCode(result.getResponseCode())
                        .message("Write confirmed on attempt " + attempt)
                        .idempotencyKey(idempKey).build();
            }

            // Failed — log and retry with backoff
            auditService.log(correlationId, ubid, AuditLog.AuditEventType.RETRY_ATTEMPTED,
                    "SWS", deptId,
                    "Attempt " + attempt + " failed: " + result.getMessage() + " — retrying",
                    AuditLog.Outcome.RETRYING);

            if (attempt < maxRetryAttempts) {
                try {
                    long backoff = retryBackoffMs * (long) Math.pow(2, attempt - 1);
                    log.debug("[RETRY] Backoff {}ms before attempt {}", backoff, attempt + 1);
                    Thread.sleep(Math.min(backoff, 5000)); // cap at 5s in demo
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        auditService.log(correlationId, ubid, AuditLog.AuditEventType.PROPAGATION_FAILED,
                "SWS", deptId,
                deptName + " write FAILED after " + maxRetryAttempts + " attempts",
                AuditLog.Outcome.FAILED);

        return DeptResult.builder()
                .departmentId(deptId).departmentName(deptName)
                .status("FAILED").responseCode("503")
                .message("Failed after " + maxRetryAttempts + " attempts")
                .idempotencyKey(idempKey).build();
    }
}
