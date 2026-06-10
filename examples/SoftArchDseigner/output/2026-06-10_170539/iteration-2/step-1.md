# ADD Step 1: Review Inputs (Iteration 2)

## Context from Iteration 1

Iteration 1 established the overall system structure:
- **Architectural Style:** Service-Based Architecture with Ports & Adapters
- **Top-Level Elements:** HPS Web Frontend (Angular SPA), API Gateway, Pricing Service, Hotel Management Service, Channel Management Adapter
- **Data Stores:** Pricing Database, Management Database
- **Messaging:** Kafka for async price publication to Channel Management System
- **Key Pattern:** API Gateway as protocol boundary; core services expose technology-agnostic ports

## Carried-Forward Risks from Iteration 1

| Risk ID | Description | Target Iteration |
|----------|-------------|-----------------|
| R5 | QA-1 (100ms publication latency) not addressed — no latency budgets or caching strategies | Iteration 2 |
| R7 | Authentication/Authorization details: token flow between Angular SPA, API Gateway, Identity Service unspecified | Iteration 2 |
| R2 | Channel Management Adapter scope: standalone vs embedded? | Iteration 2 |
| R3 | Kafka topic design, partitioning, consumer groups not detailed | Iteration 2 |

## Architectural Drivers for Iteration 2

Iteration 2 focuses on "structures supporting primary functionality." The primary functional flows are:

### Primary Use Cases to Refine

| Use Case | Rationale |
|----------|-----------|
| **HPS-2: Change Prices** | Core domain operation; triggers derived rate calculation, simulation, and publication to channel management. Most complex flow. |
| **HPS-3: Query Prices** | High-volume operation (100K→1M queries/day per QA-4). Must be performant and scalable. |
| **HPS-1: Login** | Entry point for all user interactions; security is paramount (QA-5). |

### Quality Attributes Requiring Structural Decisions Now

| QA | Rationale |
|----|-----------|
| **QA-1 (Performance):** 100ms publication after base price change. [High | High] | The internal structure of Pricing Service must support in-memory or near-real-time computation of derived rates. This is the most demanding latency requirement and must be designed into the service internals now. |
| **QA-4 (Scalability):** 100K→1M daily API queries, ≤20% latency increase. [High | High] | The query path structure must support horizontal scaling. Read/write separation and caching strategies must be designed now. |
| **QA-5 (Security):** Credentials validated; only authorized functions shown. [High | Medium] | The authentication flow and authorization model must be designed into the frontend-to-gateway-to-service chain. |

### Supporting QAs

| QA | Rationale |
|----|-----------|
| **QA-6 (Modifiability):** gRPC support without core changes. [Medium | Medium] | The port/adapter boundaries within Pricing Service and Hotel Management Service must be concretely defined to ensure protocol isolation. |
| **QA-9 (Testability):** Integration testing independent of external systems. [Medium | Medium] | Service interfaces must be designed with testability in mind — clean contracts enable stubbing. |

## Elements from Iteration 1 Available for Refinement

| Element | Refinement Status |
|---------|-------------------|
| HPS Web Frontend | Internal component structure not yet designed |
| API Gateway | Routing rules, auth flow, protocol adapter details not yet designed |
| Pricing Service | Internal modules (ports, domain logic, adapters) not yet designed |
| Hotel Management Service | Internal modules not yet designed; supporting role for pricing |
| Channel Management Adapter | Scope and interaction pattern not finalized |
| Pricing Database | Schema, read/write separation not designed |
| Kafka (price-changes topic) | Topic structure, partitioning not designed |

## Key Observations

1. **Pricing Service is the critical path** — it must compute derived rates from base prices, support simulation, serve queries at scale, and publish results, all within 100ms (QA-1).
2. **Read vs. write paths diverge** — price changes (writes) must be fast and consistent; price queries (reads) must be high-throughput and scalable. This suggests a CQRS-style separation within the Pricing Service.
3. **Hotel configuration is read-heavy for pricing** — the Pricing Service needs hotel tax rates, rate rules, and room types for every price change. These are relatively stable reference data.
4. **Authentication flow must be concrete** — CON-2 mandates cloud identity service; the token flow from Angular SPA through API Gateway to services must be specified for QA-5.