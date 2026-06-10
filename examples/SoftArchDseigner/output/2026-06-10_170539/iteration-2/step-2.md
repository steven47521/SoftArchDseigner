# ADD Step 2: Establish Iteration Goal by Selecting Drivers (Iteration 2)

## Iteration Goal

**Refine the internal structures of the top-level elements to support primary functionality** — specifically, design the internal architecture of the Pricing Service, the price query path, the authentication/authorization flow, and the integration patterns between Pricing Service and Hotel Management Service. This iteration answers: _How does the system fulfill its core use cases (Login, Change Prices, Query Prices) while satisfying the most demanding quality attributes (performance, scalability, security)?_

## Selected Architectural Drivers

| Priority | Driver | Justification |
|----------|--------|---------------|
| **Primary** | HPS-2: Change Prices | Core domain operation. Must support base/fixed price changes, derived rate calculation, simulation, and publication — all within 100ms (QA-1). |
| **Primary** | QA-1 (Performance) | 100ms publication latency is the most demanding quality attribute. The internal structure of the Pricing Service must be designed around this constraint. |
| **Primary** | HPS-3: Query Prices | High-volume operation (100K→1M/day). Must scale horizontally with ≤20% latency increase (QA-4). Read path must be separated from write path. |
| **Primary** | QA-4 (Scalability) | The query architecture must support 10x traffic growth. Requires structural decisions about caching, read replicas, and stateless services. |
| **Secondary** | HPS-1: Login | Entry point for all users. The authentication token flow and authorization model must be concretely designed. |
| **Secondary** | QA-5 (Security) | Token validation, authorized-function visibility. Must be designed into frontend, gateway, and service layers. |
| **Secondary** | QA-6 (Modifiability) | Protocol isolation must be validated by defining concrete port interfaces within Pricing Service and Hotel Management Service. |
| **Secondary** | QA-9 (Testability) | Service ports must support stubbing/mocking for integration testing without external dependencies. |
| **Carried Risk** | R5 (100ms latency) | Must be resolved — latency budgets and computational strategy required. |
| **Carried Risk** | R7 (Auth details) | Token flow between SPA, Gateway, and Identity Service must be specified. |
| **Carried Risk** | R2 (CMA scope) | Decide standalone vs. embedded for Channel Management Adapter. |
| **Carried Risk** | R3 (Kafka design) | Topic structure, partitioning strategy, consumer groups. |

## Drivers Deferred to Later Iterations

| Driver | Reason for Deferral |
|--------|---------------------|
| QA-2 (Reliability: 100% publication) | Detailed retry/idempotency mechanisms → Iteration 3 |
| QA-3 (Availability: 99.9% SLA) | Redundancy, failover patterns → Iteration 3 |
| QA-7 (Deployability) | CI/CD pipeline design → Iteration 4 |
| QA-8 (Monitorability) | Metrics collection infrastructure → Iteration 4 |
| CRN-3, CRN-4, CRN-5 | Development process concerns → Iteration 4 |
| HPS-4/5/6 (Management use cases) | Supporting functionality; detailed in Iteration 2 only as needed for pricing integration |

## Success Criteria for This Iteration

1. **Pricing Service** internal structure is designed with clear separation of write path (price changes/simulation) and read path (price queries), satisfying QA-1 and QA-4.
2. **Derived rate calculation strategy** is defined — how base price changes cascade to derived rates within 100ms.
3. **Authentication and authorization flow** is specified end-to-end: Angular SPA → API Gateway → backend services, satisfying QA-5.
4. **Pricing Service ↔ Hotel Management Service integration** is designed — how pricing obtains hotel configuration and rate rules.
5. **Kafka topic and partitioning design** for price publication is defined.
6. **Channel Management Adapter scope** decision is finalized.
7. **Concrete port interfaces** for Pricing Service and Hotel Management Service are defined, supporting QA-6 and QA-9.