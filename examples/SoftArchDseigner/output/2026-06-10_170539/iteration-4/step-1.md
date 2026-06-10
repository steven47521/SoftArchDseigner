# ADD Step 1: Review Inputs (Iteration 4)

## Context from Prior Iterations

**Iteration 1** established the overall system structure: Service-Based Architecture with 5 top-level elements — HPS Web Frontend (Angular SPA), API Gateway, Pricing Service, Hotel Management Service, Channel Management Adapter — plus two data stores and Kafka messaging.

**Iteration 2** refined primary functionality: CQRS within Pricing Service, in-memory derived rate computation with 100ms latency budget, OAuth2 PKCE authentication flow, feature-based Angular modules, cached PS↔HMS integration, Kafka topic topology, and concrete port interfaces.

**Iteration 3** designed reliability and availability: Transactional Outbox for guaranteed Kafka publication, idempotent CMA delivery with circuit breaker/retry/DLQ/reconciliation, multi-AZ deployment topology for 99.9% uptime, shared Redis cache, graceful degradation with fallback config, and 14 preliminary metrics.

## Carried-Forward Risks Requiring Resolution in Iteration 4

| Risk ID | Description | Source | Severity |
|---------|-------------|--------|----------|
| **R14** | OutboxPoller single process: if the Pricing Service write instance's OutboxPoller fails, outbox messages accumulate. Only one instance polls. | Iteration 3 | Medium |
| **R16** | CMA idempotency store consistency across instances during Kafka rebalance: in-memory LRU may miss duplicated events. | Iteration 3 | Medium |
| **R18** | Full monitoring infrastructure not designed: only metric definitions exist (14 metrics from Iteration 3). Collection, aggregation, dashboards, and alerting are missing (QA-8). | Iteration 3 | Medium |
| **R4** | API Gateway technology choice unresolved: cloud-managed vs. self-managed (Java) gateway. | Iteration 1 | Low |

## Architectural Drivers for Iteration 4

Iteration 4 focuses on "development and operations." This encompasses: CI/CD pipeline (CRN-5), work assignment (CRN-3), technical debt avoidance (CRN-4), full monitorability (QA-8), testability (QA-9), deployability (QA-7), and completing internal designs for supporting services (HPS-4/5/6).

### Primary Drivers

| Driver | Importance | Difficulty | Rationale |
|--------|------------|------------|-----------|
| **QA-8 (Monitorability)** | Medium | Medium | System operators measure price publication performance and reliability. The system must provide a mechanism to collect 100% of these metrics. 14 metrics defined in Iteration 3 — full infrastructure needed now. |
| **QA-9 (Testability)** | Medium | Medium | 100% of system elements support integration testing independent of external systems. Port/adapter pattern already enables this; must now design test harnesses, stubs, and contract tests. |
| **QA-7 (Deployability)** | Medium | Medium | Applications move between non-production environments without code changes. Externalized configuration must be concretely designed. |
| **CRN-5 (Continuous Deployment)** | — | — | Set up continuous deployment infrastructure. |
| **CRN-3 (Work Assignment)** | — | — | Assign work to development team members. Map architectural elements to team structure. |
| **CRN-4 (Technical Debt)** | — | — | Avoid introducing technical debt. Establish coding standards, review processes, and architectural fitness functions. |

### Secondary Drivers

| Driver | Rationale |
|--------|-----------|
| **HPS-4 (Manage Hotels)** | Supporting CRUD use case. Hotel Management Service internals not fully designed. |
| **HPS-5 (Manage Rates)** | Supporting CRUD use case. Rate rule management internals needed. |
| **HPS-6 (Manage Users)** | Supporting CRUD use case. User permission management internals needed. |
| **CON-3 (Git-based platform)** | Code must be hosted on proprietary Git-based platform. Shapes repository structure and CI/CD integration. |
| **CON-4 (6-month delivery)** | Initial version within 6 months; MVP demo in 2 months. The development process must support phased delivery. |
| **QA-6 (Modifiability — validation)** | Port/adapter structure defined in prior iterations. Validate that gRPC addition would indeed be isolated. |
| **R4 (Gateway choice)** | Resolve API Gateway technology decision (cloud-managed vs. self-managed). |
| **R14 (OutboxPoller)** | Design resilient outbox processing. |
| **R16 (CMA idempotency)** | Harden idempotency across CMA instances. |
| **R18 (Full monitoring)** | Design monitoring infrastructure. |

## Elements Requiring Refinement or Completion

| Element | Current Design Status | Gap |
|---------|----------------------|-----|
| **Hotel Management Service** | Ports defined for PS integration. No internal domain logic, adapters, or DB schema. | Full internal design needed (HPS-4/5/6). |
| **Management Database** | Named but no schema. | Logical schema for hotels, rates, users, permissions. |
| **Pricing Database** | Outbox table defined. Price table structure not specified. | Logical schema for prices. |
| **CI/CD Pipeline** | Not designed. | Build, test, deploy pipeline per CRN-5. |
| **Monitoring Infrastructure** | 14 metrics defined in Iteration 3. | Collection, aggregation, dashboard, alerting per QA-8. |
| **Test Architecture** | Port interfaces enable mocking. | Test strategy, harnesses, contract tests, integration test isolation per QA-9. |
| **Configuration Management** | "Externalized configuration" stated as principle. | Concrete design for per-environment config per QA-7. |
| **API Gateway** | Internal design refined. Technology choice pending. | Resolve R4: cloud-managed vs. self-managed. |
| **Team Structure** | Not defined. | Map elements to teams per CRN-3. |

## Key Observations

1. **Iteration 4 completes the architecture:** This is the final planned iteration. It must close all remaining risks, complete all internal designs, and establish the operational foundation. The architecture must be "construction-ready" afterward.

2. **QA-8 (Monitorability) closes the loop on QA-2 and QA-3:** The reliability (QA-2) and availability (QA-3) mechanisms designed in Iteration 3 need monitoring to be operationally validated. This iteration connects the metrics to actionable dashboards and alerts.

3. **CRN-3 (Work assignment) must align with the architecture:** The 5 top-level elements naturally map to team boundaries. Feature-based Angular modules also enable parallel frontend work.

4. **CRN-5 (CI/CD) and QA-7 (Deployability) are intertwined:** The deployment pipeline must support moving artifacts between environments without code changes. Externalized configuration is the key enabler.

5. **Supporting use cases (HPS-4/5/6) are CRUD-heavy:** Hotel Management Service internals follow standard layered architecture within the Ports & Adapters pattern — less novel than the Pricing Service, but must be properly designed.