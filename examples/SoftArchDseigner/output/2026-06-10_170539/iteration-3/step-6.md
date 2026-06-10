# ADD Step 6: Sketch Views and Perspectives (Iteration 3)

---

## View 1: Transactional Outbox — Reliable Price Publication

This sequence diagram shows how the Transactional Outbox pattern guarantees that every price change is published to Kafka, even in the face of failures.

```mermaid
sequenceDiagram
    participant GW as API Gateway
    participant CMD as PriceCommandHandler
    participant CALC as DerivedRateCalculator
    participant REPO as PriceRepository
    participant DB as Pricing Database
    participant REDIS as Redis Cache
    participant POLLER as OutboxPoller
    participant KAFKA as Kafka

    Note over GW,KAFKA: === HPS-2: Change Prices (with Transactional Outbox) ===

    GW->>CMD: POST /api/prices/change

    CMD->>CMD: Validate + Get rate rules
    CMD->>CALC: computeDerivedRates()
    CALC-->>CMD: Derived prices

    CMD->>REPO: savePriceChange(prices)
    REPO->>DB: BEGIN TRANSACTION
    DB-->>REPO: OK
    REPO->>DB: INSERT/UPDATE price records
    DB-->>REPO: OK
    REPO->>DB: INSERT INTO price_change_outbox (eventId, payload)
    DB-->>REPO: OK
    REPO->>DB: COMMIT TRANSACTION
    DB-->>REPO: OK
    REPO-->>CMD: success

    CMD->>REDIS: writeToCache(hotelId, dates, prices)
    REDIS-->>CMD: OK

    CMD-->>GW: 200 OK { success, changeId }
    GW-->>GW: User sees success within 100ms

    Note over POLLER,KAFKA: === Background: OutboxPoller (runs every 100ms) ===

    POLLER->>DB: SELECT * FROM outbox WHERE published_at IS NULL
    DB-->>POLLER: [ {eventId: e1, payload: ...}, ... ]

    loop For each unpublished event
        POLLER->>KAFKA: publish(event)
        KAFKA-->>POLLER: ACK
        POLLER->>DB: UPDATE outbox SET published_at=NOW() WHERE eventId=e1
    end

    Note over KAFKA: If Kafka publish succeeds but DB update fails,
    Note over KAFKA: message will be re-published (CMA handles duplicates via idempotency)
```

---

## View 2: Channel Management Adapter — Reliable CMS Delivery

This sequence diagram shows how the CMA ensures guaranteed delivery to the Channel Management System with idempotency, retry, circuit breaker, and dead letter handling.

```mermaid
sequenceDiagram
    participant KAFKA as Kafka<br/>price-changes
    participant CONSUMER as PriceChangeConsumer
    participant IDEM as IdempotencyGuard
    participant CB as CircuitBreaker
    participant TRANSFORM as MessageTransformer
    participant CMS as CMSClient
    participant CMS_EXT as Channel Mgmt<br/>System
    participant RETRY as RetryHandler
    participant DLQ as DeadLetterPublisher

    KAFKA->>CONSUMER: poll() → [event: {eventId, hotelId, prices}]

    CONSUMER->>IDEM: hasBeenProcessed(eventId)?
    IDEM-->>CONSUMER: No → continue

    CONSUMER->>CB: allowRequest()?
    CB-->>CONSUMER: Yes (CLOSED state)

    CONSUMER->>TRANSFORM: transform(event) → CMS format
    TRANSFORM-->>CONSUMER: CMS-formatted payload

    CONSUMER->>CMS: publishPrices(hotelId, prices, idempotencyKey=eventId)
    CMS->>CMS_EXT: POST /prices { Idempotency-Key: eventId, ... }

    alt CMS returns 200 OK
        CMS_EXT-->>CMS: 200 OK
        CMS-->>CONSUMER: SUCCESS
        CONSUMER->>IDEM: markProcessed(eventId)
        CONSUMER->>KAFKA: commitOffset()
    else CMS returns 5xx / timeout (TRANSIENT)
        CMS_EXT-->>CMS: 503 Service Unavailable
        CMS-->>CONSUMER: TRANSIENT_FAILURE
        CONSUMER->>RETRY: retry(event, attempt=1)
        RETRY->>RETRY: Backoff 1s with jitter
        RETRY->>CMS: retry publishPrices()
        Note over RETRY,CMS_EXT: Up to 5 retries with exponential backoff
        alt Retry succeeds
            CMS-->>RETRY: SUCCESS
            RETRY-->>CONSUMER: SUCCESS
            CONSUMER->>IDEM: markProcessed(eventId)
            CONSUMER->>KAFKA: commitOffset()
        else All retries exhausted
            RETRY-->>CONSUMER: PERMANENT_FAILURE
            CONSUMER->>DLQ: send(event, reason="RETRIES_EXHAUSTED")
            CONSUMER->>KAFKA: commitOffset()
        end
    else CMS returns 4xx (PERMANENT)
        CMS_EXT-->>CMS: 400 Bad Request
        CMS-->>CONSUMER: PERMANENT_FAILURE
        CONSUMER->>DLQ: send(event, reason="PERMANENT_FAILURE")
        CONSUMER->>KAFKA: commitOffset()
    end
```

