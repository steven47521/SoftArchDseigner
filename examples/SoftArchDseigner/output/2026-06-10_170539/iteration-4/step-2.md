# ADD Step 2: Establish Iteration Goal by Selecting Drivers (Iteration 4)

## Iteration Goal

**Complete all remaining architectural design and establish the development and operational foundation** — specifically: finish internal designs for supporting services (HMS, databases), design the CI/CD pipeline and monitoring infrastructure, define the test strategy, resolve remaining technology choices, establish team structure, and ensure the architecture is construction-ready. This iteration answers: _How is the system built, tested, deployed, monitored, and maintained by the development team?_

## Selected Architectural Drivers

### Primary Drivers

| Priority | Driver | Justification |
|----------|--------|---------------|
| **Primary** | CRN-5 (Continuous Deployment) | Set up continuous deployment infrastructure. This is an explicit architectural concern that has not been addressed. The CI/CD pipeline design must cover all 5 services plus the Angular SPA. |
| **Primary** | QA-8 (Monitorability) | Operators must measure price publication performance and reliability. 100% of metrics must be collectable. The 14 metrics defined in Iteration 3 need a collection, aggregation, dashboard, and alerting infrastructure. |
| **Primary** | QA-9 (Testability) | 100% of system elements must support integration testing independent of external systems. The port/adapter pattern enables this structurally; now we must design test harnesses, stub definitions, contract tests, and testing environments. |
| **Primary** | QA-7 (Deployability) | Applications move between non-production environments without code changes. Externalized configuration, environment profiles, and deployment manifests must be concretely designed. |
| **Primary** | CRN-3 (Work Assignment) | Assign work to development team members. The architecture-to-team mapping is a core organizational design decision that affects delivery velocity and code ownership. |

### Secondary Drivers

| Priority | Driver | Justification |
|----------|--------|---------------|
| **Secondary** | CRN-4 (Technical Debt) | Avoid introducing technical debt. Establish architectural fitness functions, code standards, and review processes. |
| **Secondary** | HPS-4/5/6 (Supporting Use Cases) | Manage Hotels, Rates, Users — these are CRUD operations that the Hotel Management Service must support. Internal design must be completed. |
| **Secondary** | CON-3 (Git Platform) | Code hosted on proprietary Git-based platform. Repository structure and branching strategy must be defined. |
| **Secondary** | CON-4 (Timeline) | 6-month delivery, 2-month MVP. The CI/CD pipeline and team assignment must support phased delivery. |
| **Secondary** | R4 (Gateway choice) | Resolve API Gateway technology: cloud-managed vs. self-managed Java gateway. |
| **Secondary** | R14 (OutboxPoller) | Design resilient outbox processing across multiple Pricing Service instances. |
| **Secondary** | R16 (CMA idempotency) | Harden idempotency across CMA instances during Kafka rebalances. |
| **Secondary** | R18 (Full monitoring) | Complete monitoring infrastructure design per QA-8. |

### Validation Driver

| Driver | Justification |
|--------|---------------|
| **QA-6 (Modifiability validation)** | Verify that adding gRPC support would indeed require no core changes. Walk through the gRPC addition scenario against the current architecture to validate the port/adapter isolation. |

## Drivers NOT Addressed (Already Satisfied)

| Driver | Status |
|--------|--------|
| QA-1 (100ms performance) | ✅ Designed in Iteration 2, preserved in Iteration 3 |
| QA-2 (100% reliability) | ✅ Designed in Iteration 3 |
| QA-3 (99.9% availability) | ✅ Designed in Iteration 3 |
| QA-4 (Scalability) | ✅ Designed in Iteration 2 |
| QA-5 (Security) | ✅ Designed in Iteration 2 |
| CON-1 (Cross-platform browsers) | ✅ Angular SPA, Iteration 1 |
| CON-2 (Cloud identity + hosting) | ✅ Iterations 1-2 |
| CON-5 (REST initially) | ✅ Iteration 1, validated in this iteration |
| CON-6 (Cloud-native) | ✅ All iterations |
| CRN-1 (Overall structure) | ✅ Iteration 1 |
| CRN-2 (Java, Angular, Kafka) | ✅ All iterations |

## Success Criteria for This Iteration

1. **CI/CD pipeline** is designed: build, test, and deployment stages for all 6 deployables (5 services + 1 SPA), per CRN-5.
2. **Monitoring infrastructure** is fully designed: metrics collection (agents/exporters), aggregation (time-series DB), dashboards, and alerting rules. The 14 metrics from Iteration 3 are operationalized. QA-8 satisfied.
3. **Test strategy** is defined: unit test scope, integration test isolation using port stubs, contract tests between services, end-to-end test boundaries. QA-9 satisfied.
4. **Externalized configuration** is concretely designed: per-environment configuration structure, secret management, feature flags. QA-7 satisfied.
5. **Team structure** is defined: mapping of architectural elements to development teams/sub-teams, aligned with team expertise (Java, Angular, Kafka — CRN-2). CRN-3 satisfied.
6. **Hotel Management Service** internal design is completed: domain logic for HPS-4/5/6, adapters, and the Management Database logical schema.
7. **Pricing Database** logical schema is completed.
8. **Remaining risks** (R4, R14, R16, R18) are resolved.
9. **QA-6 (Modifiability)** is validated by walking through a gRPC addition scenario.
10. **Architectural fitness functions** are defined to prevent technical debt (CRN-4).