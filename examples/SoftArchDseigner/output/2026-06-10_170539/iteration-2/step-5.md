# ADD Step 5: Instantiate Architectural Elements, Assign Responsibilities, and Define Interfaces (Iteration 2)

---

## 5A: Pricing Service — Internal Decomposition (CQRS + In-Memory Computation)

The Pricing Service is decomposed into internal modules following CQRS and Ports & Adapters patterns:

### Pricing Service Internal Elements

| Internal Element | Type | Responsibilities |
|------------------|------|------------------|
| **PriceCommandHandler** | Command Processor | Receives `changePrices` and `simulatePrices` requests. Validates input. Orchestrates the write flow. |
| **DerivedRateCalculator** | Domain Service | Applies business rules (rate rules) to base prices to compute derived rates. Operates entirely in-memory using pre-loaded rules. |
| **RateRuleCache** | In-Memory Store | Holds pre-loaded rate business rules and hotel tax configurations. Loaded at startup; refreshed via Kafka events from Hotel Management Service. |
| **PriceRepository** | Domain Repository (Port) | Persists price changes to the Pricing Database. Abstract interface — implemented by a DB adapter. |
| **PriceQueryHandler** | Query Processor | Handles `queryPrices` requests. Serves from the read-optimized cache. Stateless for horizontal scaling. |
| **PriceReadCache** | In-Memory Cache | Denormalized view of current prices, indexed by hotel + date + room type + rate. Updated synchronously after each write. Query responses served from cache without DB access. |
| **PriceEventPublisher** | Event Publisher (Port) | Publishes price change events to Kafka `price-changes` topic. Abstract interface — implemented by Kafka adapter. |
| **PricingDBAdapter** | Adapter (Outbound) | Implements `PriceRepository`. Executes SQL/NoSQL operations against the Pricing Database. |
| **KafkaPriceAdapter** | Adapter (Outbound) | Implements `PriceEventPublisher`. Produces messages to Kafka. |
| **RESTPricingAdapter** | Adapter (Inbound) | Exposes REST endpoints for price commands and queries. Translates HTTP requests to port method calls. |

### Pricing Service Internal Communication Flow

**Write Flow (HPS-2: Change Prices):**
```
RESTPricingAdapter → PriceCommandHandler → RateRuleCache (get rules)
    → DerivedRateCalculator (compute derived rates)
    → PriceRepository (persist)
    → PriceReadCache (update read model)
    → PriceEventPublisher → Kafka
```

**Query Flow (HPS-3: Query Prices):**
```
RESTPricingAdapter → PriceQueryHandler → PriceReadCache → Response
```

**Simulation Flow (HPS-2: Simulate):**
```
RESTPricingAdapter → PriceCommandHandler → RateRuleCache (get rules)
    → DerivedRateCalculator (compute hypothetical derived rates)
    → Response (no persistence, no cache update, no event)
```

### Port Interfaces (Technology-Agnostic)

```
// === Command Port ===
interface PriceCommandPort {
    ChangePricesResult changePrices(ChangePricesCommand command);
    SimulatedPricesResult simulatePrices(SimulatePricesCommand command);
}

// === Query Port ===
interface PriceQueryPort {
    PriceQueryResult queryPrices(PriceQuery query);
}

// === Repository Port (outbound) ===
interface PriceRepository {
    void savePriceChange(HotelId hotelId, DateRange dates, Map<RateType, Money> prices);
    PriceSnapshot loadCurrentPrices(HotelId hotelId);
}

// === Event Publisher Port (outbound) ===
interface PriceEventPublisher {
    void publishPriceChanged(PriceChangedEvent event);
}
```

### Data Types (Key Commands/Queries)

```
ChangePricesCommand {
    hotelId: HotelId
    userId: UserId
    changes: List<PriceChange>  // base price or fixed price changes
    // Each PriceChange: date, roomType, amount, changeType (BASE|FIXED)
}

SimulatePricesCommand {
    hotelId: HotelId
    changes: List<PriceChange>
}

PriceQuery {
    hotelId: HotelId
    dateRange: DateRange
    rateTypes: List<RateType>  // optional filter
}

PriceChangedEvent {
    hotelId: HotelId
    timestamp: Instant
    prices: List<PublishedPrice>
    // Each PublishedPrice: date, roomType, rateType, amount
}
```

