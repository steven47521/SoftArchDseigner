# ADD Step 5: Instantiate Architectural Elements, Assign Responsibilities, and Define Interfaces (Iteration 4)

---

## 5A: CI/CD Pipeline — Element Instantiation

### New Infrastructure Element: CI/CD Pipeline

| Attribute | Detail |
|-----------|--------|
| **Type** | Infrastructure / Process Element |
| **Technology** | Cloud-native CI/CD platform integrated with proprietary Git platform (CON-3) |
| **Repository** | Monorepo: `frontend/`, `api-gateway/`, `pricing-service/`, `hotel-management-service/`, `channel-management-adapter/`, `shared-lib/` |

### Pipeline Configuration (per deployable)

```
pipeline/
├── Jenkinsfile (or .github/workflows/ or equivalent)
├── scripts/
│   ├── build.sh
│   ├── test.sh
│   ├── container-build.sh
│   └── deploy.sh
├── docker/
│   ├── Dockerfile.pricing-service
│   ├── Dockerfile.hotel-management-service
│   ├── Dockerfile.api-gateway
│   ├── Dockerfile.channel-management-adapter
│   └── Dockerfile.frontend          # Multi-stage: build Angular → nginx
└── kubernetes/
    ├── pricing-service/
    │   ├── deployment.yaml
    │   ├── service.yaml
    │   └── configmap.yaml
    ├── hotel-management-service/
    ├── api-gateway/
    ├── channel-management-adapter/
    └── frontend/
        ├── deployment.yaml
        └── service.yaml
```

### Stage Definitions — Pricing Service Example

| Stage | Command/Action | Timeout | On Failure |
|-------|---------------|---------|------------|
| Checkout | `git clone` | 1 min | Fail build |
| Build | `mvn clean compile` | 3 min | Fail build |
| Unit Test | `mvn test` | 5 min | Fail build |
| Static Analysis | `mvn verify -Pstatic-analysis` (ArchUnit, Checkstyle, SpotBugs, OWASP dependency check) | 3 min | Warn on medium, fail on high/critical |
| Integration Test | `mvn verify -Pintegration-test` (Testcontainers: PostgreSQL, Redis, Kafka) | 10 min | Fail build |
| Contract Test | `mvn verify -Pcontract-test` (Spring Cloud Contract) | 5 min | Fail build |
| Container Build | `docker build -t pricing-service:$SHA .` + push to registry | 5 min | Fail build |
| Deploy Dev | `kubectl apply -f kubernetes/pricing-service/ -n dev` | 3 min | Fail, notify |
| Smoke Test | `newman run smoke-tests/pricing-service.postman_collection.json` | 3 min | Rollback dev, notify |
| Deploy Staging | Manual trigger → `kubectl apply -n staging` | 3 min | Fail, notify |
| Deploy Prod | Manual approval → blue-green deployment via `kubectl` | 10 min | Rollback to previous version |

---

## 5B: Monitoring Infrastructure — Element Instantiation

### New Infrastructure Elements

| Element | Type | Technology | Responsibilities |
|---------|------|-----------|-----------------|
| **Metrics Exporter (per service)** | Embedded Agent | Micrometer + Prometheus endpoint | Expose `/actuator/prometheus` on each service. Register custom meters for all 14 Iteration 3 metrics. |
| **Prometheus Server** | Managed Service (CON-6) or containerized | Prometheus | Scrape metrics endpoints every 15s. Store time-series data. Evaluate alert rules. |
| **Grafana** | Managed Service or containerized | Grafana | Render dashboards from Prometheus data sources. |
| **Alertmanager** | Managed Service or containerized | Alertmanager | Route alerts to PagerDuty, Slack, email. Handle alert grouping and inhibition. |
| **Log Aggregator** | Managed Service (CON-6) | Cloud-native log service | Collect stdout JSON logs from all containers. Index for search. |

### Prometheus Scrape Configuration

```yaml
scrape_configs:
  - job_name: 'pricing-service'
    kubernetes_sd_configs:
      - role: pod
        namespaces: [prod]
    relabel_configs:
      - source_labels: [__meta_kubernetes_pod_label_app]
        regex: pricing-service
        action: keep
    metrics_path: '/actuator/prometheus'
    scrape_interval: 15s

  - job_name: 'hotel-management-service'
    # ... similar

  - job_name: 'api-gateway'
    # ... similar

  - job_name: 'channel-management-adapter'
    # ... similar
```

