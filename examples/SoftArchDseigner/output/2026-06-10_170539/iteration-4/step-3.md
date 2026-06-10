# ADD Step 3: Choose One or More System Elements to Refine (Iteration 4)

## Elements Selected for Refinement

Iteration 4 addresses development and operations — it spans infrastructure concerns (CI/CD, monitoring, configuration, testing), team organization, and the completion of internal designs for supporting services. The following elements are selected:

---

### Element 1: CI/CD Pipeline (New Infrastructure Element)

**Why:** CRN-5 explicitly requires setting up continuous deployment infrastructure. No pipeline design exists. This is a cross-cutting infrastructure element that spans all deployables.

**Current State:** Not designed. The system has 6 deployables (HPS Web Frontend, API Gateway, Pricing Service, Hotel Management Service, Channel Management Adapter, and potentially a shared library). Each has its own build, test, and deployment needs.

**Refinement Needed:**
- Pipeline stages: Build → Unit Test → Integration Test → Static Analysis → Container Build → Deploy to Dev → Deploy to Staging → Deploy to Production
- Repository structure (CON-3: proprietary Git platform)
- Branching strategy and environment promotion flow
- Artifact management (container registry)
- Rollback strategy

---

### Element 2: Monitoring Infrastructure (New Infrastructure Element)

**Why:** QA-8 requires collecting 100% of price publication performance and reliability metrics. Iteration 3 defined 14 metrics but no collection/infrastructure design. R18 must be resolved.

**Current State:** 14 metrics defined (gauges, counters, histograms). No collectors, aggregators, dashboards, or alerting rules designed.

**Refinement Needed:**
- Metrics collection layer (agent per service)
- Time-series database for aggregation
- Dashboard definitions (operational and business)
- Alerting rules with severity levels and notification channels
- Log aggregation strategy (complementary to metrics)
- Health check endpoint standardization

---

### Element 3: Test Architecture (Cross-Cutting)

**Why:** QA-9 requires 100% of system elements support integration testing independent of external systems. The port/adapter pattern structurally enables this, but the test strategy and harnesses must be explicitly designed.

**Current State:** All outbound ports are interfaces (PriceRepository, PriceEventPublisher, CMSClient, etc.). Inbound adapters (REST) are separate from domain logic. Test isolation is structurally possible but not operationalized.

**Refinement Needed:**
- Test pyramid definition (unit / integration / contract / E2E)
- Stub/mock strategy per external dependency
- Contract test design between services
- Test data management
- Test environment design (what runs where)

---

### Element 4: Configuration Management (Cross-Cutting)

**Why:** QA-7 requires applications to move between non-production environments without code changes. Externalized configuration must be concretely designed — what is externalized, how it's injected, how secrets are managed.

**Current State:** "Externalized configuration" stated as a principle. No concrete mechanism.

**Refinement Needed:**
- Configuration categories: environment-specific, feature flags, secrets
- Configuration injection mechanism (environment variables, config server, cloud-native config maps/secrets)
- Secret management (cloud provider secret store)
- Per-environment configuration structure

---

### Element 5: Hotel Management Service — Internal Completion

**Why:** HPS-4 (Manage Hotels), HPS-5 (Manage Rates), HPS-6 (Manage Users) are three of six use cases. The HMS internal design beyond the PS integration ports has not been done. This is the last major service requiring internal design.

**Current State (from Iterations 1-2):** 
- Defined as a Java service with Ports & Adapters
- Exposes: HotelConfigProvisioningPort (for PS), ConfigChangePublisher (Kafka)
- Manages: hotels, rates, users
- No domain logic decomposition, no adapter design, no DB schema

**Refinement Needed:**
- Internal domain model: Hotel, Rate, RateRule, RoomType, User, Permission aggregates
- Inbound adapters: REST endpoints for CRUD operations
- Outbound adapters: DB adapter, Kafka adapter
- Authorization logic: hotel-level access enforcement
- Management Database logical schema

---

### Element 6: Database Logical Schemas

**Why:** Both Pricing Database and Management Database need logical schema definitions. The Pricing DB's outbox table is already partially defined; the price data itself and the management schema are not.

**Current State:**
- Pricing DB: `price_change_outbox` table designed in Iteration 3
- Management DB: referenced but no schema

**Refinement Needed:**
- Pricing DB: price tables, indexing strategy for query patterns
- Management DB: hotels, rates, rate_rules, room_types, users, permissions tables

---

### Element 7: Team Structure and Work Assignment

**Why:** CRN-3 requires assigning work to development team members. The architecture-to-team mapping is a key organizational design decision.

**Current State:** Not addressed. CRN-2 tells us team knows Java, Angular, Kafka.

**Refinement Needed:**
- Team decomposition aligned with architectural elements
- Ownership boundaries matching service boundaries
- Skill alignment (Angular → frontend team, Java → backend teams, Kafka → shared infrastructure)
- Coordination points between teams

---

### Element 8: API Gateway Technology Decision (R4 Resolution)

**Why:** R4 from Iteration 1 remains outstanding. The choice between cloud-managed and self-managed gateway affects deployment, operations, and modifiability.

**Current State:** API Gateway internal design is refined (AuthFilter, RoleEnforcer, ClaimPropagator, RequestRouter, RateLimiter, CircuitBreakers). Technology choice between cloud-managed and Java self-managed is pending.

**Refinement Needed:** Decision with rationale aligned with CON-6 (cloud-native), CRN-2 (Java expertise), QA-6 (modifiability), and operational complexity.

---

### Elements NOT Further Refined

| Element | Reason |
|---------|--------|
| Pricing Service (core) | Fully designed in Iterations 2-3 |
| HPS Web Frontend (modules) | Fully designed in Iteration 2 |
| Channel Management Adapter (reliability) | Fully designed in Iteration 3 |
| Kafka topology | Fully designed in Iteration 2 |