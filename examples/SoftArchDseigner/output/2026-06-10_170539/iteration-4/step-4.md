# ADD Step 4: Choose One or More Design Concepts (Iteration 4)

---

## 4A: CI/CD Pipeline Design

### Selected Concept: Container-Native Pipeline with Environment Promotion

| Aspect | Design |
|--------|--------|
| **Repository Structure** | Monorepo with 6 modules (per CON-3, proprietary Git platform): `frontend/`, `api-gateway/`, `pricing-service/`, `hotel-management-service/`, `channel-management-adapter/`, `shared-lib/` |
| **Branching Strategy** | Trunk-based development. Short-lived feature branches. `main` branch is always deployable. Release tags for production. |
| **Build Tool** | Maven/Gradle for Java services (per CRN-2). npm/Angular CLI for frontend. |
| **Containerization** | Each deployable produces a Docker/OCI container image. Images are immutable and tagged with Git commit SHA. |
| **Container Registry** | Cloud-managed container registry (CON-6). |
| **Pipeline Orchestrator** | Cloud-native CI/CD platform integrated with Git platform (CON-3). |

### Pipeline Stages

| Stage | Trigger | Actions | Gates |
|-------|---------|---------|-------|
| **1. Build & Unit Test** | Push to feature branch / main | Compile, run unit tests for changed modules | All unit tests pass |
| **2. Static Analysis** | Stage 1 passes | Linting, code style, dependency vulnerability scan, architectural fitness functions | No critical/high issues |
| **3. Integration Test** | Stage 2 passes | Spin up service with stubbed dependencies (per QA-9). Run integration tests against stubs. | All integration tests pass |
| **4. Contract Test** | Stage 2 passes | Publish contract; verify against consumer contracts | No contract violations |
| **5. Container Build** | Stage 3 passes (on main) | Build container image, tag with SHA + `latest` | Image builds successfully |
| **6. Deploy to Dev** | Stage 5 passes (main) | Deploy to development environment. Smoke tests. | Smoke tests pass |
| **7. Deploy to Staging** | Manual trigger (or auto on main) | Deploy to staging. Run full regression suite. | Regression passes |
| **8. Deploy to Production** | Manual approval | Blue-green or rolling deployment. Canary for Pricing Service changes. | Canary metrics healthy for 10 min |

### Environment Promotion Strategy

| Environment | Purpose | Configuration Source | Deployment Trigger |
|-------------|---------|---------------------|-------------------|
| **Dev** | Developer integration testing | `config/dev/` | Auto on merge to main |
| **Staging** | Pre-production validation, demo (CON-4: MVP demo) | `config/staging/` | Manual / daily auto |
| **Production** | Live system | `config/prod/` | Manual approval |
| **Per-Developer** (optional) | Local development | `config/local/` | Docker Compose |

### Rollback Strategy
- Container images are immutable — rollback = deploy previous image tag.
- Database migrations are backward-compatible (expand-contract pattern).
- Blue-green deployment: keep previous version running until new version is healthy for 10 minutes.

---

## 4B: Monitoring Infrastructure Design

### Selected Concept: Cloud-Native Observability Stack

| Layer | Technology | Rationale |
|-------|-----------|-----------|
| **Metrics Export** | Micrometer (Java) + Prometheus endpoint | Standard JVM ecosystem, aligns with CRN-2 (Java team knowledge). Each service exposes `GET /actuator/prometheus`. |
| **Metrics Collection** | Prometheus (cloud-managed or self-managed) | Pull-based scraping. Scrapes `/actuator/prometheus` on each service instance. |
| **Time-Series DB** | Prometheus TSDB (or cloud-managed equivalent per CON-6) | Purpose-built for metrics. Retention: 15 days raw, 1 year aggregated. |
| **Dashboards** | Grafana (or cloud-managed equivalent) | Pre-built dashboards. Operational and business views. |
| **Alerting** | Alertmanager + Notification Channels | Alert rules → PagerDuty/email/Slack based on severity. |
| **Log Aggregation** | Structured JSON logging → Cloud log aggregation service (CON-6) | Each service logs in JSON to stdout. Cloud agent collects and indexes. |
| **Distributed Tracing** | Optional future addition | Not in MVP scope but instrument with trace IDs in headers from day one. |
| **Health Checks** | Standardized `/health/live` and `/health/ready` on all services | Already defined for Pricing Service in Iteration 3. Standardize across all services. |