### Alert Rules (Prometheus)

```yaml
groups:
  - name: pricing_service_alerts
    rules:
      - alert: OutboxBacklogCritical
        expr: price_change_outbox_pending > 2000
        for: 10m
        labels: { severity: critical }
        annotations: { summary: "Outbox backlog exceeds 2000 messages" }

      - alert: PricingServiceDegraded
        expr: pricing_service_degraded_mode == 1
        for: 1m
        labels: { severity: warning }
        annotations: { summary: "Pricing Service running in degraded mode" }

      - alert: PricingLatencyHigh
        expr: histogram_quantile(0.99, price_change_latency_seconds) > 0.2
        for: 5m
        labels: { severity: warning }
        annotations: { summary: "P99 price change latency exceeds 200ms" }

  - name: cma_alerts
    rules:
      - alert: CMSCircuitBreakerOpen
        expr: cms_circuit_breaker_state == 1
        for: 2m
        labels: { severity: critical }
        annotations: { summary: "CMA circuit breaker OPEN — not delivering to CMS" }

      - alert: DLQGrowing
        expr: dead_letter_queue_size > 10
        labels: { severity: critical }
        annotations: { summary: "Dead letter queue has more than 10 messages" }

  - name: gateway_alerts
    rules:
      - alert: UpstreamUnavailable
        expr: gateway_upstream_availability == 0
        for: 1m
        labels: { severity: critical }
        annotations: { summary: "API Gateway cannot reach an upstream service" }
```

### Health Check Standardization

Every service MUST expose these endpoints (standardized across all services):

| Endpoint | Method | Success Response | Checks |
|----------|--------|-----------------|--------|
| `/health/live` | GET | `200 {"status":"UP"}` | Process alive |
| `/health/ready` | GET | `200 {"status":"UP", "components":{...}}` | DB, Redis, Kafka, external deps, cache warm |
| `/actuator/prometheus` | GET | `200` (text/plain) | Metrics in Prometheus format |

---

## 5C: Test Architecture — Element Instantiation

### Test Harness Elements

| Element | Type | Scope | Location |
|---------|------|-------|----------|
| **Unit Test Suites** | Test Code (per service) | Single class/method | `src/test/java/` per service module |
| **Integration Test Suites** | Test Code (per service) | Single service with stubbed externals | `src/test/java/` with `@IntegrationTest` profile |
| **Test Stubs / Mocks** | Test Infrastructure | Replace external ports | `src/test/java/.../stubs/` per service |
| **Contract Definitions** | Test Artifact | Service boundary contracts | `src/test/resources/contracts/` per service |
| **E2E Test Suite** | Test Code (separate module) | Critical user journeys | `e2e-tests/` module |
| **ArchUnit Rules** | Test Code (per service) | Architectural constraints | `src/test/java/.../architecture/` per service |

### Key Test Stubs (per QA-9)

| Stub Name | Stubs What | Used By |
|-----------|-----------|---------|
| `StubHotelConfigProvisioningPort` | HMS config endpoint | Pricing Service integration tests |
| `StubPriceEventPublisher` | Kafka producer | Pricing Service integration tests |
| `StubCMSClient` | CMS API | CMA integration tests |
| `StubPricingServiceClient` | Pricing Service endpoints | API Gateway integration tests |
| `StubHotelManagementServiceClient` | HMS endpoints | API Gateway integration tests |
| `StubIdentityService` | Cloud identity JWKS endpoint | API Gateway integration tests |
| `WireMockCMS` | CMS HTTP API | CMA integration tests (via WireMock) |
| `EmbeddedKafka` | Kafka broker | All services' integration tests |

### Contract Test Example: Pricing Service ↔ HMS

**Provider (HMS):**
```java
// Contract definition in HMS
@SpringCloudContract
Contract.make {
    request {
        method GET()
        url "/api/hotels/config/all"
    }
    response {
        status OK()
        body([
            [hotelId: "h1", name: "Grand Hotel", taxRate: 0.10, currency: "USD"],
            [hotelId: "h2", name: "Seaside Resort", taxRate: 0.12, currency: "USD"]
        ])
        headers { contentType(applicationJson()) }
    }
}
```

