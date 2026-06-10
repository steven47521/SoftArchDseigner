# ADD Step 5: Instantiate Architectural Elements, Assign Responsibilities, and Define Interfaces (Iteration 3)

---

## 5A: Pricing Service — Transactional Outbox Implementation

### New/Modified Internal Elements

| Element | Status | Changes |
|---------|--------|---------|
| **PriceCommandHandler** | MODIFIED | Now coordinates transactional outbox: DB write now includes outbox record insertion. No longer calls KafkaPriceAdapter directly. |
| **PriceRepository** | MODIFIED | `savePriceChange()` now writes both price data AND an outbox record in a single transaction. New method: `markOutboxPublished(eventId)`. |
| **OutboxPoller** | NEW | Background process. Polls unpublished outbox records every 100ms. Publishes to Kafka via KafkaPriceAdapter. On success, marks record as published. Runs in the same Pricing Service process. |
| **KafkaPriceAdapter** | MODIFIED | Called by OutboxPoller (not PriceCommandHandler). Producer config updated: `acks=all`, `retries=3`, `enable.idempotence=true`. |
| **RedisCacheAdapter** | NEW | Writes computed prices to Redis after DB commit. Reads prices from Redis for query path. Falls back to DB read replica if Redis unavailable. |
| **PricingServiceHealthProbe** | NEW | Exposes `/health/ready` (readiness) and `/health/live` (liveness). Readiness checks: RateRuleCache loaded, DB, Redis, Kafka connectivity. |
| **FallbackConfigStore** | NEW | Stores last-known-good hotel config and rate rules on local disk. Used in degraded mode when HMS is unreachable. |

### Modified Write Flow (with Transactional Outbox)

```
RESTPricingAdapter → PriceCommandHandler
  → RateRuleCache (get rules, in-memory)
  → DerivedRateCalculator (compute derived rates)
  → PriceRepository.savePriceChange()  ← SINGLE TRANSACTION:
      1. INSERT/UPDATE price records
      2. INSERT into price_change_outbox (eventId, hotelId, payload)
  → RedisCacheAdapter.writeToCache()    ← After transaction commit
  → Return success to caller

[Async, background]
OutboxPoller:
  → SELECT * FROM price_change_outbox WHERE published_at IS NULL
  → FOR EACH: KafkaPriceAdapter.publish(event)
  → ON SUCCESS: PriceRepository.markOutboxPublished(eventId)
```

### New/Updated Port Interfaces

```
// === Modified PriceCommandPort (unchanged signature, changed implementation) ===
interface PriceCommandPort {
    ChangePricesResult changePrices(ChangePricesCommand command);
    // Implementation now uses transactional outbox instead of direct Kafka publish
}

// === New: Outbox Repository Port ===
interface OutboxRepository {
    List<OutboxRecord> pollUnpublished(int batchSize);
    void markPublished(EventId eventId);
    void incrementRetry(EventId eventId);
}

// === New: Cache Port ===
interface PriceCachePort {
    void writePrices(HotelId hotelId, DateRange dates, Map<RateType, Map<RoomType, Money>> prices);
    Optional<PriceSnapshot> readPrices(HotelId hotelId, DateRange dates, List<RateType> rateTypes);
    void invalidate(HotelId hotelId);
}
```

### Updated Latency Budget (QA-1, 100ms)

| Step | Previous Budget | New Budget | Change |
|------|----------------|------------|--------|
| HTTP ingress + deserialization | 5ms | 5ms | — |
| Rate rule lookup | 2ms | 2ms | — |
| Derived rate computation | 15ms | 15ms | — |
| DB write (price + outbox) | 20ms | 22ms | +2ms (outbox row) |
| Redis cache write | 5ms | 3ms | -2ms (Redis faster than local) |
| Kafka produce (async) | 3ms | 0ms | -3ms (moved to background) |
| HTTP response | 5ms | 5ms | — |
| **Subtotal core** | **55ms** | **52ms** | **-3ms net improvement** |
| Network + Gateway | 20ms | 20ms | — |
| Safety margin | 25ms | 28ms | +3ms |
| **Total budget** | **100ms** | **100ms** | — |

