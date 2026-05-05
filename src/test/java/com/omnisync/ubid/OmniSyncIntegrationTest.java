package com.omnisync.ubid;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omnisync.ubid.dto.OmniSyncDTOs.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

/**
 * Integration tests for OmniSync-K UBID.
 * Tests the full propagation pipeline end-to-end.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OmniSyncIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Direction 1: SWS address update propagates to departments")
    void testSwsAddressUpdatePropagation() throws Exception {
        SwsEventRequest request = SwsEventRequest.builder()
                .ubid("KA-BIZ-99001")
                .eventType("ADDRESS_UPDATE")
                .timestamp(Instant.now())
                .payload(Map.of(
                        "registered_address", Map.of(
                                "line1", "10 Residency Road",
                                "city", "Bengaluru",
                                "state", "Karnataka",
                                "pin", "560025"
                        )
                ))
                .build();

        mockMvc.perform(post("/api/v1/omnisync/sws/event")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ubid", is("KA-BIZ-99001")))
                .andExpect(jsonPath("$.correlationId", notNullValue()))
                .andExpect(jsonPath("$.departmentResults", hasSize(4)));
    }

    @Test
    @DisplayName("Direction 2: Department event propagates back to SWS")
    void testDeptEventPropagationToSws() throws Exception {
        DeptEventRequest request = DeptEventRequest.builder()
                .ubid("KA-BIZ-99002")
                .departmentId("factories")
                .eventType("SIGNATORY_CHANGE")
                .timestamp(Instant.now())
                .payload(Map.of("authorised_signatory_name", "Priya Sharma"))
                .build();

        mockMvc.perform(post("/api/v1/omnisync/dept/event")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ubid", is("KA-BIZ-99002")))
                .andExpect(jsonPath("$.status", not("FAILED")));
    }

    @Test
    @DisplayName("Idempotency: duplicate event is skipped")
    void testIdempotencySkipsDuplicate() throws Exception {
        Instant fixedTime = Instant.parse("2026-01-01T00:00:00Z");
        SwsEventRequest request = SwsEventRequest.builder()
                .ubid("KA-BIZ-99003")
                .eventType("ADDRESS_UPDATE")
                .timestamp(fixedTime)
                .payload(Map.of("registered_address", Map.of("line1", "Test", "city", "B", "pin", "560001")))
                .build();

        String json = objectMapper.writeValueAsString(request);

        // First call — should succeed
        mockMvc.perform(post("/api/v1/omnisync/sws/event")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", not("SKIPPED")));

        // Second call — same timestamp → idempotency skip
        mockMvc.perform(post("/api/v1/omnisync/sws/event")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("SKIPPED")));
    }

    @Test
    @DisplayName("Audit trail is populated after propagation")
    void testAuditTrailPopulated() throws Exception {
        String ubid = "KA-BIZ-99004";
        SwsEventRequest request = SwsEventRequest.builder()
                .ubid(ubid).eventType("ADDRESS_UPDATE").timestamp(Instant.now())
                .payload(Map.of("registered_address", Map.of("line1", "Audit Test", "city", "B", "pin", "560001")))
                .build();

        mockMvc.perform(post("/api/v1/omnisync/sws/event")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        // Check audit trail
        mockMvc.perform(get("/api/v1/omnisync/audit/ubid/" + ubid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", not(empty())));
    }

    @Test
    @DisplayName("Webhook receiver accepts department payloads")
    void testWebhookReceiver() throws Exception {
        Map<String, Object> webhookPayload = Map.of(
                "ubid", "KA-BIZ-99005",
                "event_type", "ADDRESS_UPDATE",
                "registered_address", Map.of("line1", "Webhook Test", "city", "B", "pin", "560001")
        );

        mockMvc.perform(post("/api/v1/omnisync/webhook/gst")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(webhookPayload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("accepted")));
    }

    @Test
    @DisplayName("Dashboard stats endpoint returns valid data")
    void testDashboardStats() throws Exception {
        mockMvc.perform(get("/api/v1/omnisync/dashboard/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPropagations", greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.lastUpdated", notNullValue()));
    }

    @Test
    @DisplayName("UBID validation rejects invalid format")
    void testUbidValidation() throws Exception {
        SwsEventRequest request = SwsEventRequest.builder()
                .ubid("INVALID-UBID")
                .eventType("ADDRESS_UPDATE")
                .timestamp(Instant.now())
                .payload(Map.of())
                .build();

        mockMvc.perform(post("/api/v1/omnisync/sws/event")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is("VALIDATION_ERROR")));
    }
}
