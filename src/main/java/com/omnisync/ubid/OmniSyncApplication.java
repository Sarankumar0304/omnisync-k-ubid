package com.omnisync.ubid;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * OmniSync-K UBID — Two-Way Interoperability Middleware
 * Karnataka Single Window System ↔ Legacy Department Systems
 *
 * Architecture: API Gateway + Adapter Pattern + Event-Driven (Kafka)
 * Author: OmniSync Team
 */
@SpringBootApplication
@EnableScheduling
public class OmniSyncApplication {

    public static void main(String[] args) {
        System.out.println("""
            ╔══════════════════════════════════════════════════════════════╗
            ║          OmniSync-K UBID  —  v1.0.0                        ║
            ║  Two-Way Interoperability: Karnataka SWS ↔ Dept Systems    ║
            ║  Swagger UI  →  http://localhost:8080/swagger-ui.html      ║
            ║  H2 Console  →  http://localhost:8080/h2-console           ║
            ║  Actuator    →  http://localhost:8080/actuator/health      ║
            ╚══════════════════════════════════════════════════════════════╝
            """);
        SpringApplication.run(OmniSyncApplication.class, args);
    }
}
