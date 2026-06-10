# ADD Step 3: Choose One or More System Elements to Refine (Iteration 3)

## Elements Selected for Refinement

The iteration goal targets reliability and availability, which are cross-cutting concerns affecting multiple elements. The following elements are selected for refinement, prioritized by their role in the critical paths for QA-2 and QA-3.

---

### Element 1: Pricing Service — Write Path (Reliability Hardening)

**Why:** QA-2 requires that 100% of price changes are "successfully published (queryable)." The Pricing Service write path is where price changes are persisted and published to Kafka. We must ensure that once a user receives a success response, the price change is durable, queryable, and reliably enqueued for CMS delivery.

**Current State (Iteration 2):** PriceCommandHandler orchestrates the write: validate → compute derived rates → persist to DB → update read cache → fire-and-forget to Kafka. No idempotency. No acknowledgment tracking.

**Refinement Needed:**
- Idempotent change processing (duplicate requests must not create duplicate effects)
- Transactional outbox pattern or equivalent to ensure DB write and Kafka publish are atomic
- Price change acknowledgment tracking (is the price queryable?)
- Retry handling for transient DB failures

---

### Element 2: Channel Management Adapter (Full Reliability Design)

**Why:** QA-2 specifically requires that price changes are "received by the channel management system." The CMA is the sole bridge to the CMS. Its reliability directly determines whether QA-2 is satisfied. R10 (High severity) demands this be fully designed.

**Current State (Iteration 2):** Standalone service. PriceChangeConsumer reads from Kafka. MessageTransformer formats data. CMSClient (port) and CMSRestAdapter (adapter) communicate with CMS. Basic RetryHandler and DeadLetterQueue exist but are preliminary.

**Refinement Needed:**
- Full retry strategy with exponential backoff and jitter
- Circuit breaker for CMS to avoid cascading failures
- Idempotent CMS delivery (duplicate messages must not create duplicate prices at CMS)
- Dead letter handling with operator alerting
- Reconciliation mechanism for messages that fail permanently
- Consumer offset management (commit only after CMS confirms)

---

### Element 3: API Gateway (High Availability Hardening)

**Why:** R12 (High severity) identifies the Gateway as a single point of failure. Both browser frontend and external API consumers depend on it. QA-3 (99.9% availability) cannot be met if the Gateway is a SPOF.

**Current State (Iteration 2):** Single-tier gateway. AuthFilter, RoleEnforcer, ClaimPropagator, RequestRouter, RateLimiter. Single instance in Iteration 2 deployment views.

**Refinement Needed:**
- Multi-instance deployment with cloud load balancer
- Stateless design validation (no server-side sessions — JWT is self-contained)
- Health probe endpoints for load balancer
- Graceful shutdown (drain in-flight requests)
- Circuit breakers for downstream service calls

---

### Element 4: Pricing Service — Read Cache Consistency

**Why:** R8 (Medium severity) — when multiple Pricing Service query instances exist, a write on one instance must update caches on all instances. Stale reads violate the "published (ready for query)" aspect of QA-1 and QA-2.

**Current State (Iteration 2):** PriceReadCache is local (in-memory per instance). Write path updates local cache. Cache invalidation arrows were drawn in Iteration 2 View 4 but mechanism unspecified.

**Refinement Needed:**
- Distributed cache invalidation / update mechanism
- Options: Redis pub-sub, centralized cache (Redis), or write-through to shared cache
- Evaluation against QA-1 latency budget (cache update step is currently budgeted at 5ms)

---

### Element 5: Pricing Service — Startup and Dependency Resilience

**Why:** R9 (startup race) and R1 (HMS dependency) both affect the Pricing Service's ability to become and remain available. QA-3 requires the service to handle these gracefully.

**Current State (Iteration 2):** RateRuleCache loads all config from HMS at startup. No readiness gating. No fallback if HMS is unavailable.

**Refinement Needed:**
- Readiness probe: Pricing Service reports "ready" only after RateRuleCache is warm
- Fallback strategy for HMS unavailability during operation (retry with backoff, serve from stale cache)
- Startup resilience: if HMS is down, Pricing Service retries with backoff; does not crash-loop
- Cache staleness monitoring and alerting

---

### Element 6: Hotel Management Service (Availability Hardening)

**Why:** R1 originated from HMS being a dependency of Pricing Service. If HMS is unavailable, Pricing Service cannot refresh its RateRuleCache. Also, administrative functions (HPS-4/5/6) must remain available for the 99.9% SLA.

**Current State (Iteration 2):** Single instance in deployment views. Exposes HotelConfigProvisioningPort and ConfigChangePublisher. Full internals not designed.

**Refinement Needed (for this iteration):**
- Multi-instance deployment with load balancing
- Database-level redundancy (managed DB with read replicas)
- Graceful handling of Pricing Service config requests
- Health probe endpoints

---

### Element 7: Kafka (Infrastructure Resilience)

**Why:** Kafka is the backbone of async communication (price publication, config change notification). Its reliability directly impacts QA-2. While it's a cloud-managed service (CON-6), we must design consumer-side resilience patterns.

**Current State (Iteration 2):** Two topics defined (`price-changes`, `hotel-config-changes`). Cloud-managed Kafka assumed. Consumer groups defined.

**Refinement Needed:**
- Consumer-side resilience: retry, idempotent processing, offset commit strategy
- Monitoring: consumer lag, topic throughput (preliminary for QA-8)
- Multi-AZ deployment validation (managed service should handle this)

---

### Elements NOT Refined in This Iteration

| Element | Reason |
|---------|--------|
| HPS Web Frontend | Browser-side reliability is inherently limited; SPA is served via CDN (already highly available) |
| HPS Web Frontend internals | Already organized in Iteration 2; no reliability changes needed at component level |
| Hotel Management Service internals (domain logic) | Full internal structure deferred to Iteration 4 |
| Management Database schema | Iteration 4 |
| CI/CD pipeline | Iteration 4 |