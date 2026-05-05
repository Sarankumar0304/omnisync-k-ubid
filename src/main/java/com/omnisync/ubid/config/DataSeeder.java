package com.omnisync.ubid.config;

import com.omnisync.ubid.dto.OmniSyncDTOs.*;
import com.omnisync.ubid.service.PropagationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

/**
 * Demo Data Seeder — runs on startup to populate the system with
 * realistic Karnataka SWS scenarios so the Swagger UI and H2 console
 * show meaningful data immediately.
 *
 * Scenarios:
 *   1. Address update from SWS → propagates to Shops & Factories
 *   2. Signatory change from Factories dept → propagates to SWS
 *   3. Conflict scenario: two updates within conflict window
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

    private final PropagationService propagationService;

    @Override
    public void run(String... args) throws InterruptedException {
        log.info("[SEEDER] Seeding demo data for Karnataka SWS scenarios...");

        // ── Scenario 1: SWS Address Update ───────────────────────────────────
        log.info("[SEEDER] Scenario 1: Address update from SWS → Departments");
        propagationService.propagateFromSws(SwsEventRequest.builder()
                .ubid("KA-BIZ-00192")
                .eventType("ADDRESS_UPDATE")
                .timestamp(Instant.now())
                .payload(Map.of(
                        "registered_address", Map.of(
                                "line1", "12 MG Road",
                                "city", "Bengaluru",
                                "state", "Karnataka",
                                "pin", "560001"
                        )
                ))
                .build());

        Thread.sleep(500);

        // ── Scenario 2: Dept Signatory Change → SWS ──────────────────────────
        log.info("[SEEDER] Scenario 2: Signatory change from Factories → SWS");
        propagationService.propagateFromDept(DeptEventRequest.builder()
                .ubid("KA-BIZ-00192")
                .departmentId("factories")
                .eventType("SIGNATORY_CHANGE")
                .timestamp(Instant.now())
                .payload(Map.of(
                        "authorised_signatory_name", "Rajesh Kumar",
                        "effective_date", "2026-04-30"
                ))
                .build());

        Thread.sleep(300);

        // ── Scenario 3: Different UBID address update ─────────────────────────
        log.info("[SEEDER] Scenario 3: Second business address update");
        propagationService.propagateFromSws(SwsEventRequest.builder()
                .ubid("KA-BIZ-00193")
                .eventType("ADDRESS_UPDATE")
                .timestamp(Instant.now())
                .payload(Map.of(
                        "registered_address", Map.of(
                                "line1", "45 Brigade Road",
                                "city", "Bengaluru",
                                "state", "Karnataka",
                                "pin", "560025"
                        )
                ))
                .build());

        // ── Scenario 4: Duplicate (idempotency test) ──────────────────────────
        Thread.sleep(200);
        log.info("[SEEDER] Scenario 4: Duplicate event (should be skipped by idempotency)");
        Instant fixedTime = Instant.parse("2026-04-30T10:42:31Z");
        propagationService.propagateFromSws(SwsEventRequest.builder()
                .ubid("KA-BIZ-00194")
                .eventType("ADDRESS_UPDATE")
                .timestamp(fixedTime)
                .payload(Map.of("registered_address", Map.of("line1", "Test", "city", "Bengaluru", "pin", "560001")))
                .build());
        // Send same event again — should be skipped
        propagationService.propagateFromSws(SwsEventRequest.builder()
                .ubid("KA-BIZ-00194")
                .eventType("ADDRESS_UPDATE")
                .timestamp(fixedTime)
                .payload(Map.of("registered_address", Map.of("line1", "Test", "city", "Bengaluru", "pin", "560001")))
                .build());

        log.info("[SEEDER] ✅ Demo data seeded. Visit http://localhost:8080/swagger-ui.html to explore.");
    }
}
