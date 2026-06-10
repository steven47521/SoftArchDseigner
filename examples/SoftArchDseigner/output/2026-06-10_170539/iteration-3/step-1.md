# ADD Step 1: Review Inputs (Iteration 3)

## Context from Prior Iterations

**Iteration 1** established the overall system structure: Service-Based Architecture with 5 top-level elements (HPS Web Frontend, API Gateway, Pricing Service, Hotel Management Service, Channel Management Adapter), two data stores, and Kafka messaging.

**Iteration 2** refined primary functionality:
- Pricing Service: CQRS + in-memory derived rate computation, latency budget defined (55ms core / 100ms total)
- API Gateway: OAuth2 PKCE auth flow, JWT validation, role enforcement, claim propagation, route table
- HPS Web Frontend: 7 feature modules, AuthGuard/AdminGuard, role-based UI
- PS↔HMS integration: cached reference data with Kafka change notification (`hotel-config-changes` topic)
- Channel Management Adapter: standalone service; `price-changes` topic (6 partitions, hotelId key)
- Port interfaces defined for all services (QA-6, QA-9)

## Carried-Forward Risks Requiring Resolution in Iteration 3

| Risk ID | Description | Source | Severity |
|---------|-------------|--------|----------|
| **R10** | QA-2 (100% publication reliability): Kafka at-least-once delivery exists, but Channel Management Adapter's retry/idempotency/guaranteed-delivery mechanisms are only preliminarily designed. | Iteration 2 | **High** |
| **R12** | API Gateway as single point of failure: both frontend and external API consumers route through a single Gateway tier. Gateway outage blocks all access. | Iteration 2 | **High** |
| **R8** | Read cache consistency across Pricing Service query instances: when a write occurs, all read caches across multiple query instances must be updated. Mechanism not specified. | Iteration 2 | **Medium** |
| **R9** | RateRuleCache startup race condition: if pricing requests arrive before RateRuleCache finishes initial load from HMS, the Pricing Service returns 503. May violate QA-3 during restart. | Iteration 2 | **Medium** |
| **R1** | Data consistency between Pricing and Management DBs: Pricing Service calls HMS synchronously for config on startup. If HMS is unavailable, Pricing Service cannot start. | Iteration 1 | **Medium** |

## Primary Quality Attribute Drivers for This Iteration

| QA | Scenario | Importance | Difficulty | Rationale |
|----|----------|------------|------------|-----------|
| **QA-2 (Reliability)** | 100% of price changes successfully published (queryable) and received by Channel Management System. [HPS-2] | High | High | This is the most demanding reliability requirement. Must design idempotent publication, delivery guarantees, and failure recovery. |
| **QA-3 (Availability)** | Price query uptime SLA must reach 99.9% (outside maintenance windows). [All] | High | High | This constrains the deployment topology: redundancy, failover, health checks, graceful degradation. |

## Supporting Drivers

| Driver | Rationale |
|--------|-----------|
| **QA-1 (Performance)** | Reliability mechanisms (retries, idempotency checks) must not compromise the 100ms latency budget established in Iteration 2. |
| **QA-4 (Scalability)** | Availability mechanisms (redundancy, load balancing) must support the 100K→1M query/day scaling target. |
| **QA-8 (Monitorability)** | To verify QA-2 and QA-3 are met, the system must expose metrics on publication success/failure and query availability. Preliminary metric design needed now; full infrastructure in Iteration 4. |
| **CON-6 (Cloud-Native)** | Cloud-native patterns (managed services, auto-scaling, health checks, multi-AZ) directly support reliability and availability goals. |

## Elements from Prior Iterations Most Affected

| Element | Current State | Reliability/Availability Gap |
|---------|---------------|------------------------------|
| **API Gateway** | Single tier; routes requests, validates tokens | No redundancy (single point of failure per R12); needs multi-instance with load balancing |
| **Pricing Service (Query Tier)** | Stateless query handlers with in-memory caches | Cache invalidation across instances not designed (R8); needs consistent cache update mechanism |
| **Pricing Service (Write Tier)** | Command handler with Kafka fire-and-forget publish | No confirmation that price is queryable after write; no idempotency for duplicate writes |
| **Pricing Service (Startup)** | RateRuleCache loads from HMS at startup | Cold-start race condition (R9); no readiness gating |
| **Channel Management Adapter** | Standalone service; Kafka consumer with basic RetryHandler and DeadLetterQueue | RetryHandler is preliminary; no idempotency guarantee for CMS delivery; no circuit breaker (R10) |
| **Hotel Management Service** | Single instance in Iteration 1 deployment view | If HMS is down, Pricing Service config refresh fails and Pricing Service startup blocks (R1) |
| **Kafka** | Two topics defined; cloud-managed per CON-6 | Relies on managed service availability; no explicit consumer lag monitoring |

## Key Observations

1. **QA-2 (100% reliability) requires end-to-end guarantees:** It's not enough for Kafka to deliver at-least-once. We need: (a) idempotent price change processing in Pricing Service, (b) guaranteed delivery from Channel Management Adapter to CMS, (c) confirmation that prices are queryable, (d) reconciliation mechanisms for edge cases.

2. **QA-3 (99.9% availability) requires defense in depth:** Multiple layers must be redundant — Gateway, Pricing Service, HMS. Each service must handle dependency failures gracefully. 99.9% allows ~8.76 hours downtime/year. With cloud-native deployment, this is achievable with proper redundancy.

3. **Reliability and performance can conflict:** Adding idempotency checks, retries, and acknowledgments can add latency. Must stay within the 100ms budget (QA-1).

4. **Availability and consistency trade-offs:** To keep queries available when the write path is degraded, the read cache may serve slightly stale data — acceptable for hotel pricing where changes are infrequent and not life-critical.