---

## 5B: Channel Management Adapter — Full Reliability Internals

### New/Modified Internal Elements

| Element | Status | Details |
|---------|--------|---------|
| **PriceChangeConsumer** | MODIFIED | Consumes from `price-changes`. Manual offset commit only after CMS confirms. Batches messages for efficiency. |
| **MessageTransformer** | UNCHANGED | Transforms PriceChangedEvent to CMS format. |
| **IdempotencyGuard** | NEW | Checks if `eventId` was already published to CMS. Maintains in-memory LRU cache (24h TTL) + periodic DB persistence. |
| **CMSClient (Port)** | MODIFIED | Now accepts `idempotencyKey` parameter. |
| **CMSRestAdapter** | MODIFIED | Sends `Idempotency-Key: {eventId}` header to CMS. |
| **RetryHandler** | REPLACED (WAS PRELIMINARY) | Full exponential backoff: 1s→2s→4s→8s→16s→32s (max 5 retries). Jitter: ±25%. Classifies errors as transient/permanent. |
| **CircuitBreaker** | NEW | Monitors CMS call outcomes. CLOSED→OPEN after 5 consecutive failures in 60s. HALF_OPEN after 30s cooldown. OPEN state pauses Kafka consumer. |
| **DeadLetterPublisher** | MODIFIED | Publishes permanently failed messages to `price-changes-dlq` Kafka topic. |
| **ReconciliationAPI** | NEW | REST endpoint for operators: `POST /admin/reconciliation/replay?eventId=X`, `GET /admin/reconciliation/dlq?hotelId=X&from=Y&to=Z`. |
| **CMAHealthProbe** | NEW | `/health/ready` checks: Kafka connectivity, CMS reachability (non-blocking), DB connectivity (for idempotency store). |

### CMA Processing Flow

```
PriceChangeConsumer.poll()
  → FOR EACH message:
      IdempotencyGuard.check(eventId)
        → IF already published: skip, commit offset
        → ELSE: continue
      CircuitBreaker.allowRequest()
        → IF OPEN: pause consumer, wait for cooldown
        → ELSE: continue
      MessageTransformer.transform(event) → CMS-formatted payload
      CMSClient.publishPrices(hotelId, prices, idempotencyKey=eventId)
        → SUCCESS:
            IdempotencyGuard.record(eventId)
            Consumer.commitOffset()
        → TRANSIENT_FAILURE:
            RetryHandler.retry(message)  ← up to 5 times
        → ALL_RETRIES_EXHAUSTED:
            DeadLetterPublisher.send(message)
            Consumer.commitOffset()  ← don't block the queue
        → PERMANENT_FAILURE (4xx):
            DeadLetterPublisher.send(message)
            Consumer.commitOffset()
```

### Port Interfaces

```
// === Modified CMSClient Port ===
interface CMSClient {
    PublicationResult publishPrices(
        HotelId hotelId,
        List<PublishedPrice> prices,
        IdempotencyKey idempotencyKey
    );
}

// === New: IdempotencyStore Port ===
interface IdempotencyStore {
    boolean hasBeenProcessed(EventId eventId);
    void markProcessed(EventId eventId);
    void expireOlderThan(Duration ttl);
}

// === New: DeadLetterPort ===
interface DeadLetterPort {
    void send(PriceChangedEvent event, FailureReason reason);
    List<DeadLetteredEvent> query(HotelId hotelId, TimeRange range);
    Optional<DeadLetteredEvent> get(EventId eventId);
}

// === New: Reconciliation Port ===
interface ReconciliationPort {
    ReconciliationResult replay(EventId eventId);
    List<DeadLetteredEvent> listDeadLetters(HotelId hotelId, TimeRange range);
}
```

---

## 5C: API Gateway — High Availability Internals

### New/Modified Internal Elements

