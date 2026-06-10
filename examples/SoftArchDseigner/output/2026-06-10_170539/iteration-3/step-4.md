# ADD Step 4: Choose One or More Design Concepts (Iteration 3)

---

## 4A: Pricing Service Write Path — Transactional Outbox for Reliable Publication

### Problem
The Iteration 2 design uses fire-and-forget Kafka publish after DB commit. If the Kafka publish fails (or the process crashes between DB commit and Kafka publish), the price change is durable but not published. This violates QA-2: "100% of price changes are successfully published."

### Alternatives Evaluated

**Alternative A: Two-Phase Commit (XA) across DB and Kafka**
- QA-2: ⚠ Theoretically atomic, but Kafka doesn't natively support XA. Complex and fragile.
- QA-1: ❌ Two-phase commit adds significant latency. Would violate 100ms budget.
- CON-4 (MVP): ❌ Operational complexity too high.

**Alternative B: Transactional Outbox Pattern (SELECTED)**
- Write price change AND an outbox message in a single DB transaction. A separate process (OutboxPoller) reads the outbox table and publishes to Kafka. After successful Kafka publish, marks outbox record as published.
- QA-2: ✅ Guarantees at-least-once delivery. No published message can be lost once the DB transaction commits.
- QA-1: ✅ Outbox write is a single additional row in the same transaction — negligible latency impact (~2ms). Kafka publish is asynchronous from the user's perspective.
- CON-4: ✅ Well-known pattern, straightforward to implement.

**Alternative C: Event Sourcing**
- QA-2: ✅ Events are the source of truth.
- QA-1: ⚠ Event replay for read model adds latency.
- CON-4: ❌ High operational complexity for 2-month MVP.

### Selected: Transactional Outbox Pattern

| Aspect | Design |
|--------|--------|
| **Outbox Table** | `price_change_outbox` in Pricing Database. Columns: `id` (UUID), `aggregate_id` (hotelId), `event_type`, `payload` (JSON), `created_at`, `published_at` (nullable), `retry_count`. |
| **Atomic Write** | `PriceRepository.savePriceChange()` writes both the price data AND the outbox record in a single DB transaction. |
| **OutboxPoller** | Background thread/process in Pricing Service. Polls `price_change_outbox WHERE published_at IS NULL` every 100ms. Publishes to Kafka. On success, sets `published_at`. |
| **At-Least-Once** | If Kafka publish succeeds but the DB update to set `published_at` fails, the message will be re-published on next poll. Kafka consumer (CMA) must be idempotent (see 4B). |
| **Latency Impact** | ~2ms for additional row insert in same transaction. Well within the 100ms budget (Step 5 will recalculate). |

---

## 4B: Channel Management Adapter — Full Reliability Design

### Design Decisions

#### 4B-1: Idempotent CMS Delivery

Since the Pricing Service Transactional Outbox may re-publish duplicate messages to Kafka, the CMA must deliver idempotently to the CMS.

| Decision | Rationale |
|----------|-----------|
| **Message deduplication key** | Each `PriceChangedEvent` includes a unique `eventId` (UUID from outbox). CMA uses `eventId` as idempotency key when calling CMS. |
| **CMS-side idempotency** | If CMS supports idempotency keys natively, use them. If not, CMA maintains a local dedup cache (TTL = 24 hours) of recently published `eventId`s. |
| **Idempotency storage** | In-memory LRU cache for recent events + periodic flush to a DB table for persistence across CMA restarts. |

#### 4B-2: Retry Strategy

| Aspect | Design |
|--------|--------|
| **Retry policy** | Exponential backoff with jitter: 1s → 2s → 4s → 8s → 16s → 32s (max 5 retries). |
| **Transient vs. Permanent** | HTTP 5xx, network errors, timeouts → transient (retry). HTTP 4xx (except 429) → permanent (no retry, move to DLQ). HTTP 429 (rate limit) → retry with longer backoff, respecting Retry-After header. |
| **Consumer offset commit** | Commit Kafka offset ONLY after CMS confirms receipt (200 OK). If retries exhausted → move to DLQ → commit offset. |

#### 4B-3: Circuit Breaker