**Consumer (Pricing Service):**
```java
// Generated stub from contract, used in PS integration tests
@Autowired
StubHotelConfigProvisioningPort stubConfigPort;

@Test
void shouldLoadConfigOnStartup() {
    rateRuleCache.loadFromProvider(stubConfigPort);
    assertThat(rateRuleCache.getTaxConfig("h1")).isEqualTo(new TaxRate(0.10));
}
```

---

## 5D: Configuration Management — Element Instantiation

### New Infrastructure Elements

| Element | Type | Technology | Responsibilities |
|---------|------|-----------|-----------------|
| **ConfigMap Resources** | Kubernetes Resources | K8s ConfigMaps | Store non-secret environment-specific configuration per service per environment |
| **Secret Resources** | Kubernetes Resources / Cloud Secret Manager | K8s Secrets + Cloud Secret Store | Store and inject secrets (DB passwords, API keys, signing keys) |
| **Feature Flag Service** | Configuration Element | Environment variables with defaults | Enable/disable features per environment |

### Configuration Template (pricing-service, per environment)

```yaml
# kubernetes/pricing-service/configmap.yaml (dev example)
apiVersion: v1
kind: ConfigMap
metadata:
  name: pricing-service-config
  namespace: dev
data:
  application.yaml: |
    spring:
      datasource:
        url: jdbc:postgresql://${DB_HOST}:5432/pricing_dev
        hikari:
          maximum-pool-size: 5
      redis:
        host: ${REDIS_HOST}
        port: 6379
      kafka:
        bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS}

    pricing:
      outbox:
        poll-interval-ms: 100
        batch-size: 50
      cache:
        ttl-seconds: 86400
        startup-load-timeout-seconds: 120
      feature:
        simulation-enabled: ${FEATURE_SIMULATION_ENABLED:true}
```

### Secret Management

| Secret | Storage | Injected As | Rotation |
|--------|---------|-------------|----------|
| DB password | Cloud Secret Manager → K8s Secret | `DB_PASSWORD` env var | Quarterly |
| Redis password | Cloud Secret Manager → K8s Secret | `REDIS_PASSWORD` env var | Quarterly |
| JWT signing key (public) | K8s ConfigMap | Mounted file | N/A (public key) |
| CMS API key | Cloud Secret Manager → K8s Secret | `CMS_API_KEY` env var | Quarterly |
| Kafka credentials | Cloud Secret Manager → K8s Secret | `KAFKA_USERNAME`, `KAFKA_PASSWORD` | Quarterly |

---

## 5E: Hotel Management Service — Full Internal Design

### HMS Internal Elements

| Internal Element | Type | Responsibilities |
|------------------|------|------------------|
| **HotelController** | Inbound Adapter (REST) | `POST/GET/PUT/DELETE /api/hotels`. Validates input. Calls HotelManagementService. |
| **RateController** | Inbound Adapter (REST) | `POST/GET/PUT/DELETE /api/rates`. Manages rate definitions and rate rules. |
| **UserController** | Inbound Adapter (REST) | `GET/PUT /api/users`. Manages user permissions. |
| **ConfigProvisioningController** | Inbound Adapter (REST) | `GET /api/hotels/config/all`, `GET /api/rates/rules/all`. Serves Pricing Service config requests. |
| **HotelManagementService** | Application Service | Orchestrates hotel CRUD. Enforces hotel-level authorization. Publishes config change events. |
| **RateManagementService** | Application Service | Orchestrates rate/rule CRUD. Creates new rule versions (immutability). Publishes config change events. |
| **UserManagementService** | Application Service | Orchestrates user permission changes. Validates user exists in identity service. |
| **AuthorizationService** | Domain Service | Given a userId and hotelId, determines if user has READ or WRITE permission. Uses `X-Authorized-Hotels` header or queries `user_hotel_permissions`. |
| **HotelRepository** | Outbound Port | CRUD operations for Hotel, RoomType aggregates. |
| **RateRepository** | Outbound Port | CRUD operations for Rate, RateRule aggregates. Version management. |
| **UserRepository** | Outbound Port | CRUD operations for User, Permission aggregates. |
| **ConfigChangePublisher** | Outbound Port (already defined) | Publishes to `hotel-config-changes` Kafka topic. |
| **HMSDBAdapter** | Outbound Adapter | JPA/JDBC implementation of all repository ports. |
| **HMSKafkaAdapter** | Outbound Adapter | Implements ConfigChangePublisher. Kafka producer. |
| **HMSHealthProbe** | Cross-Cutting (defined Iteration 3) | `/health/live`, `/health/ready` endpoints. |

