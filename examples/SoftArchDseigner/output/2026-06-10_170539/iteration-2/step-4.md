# ADD Step 4: Choose One or More Design Concepts (Iteration 2)

This step selects design concepts for each element being refined. Each concept is evaluated against the primary drivers: QA-1 (100ms latency), QA-4 (scalability), QA-5 (security), QA-6 (modifiability), QA-9 (testability).

---

## 4A: Pricing Service — Internal Structure

### Design Decisions for Pricing Service

The Pricing Service must satisfy two conflicting demands:
- **Write path (HPS-2):** Compute derived rates and publish within 100ms (QA-1). Must be consistent and fast.
- **Read path (HPS-3):** Serve up to 1M queries/day with ≤20% latency increase (QA-4). Must be highly scalable and independently optimizable.

### Selected Concept: CQRS with In-Memory Computation

**CQRS (Command Query Responsibility Segregation)** separates the write model from the read model within the Pricing Service:

| Aspect | Write Side (Commands) | Read Side (Queries) |
|--------|----------------------|---------------------|
| **Purpose** | Process price changes, compute derived rates, publish events | Serve price queries at high throughput |
| **Data Model** | Domain model optimized for price calculation and consistency | Denormalized view optimized for query patterns |
| **Store** | Primary Pricing Database (write-optimized) | In-memory cache / read-optimized projection |
| **Scaling** | Vertical or limited horizontal (fewer writes) | Horizontal scaling (stateless, many instances) |

### Alternatives Evaluated

**Alternative A: Single unified service with shared data model**
- QA-1: ⚠ Reads and writes compete for resources; derived rate computation may be delayed by concurrent queries
- QA-4: ❌ Cannot scale reads independently of writes
- QA-6: ✅ Simple ports, but protocol changes affect both paths

**Alternative B: CQRS with in-memory computation (SELECTED)**
- QA-1: ✅ Derived rates are computed in-memory using pre-loaded rate rules; results projected to read cache synchronously; 100ms achievable
- QA-4: ✅ Read side uses in-memory cache; stateless query handlers scale horizontally; read replicas can be added
- QA-6: ✅ Separate command/query ports; new protocols can target either path independently

**Alternative C: Fully event-sourced with async projection**
- QA-1: ⚠ Event sourcing adds write latency; async projection means queries may see stale data for some window
- QA-4: ✅ Read projections can be highly optimized
- Complexity: ❌ Operational complexity (event store, projections) threatens 2-month MVP timeline (CON-4)

### Derived Rate Computation Strategy

Since QA-1 requires 100ms end-to-end for price publication, the computation must be near-instantaneous. The selected approach:

| Decision | Rationale |
|----------|-----------|
| **Pre-load rate rules into memory** | Rate business rules (from Hotel Management Service) change infrequently. Load at startup, refresh on change events. Eliminates cross-service call during price change. |
| **Compute derived rates in-memory** | Apply pre-loaded rules to base prices synchronously. No database round-trips during calculation. |
| **Synchronous projection to read model** | After write is committed, immediately update the read cache so queries see latest prices — critical for QA-1 "published within 100ms." |
| **Bulk computation** | When a base price changes, compute all affected derived rates in a single pass rather than per-rate queries. |

---

## 4B: API Gateway — Authentication and Routing

### Selected Concept: Token-Based Authentication with Backend-for-Frontend (BFF) Pattern

| Decision | Rationale |
|----------|-----------|
| **OAuth2 Authorization Code Flow with PKCE** | Standard for SPAs (CON-1 browser-based). Cloud identity service (CON-2) acts as the Authorization Server. |
| **API Gateway validates JWT access tokens** | Gateway validates token signature and expiry against the identity provider's public keys. No call to identity service per request — reduces latency. |
| **Token propagation to services** | Gateway forwards validated claims (user ID, roles, authorized hotels) to backend services via HTTP headers. Services do not re-validate tokens. |
| **Coarse-grained authorization at Gateway** | Gateway enforces route-level authorization (e.g., `/api/hotels/*` requires admin role). Fine-grained (hotel-level) authorization is enforced in the Hotel Management Service. |
| **Protocol adapter isolation** | REST adapter is one adapter; adding gRPC adds a parallel adapter at the gateway with the same routing logic — no changes to core services (QA-6). |

---

## 4C: HPS Web Frontend — Component Structure

### Selected Concept: Feature-Based Module Organization with Auth Guard

| Decision | Rationale |
|----------|-----------|
| **Feature modules** | Organize Angular code by feature: `LoginModule`, `PricingModule`, `HotelManagementModule`, `RateManagementModule`, `UserManagementModule`. Aligns with use cases and team work assignment (CRN-3). |
| **Auth Guard / Interceptor** | Angular route guards prevent navigation to unauthorized features. HTTP interceptor attaches JWT to all API calls. |
| **Role-based UI rendering** | Components query user roles from the decoded JWT; `*ngIf` directives show/hide functions per QA-5. |
| **Token storage in memory** | JWT stored in memory (not localStorage) to mitigate XSS risk. Refresh token stored in secure HTTP-only cookie. |

---

## 4D: Pricing Service ↔ Hotel Management Service Integration

### Selected Concept: Cached Reference Data with Change Notification

| Decision | Rationale |
|----------|-----------|
| **Pricing Service caches hotel config and rate rules locally** | Avoids synchronous cross-service call during price change processing, which would consume the 100ms budget (QA-1). |
| **Startup load + Kafka change events** | On startup, Pricing Service loads all rate rules and hotel tax configurations from Hotel Management Service. Subsequent changes are pushed via a Kafka topic (`hotel-config-changes`). |
| **Staleness tolerance** | Hotel configuration changes are infrequent. A brief propagation delay (seconds) is acceptable. Price changes always use the latest cached config — if a config change is in-flight, the price change either uses old or new config, both of which are valid states. |

---

## 4E: Channel Management Adapter — Scope Decision

### Selected Concept: Standalone Service (resolves R2)

| Decision | Rationale |
|----------|-----------|
| **Standalone service (not embedded in Pricing Service)** | Independent scaling: price publication to external systems may have different throughput characteristics than internal price computation. Failure isolation: if the Channel Management System is slow or unavailable, the Pricing Service remains operational. |
| **Kafka as buffer** | The Pricing Service publishes to Kafka and returns success to the user. The Channel Management Adapter consumes from Kafka and handles delivery to the external system. This decoupling is essential for QA-1 (100ms) — the user does not wait for external system acknowledgment. |
| **Partitioning by hotel ID** | Ensures ordering of price changes per hotel. Consumer groups allow parallel processing across hotels. |

---

## Summary of Design Concept Selections

| Element | Selected Concept | Primary Drivers Satisfied |
|---------|-----------------|--------------------------|
| Pricing Service (internals) | CQRS + In-Memory Computation | QA-1, QA-4, QA-6 |
| API Gateway (auth/routing) | Token-Based Auth + BFF Pattern | QA-5, QA-6 |
| HPS Web Frontend | Feature Modules + Auth Guard | QA-5, CRN-3 |
| PS ↔ HMS Integration | Cached Reference Data + Change Events | QA-1 |
| Channel Management Adapter | Standalone Service + Kafka Buffer | QA-1, R2, R3 |