### Metric Operationalization (from Iteration 3's 14 metrics)

| Metric Name | Alert Rule | Severity | Channel |
|-------------|------------|----------|---------|
| `price_change_outbox_pending` | > 500 for 5 min | Warning | Slack |
| `price_change_outbox_pending` | > 2000 for 10 min | Critical | PagerDuty |
| `cms_delivery_failure_total` | Rate > 5/min for 5 min | Warning | Slack |
| `cms_delivery_failure_total` | Rate > 20/min for 10 min | Critical | PagerDuty |
| `cms_circuit_breaker_state` | = 1 (OPEN) for > 2 min | Critical | PagerDuty |
| `dead_letter_queue_size` | > 0 | Warning | Slack |
| `dead_letter_queue_size` | > 10 | Critical | PagerDuty |
| `kafka_consumer_lag` | > 5000 for 5 min | Warning | Slack |
| `kafka_consumer_lag` | > 20000 for 10 min | Critical | PagerDuty |
| `pricing_service_config_staleness_seconds` | > 300 (5 min) | Warning | Slack |
| `pricing_service_config_staleness_seconds` | > 1800 (30 min) | Critical | PagerDuty |
| `pricing_service_degraded_mode` | = 1 | Warning | Slack |
| `gateway_upstream_availability` | = 0 for any service > 1 min | Critical | PagerDuty |
| `price_change_latency_p99` | > 200ms for 5 min | Warning | Slack |
| `price_query_latency_p99` | > 100ms for 5 min | Warning | Slack |

### Dashboard Definitions

| Dashboard | Audience | Key Panels |
|-----------|----------|------------|
| **Operational Overview** | Ops / SRE | Service health (green/red), request rates, error rates, P99 latency per service, circuit breaker states |
| **Publication Reliability** | Ops / Business | Price publication success rate (target: 100%), outbox pending count, CMS delivery success rate, DLQ size over time, consumer lag |
| **System Capacity** | Ops / Architects | Query throughput vs. SLA, scaling events, DB connection pools, Redis hit ratio, JVM heap/memory |
| **Business Metrics** | Product / Business | Daily price changes per hotel, simulation vs. actual change ratio, active user count |

---

## 4C: Test Architecture Design

### Selected Concept: Layered Test Pyramid with Contract Tests

| Layer | Scope | Technology | Environment | Mocking |
|-------|-------|-----------|-------------|---------|
| **Unit Tests** | Single class/method | JUnit 5 (Java), Jasmine/Karma (Angular) | In-process | All dependencies mocked |
| **Integration Tests** | Single service with stubbed externals | Spring Boot Test (Java), Angular TestBed | In-process or containerized | DB stubbed (H2 in-memory), Kafka stubbed, external APIs stubbed via port interfaces (QA-9) |
| **Contract Tests** | Service boundary (provider-consumer) | Spring Cloud Contract or Pact | CI pipeline | Consumer-side: stub provider. Provider-side: verify against contract. |
| **End-to-End Tests** | Critical user journeys only | Cypress (frontend) or REST Assured | Staging environment | All real services, external systems stubbed (CMS, Identity Service) |

### Test Isolation Strategy (QA-9)

QA-9 requires "100% of system elements support integration testing independent of external systems." This is achieved through:

| System Element | External Dependency | Isolation Mechanism |
|---------------|---------------------|---------------------|
| Pricing Service | Pricing DB | H2 in-memory database or Testcontainers |
| Pricing Service | Redis | Embedded Redis or Testcontainers |
| Pricing Service | Kafka | Embedded Kafka or Testcontainers |
| Pricing Service | Hotel Management Service | Mock/stub of HotelConfigProvisioningPort |
| Channel Mgmt Adapter | Kafka | Embedded Kafka |
| Channel Mgmt Adapter | CMS | Mock/stub of CMSClient port |
| Hotel Management Service | Management DB | H2 in-memory or Testcontainers |
| Hotel Management Service | Kafka | Embedded Kafka |
| API Gateway | Pricing Service, HMS | WireMock stubs |
| API Gateway | Identity Service | WireMock stub of JWKS endpoint |
| HPS Web Frontend | API Gateway | Angular HttpClientTestingModule or MSW |

### Architectural Fitness Functions (CRN-4)

To avoid technical debt, the following architectural fitness functions are integrated into the CI pipeline (Stage 2: Static Analysis):

