package com.omnisync.ubid.poller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omnisync.ubid.adapter.MockDepartmentAdapter;
import com.omnisync.ubid.model.AuditLog;
import com.omnisync.ubid.audit.AuditService;
import com.omnisync.ubid.dto.OmniSyncDTOs.DeptEventRequest;
import com.omnisync.ubid.model.DepartmentSnapshot;
import com.omnisync.ubid.service.PropagationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

/**
 * Sidecar Poller — Passive Change Discovery for Silent Department Systems.
 *
 * Some legacy department systems do not emit events or support webhooks.
 * The Sidecar Poller runs on a configurable schedule and:
 *   1. Polls each "silent" department system for known UBIDs
 *   2. Computes a content hash of the returned record
 *   3. Compares against the last stored snapshot
 *   4. If changed → fires a DeptEventRequest into the propagation pipeline
 *
 * This implements Change Data Capture (CDC) without modifying the source system.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SidecarPoller {

    private final MockDepartmentAdapter departmentAdapter;
    private final PropagationService propagationService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    // In-memory snapshot store (replace with DepartmentSnapshotRepository for persistence)
    private final Map<String, String> snapshotStore = new ConcurrentHashMap<>();

    // Known UBIDs to poll (in production: loaded from UBID registry)
    private static final List<String> KNOWN_UBIDS = List.of(
            "KA-BIZ-00192", "KA-BIZ-00193", "KA-BIZ-00194"
    );

    // Departments that require polling (no webhook support)
    private static final List<String> POLLING_DEPARTMENTS = List.of(
            "shops_establishment", "factories", "labour"
    );

    /**
     * Poll all silent departments every 15 seconds.
     * fixedDelayString reads from application.properties.
     */
    @Scheduled(fixedDelayString = "${omnisync.poller.interval-ms:15000}")
    public void pollAllDepartments() {
        log.debug("[POLLER] Starting poll cycle for {} departments × {} UBIDs",
                POLLING_DEPARTMENTS.size(), KNOWN_UBIDS.size());

        for (String deptId : POLLING_DEPARTMENTS) {
            for (String ubid : KNOWN_UBIDS) {
                try {
                    pollAndDetect(deptId, ubid);
                } catch (Exception e) {
                    log.error("[POLLER] Error polling dept={} ubid={}: {}", deptId, ubid, e.getMessage());
                }
            }
        }
    }

    private void pollAndDetect(String deptId, String ubid) throws JsonProcessingException {
        Map<String, Object> record = departmentAdapter.pollDepartmentRecord(deptId, ubid);

        // Remove internal marker before hashing
        boolean changed = Boolean.TRUE.equals(record.remove("_changed"));

        String snapshotKey = deptId + ":" + ubid;
        String currentHash = computeHash(objectMapper.writeValueAsString(record));
        String storedHash = snapshotStore.get(snapshotKey);

        if (storedHash == null) {
            // First poll — store baseline snapshot
            snapshotStore.put(snapshotKey, currentHash);
            log.debug("[POLLER] Baseline snapshot stored: dept={} ubid={}", deptId, ubid);
            return;
        }

        if (changed || !currentHash.equals(storedHash)) {
            log.info("[POLLER] ✦ Change detected! dept={} ubid={}", deptId, ubid);

            // Update snapshot
            snapshotStore.put(snapshotKey, currentHash);

            // Audit the detection
            auditService.log(
                    UUID.randomUUID().toString(), ubid,
                    AuditLog.AuditEventType.POLLING_CHANGE_DETECTED,
                    deptId, "POLLER",
                    "Change detected via polling in " + deptId + " for UBID " + ubid,
                    AuditLog.Outcome.SUCCESS);

            // Fire into propagation pipeline → SWS
            DeptEventRequest event = DeptEventRequest.builder()
                    .ubid(ubid)
                    .departmentId(deptId)
                    .eventType(inferEventType(record))
                    .payload(record)
                    .timestamp(Instant.now())
                    .build();

            propagationService.propagateFromDept(event);
        }
    }

    private String inferEventType(Map<String, Object> record) {
        if (record.containsKey("authorised_signatory")) return "SIGNATORY_CHANGE";
        if (record.containsKey("registered_address"))  return "ADDRESS_UPDATE";
        return "GENERIC_UPDATE";
    }

    private String computeHash(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            return String.valueOf(content.hashCode());
        }
    }

    private static class ConcurrentHashMap<K, V> extends java.util.concurrent.ConcurrentHashMap<K, V> {}
}
