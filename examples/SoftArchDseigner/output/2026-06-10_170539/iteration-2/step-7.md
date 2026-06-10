# ADD Step 7: Analyze Design and Review Iteration Goal (Iteration 2)

## Analysis Against Iteration Success Criteria

### Criterion 1: Pricing Service internal structure with separation of write path and read path ✅

**PASS** — The Pricing Service is decomposed into 10 internal elements following CQRS. The write path (PriceCommandHandler → DerivedRateCalculator → PriceRepository → PriceReadCache → PriceEventPublisher) is cleanly separated from the read path (PriceQueryHandler → PriceReadCache). Technology-agnostic ports (PriceCommandPort, PriceQueryPort, PriceRepository, PriceEventPublisher) enable protocol isolation and testability.

### Criterion 2: Derived rate calculation strategy defined ✅

**PASS** — Derived rates are computed in-memory by the DerivedRateCalculator using pre-loaded rate rules from the RateRuleCache. The computation strategy avoids cross-service calls and database round-trips during calculation. A latency budget allocates 15ms for computation within the 100ms total budget (QA-1).

### Criterion 3: Authentication and authorization flow specified end-to-end ✅

**PASS** — The OAuth2 Authorization Code flow with PKCE is specified from Angular SPA through API Gateway to the Cloud Identity Service. The API Gateway validates JWT tokens, enforces route-level authorization, and propagates claims (`X-User-Id`, `X-User-Roles`, `X-Authorized-Hotels`) to backend services. The Angular SPA uses AuthGuard, AdminGuard, and role-based UI rendering to satisfy QA-5.

### Criterion 4: Pricing Service ↔ Hotel Management Service integration designed ✅

**PASS** — The integration uses cached reference data with change notification. Rate rules and hotel configurations are loaded at Pricing Service startup and kept fresh via the `hotel-config-changes` Kafka topic. This avoids synchronous cross-service calls on the critical price-change path, preserving the 100ms latency budget. The staleness tolerance window is explicitly accepted.

### Criterion 5: Kafka topic and partitioning design defined ✅

**PASS** — Two Kafka topics are designed:
- `price-changes`: 6 partitions, hotelId key, replication factor 3, consumer group `channel-management-adapter`
- `hotel-config-changes`: 3 partitions, hotelId key, replication factor 3, consumer group `ps-config`

Partitioning by hotelId ensures message ordering per hotel.

### Criterion 6: Channel Management Adapter scope decision finalized ✅

**PASS** — R2 is resolved: the Channel Management Adapter is a standalone service, not embedded in the Pricing Service. This supports independent scaling, failure isolation, and clean separation of concerns. Kafka acts as the decoupling buffer so that price change responses do not wait for external system acknowledgment.

### Criterion 7: Concrete port interfaces defined ✅

**PASS** — Technology-agnostic port interfaces are defined for:
- Pricing Service: `PriceCommandPort`, `PriceQueryPort`, `PriceRepository`, `PriceEventPublisher`
- Hotel Management Service: `HotelConfigProvisioningPort`, `ConfigChangePublisher`
- Channel Management Adapter: `CMSClient`

All ports support mock/stub implementations for integration testing (QA-9) and protocol-independent core logic (QA-6).

---

## Driver Satisfaction Review

