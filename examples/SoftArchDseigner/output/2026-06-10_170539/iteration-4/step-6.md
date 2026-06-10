# ADD Step 6: Sketch Views and Perspectives (Iteration 4)

---

## View 1: CI/CD Pipeline Flow

This view shows the pipeline stages from commit to production for a backend service (applies to Pricing Service, HMS, CMA, and API Gateway).

```mermaid
graph TB
    GIT[Git Push<br/>Feature Branch / Main] --> BUILD[Stage 1: Build & Unit Test<br/>mvn compile + test]
    BUILD --> STATIC[Stage 2: Static Analysis<br/>ArchUnit, Checkstyle, SpotBugs, OWASP]
    STATIC --> INT_TEST[Stage 3: Integration Test<br/>Testcontainers: DB, Redis, Kafka stubs]
    INT_TEST --> CONTRACT[Stage 4: Contract Test<br/>Spring Cloud Contract verification]
    CONTRACT --> CONTAINER[Stage 5: Container Build<br/>docker build + push to registry]

    CONTAINER -->|auto on main| DEV[Stage 6: Deploy to Dev<br/>kubectl apply -n dev]
    DEV --> SMOKE[Smoke Tests<br/>Health + basic API checks]
    SMOKE -->|pass| DEV_DONE[✅ Dev Deployed]

    DEV_DONE -->|manual trigger| STAGING[Stage 7: Deploy to Staging<br/>kubectl apply -n staging]
    STAGING --> REGRESSION[Regression Tests<br/>Full suite]
    REGRESSION -->|pass| STAGING_DONE[✅ Staging Deployed]

    STAGING_DONE -->|manual approval| PROD[Stage 8: Deploy to Production<br/>Blue-Green Deployment]
    PROD --> CANARY[Canary Analysis<br/>10 min health + metric check]
    CANARY -->|pass| PROD_DONE[✅ Production Deployed]
    CANARY -->|fail| ROLLBACK[Rollback<br/>Switch to previous version]
```

---

## View 2: Monitoring and Observability Architecture

This view shows the monitoring stack: metrics collection, aggregation, dashboards, and alerting.

```mermaid
graph TB
    subgraph "Application Tier"
        PS[Pricing Service<br/>:8080/actuator/prometheus]
        HMS[Hotel Management Service<br/>:8080/actuator/prometheus]
        GW[API Gateway<br/>:8080/actuator/prometheus]
        CMA[Channel Mgmt Adapter<br/>:8080/actuator/prometheus]
    end

    subgraph "Monitoring Tier"
        PROM[Prometheus Server<br/>Scrapes every 15s<br/>Alert rule evaluation]
        GRAFANA[Grafana<br/>Dashboards]
        AM[Alertmanager<br/>Alert routing]
    end

    subgraph "Logging Tier"
        LOGS[Cloud Log Aggregator<br/>JSON stdout collection]
    end

    subgraph "Notification Channels"
        PD[PagerDuty<br/>Critical alerts]
        SLACK[Slack<br/>Warning alerts]
        EMAIL[Email<br/>Daily digest]
    end

    PROM -->|scrape /actuator/prometheus| PS
    PROM -->|scrape /actuator/prometheus| HMS
    PROM -->|scrape /actuator/prometheus| GW
    PROM -->|scrape /actuator/prometheus| CMA

    PROM --> GRAFANA
    PROM -->|fire alerts| AM
    AM --> PD
    AM --> SLACK
    AM --> EMAIL

    PS -->|stdout JSON| LOGS
    HMS -->|stdout JSON| LOGS
    GW -->|stdout JSON| LOGS
    CMA -->|stdout JSON| LOGS

    GRAFANA -.->|data source| PROM
    LOGS -.->|correlate with metrics| GRAFANA
```

---

## View 3: Test Architecture — Layered Test Pyramid

This view shows the test layers, their scope, and the isolation mechanisms per QA-9.