---

## View 3: High Availability Deployment Topology

This deployment view shows the multi-instance, multi-AZ deployment topology that achieves 99.9% availability (QA-3). All tiers are redundant.

```mermaid
graph TB
    subgraph "Cloud Load Balancer"
        LB[Load Balancer<br/>Health Checks]
    end

    subgraph "Availability Zone A"
        subgraph "Gateway Tier (AZ-A)"
            GW_A1[API Gateway<br/>Instance A1]
            GW_A2[API Gateway<br/>Instance A2]
        end
        subgraph "Pricing Query Tier (AZ-A)"
            PSQ_A1[PriceQueryHandler<br/>Instance A1]
            PSQ_A2[PriceQueryHandler<br/>Instance A2]
        end
        subgraph "Pricing Write Tier (AZ-A)"
            PSW_A[PriceCommandHandler<br/>Instance A]
        end
        subgraph "HMS Tier (AZ-A)"
            HMS_A[Hotel Mgmt Service<br/>Instance A]
        end
        subgraph "CMA Tier (AZ-A)"
            CMA_A[Channel Mgmt Adapter<br/>Instance A]
        end
    end

    subgraph "Availability Zone B"
        subgraph "Gateway Tier (AZ-B)"
            GW_B1[API Gateway<br/>Instance B1]
        end
        subgraph "Pricing Query Tier (AZ-B)"
            PSQ_B1[PriceQueryHandler<br/>Instance B1]
            PSQ_B2[PriceQueryHandler<br/>Instance B2]
        end
        subgraph "Pricing Write Tier (AZ-B)"
            PSW_B[PriceCommandHandler<br/>Instance B]
        end
        subgraph "HMS Tier (AZ-B)"
            HMS_B[Hotel Mgmt Service<br/>Instance B]
        end
        subgraph "CMA Tier (AZ-B)"
            CMA_B[Channel Mgmt Adapter<br/>Instance B]
        end
    end

    subgraph "Managed Services (Multi-AZ)"
        REDIS[(Redis Cluster<br/>1 Primary + 2 Replicas)]
        PDB_PRIMARY[(Pricing DB<br/>Primary)]
        PDB_REPLICA[(Pricing DB<br/>Read Replica)]
        MDB_PRIMARY[(Management DB<br/>Primary)]
        MDB_REPLICA[(Management DB<br/>Read Replica)]
        KAFKA_SVC{{Managed Kafka<br/>3 Brokers, RF=3}}
    end

    subgraph "CDN (Global)"
        CDN[Angular SPA<br/>Static Assets]
    end

    LB --> GW_A1
    LB --> GW_A2
    LB --> GW_B1

    GW_A1 --> PSQ_A1
    GW_A1 --> PSQ_A2
    GW_A2 --> PSQ_A1
    GW_A2 --> PSQ_A2
    GW_B1 --> PSQ_B1
    GW_B1 --> PSQ_B2

    GW_A1 --> PSW_A
    GW_A2 --> PSW_A
    GW_B1 --> PSW_B

    GW_A1 --> HMS_A
    GW_A2 --> HMS_A
    GW_B1 --> HMS_B

    PSQ_A1 --> REDIS
    PSQ_A1 -.->|fallback| PDB_REPLICA
    PSQ_A2 --> REDIS
    PSQ_A2 -.->|fallback| PDB_REPLICA
    PSQ_B1 --> REDIS
    PSQ_B1 -.->|fallback| PDB_REPLICA
    PSQ_B2 --> REDIS
    PSQ_B2 -.->|fallback| PDB_REPLICA

    PSW_A --> PDB_PRIMARY
    PSW_A --> REDIS
    PSW_B --> PDB_PRIMARY
    PSW_B --> REDIS

    HMS_A --> MDB_PRIMARY
    HMS_B --> MDB_PRIMARY

    PSW_A --> KAFKA_SVC
    PSW_B --> KAFKA_SVC
    KAFKA_SVC --> CMA_A
    KAFKA_SVC --> CMA_B
```

---

## View 4: Pricing Service Startup and Degraded Mode

This state diagram shows the Pricing Service startup sequence and transitions between normal and degraded modes.

