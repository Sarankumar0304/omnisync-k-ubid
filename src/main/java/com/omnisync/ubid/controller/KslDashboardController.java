package com.omnisync.ubid.controller;

import com.omnisync.ubid.audit.AuditService;
import com.omnisync.ubid.dto.OmniSyncDTOs.DashboardStats;
import com.omnisync.ubid.model.AuditLog;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/ksl")
@RequiredArgsConstructor
@CrossOrigin("*")
public class KslDashboardController {

    private final AuditService auditService;

    @GetMapping("/stats")
    public ResponseEntity<DashboardStats> stats() {
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

    @GetMapping("/audit/conflicts")
    public ResponseEntity<List<AuditLog>> conflicts() {
        return ResponseEntity.ok(auditService.getAllEntries());
    }

    @GetMapping("/registry")
    public ResponseEntity<List<Map<String, Object>>> registry() {
        return ResponseEntity.ok(List.of(
                Map.of("ubid", "KA-BIZ-00192", "businessName", "Demo Business 1", "status", "ACTIVE"),
                Map.of("ubid", "KA-BIZ-00193", "businessName", "Demo Business 2", "status", "ACTIVE"),
                Map.of("ubid", "KA-BIZ-00194", "businessName", "Demo Business 3", "status", "ACTIVE")
        ));
    }
}