```mermaid
graph TB
    subgraph "E2E Tests (Critical Journeys Only)"
        E2E[Cypress / REST Assured<br/>Staging Environment<br/>External systems stubbed]
    end

    subgraph "Contract Tests (Service Boundaries)"
        CT_PS_HMS[PS ↔ HMS<br/>Spring Cloud Contract]
        CT_GW_PS[GW ↔ PS<br/>OpenAPI Contract]
        CT_GW_HMS[GW ↔ HMS<br/>OpenAPI Contract]
        CT_CMA_CMS[CMA ↔ CMS<br/>Stub Contract]
    end

    subgraph "Integration Tests (Per Service, Isolated)"
        IT_PS[Pricing Service<br/>Testcontainers: PostgreSQL, Redis, Kafka<br/>HMS stubbed via port interface]
        IT_HMS[Hotel Management Service<br/>Testcontainers: PostgreSQL, Kafka<br/>Identity Service stubbed]
        IT_GW[API Gateway<br/>WireMock: PS, HMS, Identity Service]
        IT_CMA[Channel Mgmt Adapter<br/>Testcontainers: Kafka<br/>CMS stubbed via CMSClient port]
        IT_FE[HPS Web Frontend<br/>HttpClientTestingModule<br/>API Gateway stubbed via MSW]
    end

    subgraph "Unit Tests (Per Class/Method)"
        UT_PS[Pricing Service<br/>JUnit 5 + Mockito<br/>All deps mocked]
        UT_HMS[Hotel Management Service<br/>JUnit 5 + Mockito<br/>All deps mocked]
        UT_GW[API Gateway<br/>JUnit 5 + Mockito<br/>All deps mocked]
        UT_CMA[Channel Mgmt Adapter<br/>JUnit 5 + Mockito<br/>All deps mocked]
        UT_FE[HPS Web Frontend<br/>Jasmine/Karma<br/>All deps mocked]
    end

    subgraph "Architecture Fitness Functions"
        ARCH[ArchUnit Rules<br/>Layer dependency checks<br/>Port isolation checks<br/>Run in CI Stage 2]
    end

    E2E --> CT_PS_HMS
    CT_PS_HMS --> IT_PS
    CT_PS_HMS --> IT_HMS
    CT_GW_PS --> IT_GW
    CT_GW_PS --> IT_PS
    CT_GW_HMS --> IT_GW
    CT_GW_HMS --> IT_HMS
    CT_CMA_CMS --> IT_CMA

    IT_PS --> UT_PS
    IT_HMS --> UT_HMS
    IT_GW --> UT_GW
    IT_CMA --> UT_CMA
    IT_FE --> UT_FE

    ARCH -.->|validates| UT_PS
    ARCH -.->|validates| UT_HMS
    ARCH -.->|validates| UT_GW
    ARCH -.->|validates| UT_CMA
```

---

## View 4: Hotel Management Service — Internal Structure

This view shows the completed internal design of the Hotel Management Service following layered architecture within Ports & Adapters.

```mermaid
graph TB
    subgraph "Inbound Adapters (REST)"
        HC[HotelController<br/>CRUD /api/hotels]
        RC[RateController<br/>CRUD /api/rates]
        UC[UserController<br/>CRUD /api/users]
        CPC[ConfigProvisioningController<br/>GET /api/hotels/config/all<br/>GET /api/rates/rules/all]
    end

    subgraph "Application Services"
        HMSVC[HotelManagementService<br/>Hotel CRUD orchestration<br/>Authorization enforcement]
        RMSVC[RateManagementService<br/>Rate/Rule CRUD orchestration<br/>Version management]
        UMSVC[UserManagementService<br/>Permission management]
    end

    subgraph "Domain Services"
        AUTH[AuthorizationService<br/>Hotel-level access control]
    end

    subgraph "Outbound Ports (Interfaces)"
        HOTEL_REPO[HotelRepository]
        RATE_REPO[RateRepository]
        USER_REPO[UserRepository]
        CFG_PUB[ConfigChangePublisher]
    end

    subgraph "Outbound Adapters"
        DB_ADPT[HMSDBAdapter<br/>JPA/JDBC]
        KAFKA_ADPT[HMSKafkaAdapter<br/>Kafka Producer]
    end

    subgraph "Cross-Cutting"
        HEALTH[HMSHealthProbe<br/>GET /health/live<br/>GET /health/ready]
    end

    subgraph "External"
        MDB[(Management<br/>Database)]
        CFG_KAFKA{{Kafka<br/>hotel-config-changes}}
    end

    HC --> HMSVC
    RC --> RMSVC
    UC --> UMSVC
    CPC --> HMSVC
    CPC --> RMSVC

    HMSVC --> AUTH
    HMSVC --> HOTEL_REPO
    HMSVC --> CFG_PUB

    RMSVC --> RATE_REPO
    RMSVC --> CFG_PUB

    UMSVC --> USER_REPO

    HOTEL_REPO --> DB_ADPT --> MDB
    RATE_REPO --> DB_ADPT
    USER_REPO --> DB_ADPT

    CFG_PUB --> KAFKA_ADPT --> CFG_KAFKA
```

---

## View 5: Team Structure and Ownership Map

This view aligns the development teams with the architectural elements, showing ownership boundaries and coordination points.

