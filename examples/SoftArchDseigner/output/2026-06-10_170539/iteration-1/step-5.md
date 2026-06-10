# ADD Step 5: Instantiate Architectural Elements, Assign Responsibilities, and Define Interfaces (Iteration 1)

## Top-Level Architectural Elements

Based on the Service-Based Architecture with Ports & Adapters (Alternative D, Step 4), the Hotel Pricing System is decomposed into the following top-level elements:

---

### Element 1: HPS Web Frontend (Angular SPA)

| Attribute | Detail |
|-----------|--------|
| **Type** | Application Component — Browser-Based SPA |
| **Technology** | Angular (per CRN-2) |
| **Deployment** | Static web assets served via CDN/cloud storage; runs in user's browser |

**Responsibilities:**
- Render user interface for all use cases (Login, Change Prices, Query Prices, Manage Hotels, Manage Rates, Manage Users)
- Authenticate users via cloud identity service (CON-2) — redirect to identity provider, handle tokens
- Enforce client-side authorization: show only functions the authenticated user is permitted to access
- Communicate exclusively with the API Gateway via REST (CON-5)
- Support Windows, OSX, Linux browsers (CON-1)

**Provided Interface:**
- Browser-based graphical user interface (HTML/CSS/JS rendered in browser)

**Required Interface (consumes):**
- `REST /api/*` — All backend operations via the API Gateway
- Cloud Identity Service OAuth2/OIDC endpoints (external, per CON-2)

---

### Element 2: API Gateway

| Attribute | Detail |
|-----------|--------|
| **Type** | Infrastructure Component — API Gateway |
| **Technology** | Java (per CRN-2), cloud-managed gateway service (cloud-native, CON-6) |
| **Deployment** | Containerized, cloud-hosted |

**Responsibilities:**
- Provide a single entry point for all external requests (both browser SPA and external API consumers)
- Route requests to appropriate backend services (Pricing Service, Hotel Management Service)
- Authenticate/validate tokens against User Identity Service
- Enforce coarse-grained authorization (verify user has access before routing)
- Act as protocol boundary — expose REST today; support additional protocols (gRPC) via new adapters without changing core services (QA-6)
- Handle cross-cutting concerns: rate limiting, request logging, CORS

**Provided Interfaces:**
- `REST /api/prices/*` → routes to Pricing Service
- `REST /api/hotels/*` → routes to Hotel Management Service
- `REST /api/rates/*` → routes to Hotel Management Service
- `REST /api/users/*` → routes to Hotel Management Service
- `REST /api/auth/*` → authentication endpoints

**Required Interfaces (consumes):**
- Pricing Service internal REST/gRPC endpoints
- Hotel Management Service internal REST/gRPC endpoints
- User Identity Service token validation endpoints (external, per CON-2)

---

### Element 3: Pricing Service

| Attribute | Detail |
|-----------|--------|
| **Type** | Business Service — Core Domain Service |
| **Technology** | Java (per CRN-2), Ports & Adapters pattern |
| **Deployment** | Containerized, cloud-hosted, independently scalable |

**Responsibilities:**
- Process price changes (base prices, fixed prices) [HPS-2]
- Calculate derived rate prices from base prices using business rules [HPS-2]
- Support price simulation before committing changes [HPS-2]
- Serve price queries for specific hotels [HPS-3]
- Publish price changes to Channel Management System via Kafka [HPS-2, QA-2]
- Persist and retrieve pricing data

**Provided Interfaces (Ports — technology-agnostic):**
- `changePrices(hotelId, dates, prices)` — Apply base/fixed price changes; triggers derived rate calculation
- `simulatePrices(hotelId, dates, prices)` — Simulate price changes without persisting
- `queryPrices(hotelId, dateRange, rateTypes)` — Return current prices
- `publishPrices(hotelId, prices)` — Publish price changes (triggers Kafka event)

**Required Interfaces (consumes):**
- Hotel Management Service: `getHotelConfiguration(hotelId)` — Retrieve hotel tax rates, available rates, room types for price calculation
- Hotel Management Service: `getRateRules(rateId)` — Retrieve business rules for derived rate calculation
- Kafka Topic `price-changes` — Publish price change events to Channel Management System
- Pricing Database — Persist and query price data

---

