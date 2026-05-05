package com.omnisync.ubid.adapter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Mock Department Adapter — simulates real department system API calls.
 *
 * In Round 2 (sandbox), this is replaced by actual HTTP clients (RestTemplate/WebClient)
 * pointing to mock SWS and department endpoints.
 *
 * In production, each department gets its own adapter implementation:
 *   - ShopsEstablishmentAdapter (SOAP/XML over HTTPS)
 *   - FactoriesAdapter (SQL API via REST)
 *   - GstAdapter (REST JSON)
 *   - LabourDeptAdapter (REST JSON with polling)
 */
@Component
@Slf4j
public class MockDepartmentAdapter {

    private static final Random RANDOM = new Random();

    /** All registered departments */
    public static final List<DepartmentInfo> DEPARTMENTS = List.of(
            new DepartmentInfo("shops_establishment", "Shops & Establishments", "SOAP"),
            new DepartmentInfo("factories",           "Factories Department",   "SQL_API"),
            new DepartmentInfo("gst",                 "GST Department",         "REST"),
            new DepartmentInfo("labour",              "Labour Department",       "REST")
    );

    /**
     * Simulate writing a translated payload to a department system.
     * Returns a mock HTTP/API response code.
     *
     * In production: replaced with actual HTTP call + retry logic.
     */
    public AdapterResult pushToDepartment(String departmentId,
                                          String ubid,
                                          String translatedPayload,
                                          String idempotencyKey) {
        log.info("[ADAPTER→{}] Pushing update for UBID={}", departmentId, ubid);

        // Simulate 90% success rate, 10% transient failure (triggers retry)
        boolean success = RANDOM.nextInt(10) > 0;

        if (!success) {
            log.warn("[ADAPTER→{}] Simulated transient failure for UBID={}", departmentId, ubid);
            return AdapterResult.builder()
                    .departmentId(departmentId)
                    .success(false)
                    .responseCode("503")
                    .message("Service temporarily unavailable — will retry")
                    .build();
        }

        log.info("[ADAPTER→{}] ✓ Write confirmed for UBID={}", departmentId, ubid);
        return AdapterResult.builder()
                .departmentId(departmentId)
                .success(true)
                .responseCode("200")
                .message("Write confirmed")
                .idempotencyKey(idempotencyKey)
                .build();
    }

    /**
     * Simulate polling a "silent" department system for changes.
     * Returns current record JSON for snapshot comparison.
     *
     * In production: actual REST/SOAP/DB call to fetch record by UBID.
     */
    public Map<String, Object> pollDepartmentRecord(String departmentId, String ubid) {
        log.debug("[POLLER→{}] Polling record for UBID={}", departmentId, ubid);

        // Simulate occasionally returning a changed record
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("ubid", ubid);
        record.put("department", departmentId);
        record.put("polled_at", java.time.Instant.now().toString());

        // Randomly simulate a change 20% of the time
        if (RANDOM.nextInt(5) == 0) {
            record.put("authorised_signatory", "Rajesh Kumar (Updated)");
            record.put("_changed", true);
        } else {
            record.put("authorised_signatory", "Rajesh Kumar");
            record.put("_changed", false);
        }

        record.put("registered_address", Map.of(
                "line1", "12 MG Road",
                "city", "Bengaluru",
                "pin", "560001"
        ));

        return record;
    }

    /**
     * Push a canonically-translated payload back to SWS.
     */
    public AdapterResult pushToSws(String ubid, Map<String, Object> payload, String correlationId) {
        log.info("[ADAPTER→SWS] Pushing update for UBID={} corr={}", ubid, correlationId);
        return AdapterResult.builder()
                .departmentId("SWS")
                .success(true)
                .responseCode("200")
                .message("SWS record updated successfully")
                .build();
    }

    public record DepartmentInfo(String id, String name, String protocol) {}

    @lombok.Data
    @lombok.Builder
    public static class AdapterResult {
        private String departmentId;
        private boolean success;
        private String responseCode;
        private String message;
        private String idempotencyKey;
    }
}