| Element | Status | Details |
|---------|--------|---------|
| **RequestRouter** | UNCHANGED | Routes to downstream services per route table. |
| **AuthFilter** | UNCHANGED | JWT validation is stateless — works across instances. |
| **RoleEnforcer** | UNCHANGED | Role check from JWT claims — stateless. |
| **CircuitBreaker (Downstream)** | NEW | Per-downstream-service circuit breaker. If Pricing Service is degraded, Gateway returns 503 rather than blocking threads. |
| **GatewayHealthProbe** | NEW | `GET /health` returns 200 if: (a) Gateway process is alive, (b) at least one downstream service is reachable. Used by cloud load balancer. |
| **DistributedRateLimiter** | NEW | Token bucket algorithm backed by Redis. Per-user and per-IP counters. Falls back to per-instance limits if Redis unavailable. |
| **GracefulShutdownHandler** | NEW | On SIGTERM: stop accepting new connections, complete in-flight requests (30s max), then exit. |

### Gateway Redundancy Configuration

| Aspect | Value |
|--------|-------|
| **Min instances** | 2 |
| **Max instances** | 6 (auto-scaled on CPU > 70%) |
| **AZ distribution** | Spread across 2+ availability zones |
| **Load balancer** | Cloud-managed, round-robin |
| **Health check path** | `GET /health` |
| **Health check interval** | 10s |
| **Unhealthy threshold** | 3 consecutive failures |
| **Graceful shutdown timeout** | 30s |

---

## 5D: Pricing Service — Read Cache Architecture (Redis)

### Redis Cache Design

| Aspect | Design |
|--------|--------|
| **Cache Type** | Cloud-managed Redis cluster (CON-6) |
| **Key Pattern** | `price:{hotelId}:{date}:{roomType}:{rateType}` |
| **Value** | JSON: `{ "amount": 199.00, "currency": "USD", "computedAt": "2025-01-15T10:30:00Z" }` |
| **TTL** | 24 hours (prices typically change daily; stale data auto-expires) |
| **Write Strategy** | Write-through: Pricing Service writes to Redis after every price change |
| **Read Strategy** | Cache-aside: QueryHandler reads Redis first; on miss, reads DB read replica and populates cache |
| **Fallback** | If Redis unavailable, QueryHandler reads directly from Pricing DB read replica |
| **Cluster** | 1 primary + 2 replicas across AZs |

### RedisCacheAdapter Implementation

```
class RedisCacheAdapter implements PriceCachePort {
    writePrices(hotelId, dates, prices):
        pipeline = redis.pipeline()
        FOR EACH (date, roomType, rateType) → amount:
            key = "price:{hotelId}:{date}:{roomType}:{rateType}"
            pipeline.setex(key, 86400, toJson(amount))  // 24h TTL
        pipeline.execute()

    readPrices(hotelId, dates, rateTypes):
        keys = buildKeys(hotelId, dates, rateTypes)
        values = redis.mget(keys)
        IF all found: return PriceSnapshot.from(values)
        ELSE: fallback to DB read replica → populate cache → return
}
```

---

## 5E: Pricing Service — Startup and Dependency Resilience

### Startup Sequence with Readiness Gating

| Phase | Action | Readiness Signal |
|-------|--------|-----------------|
| 1 | Connect to DB | Not ready |
| 2 | Connect to Redis | Not ready |
| 3 | Connect to Kafka | Not ready |
| 4 | Load RateRuleCache from HMS (retry up to 2 min) | Not ready |
| 4a | If HMS unreachable after 2 min → load FallbackConfigStore | Ready (degraded=true) |
| 5 | Start OutboxPoller | Ready (if step 4 succeeded) |
| 6 | Signal readiness | Ready (or Ready+degraded) |

### Degraded Mode Behavior

| Scenario | Behavior |
|----------|----------|
| HMS unreachable during operation | RateRuleCache serves last-known-good config. Retry HMS every 30s. Metric emitted: `pricing_service_config_staleness_seconds`. |
| Redis unreachable during operation | Query path falls back to DB read replica. Write path continues to update Redis (buffered). Metric: `pricing_service_redis_available = 0`. |
| DB primary unreachable | Price changes fail immediately with 503. Read path serves from Redis cache (stale but available). |
| Kafka unreachable | OutboxPoller backs off. Messages accumulate in outbox table. Alert at 1000 pending messages. |