| Aspect | Design |
|--------|--------|
| **Pattern** | Circuit Breaker on CMSClient calls. States: CLOSED → OPEN (after 5 consecutive failures in 60s window) → HALF_OPEN (after 30s cooldown) → CLOSED (on first success). |
| **Behavior when OPEN** | CMA pauses consumption (pauses Kafka consumer). Messages accumulate in Kafka (retention = 7 days). Prevents resource exhaustion from repeated failed calls. |
| **Recovery** | After cooldown, one probe request. If success → close circuit, resume consumption. If failure → remain open, reset cooldown timer. |

#### 4B-4: Dead Letter Queue and Reconciliation

| Aspect | Design |
|--------|--------|
| **DLQ Topic** | Kafka topic `price-changes-dlq`. Messages that exhaust retries or encounter permanent failures are published here. |
| **DLQ Consumer** | A lightweight process reads DLQ and stores messages in a database/object store for operator inspection. |
| **Operator Alert** | When a message lands in DLQ, emit a metric that triggers an alert (QA-8 foundation). |
| **Reconciliation API** | CMA exposes `POST /admin/reconciliation/replay?eventId=X` endpoint for operators to manually replay a failed message after root cause is resolved. |

---

## 4C: API Gateway — High Availability

### Selected: Stateless Multi-Instance Gateway with Cloud Load Balancer

| Aspect | Design |
|--------|--------|
| **Statelessness** | Gateway stores no session state. JWT tokens are self-contained — any instance can validate any token. No session affinity required. |
| **Multi-Instance** | Minimum 2 instances, deployed across availability zones (CON-6). Cloud load balancer distributes traffic (round-robin or least-connections). |
| **Health Probe** | `GET /health` endpoint. Returns 200 if Gateway is healthy. Load balancer removes unhealthy instances. Health check includes: connectivity to downstream services, token signing key availability. |
| **Graceful Shutdown** | On SIGTERM: stop accepting new connections, drain in-flight requests (30s timeout), then exit. Kubernetes/container orchestrator handles this natively (CON-6). |
| **Circuit Breakers (Downstream)** | Gateway adds circuit breakers for calls to Pricing Service and Hotel Management Service. If Pricing Service is slow, Gateway returns 503 with Retry-After rather than blocking threads. |
| **Rate Limiting (Distributed)** | Rate limiter uses a shared Redis instance (cloud-managed) for distributed counters. Falls back to per-instance limits if Redis is unavailable — slightly less accurate but still protective. |

---

## 4D: Pricing Service — Read Cache Consistency

### Alternatives Evaluated

**Alternative A: Local cache with pub-sub invalidation**
- Each write instance publishes an invalidation event. All query instances subscribe.
- QA-1: ✅ Fast — pub-sub message adds ~5ms.
- QA-3: ⚠ If a query instance misses an invalidation message (network blip), its cache is stale until next write.
- Complexity: Medium.

**Alternative B: Shared distributed cache (Redis) — SELECTED**
- All query instances read from a shared Redis cache. Write instances update Redis directly.
- QA-1: ✅ Redis write is ~2-3ms within same AZ. Slightly higher cross-AZ but still within budget.
- QA-3: ✅ No cache consistency issues — single source of truth. Query instances can come and go freely.
- Complexity: Low — cloud-managed Redis service (CON-6).

**Alternative C: No cache — always read from DB read replicas**
- QA-1: ⚠ DB read latency (5-15ms per query) may accumulate under load. Less predictable.
- QA-4: ⚠ Can scale read replicas, but cache is more cost-effective for hot data.
- Complexity: Lowest, but performs worse.

### Selected: Shared Distributed Cache (Redis)

| Aspect | Design |
|--------|--------|
| **Cache Store** | Cloud-managed Redis (CON-6). Single cluster with read replicas across AZs. |
| **Write Path** | After DB commit + outbox write, `PriceCommandHandler` writes computed prices to Redis: `SET price:{hotelId}:{date}:{roomType}:{rateType} = {amount}` with TTL = 24 hours. |
| **Read Path** | `PriceQueryHandler` calls `MGET` (batch read) from Redis for the requested date range. No DB access on the query path. |
| **Fallback** | If Redis is unavailable, query handler falls back to Pricing DB read replica. Slower but available — supports QA-3. |
| **Latency Budget** | Redis write: 2-3ms (was 5ms for local cache update in Iteration 2 budget). Saves 2ms. |