### HMS Port Interfaces (New/Refined)

```
// === Hotel Management Ports ===
interface HotelRepository {
    Hotel save(Hotel hotel);
    Optional<Hotel> findById(HotelId id);
    List<Hotel> findAll();
    void delete(HotelId id);
    List<Hotel> findAuthorizedForUser(UserId userId);
}

// === Rate Management Ports ===
interface RateRepository {
    Rate save(Rate rate);
    Optional<Rate> findById(RateId id);
    List<Rate> findByHotelId(HotelId hotelId);
    RateRule saveRule(RateId rateId, RateRule rule);  // Creates new version
    List<RateRule> findActiveRules(RateId rateId);
    List<RateRule> findAllActiveRules();  // For Pricing Service config load
}

// === User Management Ports ===
interface UserRepository {
    User save(User user);
    Optional<User> findById(UserId id);
    Optional<User> findByExternalId(String externalId);
    void updatePermissions(UserId userId, List<HotelPermission> permissions);
    List<HotelPermission> getPermissions(UserId userId);
}

// === Config Provisioning Port (for Pricing Service) ===
interface HotelConfigProvisioningPort {
    HotelConfigSnapshot getAllHotelConfigurations();
    RateRulesSnapshot getAllRateRules();
}
```

### Authorization Model

```
// HMS extracts from request headers (propagated by API Gateway)
// X-User-Id: "user-123"
// X-User-Roles: "BUSINESS_USER"
// X-Authorized-Hotels: "h1,h2,h3"  // WRITE access
// X-Authorized-Hotels-Read: "h1,h2,h3,h4,h5"  // READ access (optional)

// Application services enforce:
class HotelManagementService {
    void updateHotel(UserId userId, HotelId hotelId, HotelData data) {
        if (!authorizationService.canWrite(userId, hotelId)) {
            throw new ForbiddenException("Not authorized for hotel " + hotelId);
        }
        // ... proceed with update
    }

    List<Hotel> listHotels(UserId userId) {
        return hotelRepository.findAuthorizedForUser(userId);  // DB-level filter
    }
}
```

---

## 5F: Team Structure and Work Assignment (CRN-3)

### Team Decomposition

Based on the architectural elements and CRN-2 (Java, Angular, Kafka expertise):

| Team | Size | Ownership | Key Skills |
|------|------|-----------|------------|
| **Frontend Team** | 2-3 developers | HPS Web Frontend (Angular SPA) | Angular, TypeScript, OAuth2/OIDC, RxJS |
| **Pricing Team** | 2-3 developers | Pricing Service, Channel Management Adapter | Java, Spring Boot, Kafka, Redis, PostgreSQL, CQRS |
| **Hotel Management Team** | 1-2 developers | Hotel Management Service, Management Database | Java, Spring Boot, PostgreSQL, REST APIs |
| **Platform/Infrastructure Team** | 1-2 developers | API Gateway, CI/CD, Monitoring, Configuration, Kafka topics, Shared Library | Java, Spring Cloud Gateway, Docker, Kubernetes, Prometheus, Grafana |

### Team Coordination Points

| Coordination Point | Teams Involved | Mechanism |
|--------------------|---------------|-----------|
| API Gateway ↔ Backend Services | Platform + Pricing + HMS | REST API contracts (OpenAPI spec), contract tests |
| Pricing Service ↔ HMS (config) | Pricing + HMS | Contract tests (Spring Cloud Contract), `hotel-config-changes` schema |
| Frontend ↔ API Gateway | Frontend + Platform | OpenAPI spec for all endpoints, CORS configuration |
| Pricing Service → CMA (Kafka) | Pricing | `PriceChangedEvent` schema in shared-lib |
| HMS → Pricing Service (Kafka) | HMS + Pricing | `ConfigChangeEvent` schema in shared-lib |
| CI/CD Pipeline | Platform + All teams | Standardized Dockerfiles, Kubernetes manifests per service |

