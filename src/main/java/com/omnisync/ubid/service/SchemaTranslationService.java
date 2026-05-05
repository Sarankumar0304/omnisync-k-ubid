package com.omnisync.ubid.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Schema Translation Engine.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SchemaTranslationService {

    private ObjectMapper objectMapper = new ObjectMapper();


    public String translate(String departmentId, String eventType, Map<String, Object> swsPayload) {
        log.info("[TRANSLATION] Translating eventType={} for dept={}", eventType, departmentId);

        return switch (departmentId.toLowerCase()) {
            case "shops_establishment" -> translateToShopsXml(swsPayload, eventType);
            case "factories" -> translateToFactoriesSql(swsPayload, eventType);
            case "gst" -> translateToGstJson(swsPayload, eventType);
            case "labour" -> translateToLabourJson(swsPayload, eventType);
            default -> {
                log.warn("[TRANSLATION] Unknown department: {} — using passthrough", departmentId);
                yield toJson(swsPayload);
            }
        };
    }

    public Map<String, Object> translateToSwsFormat(String departmentId,
                                                    String eventType,
                                                    Map<String, Object> deptPayload) {

        log.info("[TRANSLATION] Translating dept={} eventType={} → SWS canonical", departmentId, eventType);

        Map<String, Object> canonical = new HashMap<>();

        switch (departmentId.toLowerCase()) {
            case "shops_establishment" -> translateShopsToCanonical(deptPayload, canonical);
            case "factories" -> translateFactoriesToCanonical(deptPayload, canonical);
            case "gst" -> translateGstToCanonical(deptPayload, canonical);
            case "labour" -> translateLabourToCanonical(deptPayload, canonical);
            default -> canonical.putAll(deptPayload);
        }

        return canonical;
    }

    // ─────────────────────────────────────────────────────────────

    private String translateToShopsXml(Map<String, Object> payload, String eventType) {

        Object addr = payload.get("registered_address");

        if (addr instanceof Map) {
            Map<String, Object> address = (Map<String, Object>) addr;

            return """
                    <UpdateEstablishmentAddress xmlns="http://shops.karnataka.gov.in/ws">
                      <EstabRegNo>SE-KA-%s</EstabRegNo>
                      <NewAddress>
                        <AddressLine1>%s</AddressLine1>
                        <City>%s</City>
                        <StateName>%s</StateName>
                        <PINCode>%s</PINCode>
                      </NewAddress>
                    </UpdateEstablishmentAddress>
                    """.formatted(
                    payload.getOrDefault("ubid", ""),
                    address.get("line1"),
                    address.get("city"),
                    address.getOrDefault("state", "Karnataka"),
                    address.get("pin")
            );
        }

        if ("SIGNATORY_CHANGE".equals(eventType)) {
            return """
                    <UpdateAuthorisedSignatory xmlns="http://shops.karnataka.gov.in/ws">
                      <EstabRegNo>SE-KA-%s</EstabRegNo>
                      <AuthSignatory>%s</AuthSignatory>
                    </UpdateAuthorisedSignatory>
                    """.formatted(
                    payload.getOrDefault("ubid", ""),
                    payload.getOrDefault("authorised_signatory", "")
            );
        }

        return "<UnknownEvent/>";
    }

    private String translateToFactoriesSql(Map<String, Object> payload, String eventType) {

        Object addr = payload.get("registered_address");

        if (addr instanceof Map) {
            Map<String, Object> address = (Map<String, Object>) addr;

            return """
                    {
                      "operation": "UPDATE",
                      "table": "factory_registrations",
                      "set": {
                        "address_line1": "%s",
                        "address_city": "%s",
                        "address_pin": "%s",
                        "last_modified": "%s",
                        "modified_by": "OMNISYNC_GATEWAY"
                      },
                      "where": {
                        "factory_ubid": "%s"
                      }
                    }
                    """.formatted(
                    address.get("line1"),
                    address.get("city"),
                    address.get("pin"),
                    java.time.Instant.now(),
                    payload.getOrDefault("ubid", "")
            );
        }

        return "{}";
    }

    private String translateToGstJson(Map<String, Object> payload, String eventType) {

        Object addr = payload.get("registered_address");

        if (addr instanceof Map) {
            Map<String, Object> address = (Map<String, Object>) addr;

            return """
                    {
                      "update_type": "PRINCIPAL_ADDRESS",
                      "core_business_details": {
                        "prncipalPlaceBusiness": {
                          "addrBnm": "%s",
                          "addrCity": "%s",
                          "addrPncd": "%s",
                          "addrStcd": "29"
                        }
                      }
                    }
                    """.formatted(
                    address.get("line1"),
                    address.get("city"),
                    address.get("pin")
            );
        }

        return "{}";
    }

    private String translateToLabourJson(Map<String, Object> payload, String eventType) {

        Object addr = payload.get("registered_address");

        if (addr instanceof Map) {
            Map<String, Object> address = (Map<String, Object>) addr;

            return """
                    {
                      "update_type": "office_address",
                      "office_address": "%s, %s - %s",
                      "contact_person": "%s"
                    }
                    """.formatted(
                    address.get("line1"),
                    address.getOrDefault("city", ""),
                    address.getOrDefault("pin", ""),
                    payload.getOrDefault("authorised_signatory", "")
            );
        }

        return "{}";
    }

    // ─────────────────────────────────────────────────────────────

    private void translateShopsToCanonical(Map<String, Object> dept, Map<String, Object> canonical) {

        Object signatory = dept.get("AuthSignatory");
        if (signatory != null) {
            canonical.put("authorised_signatory", signatory);
        }

        if (dept.containsKey("AddressLine1")) {
            canonical.put("registered_address", Map.of(
                    "line1", dept.getOrDefault("AddressLine1", ""),
                    "city", dept.getOrDefault("City", ""),
                    "pin", dept.getOrDefault("PINCode", "")
            ));
        }
    }

    private void translateFactoriesToCanonical(Map<String, Object> dept, Map<String, Object> canonical) {

        if (dept.containsKey("address_line1")) {
            canonical.put("registered_address", Map.of(
                    "line1", dept.getOrDefault("address_line1", ""),
                    "city", dept.getOrDefault("address_city", ""),
                    "pin", dept.getOrDefault("address_pin", "")
            ));
        }

        Object signatory = dept.get("authorised_signatory_name");
        if (signatory != null) {
            canonical.put("authorised_signatory", signatory);
        }
    }

    private void translateGstToCanonical(Map<String, Object> dept, Map<String, Object> canonical) {
        canonical.putAll(dept);
    }

    private void translateLabourToCanonical(Map<String, Object> dept, Map<String, Object> canonical) {
        canonical.putAll(dept);
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }
}
