# 🔄 OmniSync-K UBID
### Event-Driven Middleware for Two-Way Government Interoperability

> **Karnataka Hackathon 2026 — Theme 2: Two-Way Interoperability**
> Connects Karnataka's Single Window System (SWS) ↔ 40+ Legacy Department Systems via UBID

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.5-brightgreen)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-17-orange)](https://openjdk.org/projects/jdk/17/)
[![License](https://img.shields.io/badge/License-Demo-blue)](LICENSE)

---

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    OmniSync-K UBID Middleware                │
│                                                             │
│  SWS ──► [API Gateway] ──► [Schema Translator]             │
│                    │              │                          │
│                    ▼              ▼                          │
│             [Conflict Resolver] [Adapter Layer]             │
│                    │              │                          │
│                    ▼              ▼                          │
│            [Idempotency Store]  [Retry Queue]               │
│                (Redis)          (Kafka/Mock)                 │
│                    │              │                          │
│                    ▼              ▼                          │
│             [Audit Ledger]   [Sidecar Poller]               │
│              (PostgreSQL)    (CDC for silent depts)         │
│                    │                                         │
│  Departments ◄─────┴──── Shops & Estab │ Factories │ GST   │
└─────────────────────────────────────────────────────────────┘
```

## ⚡ Quick Start (Local — 5 minutes)

### Prerequisites
- Java 17+
- Maven 3.8+

**No Kafka, no Redis, no PostgreSQL install needed!**
The app uses embedded H2, embedded Redis, and a mock Kafka bus for local development.

### Run

```bash
# Clone
git clone https://github.com/YOUR_USERNAME/omnisync-k-ubid.git
cd omnisync-k-ubid

# Build
mvn clean package -DskipTests

# Run
mvn spring-boot:run
```

### See Output Immediately

Once started, open these in your browser:

| URL | What you see |
|-----|-------------|
| http://localhost:8080/swagger-ui.html | **Interactive API docs — try all endpoints** |
| http://localhost:8080/h2-console | **Database — see audit logs and service requests** |
| http://localhost:8080/actuator/health | Health check |
| http://localhost:8080/api/v1/omnisync/info | API overview |
| http://localhost:8080/api/v1/omnisync/dashboard/stats | Live stats |

**H2 Console login:**
- JDBC URL: `jdbc:h2:mem:omnisyncdb`
- Username: `sa`
- Password: *(leave blank)*

---

## 🧪 Try the 3 Key Scenarios via Swagger UI

Open **http://localhost:8080/swagger-ui.html**

### Scenario 1 — SWS Address Update → All Departments
`POST /api/v1/omnisync/sws/event`
```json
{
  "ubid": "KA-BIZ-00192",
  "eventType": "ADDRESS_UPDATE",
  "timestamp": "2026-04-30T10:42:31Z",
  "payload": {
    "registered_address": {
      "line1": "12 MG Road",
      "city": "Bengaluru",
      "state": "Karnataka",
      "pin": "560001"
    }
  }
}
```

### Scenario 2 — Department Signatory Change → SWS
`POST /api/v1/omnisync/dept/event`
```json
{
  "ubid": "KA-BIZ-00192",
  "departmentId": "factories",
  "eventType": "SIGNATORY_CHANGE",
  "timestamp": "2026-04-30T10:55:00Z",
  "payload": {
    "authorised_signatory_name": "Rajesh Kumar"
  }
}
```

### Scenario 3 — View Full Audit Trail
`GET /api/v1/omnisync/audit/ubid/KA-BIZ-00192`

---

## 📁 Project Structure

```
omnisync-k-ubid/
├── src/main/java/com/omnisync/ubid/
│   ├── OmniSyncApplication.java          # Entry point
│   ├── controller/
│   │   ├── OmniSyncController.java       # REST API (SWS events, Dept events, Webhooks, Audit)
│   │   └── GlobalExceptionHandler.java   # Structured error responses
│   ├── service/
│   │   ├── PropagationService.java       # Core orchestration engine (both directions)
│   │   ├── SchemaTranslationService.java # Multi-protocol translator (SOAP/SQL/REST)
│   │   ├── IdempotencyService.java       # Redis-backed deduplication
│   │   └── ServiceRequestRepository.java # JPA repository
│   ├── audit/
│   │   ├── AuditService.java             # Immutable append-only ledger
│   │   └── AuditLogRepository.java       # JPA repository
│   ├── conflict/
│   │   └── ConflictResolutionService.java# Policy engine (SWS_PRIORITY, FIELD_MERGE, etc.)
│   ├── adapter/
│   │   └── MockDepartmentAdapter.java    # Mock dept system calls (replace with real in Round 2)
│   ├── poller/
│   │   └── SidecarPoller.java           # Scheduled CDC polling for silent systems
│   ├── kafka/
│   │   └── OmniSyncEventBus.java        # Kafka (mock for local, real in prod)
│   ├── model/
│   │   ├── ServiceRequest.java           # Core domain entity
│   │   ├── AuditLog.java                # Immutable audit ledger entity
│   │   └── DepartmentSnapshot.java      # Polling snapshot entity
│   ├── dto/
│   │   └── OmniSyncDTOs.java            # Request/response DTOs
│   └── config/
│       ├── AppConfig.java               # Redis, Jackson, OpenAPI config
│       └── DataSeeder.java              # Demo data on startup
└── src/main/resources/
    ├── application.properties            # Full configuration
    └── department-adapters.json          # Externalized dept mapping config
```

## 🔑 Key Technical Decisions

| Challenge | OmniSync-K Solution |
|-----------|---------------------|
| Split-brain sync | Bidirectional event-driven middleware |
| Heterogeneous schemas | Adapter pattern + externalized mapping.json |
| Silent legacy systems | Sidecar Poller (snapshot diff / CDC) |
| Duplicate writes | Redis-backed SHA-256 idempotency keys |
| Concurrent conflicts | Configurable Policy Engine (SWS_PRIORITY / FIELD_MERGE) |
| Audit & traceability | PostgreSQL append-only immutable ledger |
| At-least-once delivery | Kafka + exponential backoff retry queue |

## 🚀 GitHub Setup

```bash
# Initialize and push
git init
git add .
git commit -m "feat: OmniSync-K UBID — Karnataka Hackathon 2026"
git branch -M main
git remote add origin https://github.com/YOUR_USERNAME/omnisync-k-ubid.git
git push -u origin main
```

## 🧪 Run Tests

```bash
mvn test
```

7 integration tests covering:
- ✅ Direction 1: SWS → Departments
- ✅ Direction 2: Dept → SWS  
- ✅ Idempotency (duplicate skip)
- ✅ Audit trail population
- ✅ Webhook receiver
- ✅ Dashboard stats
- ✅ UBID validation

---

*OmniSync Team — Karnataka Hackathon 2026 | Theme 2: Two-Way Interoperability*
