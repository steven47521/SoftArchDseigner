# ADD Step 6: Sketch Views and Perspectives (Iteration 2)

---

## View 1: Pricing Service Internal Structure (CQRS)

This view shows the internal decomposition of the Pricing Service using the CQRS pattern, with clear separation of write (command) and read (query) paths, and in-memory computation of derived rates.

```mermaid
graph TB
    subgraph "Inbound"
        REST[RESTPricingAdapter<br/><i>Inbound Adapter</i>]
    end

    subgraph "Write Side (Commands)"
        CMD[PriceCommandHandler<br/><i>Command Processor</i>]
        CALC[DerivedRateCalculator<br/><i>Domain Service</i>]
        CACHE[RateRuleCache<br/><i>In-Memory Store</i>]
        REPO[PriceRepository<br/><i>Outbound Port</i>]
    end

    subgraph "Read Side (Queries)"
        QRY[PriceQueryHandler<br/><i>Query Processor</i>]
        RDCACHE[PriceReadCache<br/><i>In-Memory Cache</i>]
    end

    subgraph "Outbound Adapters"
        DB_ADPT[PricingDBAdapter<br/><i>DB Adapter</i>]
        KAFKA_ADPT[KafkaPriceAdapter<br/><i>Kafka Adapter</i>]
    end

    subgraph "External"
        PDB[(Pricing<br/>Database)]
        KAFKA{{Kafka<br/>price-changes}}
    end

    REST -->|changePrices / simulatePrices| CMD
    REST -->|queryPrices| QRY

    CMD --> CACHE
    CMD --> CALC
    CMD --> REPO
    CMD --> RDCACHE
    CMD -->|publish event| KAFKA_ADPT

    QRY --> RDCACHE

    CACHE -.->|startup load + Kafka events| HMS_CONFIG[Hotel Config<br/>from HMS]

    REPO --> DB_ADPT --> PDB
    KAFKA_ADPT --> KAFKA
```

---

## View 2: Authentication Flow (Sequence)

This sequence diagram shows the end-to-end OAuth2 Authorization Code flow with PKCE, from the user's browser through the Angular SPA, API Gateway, and Cloud Identity Service.

```mermaid
sequenceDiagram
    actor User
    participant SPA as Angular SPA<br/>(Browser)
    participant GW as API Gateway
    participant IDP as Cloud Identity<br/>Service

    Note over User,IDP: === Login Flow ===

    User->>SPA: Clicks "Login"
    SPA->>SPA: Generate PKCE code_verifier + code_challenge
    SPA->>IDP: Redirect: /authorize?code_challenge=...&state=...

    IDP->>User: Present login form
    User->>IDP: Enter credentials

    IDP->>SPA: Redirect: /auth/callback?code=...&state=...
    SPA->>GW: POST /api/auth/token { code, code_verifier }
    GW->>IDP: POST /token { code, code_verifier }
    IDP->>GW: { access_token (JWT), refresh_token }
    GW->>SPA: { access_token }
    GW->>SPA: Set-Cookie: refresh_token (HTTP-only, Secure)

    Note over User,IDP: === Authenticated Request Flow ===

    User->>SPA: Perform action (e.g., Change Prices)
    SPA->>GW: GET/POST /api/...<br/>Authorization: Bearer <access_token>
    GW->>GW: Validate JWT signature + expiry
    GW->>GW: Check roles for route
    GW->>GW: Extract claims (userId, roles, hotels)
    GW->>BACKEND: Forward request + headers<br/>X-User-Id, X-User-Roles, X-Authorized-Hotels
    BACKEND->>GW: Response
    GW->>SPA: Response
```

---

## View 3: Price Change Flow — Component Interaction (HPS-2)

This component interaction diagram shows the end-to-end flow for the Change Prices use case, tracing through all refined elements.

```mermaid
sequenceDiagram
    actor User
    participant SPA as Angular SPA
    participant GW as API Gateway
    participant PS_CMD as Pricing Service<br/>CommandHandler
    participant PS_CACHE as RateRuleCache
    participant PS_CALC as DerivedRateCalculator
    participant PS_REPO as PriceRepository
    participant PS_RCACHE as PriceReadCache
    participant KAFKA as Kafka
    participant CMA as Channel Mgmt<br/>Adapter
    participant CMS_EXT as Channel Mgmt<br/>System

    Note over User,CMS_EXT: === HPS-2: Change Prices ===

    User->>SPA: Select hotel, dates, base price changes
    SPA->>GW: POST /api/prices/change { hotelId, changes[] }
    GW->>GW: Validate JWT, check roles
    GW->>PS_CMD: Forward + X-User-Id, X-Authorized-Hotels

    PS_CMD->>PS_CMD: Validate hotel is in authorized hotels
    PS_CMD->>PS_CACHE: getRateRules(hotelId), getTaxConfig(hotelId)
    PS_CACHE-->>PS_CMD: Rate rules, tax config (in-memory)

    PS_CMD->>PS_CALC: computeDerivedRates(basePrices, rules, taxConfig)
    PS_CALC-->>PS_CMD: Map<RoomType, RateType, Money>

    PS_CMD->>PS_REPO: savePriceChange(hotelId, dates, allPrices)
    PS_REPO-->>PS_CMD: OK

    PS_CMD->>PS_RCACHE: updateReadCache(hotelId, dates, allPrices)
    PS_RCACHE-->>PS_CMD: OK

    PS_CMD->>KAFKA: publish PriceChangedEvent (async, fire-and-forget)
    PS_CMD-->>GW: ChangePricesResult { success }
    GW-->>SPA: 200 OK { success }
    SPA-->>User: "Prices updated" (within 100ms)

    Note over KAFKA,CMS_EXT: === Async Publication ===

    KAFKA->>CMA: Consume PriceChangedEvent (hotelId partition)
    CMA->>CMA: Transform to CMS format
    CMA->>CMS_EXT: POST /prices { hotelId, prices[] }
    CMS_EXT-->>CMA: ACK
```