```mermaid
graph TB
    subgraph "Frontend Team (2-3 devs, Angular)"
        FE[HPS Web Frontend<br/>Angular SPA<br/>7 Feature Modules]
    end

    subgraph "Pricing Team (2-3 devs, Java/Kafka)"
        PS[Pricing Service<br/>CQRS + Outbox]
        CMA[Channel Mgmt Adapter<br/>Idempotency + Retry + DLQ]
    end

    subgraph "Hotel Management Team (1-2 devs, Java)"
        HMS[Hotel Management Service<br/>Layered Architecture]
    end

    subgraph "Platform Team (1-2 devs, Java/Infra)"
        GW[API Gateway<br/>Spring Cloud Gateway]
        CICD[CI/CD Pipeline]
        MON[Monitoring + Alerting]
        CFG[Config Management]
    end

    subgraph "Shared"
        LIB[shared-lib<br/>Events + API DTOs + Test Fixtures]
    end

    subgraph "Infrastructure"
        KAFKA{{Kafka Clusters}}
        DB[(Databases)]
        REDIS[(Redis)]
    end

    FE -->|OpenAPI contract| GW
    PS -->|Contract test| HMS
    PS -->|PriceChangedEvent schema| LIB
    HMS -->|ConfigChangeEvent schema| LIB
    GW -->|OpenAPI spec| LIB
    PS --> KAFKA
    CMA --> KAFKA
    HMS --> KAFKA
    PS --> DB
    PS --> REDIS
    HMS --> DB
    CMA --> REDIS
```

---

## View 6: Complete System Architecture — Consolidated View

This is the consolidated architecture diagram incorporating all design decisions from all four iterations.

```mermaid
graph TB
    subgraph "External"
        USERS[Users<br/>Browsers]
        EXT_API[External API<br/>Consumers]
        IDP[Cloud Identity<br/>Service]
        CMS[Channel Mgmt<br/>System]
    end

    subgraph "CDN"
        CDN[Angular SPA<br/>Static Assets]
    end

    subgraph "API Gateway Tier (HA: 2+)"
        GW_LB[Cloud Load Balancer]
        GW[Spring Cloud Gateway<br/>Java<br/>Auth, Rate Limit, Circuit Breaker]
    end

    subgraph "Service Tier"
        subgraph "Pricing Service (CQRS)"
            PS_WRITE[Write Path<br/>CommandHandler + OutboxPoller]
            PS_QUERY[Query Path<br/>QueryHandler (HA: N instances)]
        end
        subgraph "Hotel Management Service (HA: 2+)"
            HMS_SVC[HMS<br/>Layered Architecture<br/>Hotels, Rates, Users]
        end
        subgraph "Channel Mgmt Adapter (HA: 2)"
            CMA_SVC[CMA<br/>Idempotency, Retry, CB, DLQ]
        end
    end

    subgraph "Data Tier (Managed, Multi-AZ)"
        PDB[(Pricing DB<br/>+ outbox)]
        MDB[(Management DB<br/>+ read replica)]
        REDIS_CLUSTER[(Redis Cluster<br/>Cache + Idempotency)]
    end

    subgraph "Messaging (Managed, Multi-AZ, RF=3)"
        KAFKA_PC{{price-changes}}
        KAFKA_CFG{{hotel-config-changes}}
        KAFKA_DLQ{{price-changes-dlq}}
    end

    subgraph "Operations Tier"
        PROM[Prometheus]
        GRAFANA[Grafana]
        CICD[CI/CD Pipeline]
    end

    USERS --> CDN
    USERS --> GW_LB
    EXT_API --> GW_LB
    GW_LB --> GW
    GW -->|JWT validation| IDP

    GW -->|REST| PS_WRITE
    GW -->|REST| PS_QUERY
    GW -->|REST| HMS_SVC

    PS_WRITE --> PDB
    PS_WRITE --> REDIS_CLUSTER
    PS_WRITE -->|OutboxPoller| KAFKA_PC
    PS_QUERY --> REDIS_CLUSTER
    PS_QUERY -.->|fallback| PDB

    HMS_SVC --> MDB
    HMS_SVC --> KAFKA_CFG

    KAFKA_PC --> CMA_SVC
    KAFKA_CFG --> PS_WRITE
    CMA_SVC --> KAFKA_DLQ
    CMA_SVC --> CMS
    CMA_SVC --> REDIS_CLUSTER

    PS_WRITE -->|metrics| PROM
    PS_QUERY -->|metrics| PROM
    HMS_SVC -->|metrics| PROM
    GW -->|metrics| PROM
    CMA_SVC -->|metrics| PROM
    PROM --> GRAFANA

    CICD -.->|deploys| GW
    CICD -.->|deploys| PS_WRITE
    CICD -.->|deploys| PS_QUERY
    CICD -.->|deploys| HMS_SVC
    CICD -.->|deploys| CMA_SVC
    CICD -.->|deploys| CDN
```

---

## Summary of Views

| View | Type | Primary Focus |
|------|------|---------------|
| View 1: CI/CD Pipeline | Flow Diagram | Build → Deploy pipeline stages (CRN-5) |
| View 2: Monitoring Architecture | Component Diagram | Metrics collection, dashboards, alerting (QA-8) |
| View 3: Test Architecture | Layered Diagram | Test pyramid with isolation mechanisms (QA-9) |
| View 4: HMS Internal Structure | Component Diagram | Completed HMS internal design (HPS-4/5/6) |
| View 5: Team Ownership Map | Allocation Diagram | Architecture-to-team mapping (CRN-3) |
| View 6: Consolidated Architecture | Component Diagram | Complete system architecture from all 4 iterations |