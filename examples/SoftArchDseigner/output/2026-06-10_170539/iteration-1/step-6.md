# ADD Step 6: Sketch Views and Perspectives (Iteration 1)

## View 1: System Context Diagram

The system context diagram shows the Hotel Pricing System as a single entity interacting with all external actors and systems identified in Step 3.

```mermaid
graph TB
    subgraph "External Actors & Systems"
        BU[Business User<br/><i>Human</i>]
        AD[Administrator<br/><i>Human</i>]
        WB[Web Browser<br/><i>Windows / OSX / Linux</i>]
        UIS[User Identity Service<br/><i>Cloud Provider</i>]
        CMS[Channel Management System<br/><i>External</i>]
        EXT[External Systems<br/><i>Query API Consumers</i>]
    end

    subgraph "Hotel Pricing System"
        HPS[Hotel Pricing<br/>System]
    end

    BU --> WB
    AD --> WB
    WB -->|HTTPS| HPS
    EXT -->|REST API| HPS
    HPS -->|Auth Requests| UIS
    HPS -->|Price Publication| CMS
```

---

## View 2: Primary Module/Decomposition View

This view shows the top-level decomposition of the Hotel Pricing System into its 5 constituent architectural elements, their data stores, internal communication paths, and external integrations.

```mermaid
graph TB
    subgraph "External"
        WB[Web Browser<br/>Angular SPA Client]
        EXT[External Systems<br/>Query API Consumers]
        UIS[User Identity Service<br/>Cloud Provider]
        CMS[Channel Management System]
    end

    subgraph "Hotel Pricing System"
        subgraph "Frontend Tier"
            FE[HPS Web Frontend<br/><i>Angular SPA</i>]
        end

        subgraph "Gateway Tier"
            GW[API Gateway<br/><i>Java / Cloud-Managed</i>]
        end

        subgraph "Service Tier"
            PS[Pricing Service<br/><i>Java</i>]
            HMS[Hotel Management Service<br/><i>Java</i>]
            CMA[Channel Management Adapter<br/><i>Java / Kafka</i>]
        end

        subgraph "Data Tier"
            PDB[(Pricing<br/>Database)]
            MDB[(Management<br/>Database)]
        end

        subgraph "Messaging"
            KAFKA{{Kafka<br/>price-changes}}
        end
    end

    WB -->|HTTPS/REST| GW
    EXT -->|REST API| GW
    FE -.->|Served via CDN| WB

    GW -->|REST| PS
    GW -->|REST| HMS
    GW -->|Token Validation| UIS

    PS -->|REST| HMS
    PS --> KAFKA
    PS --> PDB

    HMS --> MDB

    KAFKA --> CMA
    CMA -->|Push Prices| CMS
```

---

## View 3: Use Case Allocation View

This view maps each use case to the architectural elements involved, showing the flow through the system.

```mermaid
graph LR
    subgraph "HPS-1: Login"
        L1[HPS Web Frontend] --> L2[API Gateway] --> L3[User Identity Service]
    end

    subgraph "HPS-2: Change Prices"
        C1[HPS Web Frontend] --> C2[API Gateway] --> C3[Pricing Service]
        C3 --> C4[Hotel Management Service]
        C3 --> C5[Kafka] --> C6[Channel Mgmt Adapter] --> C7[Channel Mgmt System]
    end

    subgraph "HPS-3: Query Prices"
        Q1[HPS Web Frontend / External Systems] --> Q2[API Gateway] --> Q3[Pricing Service]
    end

    subgraph "HPS-4/5/6: Manage Hotels / Rates / Users"
        M1[HPS Web Frontend] --> M2[API Gateway] --> M3[Hotel Management Service]
    end
```

---

## View 4: Deployment View (Cloud-Native)

This deployment view illustrates the cloud-native deployment topology per CON-6. All services are containerized and deployed in the cloud, with the Angular SPA served via CDN.

```mermaid
graph TB
    subgraph "Cloud Environment"
        subgraph "CDN"
            CDN[Angular SPA<br/>Static Assets]
        end

        subgraph "Container Orchestration Cluster"
            subgraph "Gateway Pods"
                GW1[API Gateway<br/>Instance 1]
                GW2[API Gateway<br/>Instance 2]
            end

            subgraph "Pricing Pods"
                PS1[Pricing Service<br/>Instance 1]
                PS2[Pricing Service<br/>Instance 2]
            end

            subgraph "Management Pods"
                HMS1[Hotel Mgmt Service<br/>Instance 1]
            end

            subgraph "Adapter Pods"
                CMA1[Channel Mgmt Adapter<br/>Instance 1]
            end
        end

        subgraph "Managed Services"
            KAFKA_SVC[Managed Kafka<br/>Service]
            PDB_SVC[Managed Pricing<br/>Database]
            MDB_SVC[Managed Management<br/>Database]
            IDP[Cloud Identity<br/>Provider]
        end
    end

    subgraph "External"
        USERS[Users<br/>Browsers]
        EXT_API[External API<br/>Consumers]
        CMS_EXT[Channel Mgmt<br/>System]
    end

    USERS --> CDN
    USERS --> GW1
    USERS --> GW2
    EXT_API --> GW1
    EXT_API --> GW2

    GW1 --> PS1
    GW1 --> PS2
    GW2 --> PS1
    GW2 --> PS2
    GW1 --> HMS1
    GW2 --> HMS1

    PS1 --> PDB_SVC
    PS2 --> PDB_SVC
    HMS1 --> MDB_SVC

    PS1 --> KAFKA_SVC
    PS2 --> KAFKA_SVC
    KAFKA_SVC --> CMA1
    CMA1 --> CMS_EXT

    GW1 --> IDP
    GW2 --> IDP
```

---

## Summary of Views

| View | Type | Purpose |
|------|------|---------|
| System Context | Context Diagram | Define system boundary and external entities |
| Module/Decomposition | Component-and-Connector | Show top-level elements, their relationships, and data stores |
| Use Case Allocation | Allocation View | Trace use cases to architectural elements |
| Deployment | Deployment Diagram | Show cloud-native topology with containerization, scaling, and managed services |