---

## View 4: Query Path Scalability (HPS-3)

This deployment view focuses on the query path, showing how stateless query handling and in-memory caches enable horizontal scaling for QA-4.

```mermaid
graph TB
    subgraph "Load Balancer"
        LB[Cloud Load Balancer]
    end

    subgraph "Gateway Tier (2+ instances)"
        GW1[API Gateway<br/>Instance 1]
        GW2[API Gateway<br/>Instance 2]
    end

    subgraph "Pricing Query Tier (3+ instances, auto-scaled)"
        subgraph "Instance 1"
            QH1[PriceQueryHandler]
            RC1[PriceReadCache]
        end
        subgraph "Instance 2"
            QH2[PriceQueryHandler]
            RC2[PriceReadCache]
        end
        subgraph "Instance N"
            QHN[PriceQueryHandler]
            RCN[PriceReadCache]
        end
    end

    subgraph "Pricing Write Tier (1-2 instances)"
        PS_WRITE[PriceCommandHandler<br/>+ Write Path]
    end

    subgraph "Data Tier"
        PDB_WRITE[(Pricing DB<br/>Primary - Write)]
        PDB_READ1[(Pricing DB<br/>Read Replica 1)]
        PDB_READ2[(Pricing DB<br/>Read Replica 2)]
    end

    LB --> GW1
    LB --> GW2

    GW1 --> QH1
    GW1 --> QH2
    GW2 --> QH1
    GW2 --> QH2

    QH1 --> RC1
    QH2 --> RC2
    QHN --> RCN

    PS_WRITE --> PDB_WRITE
    PS_WRITE -.->|cache invalidation| RC1
    PS_WRITE -.->|cache invalidation| RC2
    PS_WRITE -.->|cache invalidation| RCN

    PDB_WRITE -->|replication| PDB_READ1
    PDB_WRITE -->|replication| PDB_READ2
```

---

## View 5: Integration — Pricing Service ↔ Hotel Management Service

This view shows how the Pricing Service obtains hotel configuration and rate rules from the Hotel Management Service, including the caching and change notification mechanism.

```mermaid
graph TB
    subgraph "Pricing Service"
        RRC[RateRuleCache<br/><i>In-Memory</i>]
        PS_CMD[PriceCommandHandler]
    end

    subgraph "Hotel Management Service"
        HMS_API[HotelConfigProvisioningPort]
        HMS_PUB[ConfigChangePublisher]
    end

    subgraph "Messaging"
        CONFIG_KAFKA{{Kafka<br/>hotel-config-changes}}
        PRICE_KAFKA{{Kafka<br/>price-changes}}
    end

    subgraph "Admin Flow"
        ADMIN[Administrator]
        HMS_MGMT[Hotel/Rate<br/>Management]
    end

    RRC -->|Startup: GET /api/hotels/config/all| HMS_API
    RRC -->|Startup: GET /api/rates/rules/all| HMS_API

    ADMIN --> HMS_MGMT
    HMS_MGMT -->|on change| HMS_PUB
    HMS_PUB -->|publish event| CONFIG_KAFKA
    CONFIG_KAFKA -->|consume event| RRC

    PS_CMD -->|in-memory lookup| RRC

    HMS_API -.->|REST| RRC
```

---

## View 6: Kafka Topic and Consumer Topology

```mermaid
graph LR
    subgraph "Kafka Cluster"
        subgraph "price-changes (6 partitions, RF=3)"
            P0[Partition 0<br/>hotelA, hotelB]
            P1[Partition 1<br/>hotelC]
            P2[Partition 2<br/>hotelD, hotelE]
            P3[Partition 3<br/>hotelF]
            P4[Partition 4<br/>hotelG, hotelH]
            P5[Partition 5<br/>hotelI]
        end
        subgraph "hotel-config-changes (3 partitions, RF=3)"
            C0[Partition 0]
            C1[Partition 1]
            C2[Partition 2]
        end
    end

    PS[Pricing Service<br/>Producer] --> P0
    PS --> P1
    PS --> P2
    PS --> P3
    PS --> P4
    PS --> P5

    CMA1[Channel Mgmt Adapter<br/>Instance 1<br/>Consumer Group: cma-group] --> P0
    CMA1 --> P1
    CMA1 --> P2
    CMA2[Channel Mgmt Adapter<br/>Instance 2<br/>Consumer Group: cma-group] --> P3
    CMA2 --> P4
    CMA2 --> P5

    PS_CACHE[Pricing Service<br/>RateRuleCache<br/>Consumer Group: ps-config] --> C0
    PS_CACHE --> C1
    PS_CACHE --> C2

    HMS[HMS<br/>ConfigChangePublisher<br/>Producer] --> C0
    HMS --> C1
    HMS --> C2
```

---

## Summary of Views

| View | Type | Primary Purpose |
|------|------|----------------|
| View 1: Pricing Service CQRS | Component Diagram | Internal structure of the critical Pricing Service |
| View 2: Authentication Flow | Sequence Diagram | End-to-end OAuth2/PKCE flow for QA-5 |
| View 3: Price Change Flow | Sequence Diagram | End-to-end HPS-2 flow through all refined elements |
| View 4: Query Path Scalability | Deployment Diagram | Horizontal scaling of read path for QA-4 |
| View 5: PS↔HMS Integration | Component Diagram | Cached reference data with change notification |
| View 6: Kafka Topology | Component Diagram | Topic partitioning, consumer groups |