| Fitness Function | Check | Tool |
|-----------------|-------|------|
| **Layer Dependency** | Domain layer has no dependency on adapters | ArchUnit (Java) |
| **Port Isolation** | Core logic only references port interfaces, not adapter implementations | ArchUnit |
| **No Cross-Service DB Access** | Services only access their own database | ArchUnit |
| **Kafka Topic Ownership** | Only Pricing Service produces to `price-changes` | Code review + ArchUnit |
| **API Gateway Route Pattern** | All routes follow defined pattern | Custom lint rule |
| **Angular Module Boundaries** | Feature modules don't import internals of other feature modules | ESLint plugin |

---

## 4D: Configuration Management Design

### Selected Concept: Cloud-Native Externalized Configuration

| Configuration Category | Mechanism | Example |
|------------------------|-----------|---------|
| **Environment-specific** | Environment variables injected via container orchestrator ConfigMaps (CON-6) | `DB_HOST`, `REDIS_HOST`, `KAFKA_BOOTSTRAP_SERVERS` |
| **Secrets** | Cloud secret manager (CON-6). Injected as environment variables or mounted files. Never in code or config files. | `DB_PASSWORD`, `JWT_SIGNING_KEY`, `CMS_API_KEY` |
| **Feature Flags** | Configuration service or environment variables with sensible defaults | `FEATURE_SIMULATION_ENABLED=true`, `FEATURE_GRPC_ENABLED=false` |
| **Service Discovery** | Kubernetes DNS (CON-6). Services reference each other by service name. | `pricing-service:8080`, `hotel-management-service:8080` |

### Per-Environment Configuration Structure (QA-7)

```
config/
├── dev/
│   ├── pricing-service.yaml       # Dev DB, low-cost Redis tier
│   ├── hotel-management-service.yaml
│   ├── api-gateway.yaml
│   └── channel-management-adapter.yaml
├── staging/
│   ├── pricing-service.yaml       # Staging DB, mirrors prod topology
│   ├── ...
└── prod/
    ├── pricing-service.yaml       # Prod DB, multi-AZ Redis, full replicas
    ├── ...
```

**Key principle (QA-7):** The same container image is deployed to dev, staging, and prod. Only the injected configuration differs. No code changes between environments.

---

## 4E: Hotel Management Service — Internal Design

### Selected Concept: Standard Layered Architecture within Ports & Adapters

Since HPS-4/5/6 are CRUD-heavy (manage hotels, rates, users), a full CQRS separation (like Pricing Service) is unnecessary. A standard layered architecture within the Ports & Adapters pattern is appropriate.

| Layer | Responsibility |
|-------|---------------|
| **Domain Model** | Hotel, Rate, RateRule, RoomType, User, Permission aggregates. Business invariants (e.g., a hotel must have at least one room type). |
| **Application Services** | Orchestrate use cases: HotelManagementService, RateManagementService, UserManagementService. Transaction management. Authorization enforcement. |
| **Ports (Interfaces)** | HotelRepository, RateRepository, UserRepository, ConfigChangePublisher (already defined). |
| **Inbound Adapters** | REST controllers for CRUD operations. |
| **Outbound Adapters** | DB adapter (JPA/JDBC), Kafka adapter (already partially defined). |

### Key Design Decisions

| Decision | Rationale |
|----------|-----------|
| **Single DB transaction per use case** | CRUD operations are simple — no need for complex sagas or event sourcing. |
| **Hotel-level authorization** | Each user has a list of authorized hotel IDs. Application services filter queries and validate mutations against this list. The `X-Authorized-Hotels` header from API Gateway is the source of truth. |
| **Rate rule immutability** | When a rate rule is updated, create a new version rather than mutating in place. Old versions retained for audit (and because Pricing Service may briefly use a stale cached version). |
| **Config change events on every mutation** | Any change to hotel config or rate rules publishes to `hotel-config-changes` Kafka topic for Pricing Service cache refresh. |

---

## 4F: Database Logical Schemas

### Pricing Database Schema