### Latency Budget for QA-1 (100ms)

| Step | Budget | Notes |
|------|--------|-------|
| HTTP ingress + deserialization | 5ms | Gateway → REST Adapter |
| Rate rule lookup (in-memory) | 2ms | Pre-loaded; O(1) hash lookup |
| Derived rate computation | 15ms | Arithmetic operations; bulk computation |
| Database write | 20ms | Single transaction, indexed by hotel+date |
| Read cache update | 5ms | In-memory put |
| Kafka produce (async) | 3ms | Fire-and-forget; no wait for ack |
| HTTP response serialization | 5ms | — |
| **Subtotal core** | **55ms** | — |
| Network + Gateway overhead | 20ms | — |
| **Safety margin** | **25ms** | — |
| **Total budget** | **100ms** | — |

---

## 5B: API Gateway — Internal Decomposition

### API Gateway Internal Elements

| Internal Element | Type | Responsibilities |
|------------------|------|------------------|
| **AuthFilter** | Inbound Interceptor | Intercepts all requests. Extracts JWT from Authorization header. Validates signature and expiry against identity provider JWKS. Rejects unauthenticated requests. |
| **RoleEnforcer** | Inbound Interceptor | Checks JWT claims for required roles per route. Rejects unauthorized requests. |
| **ClaimPropagator** | Outbound Enricher | Extracts user ID, roles, authorized hotels from JWT claims. Attaches as HTTP headers (`X-User-Id`, `X-User-Roles`, `X-Authorized-Hotels`) to downstream service calls. |
| **RequestRouter** | Core Router | Routes requests to backend services based on URL path patterns. Maintains route table. |
| **RESTIngressAdapter** | Inbound Adapter | Accepts REST/HTTP requests. Today the only protocol. |
| **RESTEgressAdapter** | Outbound Adapter | Forwards requests to backend services via REST. |
| **RateLimiter** | Cross-Cutting | Per-user / per-IP rate limiting to protect backend services. |

### API Gateway Route Table

| Path Pattern | Target Service | Required Role | Rate Limit |
|-------------|---------------|---------------|------------|
| `POST /api/auth/login` | Identity Service (redirect) | None | 10/min per IP |
| `POST /api/auth/refresh` | Identity Service | Authenticated | 10/min per user |
| `GET  /api/prices` | Pricing Service (query) | Authenticated | 1000/min per user |
| `POST /api/prices/change` | Pricing Service (command) | Authenticated | 100/min per user |
| `POST /api/prices/simulate` | Pricing Service (command) | Authenticated | 100/min per user |
| `GET/POST/PUT/DELETE /api/hotels/**` | Hotel Management Service | Admin | 500/min per user |
| `GET/POST/PUT/DELETE /api/rates/**` | Hotel Management Service | Admin | 500/min per user |
| `GET/POST/PUT/DELETE /api/users/**` | Hotel Management Service | Admin | 200/min per user |

### Authentication Flow (End-to-End)

```
1. User → Angular SPA: Clicks "Login"
2. Angular SPA → Cloud Identity Service: Redirect to OAuth2 /authorize (PKCE)
3. User → Identity Service: Enters credentials
4. Identity Service → Angular SPA: Redirect with authorization code
5. Angular SPA → API Gateway (/api/auth/token): Exchange code for tokens
6. API Gateway → Identity Service: POST /token (code + code_verifier)
7. Identity Service → API Gateway: { access_token (JWT), refresh_token }
8. API Gateway → Angular SPA: { access_token, refresh_token (HTTP-only cookie) }
9. Angular SPA: Stores access_token in memory, includes in Authorization header
10. API Gateway (per request): Validates JWT, propagates claims to services
```

---

## 5C: HPS Web Frontend — Internal Decomposition

### Angular Module Structure (Feature-Based)

