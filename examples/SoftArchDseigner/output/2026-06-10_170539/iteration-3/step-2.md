# ADD Step 2: Establish Iteration Goal by Selecting Drivers (Iteration 3)

## Iteration Goal

**Design reliability and availability mechanisms across the system** — specifically, ensure that 100% of price changes are successfully published and received (QA-2), that the system achieves 99.9% uptime for price queries (QA-3), and that failure modes are handled gracefully. This iteration answers: _How does the system guarantee reliable publication and remain available even when components fail?_

## Selected Architectural Drivers

| Priority | Driver | Justification |
|----------|--------|---------------|
| **Primary** | QA-2 (Reliability) | 100% of price changes successfully published (queryable) AND received by Channel Management System. This is the most demanding quality requirement. Requires end-to-end reliability design: idempotency, guaranteed delivery, acknowledgment tracking, and reconciliation. |
| **Primary** | QA-3 (Availability) | Price query uptime SLA of 99.9%. Requires redundancy at every tier, health checking, graceful degradation, and failure isolation. Directly shapes deployment topology and resilience patterns. |
| **Primary** | R10 (100% publication) | Carried-forward risk from Iteration 2. CMA retry/idempotency/guaranteed-delivery mechanisms must be fully designed. |
| **Primary** | R12 (Gateway SPOF) | Carried-forward risk from Iteration 2. API Gateway redundancy and failover must be designed. |
| **Secondary** | R8 (Cache consistency) | Carried-forward risk from Iteration 2. Multi-instance read cache update mechanism must be specified. |
| **Secondary** | R9 (Startup race) | Carried-forward risk from Iteration 2. RateRuleCache warm-up / readiness gating must be designed to avoid 503 during restart. |
| **Secondary** | R1 (Cross-service consistency) | Carried-forward risk from Iteration 1. HMS unavailability impact on Pricing Service must be mitigated. |
| **Secondary** | QA-8 (Monitorability) | To verify QA-2 and QA-3, metrics collection points must be identified and preliminary metric definitions provided. Full infrastructure in Iteration 4. |
| **Tertiary** | QA-1 (Performance) | Reliability mechanisms must not compromise the 100ms latency budget. Every reliability addition is checked against the latency budget. |
| **Tertiary** | CON-6 (Cloud-Native) | Cloud-native patterns (multi-AZ, managed services, health probes, auto-scaling) are the primary enablers for availability. |

## Drivers NOT Addressed (Deferred)

| Driver | Reason |
|--------|--------|
| QA-4 (Scalability detail) | Already structurally addressed in Iteration 2; scaling policies deferred to Iteration 4 |
| QA-5 (Security detail) | Auth flow already designed in Iteration 2 |
| QA-6 (Modifiability) | Port/adapter structure already defined in Iterations 1-2 |
| QA-7 (Deployability) | CI/CD pipeline design → Iteration 4 |
| QA-9 (Testability) | Test patterns → Iteration 4 |
| CRN-3, CRN-4, CRN-5 | Development process → Iteration 4 |
| HPS-4/5/6 (Management internals) | Supporting CRUD → Iteration 4 |

## Success Criteria for This Iteration

1. **End-to-end reliable publication** is designed: idempotent price change processing in Pricing Service, guaranteed Kafka delivery, and assured CMS delivery with acknowledgment tracking. All failure modes (transient, permanent) are handled. QA-2 satisfied.
2. **API Gateway redundancy** is designed: multi-instance Gateway with load balancing, health checks, and session-independent token validation (R12 resolved).
3. **99.9% availability deployment topology** is designed: redundancy at Gateway, Pricing Service (query and write), Hotel Management Service, and CMA tiers. Multi-AZ deployment per CON-6.
4. **Read cache consistency** mechanism is specified across Pricing Service query instances (R8 resolved).
5. **Graceful startup and dependency failure** handling is designed: readiness probes, RateRuleCache warm-up gating (R9), degraded operation when HMS is unavailable (R1).
6. **Channel Management Adapter reliability** is fully specified: retry with exponential backoff, circuit breaker, dead letter queue, idempotent CMS delivery, and reconciliation (R10 resolved).
7. **Preliminary metrics** for reliability and availability are identified (foundation for QA-8 / Iteration 4).
8. **QA-1 latency budget** remains intact: all reliability additions are verified against the 100ms budget.