```sql
-- Core price data
CREATE TABLE prices (
    hotel_id        VARCHAR(36) NOT NULL,
    date            DATE NOT NULL,
    room_type_id    VARCHAR(36) NOT NULL,
    rate_id         VARCHAR(36) NOT NULL,
    amount          DECIMAL(10,2) NOT NULL,
    currency        CHAR(3) NOT NULL DEFAULT 'USD',
    price_type      ENUM('BASE', 'DERIVED', 'FIXED') NOT NULL,
    base_price_id   VARCHAR(36),  -- FK to self for derived rates
    changed_by      VARCHAR(36) NOT NULL,
    changed_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (hotel_id, date, room_type_id, rate_id)
);

CREATE INDEX idx_prices_query ON prices(hotel_id, date, room_type_id);

-- Transactional outbox (from Iteration 3)
CREATE TABLE price_change_outbox (
    id              UUID PRIMARY KEY,
    aggregate_id    VARCHAR(36) NOT NULL,  -- hotelId
    event_type      VARCHAR(100) NOT NULL,
    payload         JSONB NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at    TIMESTAMP,
    retry_count     INT NOT NULL DEFAULT 0
);

CREATE INDEX idx_outbox_pending ON price_change_outbox(published_at)
    WHERE published_at IS NULL;
```

### Management Database Schema

```sql
CREATE TABLE hotels (
    id              UUID PRIMARY KEY,
    name            VARCHAR(200) NOT NULL,
    address         TEXT,
    currency        CHAR(3) NOT NULL DEFAULT 'USD',
    tax_rate        DECIMAL(5,4) NOT NULL,  -- e.g., 0.1000 = 10%
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE room_types (
    id              UUID PRIMARY KEY,
    hotel_id        UUID NOT NULL REFERENCES hotels(id),
    name            VARCHAR(100) NOT NULL,  -- e.g., "Standard", "Deluxe", "Suite"
    base_capacity   INT NOT NULL DEFAULT 2,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (hotel_id, name)
);

CREATE TABLE rates (
    id              UUID PRIMARY KEY,
    hotel_id        UUID NOT NULL REFERENCES hotels(id),
    name            VARCHAR(100) NOT NULL,  -- e.g., "Flexible", "Non-Refundable", "Breakfast Included"
    description     TEXT,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE rate_rules (
    id              UUID PRIMARY KEY,
    rate_id         UUID NOT NULL REFERENCES rates(id),
    version         INT NOT NULL,
    rule_type       ENUM('PERCENTAGE_ADJUSTMENT', 'FIXED_ADJUSTMENT', 'CONDITIONAL') NOT NULL,
    adjustment      DECIMAL(10,4) NOT NULL,  -- percentage (0.10) or fixed amount
    condition       JSONB,  -- e.g., {"minStay": 3, "advanceBookingDays": 14}
    effective_from  TIMESTAMP NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (rate_id, version)
);

CREATE TABLE users (
    id              UUID PRIMARY KEY,
    external_id     VARCHAR(100) NOT NULL UNIQUE,  -- ID from Cloud Identity Service
    username        VARCHAR(100) NOT NULL,
    role            ENUM('ADMIN', 'BUSINESS_USER') NOT NULL,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE user_hotel_permissions (
    user_id         UUID NOT NULL REFERENCES users(id),
    hotel_id        UUID NOT NULL REFERENCES hotels(id),
    permission      ENUM('READ', 'WRITE') NOT NULL,
    granted_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, hotel_id, permission)
);

CREATE INDEX idx_user_permissions ON user_hotel_permissions(user_id);
CREATE INDEX idx_room_types_hotel ON room_types(hotel_id);
CREATE INDEX idx_rates_hotel ON rates(hotel_id);
```

---

## 4G: API Gateway Technology Decision (R4 Resolution)

### Alternatives Revisited

**Option A: Cloud-Managed API Gateway (AWS API Gateway / Azure API Management / GCP API Gateway)**

| Criterion | Assessment |
|-----------|------------|
| CON-6 (Cloud-Native) | ✅ Fully managed, auto-scaling, integrated with cloud ecosystem |
| CRN-2 (Java expertise) | ⚠ Team has less control; configuration via cloud-specific DSL, not Java |
| QA-6 (Modifiability) | ⚠ Adding gRPC depends on cloud provider's feature set. Some support it, others don't. |
| Operational Complexity | ✅ Low — managed service handles scaling, TLS, DDoS protection |
| Custom Auth Logic | ⚠ Cloud gateway supports JWT validation but custom claim propagation (X-User-Id, X-Authorized-Hotels) may require Lambda/Function integration — adds complexity |

**Option B: Self-Managed Java Gateway (Spring Cloud Gateway)**