| Module | Components | Routes | Guards |
|--------|-----------|--------|--------|
| **AppModule** | Shell, NavBar, Layout | `/` | — |
| **LoginModule** | LoginComponent, CallbackComponent | `/login`, `/auth/callback` | None |
| **PricingModule** | PriceChangeComponent, PriceSimulationComponent, PriceQueryComponent, HotelSelectorComponent | `/pricing/change`, `/pricing/simulate`, `/pricing/query` | AuthGuard |
| **HotelManagementModule** | HotelListComponent, HotelEditComponent, TaxRateComponent, RoomTypeComponent | `/admin/hotels/**` | AuthGuard, AdminGuard |
| **RateManagementModule** | RateListComponent, RateEditComponent, RateRuleBuilder | `/admin/rates/**` | AuthGuard, AdminGuard |
| **UserManagementModule** | UserListComponent, PermissionEditorComponent | `/admin/users/**` | AuthGuard, AdminGuard |
| **CoreModule** | AuthService, ApiService, TokenInterceptor, AuthGuard, AdminGuard | — | — |

### Key Frontend Services (Angular Injectable)

| Service | Responsibility |
|---------|---------------|
| **AuthService** | OAuth2 flow orchestration, token storage/refresh, user session management |
| **TokenInterceptor** | Attaches `Authorization: Bearer <token>` to all outbound HTTP requests |
| **ApiService** | Typed HTTP client wrapping REST calls to API Gateway |
| **AuthGuard** | Route guard: checks if user is authenticated before activating route |
| **AdminGuard** | Route guard: checks if user has admin role from JWT claims |
| **HotelAuthorizationService** | From decoded JWT, provides list of authorized hotels for UI filtering |

### Authorization-Driven UI (QA-5)

- NavBar reads roles from AuthService; renders navigation links only for authorized modules.
- HotelSelectorComponent queries `authorizedHotels` claim from JWT; shows only permitted hotels.
- Admin-only routes (`/admin/**`) are protected by AdminGuard.
- Business users never see admin navigation or can access admin routes.

---

## 5D: Pricing Service ↔ Hotel Management Service Integration

### Integration Design

The Pricing Service depends on Hotel Management Service for two data sets:
1. **Hotel configuration:** tax rates, room types per hotel
2. **Rate business rules:** how derived rates are computed from base prices

### Caching Strategy

| Aspect | Design |
|--------|--------|
| **Initial Load** | On Pricing Service startup, `RateRuleCache` calls `GET /api/hotels/config/all` and `GET /api/rates/rules/all` from HMS to populate its in-memory cache. |
| **Change Notification** | When an administrator modifies hotel config or rate rules (HPS-4, HPS-5), HMS publishes events to Kafka topic `hotel-config-changes`. Pricing Service's `RateRuleCache` consumes these events and updates its in-memory store. |
| **Staleness Window** | Config changes propagate within seconds. During the propagation window, price changes may use slightly stale config. This is acceptable because: (a) config changes are infrequent; (b) the window is short; (c) both old and new configs are valid states. |
| **Cache Miss** | If cache is empty (e.g., after restart before initial load completes), pricing operations are rejected with a 503 until cache is warm. |

### New Kafka Topic: `hotel-config-changes`

| Attribute | Value |
|-----------|-------|
| **Topic Name** | `hotel-config-changes` |
| **Producers** | Hotel Management Service |
| **Consumers** | Pricing Service (RateRuleCache) |
| **Partition Key** | hotelId |
| **Message Format** | `{ eventType: "RATE_UPDATED" | "HOTEL_CONFIG_UPDATED", entityId: string, timestamp: ISO8601 }` |
| **Retention** | 7 days (for replay on restart) |

### Hotel Management Service — New/Refined Ports for Pricing Integration

```
// === Config Provisioning Port (for Pricing Service consumption) ===
interface HotelConfigProvisioningPort {
    HotelConfigSnapshot getAllHotelConfigurations();
    RateRulesSnapshot getAllRateRules();
    List<ConfigChangeEvent> getConfigChangesSince(Timestamp since);  // for catch-up
}

// === Config Change Publisher Port ===
interface ConfigChangePublisher {
    void publishConfigChanged(ConfigChangeEvent event);
}
```

---

## 5E: Channel Management Adapter — Standalone Service Design

### Decision: Standalone Service (R2 Resolved)

The Channel Management Adapter is a standalone Java service, not embedded in the Pricing Service.

### Channel Management Adapter Internal Elements