### Health Probe Endpoints

| Endpoint | Purpose | Success Conditions |
|----------|---------|-------------------|
| `GET /health/live` | Liveness (should restart if fail) | Process is alive. Minimal check. |
| `GET /health/ready` | Readiness (should receive traffic if pass) | RateRuleCache loaded OR degraded mode. DB connected. Redis connected. Kafka connected. |

---

## 5F: Hotel Management Service — Availability Configuration

### Multi-Instance Configuration

| Aspect | Value |
|--------|-------|
| **Min instances** | 2 |
| **AZ distribution** | 2+ AZs |
| **Load balancer** | Cloud-managed, round-robin |
| **Health probe** | `GET /health` (checks DB) |
| **Database** | Managed DB: 1 primary + 1 read replica, multi-AZ, auto-failover |
| **Kafka producer** | `acks=all`, `retries=3`, `enable.idempotence=true` |

### New Elements

| Element | Status | Details |
|---------|--------|---------|
| **HMSHealthProbe** | NEW | `GET /health` checks DB connectivity |
| **ConfigProvisioningController** | NEW (Refinement of existing port) | Handles Pricing Service requests for config. Stateless — any HMS instance can serve. Response includes `Last-Modified` timestamp for caching. |

---

## 5G: Preliminary Metrics for QA-8

These metric definitions provide the foundation for the full monitoring infrastructure to be designed in Iteration 4.

| Metric Name | Type | Source | Description |
|-------------|------|--------|-------------|
| `price_change_publication_success_total` | Counter | Pricing Service (OutboxPoller) | Count of successfully published outbox messages |
| `price_change_publication_failure_total` | Counter | Pricing Service (OutboxPoller) | Count of failed outbox publish attempts |
| `price_change_outbox_pending` | Gauge | Pricing Service | Number of unpublished outbox records |
| `cms_delivery_success_total` | Counter | CMA | Count of successful CMS deliveries |
| `cms_delivery_failure_total` | Counter | CMA | Count of failed CMS deliveries |
| `cms_delivery_retry_total` | Counter | CMA | Count of CMS delivery retries |
| `cms_circuit_breaker_state` | Gauge | CMA | 0=CLOSED, 1=OPEN, 2=HALF_OPEN |
| `dead_letter_queue_size` | Gauge | CMA | Number of messages in DLQ |
| `kafka_consumer_lag` | Gauge | CMA, Pricing Service | Consumer lag in message count |
| `pricing_service_config_staleness_seconds` | Gauge | Pricing Service | Time since last successful HMS config refresh |
| `pricing_service_degraded_mode` | Gauge | Pricing Service | 0=normal, 1=degraded |
| `gateway_upstream_availability` | Gauge | API Gateway | 1=available, 0=unavailable (per downstream service) |
| `price_query_latency_p99` | Histogram | Pricing Service (QueryHandler) | P99 query latency in ms |
| `price_change_latency_p99` | Histogram | Pricing Service (CommandHandler) | P99 change latency in ms |

---

## Summary: Risk Resolution Status

| Risk ID | Status | Resolution |
|---------|--------|------------|
| **R10** (100% publication) | ✅ RESOLVED | Transactional Outbox ensures DB→Kafka atomicity. CMA with idempotent delivery, retry, circuit breaker, DLQ, and reconciliation API ensures CMS receipt. |
| **R12** (Gateway SPOF) | ✅ RESOLVED | Multi-instance Gateway with cloud load balancer, health probes, graceful shutdown, and downstream circuit breakers. |
| **R8** (Cache consistency) | ✅ RESOLVED | Shared Redis cache replaces local caches. Write-through from Pricing Service. Fallback to DB read replica. |
| **R9** (Startup race) | ✅ RESOLVED | Readiness probe with gating on RateRuleCache load. Degraded mode with fallback config file. |
| **R1** (PS↔HMS dependency) | ✅ RESOLVED | Degraded mode with fallback config. Background retry. HMS multi-instance for availability. |