---

## 4E: Pricing Service — Startup and Dependency Resilience

### Selected: Readiness Gating + Graceful Degradation

| Aspect | Design |
|--------|--------|
| **Readiness Probe** | `GET /health/ready`. Returns 200 only when: (a) RateRuleCache is fully loaded, (b) DB connection is healthy, (c) Redis connection is healthy, (d) Kafka connection is healthy. Kubernetes uses this to control traffic routing (CON-6). |
| **Startup Sequence** | 1. Connect to DB, Redis, Kafka. 2. Load RateRuleCache from HMS (with retry: 5 attempts, 2s backoff). 3. Begin polling outbox. 4. Signal readiness. |
| **HMS Unavailable at Startup** | Retry loading config for up to 2 minutes. If still unavailable, signal readiness anyway but with `degraded=true`. In degraded mode, Pricing Service uses a local fallback config file (last known good configuration, updated on each successful HMS sync). |
| **HMS Unavailable During Operation** | RateRuleCache continues serving from last-known-good cache. Logs a warning. Emits a metric for alerting (QA-8 foundation). Retries in background every 30s. |
| **Cache Staleness Monitoring** | If HMS has been unreachable for > 5 minutes, emit an alert. Cache staleness beyond 30 minutes triggers a critical alert. |

---

## 4F: Hotel Management Service — Multi-Instance Availability

### Design Decisions

| Aspect | Design |
|--------|--------|
| **Multi-Instance** | Minimum 2 instances across AZs. Stateless design (no sticky sessions). Cloud load balancer distributes requests. |
| **Database Redundancy** | Managed database with primary + read replica across AZs (CON-6). Automatic failover. |
| **Health Probe** | `GET /health`. Checks DB connectivity. |
| **Config Provisioning for Pricing Service** | Remains REST-based. Multiple HMS instances behind load balancer — Pricing Service calls the load-balanced endpoint. |
| **Kafka Producer Resilience** | `ConfigChangePublisher` uses Kafka's built-in retries (`retries=3`, `acks=all`). If Kafka is unavailable, config changes are still persisted to DB; a background process reconciles unpublished events. |

---

## 4G: Kafka Resilience Patterns

| Aspect | Design |
|--------|--------|
| **Producer Settings (Pricing Service)** | `acks=all` (wait for all in-sync replicas), `retries=3`, `enable.idempotence=true` (Kafka-level dedup). |
| **Consumer Offset Commit (CMA)** | Manual commit after CMS confirms receipt. This ensures at-least-once delivery even if CMA crashes mid-processing. |
| **Consumer Lag Monitoring** | Expose consumer lag metrics. If lag > 1000 messages or > 60s, trigger alert (QA-8 foundation). |
| **Multi-AZ** | Managed Kafka deployed across 3 AZs (CON-6). Replication factor = 3. Min in-sync replicas = 2. |

---

## Summary of Design Concept Selections

| Element | Selected Concept | Primary Driver |
|---------|-----------------|----------------|
| Pricing Service (Write) | Transactional Outbox Pattern | QA-2 (reliable publication) |
| Channel Management Adapter | Idempotent delivery + Exponential Backoff + Circuit Breaker + DLQ | QA-2 (guaranteed CMS receipt) |
| API Gateway | Stateless multi-instance + Cloud LB + Health probes + Circuit breakers | QA-3 (99.9% availability), R12 |
| Pricing Service (Cache) | Shared Redis cache with DB fallback | R8 (cache consistency), QA-3 |
| Pricing Service (Startup) | Readiness gating + degraded mode + fallback config | R9, R1, QA-3 |
| Hotel Management Service | Multi-instance + DB redundancy + Kafka producer retries | QA-3, R1 |
| Kafka | Producer idempotence + manual offset commit + lag monitoring | QA-2, QA-3 |