| Driver | Status | Evidence |
|--------|--------|----------|
| **HPS-2: Change Prices** | ✅ | Full end-to-end flow designed (View 3). All steps from UI through computation to Kafka publication are assigned to specific internal elements. |
| **QA-1 (100ms Performance)** | ✅ | Latency budget: 55ms core processing + 20ms network + 25ms margin = 100ms. In-memory computation, async Kafka produce, pre-loaded rules all contribute. |
| **HPS-3: Query Prices** | ✅ | Stateless PriceQueryHandler with in-memory PriceReadCache enables horizontal scaling (View 4). Read replicas for DB. |
| **QA-4 (Scalability)** | ✅ | Query tier can auto-scale to N instances. In-memory caches updated synchronously after writes. Read path is fully stateless. |
| **HPS-1: Login** | ✅ | OAuth2 PKCE flow specified (View 2). Token management in Angular SPA via memory + HTTP-only cookie. |
| **QA-5 (Security)** | ✅ | JWT validation at Gateway, role-based route enforcement, claim propagation to services, AuthGuard/AdminGuard in SPA, authorized-hotel filtering in UI. |
| **QA-6 (Modifiability)** | ✅ | All core logic behind technology-agnostic ports. Adding gRPC = new adapter at Gateway + new inbound adapter in Pricing Service — no core changes. |
| **QA-9 (Testability)** | ✅ | All outbound ports (PriceRepository, PriceEventPublisher, CMSClient) are interfaces — mockable. In-memory caches can be pre-populated in tests. |
| **R5 (100ms latency)** | ✅ RESOLVED | CQRS + in-memory computation + latency budget. See criterion 2. |
| **R7 (Auth details)** | ✅ RESOLVED | Complete OAuth2 PKCE flow. See criterion 3. |
| **R2 (CMA scope)** | ✅ RESOLVED | Standalone service. See criterion 6. |
| **R3 (Kafka design)** | ✅ RESOLVED | Two topics, partitioning by hotelId, consumer groups. See criterion 5. |

---

## Risks and Open Issues

| ID | Risk / Issue | Severity | Mitigation / Notes |
|----|-------------|----------|---------------------|
| **R8** | **Read cache consistency across instances:** When the Pricing Service has multiple query instances, a write must invalidate/update all read caches. The current design shows cache invalidation arrows but does not specify the mechanism (pub-sub, distributed cache, or write-through). | Medium | Address in Iteration 3 (Reliability/Availability). Options: Redis pub-sub, direct broadcast, or shared distributed cache (e.g., Redis). |
| **R9** | **RateRuleCache startup race condition:** If pricing requests arrive before the RateRuleCache has finished its initial load from HMS, the Pricing Service rejects them (503). This may violate QA-3 (99.9% availability) during deployment/restart. | Medium | Address in Iteration 3. Consider: graceful startup with readiness probes, or fallback to direct HMS call during warm-up. |
| **R10** | **QA-2 (100% publication reliability):** Kafka provides at-least-once delivery, but the Channel Management Adapter's RetryHandler and DeadLetterQueue are only preliminarily designed. Full idempotency and guaranteed delivery are not yet addressed. | High | Must be addressed in Iteration 3. |
| **R11** | **Price simulation not isolated from production data:** The simulation flow shares the RateRuleCache but skips persistence. However, the simulation command executes in the same service instance as production changes. No resource isolation. | Low | Acceptable for MVP. Could be addressed with separate simulation endpoints or rate limiting. |
| **R12** | **API Gateway as single point of failure:** Both frontend and external API consumers route through the Gateway. A Gateway outage blocks all access. | High | Address in Iteration 3. Need redundancy, health checks, and potentially separate gateways for internal vs. external traffic. |
| **R13** | **Hotel Management Service internal design:** Only the ports needed by Pricing Service are defined. Full internal structure (domain logic, adapters, database schema) is deferred. | Low | Addressed in Iteration 4 (Development & Operations). |

---

## Iteration Goal: PASSED ✅

The iteration goal — "Refine the internal structures of the top-level elements to support primary functionality" — has been achieved:

- **Pricing Service** is fully decomposed with CQRS, in-memory computation, and defined latency budgets supporting QA-1 (100ms) and QA-4 (scalability).
- **Authentication/authorization** is designed end-to-end (OAuth2 PKCE, JWT validation, role enforcement, claim propagation) satisfying QA-5.
- **PS↔HMS integration** uses cached reference data with Kafka change notifications, avoiding cross-service calls on the critical path.
- **Kafka topology** is defined with two topics, hotelId-based partitioning, and consumer groups.
- **Channel Management Adapter** is confirmed as a standalone service with Kafka as buffer.
- **All carried-forward risks from Iteration 1** (R2, R3, R5, R7) are resolved.
- **Six architectural views** document the refined structures.
- **New risks** (R8–R13) are identified for Iteration 3 (Reliability/Availability) and Iteration 4 (Development/Operations).