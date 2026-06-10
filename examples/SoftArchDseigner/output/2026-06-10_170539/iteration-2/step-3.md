# ADD Step 3: Choose One or More System Elements to Refine (Iteration 2)

## Elements Selected for Refinement

Based on the iteration goal (supporting primary functionality: Change Prices, Query Prices, Login), the following elements from Iteration 1 are selected for internal refinement:

### Element 1: Pricing Service (Primary Focus)

**Why:** This is the critical path for both HPS-2 (Change Prices) and HPS-3 (Query Prices). QA-1 (100ms latency) and QA-4 (scalability to 1M queries/day) both center on this element. Its internal structure — how it computes derived rates, separates reads from writes, and integrates with Hotel Management Service — determines whether the system's most demanding quality attributes are met.

**Current State from Iteration 1:** Defined as a Java service with Ports & Adapters pattern, owning the Pricing Database. High-level ports: `changePrices`, `simulatePrices`, `queryPrices`, `publishPrices`. No internal decomposition.

**Refinement Needed:**
- Internal module decomposition (domain logic, ports, adapters)
- Write path: price change processing, derived rate calculation, simulation
- Read path: query optimization, caching strategy for QA-4 scalability
- Database schema direction (read/write separation)
- Latency budget allocation for QA-1

### Element 2: API Gateway

**Why:** The API Gateway is the protocol boundary (QA-6) and the enforcement point for authentication (QA-5, HPS-1). Its routing rules, token validation flow, and protocol adapter design must be concretely specified.

**Current State from Iteration 1:** Routes `/api/prices/*`, `/api/hotels/*`, `/api/rates/*`, `/api/users/*`, `/api/auth/*`. Validates tokens against User Identity Service. No internal structure.

**Refinement Needed:**
- Authentication flow (token acquisition, validation, propagation to services)
- Authorization model (coarse-grained: which routes require which roles)
- Protocol adapter structure (REST today, gRPC-ready)
- Rate limiting and request routing rules

### Element 3: HPS Web Frontend (Angular SPA)

**Why:** HPS-1 (Login) starts here. QA-5 (Security) requires that only authorized functions are shown. The frontend must handle OAuth2/OIDC flows with the cloud identity service and manage tokens.

**Current State from Iteration 1:** Angular SPA served via CDN. Handles user authentication redirect, renders UI. No internal component structure.

**Refinement Needed:**
- Authentication flow (OAuth2/OIDC with cloud identity provider)
- Token management (storage, refresh, attachment to API calls)
- Authorization-driven UI (show/hide functions based on permissions)
- Component/module structure aligned with use cases

### Element 4: Channel Management Adapter

**Why:** Carried-forward risk R2 requires deciding its scope (standalone vs. embedded). R3 requires Kafka topic design. This element is the bridge between the Pricing Service and the external Channel Management System.

**Current State from Iteration 1:** Java/Kafka consumer. Subscribes to `price-changes` topic. Pushes prices to Channel Management System. Scope not finalized.

**Refinement Needed:**
- Scope decision: standalone service vs. module within Pricing Service
- Kafka topic design (partitioning key, retention, consumer groups)
- Failure handling strategy (preliminary — full reliability in Iteration 3)

### Element 5: Hotel Management Service (Partial)

**Why:** The Pricing Service depends on Hotel Management Service for hotel configuration (tax rates, room types) and rate business rules. The integration interface between these two services must be designed to avoid the Pricing Service being blocked.

**Current State from Iteration 1:** Java service. Ports: CRUD for hotels, rates, users. Exposes `getHotelConfiguration` and `getRateRules` for Pricing Service. No internal structure.

**Refinement Needed (for this iteration):**
- `getHotelConfiguration` and `getRateRules` interface contract details
- Caching strategy for reference data consumed by Pricing Service
- Authorization model (hotel-level access control)

### Elements NOT Refined in This Iteration

| Element | Reason |
|---------|--------|
| Hotel Management Service (full internals) | Supporting CRUD operations; detailed in Iteration 4 |
| Management Database (schema) | Schema design deferred to Iteration 4 |
| Pricing Database (full schema) | Logical structure defined; physical schema in Iteration 4 |