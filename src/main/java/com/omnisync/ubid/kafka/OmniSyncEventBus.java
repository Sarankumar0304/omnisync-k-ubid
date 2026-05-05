package com.omnisync.ubid.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Kafka Event Bus — Mock implementation for local development.
 *
 * In production: replace with actual KafkaTemplate<String, String> injection.
 * Topics:
 *   - omnisync.sws.events       (SWS → Dept events)
 *   - omnisync.dept.events      (Dept → SWS events)
 *   - omnisync.retry.queue      (Failed propagations awaiting retry)
 *   - omnisync.audit.events     (Audit entries for external SIEM)
 *   - omnisync.conflict.alerts  (Conflict detection alerts)
 *
 * The mock uses an in-memory LinkedBlockingQueue to simulate
 * Kafka's publish-subscribe semantics without requiring a broker.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OmniSyncEventBus {

    private final ObjectMapper objectMapper;

    @Value("${omnisync.kafka.mock:true}")
    private boolean mockMode;

    // In-memory queues simulating Kafka topics
    private final BlockingQueue<String> swsEventQueue   = new LinkedBlockingQueue<>();
    private final BlockingQueue<String> deptEventQueue  = new LinkedBlockingQueue<>();
    private final BlockingQueue<String> retryQueue      = new LinkedBlockingQueue<>();
    private final BlockingQueue<String> auditEventQueue = new LinkedBlockingQueue<>();

    public static final String TOPIC_SWS_EVENTS      = "omnisync.sws.events";
    public static final String TOPIC_DEPT_EVENTS     = "omnisync.dept.events";
    public static final String TOPIC_RETRY_QUEUE     = "omnisync.retry.queue";
    public static final String TOPIC_AUDIT_EVENTS    = "omnisync.audit.events";
    public static final String TOPIC_CONFLICT_ALERTS = "omnisync.conflict.alerts";

    /**
     * Publish an event to the appropriate topic.
     */
    public void publish(String topic, String key, Object payload) {
        try {
            String message = objectMapper.writeValueAsString(Map.of(
                    "key", key,
                    "topic", topic,
                    "payload", payload,
                    "timestamp", java.time.Instant.now().toString()
            ));

            if (mockMode) {
                publishMock(topic, message);
            } else {
                // Production: kafkaTemplate.send(topic, key, message);
                log.info("[KAFKA] Would publish to topic={} key={}", topic, key);
            }
        } catch (JsonProcessingException e) {
            log.error("[KAFKA] Serialization error: {}", e.getMessage());
        }
    }

    private void publishMock(String topic, String message) {
        switch (topic) {
            case TOPIC_SWS_EVENTS      -> swsEventQueue.offer(message);
            case TOPIC_DEPT_EVENTS     -> deptEventQueue.offer(message);
            case TOPIC_RETRY_QUEUE     -> retryQueue.offer(message);
            case TOPIC_AUDIT_EVENTS    -> auditEventQueue.offer(message);
            default -> log.debug("[KAFKA-MOCK] Unknown topic: {}", topic);
        }
        log.debug("[KAFKA-MOCK] Published to topic={} queue-depth={}",
                topic, getQueueDepth(topic));
    }

    public int getQueueDepth(String topic) {
        return switch (topic) {
            case TOPIC_SWS_EVENTS  -> swsEventQueue.size();
            case TOPIC_DEPT_EVENTS -> deptEventQueue.size();
            case TOPIC_RETRY_QUEUE -> retryQueue.size();
            default -> 0;
        };
    }

    public int getTotalPending() {
        return swsEventQueue.size() + deptEventQueue.size() + retryQueue.size();
    }
}