### Shared Library (`shared-lib/`)

| Artifact | Owners | Contents |
|----------|--------|----------|
| `shared-lib-events` | Pricing + HMS | `PriceChangedEvent`, `ConfigChangeEvent`, `HotelConfigSnapshot`, `RateRulesSnapshot` DTOs |
| `shared-lib-api` | Platform + All | OpenAPI-generated client interfaces, common error responses, pagination types |
| `shared-lib-test` | All | Test fixtures, stub base classes, embedded container configurations |

---

## 5G: QA-6 Modifiability Validation — gRPC Addition Scenario

### Scenario: Add gRPC support for external price queries

Per QA-6: "Support for price query endpoints using protocols other than REST (e.g., gRPC) is added without changing core components."

### Walkthrough

| Step | Action | Elements Changed | Core Logic Changed? |
|------|--------|-----------------|---------------------|
| 1 | Define `.proto` file for `PriceQueryService` with `queryPrices` RPC | New file: `pricing-service/src/main/proto/price_query.proto` | No |
| 2 | Generate Java gRPC stubs from `.proto` | Build tool generates code | No |
| 3 | Implement `GrpcPricingAdapter` as new inbound adapter in Pricing Service | New class: `GrpcPricingAdapter implements BindableService` | No — it calls `PriceQueryPort.queryPrices()` which already exists |
| 4 | Add gRPC route in API Gateway (Spring Cloud Gateway supports gRPC) | New route config in `api-gateway` | No — route configuration only |
| 5 | Deploy | New Pricing Service version with gRPC adapter + Gateway route update | No |

**Core components unchanged:**
- `PriceQueryPort` interface — no change
- `PriceQueryHandler` — no change
- `PriceReadCache` / Redis — no change
- `PriceRepository` — no change
- `DerivedRateCalculator` — no change
- `PriceCommandPort` — no change

**Conclusion: QA-6 is satisfied.** The Ports & Adapters pattern isolates protocol concerns. Adding gRPC requires only new inbound adapters — zero core logic changes.

---

## 5H: Risk Resolution Summary

| Risk ID | Status | Resolution |
|---------|--------|------------|
| **R14** (OutboxPoller SPOF) | ✅ RESOLVED | Cooperative polling: all Pricing Service instances run OutboxPoller with `SELECT ... FOR UPDATE SKIP LOCKED` for row-level distributed locking |
| **R16** (CMA idempotency) | ✅ RESOLVED | Shared Redis idempotency store with local LRU fallback. 48h TTL. Atomic SET NX. |
| **R18** (Full monitoring) | ✅ RESOLVED | Micrometer + Prometheus + Grafana + Alertmanager stack. 14 metrics operationalized with alert rules. 4 dashboards defined. Health check standardization. |
| **R4** (Gateway choice) | ✅ RESOLVED | Self-managed Spring Cloud Gateway (Java). Aligns with CRN-2, QA-6, and custom auth logic. |

---

## 5I: Construction Readiness Checklist

| Criterion | Status | Evidence |
|-----------|--------|----------|
| All use cases mapped to elements | ✅ | HPS-1→Frontend+Gateway, HPS-2→Pricing+CMA, HPS-3→Pricing, HPS-4/5/6→HMS |
| All QAs addressed | ✅ | QA-1 (Iter 2), QA-2 (Iter 3), QA-3 (Iter 3), QA-4 (Iter 2), QA-5 (Iter 2), QA-6 (Iter 2+4), QA-7 (Iter 4), QA-8 (Iter 3+4), QA-9 (Iter 4) |
| All CRNs addressed | ✅ | CRN-1 (Iter 1), CRN-2 (all iters), CRN-3 (Iter 4), CRN-4 (Iter 4), CRN-5 (Iter 4) |
| All CONs satisfied | ✅ | CON-1–6 addressed across all iterations |
| All risks resolved or accepted | ✅ | R1–R19 resolved or accepted at low severity |
| Database schemas defined | ✅ | Pricing DB + Management DB (Iter 4) |
| CI/CD pipeline designed | ✅ | Iter 4 |
| Team structure defined | ✅ | Iter 4 |
| Monitoring designed | ✅ | Iter 3 (metrics) + Iter 4 (infrastructure) |
| Test strategy defined | ✅ | Iter 4 |