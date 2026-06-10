# ADD Step 1: Review Inputs (Iteration 1)

## Input Inventory

### Functional Requirements (Use Cases)
The Hotel Pricing System (HPS) supports six use cases:
- **HPS-1: Login** – Users authenticate via a cloud-based identity service. Post-login, users are authorized per hotel.
- **HPS-2: Change Prices** – Users modify base/fixed prices; derived rates are computed; simulation is allowed; prices pushed to channel management and queryable externally.
- **HPS-3: Query Prices** – Users and external systems query prices via UI or query API.
- **HPS-4: Manage Hotels** – Administrators manage hotel data, tax rates, rates, room types.
- **HPS-5: Manage Rates** – Administrators manage rate definitions and business rules.
- **HPS-6: Manage Users** – Administrators manage user permissions.

### Quality Attribute Scenarios
- **QA-1 (Performance):** Price changes published within 100ms. [HPS-2 | High | High]
- **QA-2 (Reliability):** 100% of price changes successfully published and received by channel management. [HPS-2 | High | High]
- **QA-3 (Availability):** Price query uptime SLA of 99.9%. [All | High | High]
- **QA-4 (Scalability):** Support 100K → 1M daily API queries with ≤20% latency increase. [HPS-3 | High | High]
- **QA-5 (Security):** Login validated against user identity service; authorized functions only. [All | High | Medium]
- **QA-6 (Modifiability):** Add new protocols (e.g., gRPC) without changing core components. [All | Medium | Medium]
- **QA-7 (Deployability):** Move between non-production environments without code changes. [All | Medium | Medium]
- **QA-8 (Monitorability):** Collect 100% of price publication performance/reliability metrics. [HPS-2 | Medium | Medium]
- **QA-9 (Testability):** 100% of elements support integration testing independent of external systems. [All | Medium | Medium]

### Architectural Concerns
- **CRN-1:** Establish preliminary overall system structure.
- **CRN-2:** Leverage team knowledge of Java, Angular framework, and Kafka.
- **CRN-3:** Assign work to development team members.
- **CRN-4:** Avoid introducing technical debt.
- **CRN-5:** Set up continuous deployment infrastructure.

### Constraints
- **CON-1:** Web browsers on Windows, OSX, Linux across different devices.
- **CON-2:** Cloud provider identity service; resources hosted in the cloud.
- **CON-3:** Code hosted on proprietary Git-based platform.
- **CON-4:** Initial version in 6 months; MVP demo in 2 months.
- **CON-5:** Initially REST API; may need other protocols later.
- **CON-6:** Cloud-native approach prioritized.

## Architectural Drivers for Iteration 1

For Iteration 1 ("Establish overall system structure"), the primary drivers are:

| Driver | Rationale |
|--------|-----------|
| **CRN-1** | This is the explicit purpose of this iteration: establish preliminary overall system structure. |
| **CON-1** | Constrains deployment architecture (web-based, cross-platform browsers). |
| **CON-2** | Dictates cloud hosting and external identity service integration. |
| **CON-6** | Cloud-native approach must shape the overall structural decisions. |
| **CRN-2** | Team expertise (Java, Angular, Kafka) guides technology choices in the structure. |
| **QA-6** | Modifiability for protocol changes influences early modularization decisions. |
| **QA-7** | Deployability across environments influences packaging and configuration structure. |
| **CON-4** | MVP in 2 months constrains scope and structural complexity. |
| **CON-5** | REST first but protocol flexibility needed — influences API boundary design. |
| **QA-3** | High availability (99.9%) influences deployment topology from the start. |

Other QAs (QA-1, QA-2, QA-4, QA-8, QA-9) will be addressed more deeply in later iterations.

## Key Observations
- Greenfield development — we start with the system itself as the only element.
- Cloud-native, web-based, with team expertise in Java (backend), Angular (frontend), Kafka (messaging).
- External systems: User Identity Service (cloud provider), Channel Management System.
- Two user types: business users and administrators.
- Two interaction channels: Web UI (browser) and Query API (external systems).