| Criterion | Assessment |
|-----------|------------|
| CON-6 (Cloud-Native) | ✅ Runs in containers, can be deployed like any microservice. Still cloud-native. |
| CRN-2 (Java expertise) | ✅ Full Java control. Team already knows Spring ecosystem. Custom filters are straightforward Java code. |
| QA-6 (Modifiability) | ✅ Adding gRPC: add Spring Cloud Gateway gRPC route. Same codebase, no external dependency. |
| Operational Complexity | ⚠ Moderate — team manages scaling, TLS, updates. But containerized, so similar to other services. |
| Custom Auth Logic | ✅ Trivial — AuthFilter, RoleEnforcer, ClaimPropagator are Spring filters. Already designed in Iteration 2. |
| CON-4 (2-Month MVP) | ✅ Team already knows Java/Spring — no learning curve for gateway technology. |

### Decision: Self-Managed Java Gateway (Spring Cloud Gateway)

**Rationale:** The gateway's custom logic (JWT claim extraction, role enforcement, claim propagation, distributed rate limiting with Redis) is non-trivial and directly leverages the team's Java expertise (CRN-2). A cloud-managed gateway would require expressing this logic in the cloud provider's specific extension mechanism (Lambda, custom authorizer), adding complexity and potential vendor lock-in. Spring Cloud Gateway runs in containers, fully aligned with CON-6 (cloud-native). Adding gRPC support (QA-6 validation) is straightforward with Spring's gRPC integration.

---

## 4H: OutboxPoller Resilience (R14 Resolution)

**Problem:** If the Pricing Service write instance's OutboxPoller fails or the instance crashes, outbox messages accumulate with no other instance polling.

### Selected Solution: Cooperative Polling with Distributed Lock

| Aspect | Design |
|--------|--------|
| **All Pricing Service instances run OutboxPoller** | Both write instances (and even query instances) can run the poller. Eliminates single-process risk. |
| **Distributed lock per outbox record** | Use `SELECT ... FOR UPDATE SKIP LOCKED` (PostgreSQL) or Redis distributed lock to ensure only one instance processes each outbox record. |
| **Lock granularity** | Per-row locking. Each poller selects a batch of unlocked records, locks them, publishes to Kafka, and marks as published. |
| **Failure recovery** | If an instance crashes holding locks, the DB transaction rolls back (locks released). Next poll cycle picks up the records. |
| **No leader election needed** | Cooperative polling is simpler and more robust than leader election. |

---

## 4I: CMA Idempotency Hardening (R16 Resolution)

**Problem:** CMA instances maintain in-memory LRU caches for idempotency. During Kafka consumer group rebalance, a different CMA instance may receive the same event, and its cache won't have the idempotency record.

### Selected Solution: Shared Idempotency Store

| Aspect | Design |
|--------|--------|
| **Shared store** | Replace per-instance in-memory LRU with a shared Redis cache (already available per Iteration 3). |
| **Key pattern** | `idempotency:cma:{eventId}` with TTL = 48 hours (twice the Kafka retention). |
| **Check-and-set** | `SET idempotency:cma:{eventId} "processed" NX EX 172800`. Returns OK if new, nil if already exists. Atomic. |
| **Fallback** | If Redis is unavailable, CMA falls back to a local in-memory LRU (less reliable but prevents total failure). The circuit breaker on CMS already prevents duplicate delivery to CMS from different instances. |
| **Reconciliation** | Periodic sweep: query CMS for recently published price IDs and reconcile with idempotency store. |

---

## Summary of Design Concept Selections

| Element | Selected Concept | Primary Driver |
|---------|-----------------|----------------|
| CI/CD Pipeline | Container-native, trunk-based, environment promotion | CRN-5, CON-3, CON-4 |
| Monitoring | Micrometer + Prometheus + Grafana + Alertmanager | QA-8, R18 |
| Testing | Layered pyramid with contract tests, port stubs, fitness functions | QA-9, CRN-4 |
| Configuration | Environment variables + cloud secret manager + feature flags | QA-7 |
| HMS Internals | Standard layered architecture with hotel-level authorization | HPS-4/5/6 |
| Database Schemas | Normalized relational with outbox table | All use cases |
| API Gateway Tech | Self-managed Spring Cloud Gateway (Java) | R4, CRN-2, QA-6 |
| OutboxPoller | Cooperative polling with DB row locking | R14 |
| CMA Idempotency | Shared Redis with local fallback | R16 |