### Element 4: Hotel Management Service

| Attribute | Detail |
|-----------|--------|
| **Type** | Business Service — Supporting Domain Service |
| **Technology** | Java (per CRN-2), Ports & Adapters pattern |
| **Deployment** | Containerized, cloud-hosted |

**Responsibilities:**
- Manage hotel information including tax rates, available rates, room types [HPS-4]
- Manage rate definitions and business rules [HPS-5]
- Manage user permissions [HPS-6]
- Serve hotel configuration data to Pricing Service for price calculation
- Enforce user authorization at the hotel level (users may only query/modify authorized hotels)

**Provided Interfaces (Ports — technology-agnostic):**
- `createHotel(hotelData)`, `updateHotel(hotelId, hotelData)`, `getHotel(hotelId)` — Hotel CRUD [HPS-4]
- `getHotelConfiguration(hotelId)` — Return tax rates, room types, available rates for pricing
- `createRate(rateData)`, `updateRate(rateId, rateData)`, `getRate(rateId)` — Rate CRUD [HPS-5]
- `getRateRules(rateId)` — Return business rules for a specific rate
- `updateUserPermissions(userId, permissions)` — Manage user access [HPS-6]
- `getAuthorizedHotels(userId)` — Return hotels a user is authorized for

**Required Interfaces (consumes):**
- Management Database — Persist and query hotel, rate, user data
- User Identity Service — Validate user existence and attributes (external, CON-2)

---

### Element 5: Channel Management Adapter

| Attribute | Detail |
|-----------|--------|
| **Type** | Integration Adapter — Outbound Event Publisher |
| **Technology** | Java, Kafka Producer (per CRN-2) |
| **Deployment** | Containerized, cloud-hosted (may be embedded within Pricing Service or standalone) |

**Responsibilities:**
- Consume price change events from the Pricing Service
- Transform price data into the format expected by the Channel Management System
- Reliably deliver price changes to the Channel Management System [QA-2]
- Handle delivery failures with retry mechanisms

**Provided Interfaces:**
- Kafka Consumer: subscribes to `price-changes` topic
- Outbound API calls to Channel Management System (external system)

**Required Interfaces (consumes):**
- Kafka Topic `price-changes` — Inbound price change events
- Channel Management System API — Outbound price publication

---

## Data Store Elements

| Data Store | Owned By | Content |
|------------|----------|---------|
| **Pricing Database** | Pricing Service | Current prices, price history, derived rates |
| **Management Database** | Hotel Management Service | Hotel definitions, rate rules, tax rates, room types, user permissions |

---

## Internal Communication Patterns

| From | To | Mechanism | Rationale |
|------|----|-----------|-----------|
| API Gateway | Pricing Service | Synchronous REST/HTTP | Request-response query/command pattern |
| API Gateway | Hotel Management Service | Synchronous REST/HTTP | Request-response query/command pattern |
| Pricing Service | Hotel Management Service | Synchronous REST/HTTP | Hotel config & rate rules needed for price calculation |
| Pricing Service | Channel Management Adapter | Asynchronous (Kafka) | Decouple price publication from price change transactions; reliability (QA-2) |

---

## External Integration Points

| External System | Integrated Via | Direction |
|-----------------|----------------|-----------|
| User Identity Service | API Gateway + HPS Web Frontend | Outbound (auth) |
| Channel Management System | Channel Management Adapter | Outbound (push prices) |
| External Query API Consumers | API Gateway | Inbound (query) |
| Web Browsers | HPS Web Frontend | Inbound (UI) |

---

## Element Allocation to Use Cases

| Use Case | Primary Element(s) |
|----------|-------------------|
| HPS-1 Login | HPS Web Frontend, API Gateway, User Identity Service (external) |
| HPS-2 Change Prices | HPS Web Frontend → API Gateway → Pricing Service → Channel Management Adapter |
| HPS-3 Query Prices | HPS Web Frontend or External Systems → API Gateway → Pricing Service |
| HPS-4 Manage Hotels | HPS Web Frontend → API Gateway → Hotel Management Service |
| HPS-5 Manage Rates | HPS Web Frontend → API Gateway → Hotel Management Service |
| HPS-6 Manage Users | HPS Web Frontend → API Gateway → Hotel Management Service |