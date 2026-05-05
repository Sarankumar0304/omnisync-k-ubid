package com.omnisync.ubid.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import redis.embedded.RedisServer;

import java.io.IOException;

/**
 * Application configuration:
 *   - Embedded Redis (for local dev — no Redis install needed)
 *   - Jackson ObjectMapper with Java time support
 *   - OpenAPI / Swagger UI branding
 */
@Configuration
@Slf4j
public class AppConfig {

    @Value("${omnisync.redis.embedded:true}")
    private boolean useEmbeddedRedis;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    private RedisServer redisServer;

    @PostConstruct
    public void startEmbeddedRedis() {
        if (useEmbeddedRedis) {
            try {
                redisServer = new RedisServer(redisPort);
                redisServer.start();
                log.info("[CONFIG] Embedded Redis started on port {}", redisPort);
            } catch (Exception e) {
                log.warn("[CONFIG] Embedded Redis start failed (may already be running): {}", e.getMessage());
            }
        }
    }

    @PreDestroy
    public void stopEmbeddedRedis() {
        if (redisServer != null && redisServer.isActive()) {
            try {
                redisServer.stop();
                log.info("[CONFIG] Embedded Redis stopped");
            } catch (Exception e) {
                log.warn("[CONFIG] Embedded Redis stop error: {}", e.getMessage());
            }
        }
    }

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("OmniSync-K UBID API")
                        .description("""
                                Event-Driven Middleware for Two-Way Government Interoperability.
                                
                                Connects Karnataka's Single Window System (SWS) with 40+ legacy department systems
                                using UBID as the universal join key.
                                
                                **Direction 1:** SWS → Department Systems (Address Change, Signatory Update, etc.)
                                **Direction 2:** Department Systems → SWS (via webhook, polling, or CDC)
                                """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("OmniSync Team")
                                .email("team@omnisync-k.gov.in"))
                        .license(new License().name("Karnataka Government Hackathon 2026")));
    }
}