| Internal Element | Type | Responsibilities |
|------------------|------|------------------|
| **PriceChangeConsumer** | Kafka Consumer | Consumes messages from `price-changes` topic. Partitions by hotelId for ordering. |
| **MessageTransformer** | Data Mapper | Transforms internal `PriceChangedEvent` format to the Channel Management System's expected format. |
| **CMSClient** | Outbound Adapter (Port) | Sends price data to the external Channel Management System. Abstract interface for testability (QA-9). |
| **CMSRestAdapter** | Adapter | Implements `CMSClient` using REST/HTTP to the Channel Management System. |
| **RetryHandler** | Resilience | Handles transient failures when publishing to CMS. Preliminary: retry with backoff. Full reliability design in Iteration 3. |
| **DeadLetterQueue** | Failure Sink | Stores messages that fail after all retries, for operator inspection. |

### Kafka Topic: `price-changes` (R3 Resolved)

| Attribute | Value |
|-----------|-------|
| **Topic Name** | `price-changes` |
| **Partition Key** | hotelId |
| **Partitions** | 6 (supports parallel processing by hotel) |
| **Replication Factor** | 3 (cloud-native, CON-6) |
| **Retention** | 7 days |
| **Consumer Group** | `channel-management-adapter` |
| **Delivery Semantics** | At-least-once (idempotency handled by CMS-side dedup or message ID) |

### CMSClient Port Interface (Technology-Agnostic)

```
interface CMSClient {
    PublicationResult publishPrices(HotelId hotelId, List<PublishedPrice> prices);
}

enum PublicationResult {
    SUCCESS, TRANSIENT_FAILURE, PERMANENT_FAILURE
}
```

---

## 5F: Summary — All New Elements and Interfaces

### New Internal Elements Across All Refined Components

| Parent Element | New Internal Element | Type |
|---------------|---------------------|------|
| Pricing Service | PriceCommandHandler | Command Processor |
| Pricing Service | DerivedRateCalculator | Domain Service |
| Pricing Service | RateRuleCache | In-Memory Store |
| Pricing Service | PriceRepository | Outbound Port |
| Pricing Service | PriceQueryHandler | Query Processor |
| Pricing Service | PriceReadCache | In-Memory Cache |
| Pricing Service | PriceEventPublisher | Outbound Port |
| Pricing Service | PricingDBAdapter | Outbound Adapter |
| Pricing Service | KafkaPriceAdapter | Outbound Adapter |
| Pricing Service | RESTPricingAdapter | Inbound Adapter |
| API Gateway | AuthFilter | Inbound Interceptor |
| API Gateway | RoleEnforcer | Inbound Interceptor |
| API Gateway | ClaimPropagator | Outbound Enricher |
| API Gateway | RequestRouter | Core Router |
| API Gateway | RESTIngressAdapter | Inbound Adapter |
| API Gateway | RESTEgressAdapter | Outbound Adapter |
| API Gateway | RateLimiter | Cross-Cutting |
| HPS Web Frontend | AppModule, LoginModule, PricingModule, HotelManagementModule, RateManagementModule, UserManagementModule, CoreModule | Angular Modules |
| HPS Web Frontend | AuthService, TokenInterceptor, ApiService, AuthGuard, AdminGuard, HotelAuthorizationService | Angular Services |
| HMS (partial) | HotelConfigProvisioningPort | Inbound Port |
| HMS (partial) | ConfigChangePublisher | Outbound Port |
| Channel Mgmt Adapter | PriceChangeConsumer | Kafka Consumer |
| Channel Mgmt Adapter | MessageTransformer | Data Mapper |
| Channel Mgmt Adapter | CMSClient | Outbound Port |
| Channel Mgmt Adapter | CMSRestAdapter | Outbound Adapter |
| Channel Mgmt Adapter | RetryHandler | Resilience |
| Channel Mgmt Adapter | DeadLetterQueue | Failure Sink |

### Carried-Forward Risks Resolution

| Risk ID | Status | Resolution |
|---------|--------|------------|
| R5 (100ms latency) | ✅ RESOLVED | CQRS + in-memory computation + latency budget defined (55ms core, 100ms total) |
| R7 (Auth details) | ✅ RESOLVED | OAuth2 PKCE flow, JWT validation at Gateway, claim propagation to services specified |
| R2 (CMA scope) | ✅ RESOLVED | Standalone service with Kafka buffer |
| R3 (Kafka design) | ✅ RESOLVED | `price-changes` topic: 6 partitions, hotelId key, replication factor 3; `hotel-config-changes` topic for HMS→PS config sync |