```mermaid
stateDiagram-v2
    [*] --> Starting

    Starting --> ConnectingInfra: Process starts
    ConnectingInfra --> LoadingConfig: DB, Redis, Kafka connected

    LoadingConfig --> Ready: HMS reachable,<br/>RateRuleCache loaded
    LoadingConfig --> Degraded: HMS unreachable<br/>after 2 min retry,<br/>FallbackConfigStore loaded

    Ready --> Ready: Normal operation<br/>Config refreshes via Kafka
    Ready --> Degraded: HMS becomes unreachable<br/>(30s timeout × 3 attempts)

    Degraded --> Degraded: Serve from fallback config<br/>Retry HMS every 30s
    Degraded --> Ready: HMS becomes reachable<br/>RateRuleCache refreshed

    Degraded --> DegradedRedis: Redis becomes unreachable
    DegradedRedis --> DegradedRedis: Query from DB read replica
    DegradedRedis --> Degraded: Redis recovered

    Ready --> DegradedRedis: Redis becomes unreachable
    DegradedRedis --> Ready: Redis recovered

    note right of Degraded
        Metrics emitted:
        pricing_service_degraded_mode = 1
        pricing_service_config_staleness_seconds
    end note
```

---

## View 5: Circuit Breaker State Machine (CMA)

```mermaid
stateDiagram-v2
    [*] --> CLOSED

    CLOSED --> OPEN: 5 consecutive failures<br/>in 60s window
    OPEN --> HALF_OPEN: 30s cooldown elapsed

    HALF_OPEN --> CLOSED: Probe request succeeds
    HALF_OPEN --> OPEN: Probe request fails

    note right of CLOSED
        Normal operation
        Failures counted in sliding window
        Requests pass through to CMS
    end note

    note right of OPEN
        Kafka consumer paused
        Messages accumulate in Kafka
        Metric: cms_circuit_breaker_state=1
    end note

    note right of HALF_OPEN
        One probe request allowed
        Success → CLOSED (resume consumer)
        Failure → OPEN (reset cooldown)
    end note
```

---

## View 6: End-to-End Reliability — Component Overview

This diagram consolidates all reliability mechanisms across the system into a single view.

```mermaid
graph TB
    subgraph "HPS Web Frontend"
        SPA[Angular SPA<br/>Browser-based]
    end

    subgraph "API Gateway (HA: 2+ instances)"
        GW_LB[Cloud Load Balancer]
        GW[Gateway<br/>Stateless, JWT validation<br/>Circuit Breakers for downstream]
    end

    subgraph "Pricing Service — Write"
        PSW_CMD[PriceCommandHandler]
        PSW_OUTBOX[OutboxPoller]
        PSW_DB[(Pricing DB<br/>+ outbox table)]
        PSW_REDIS[(Redis Cache)]
    end

    subgraph "Pricing Service — Query (HA: N instances)"
        PSQ[PriceQueryHandler<br/>Redis-first, DB fallback]
    end

    subgraph "Hotel Management Service (HA: 2+ instances)"
        HMS[HMS<br/>Stateless, Multi-instance]
        HMS_DB[(Management DB<br/>+ Read Replica)]
    end

    subgraph "Kafka (Managed, Multi-AZ, RF=3)"
        KAFKA_PC{{price-changes}}
        KAFKA_DLQ{{price-changes-dlq}}
        KAFKA_CFG{{hotel-config-changes}}
    end

    subgraph "Channel Management Adapter (HA: 2 instances)"
        CMA[CMA<br/>Idempotency, Retry, Circuit Breaker, DLQ]
        CMA_IDEM[(Idempotency Store)]
    end

    subgraph "External"
        CMS[Channel Management System]
        IDP[Cloud Identity Service]
    end

    SPA --> GW_LB --> GW
    GW --> PSW_CMD
    GW --> PSQ
    GW --> HMS
    GW --> IDP

    PSW_CMD --> PSW_DB
    PSW_CMD --> PSW_REDIS
    PSW_OUTBOX --> PSW_DB
    PSW_OUTBOX --> KAFKA_PC

    PSQ --> PSW_REDIS
    PSQ -.->|fallback| PSW_DB

    HMS --> HMS_DB
    HMS --> KAFKA_CFG

    KAFKA_PC --> CMA
    CMA --> CMS
    CMA --> KAFKA_DLQ
    CMA --> CMA_IDEM
```

---

## Summary of Views

| View | Type | Primary Focus |
|------|------|---------------|
| View 1: Transactional Outbox | Sequence Diagram | Atomic DB+Kafka publication (QA-2) |
| View 2: CMA Reliable Delivery | Sequence Diagram | Idempotency, retry, circuit breaker, DLQ flow (QA-2) |
| View 3: HA Deployment Topology | Deployment Diagram | Multi-AZ redundancy for 99.9% uptime (QA-3) |
| View 4: Startup & Degraded Mode | State Diagram | Graceful startup and dependency failure handling (R1, R9) |
| View 5: Circuit Breaker | State Diagram | CMA circuit breaker states and transitions |
| View 6: End-to-End Reliability | Component Diagram | Consolidated view of all reliability mechanisms |