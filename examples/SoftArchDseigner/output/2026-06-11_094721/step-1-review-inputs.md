# ADD Step 1: Review Inputs

## Architectural drivers identified

### Functional drivers
- HPS-1 Login: authenticate users through the cloud provider identity service and restrict visible/allowed functions to authorized scope. This is an architectural driver because it impacts system context and security boundaries (HPS-1, QA-5, CON-2).
- HPS-2 Change Prices: support simulation, change submission, calculation of derived rate prices, and publication of changed prices to external systems. This is a primary functional driver because it is tied to the highest-priority performance and reliability scenarios (HPS-2, QA-1, QA-2).
- HPS-3 Query Prices: support price queries from both user interface and external systems. This is an architectural driver because it is tied to availability, scalability, and protocol concerns (HPS-3, QA-3, QA-4, QA-6, CON-5).
- HPS-4 Manage Hotels, HPS-5 Manage Rates, HPS-6 Manage Users: these administrative capabilities are drivers for structure because they imply management functions, authorization checks, and maintainable separation of responsibilities (HPS-4, HPS-5, HPS-6, QA-5).

### Quality attribute drivers
- QA-1 Performance: when base prices are changed for a hotel/date, all resulting rate and room type prices must be published and ready for query within 100 ms. High importance and high difficulty; strong driver for initial structure.
- QA-2 Reliability: 100% of price changes must be published and received by the channel management system. High importance and high difficulty; strong driver for integration and publication structure.
- QA-3 Availability: price query uptime SLA of 99.9% outside maintenance windows. High importance and high difficulty; driver for deployable runtime structure.
- QA-4 Scalability: support growth from 100,000 to 1,000,000 API price queries/day with no more than 20% average latency increase. High importance and high difficulty; driver for query path structure.
- QA-5 Security: login via frontend, validation against cloud identity service, and authorized function visibility. High importance; driver for frontend/backend boundary and identity integration.
- QA-6 Modifiability: add non-REST price query protocols without changing core components. Driver for interface boundaries.
- QA-7 Deployability: move applications between non-production environments without code changes. Driver for deployment and configuration approach.
- QA-8 Monitorability: collect 100% of price publication performance and reliability metrics as needed. Driver for observability hooks.
- QA-9 Testability: all system elements support integration testing independent of external systems. Driver for explicit external boundaries and replaceable adapters.

### Constraints and concerns affecting architecture
- CRN-1 Establish preliminary overall system structure: direct driver for Iteration 1.
- CRN-2 Leverage team knowledge of Java, Angular framework, and Kafka: constrains technology choices for initial structure.
- CRN-3 Assign work to development team members: supports choosing a decomposition with clear responsibility boundaries.
- CRN-4 Avoid introducing technical debt: favors explicit boundaries early.
- CRN-5 Set up continuous deployment infrastructure: relevant to later deployment-oriented decisions but already influences container-friendly decomposition.
- CON-1 Users interact through web browsers on Windows, OSX, and Linux across devices: requires web frontend.
- CON-2 Users managed via cloud provider identity service; resources hosted in the cloud: requires cloud identity integration and cloud-hosted deployment.
- CON-3 Code hosted on proprietary Git-based platform: delivery constraint, not a primary structural driver for Iteration 1.
- CON-4 Initial version within 6 months; MVP demo within 2 months: favors simple, understandable initial decomposition.
- CON-5 Initial interaction via REST API; may need other protocols later: driver for API boundary design and protocol isolation.
- CON-6 Cloud-native approach prioritized: driver for deployable service/runtime structure.

## Drivers selected as most influential for early architecture
For Iteration 1, the most influential architectural drivers are:
- CRN-1 preliminary overall system structure
- HPS-2 price change and publication flow
- HPS-3 price query access
- QA-1 performance for publication readiness
- QA-2 reliability of publication and channel delivery
- QA-3 availability for price query
- QA-4 scalability for price queries
- QA-5 security with cloud identity integration
- QA-6 protocol modifiability
- CON-1 browser-based access
- CON-2 cloud identity/cloud hosting
- CON-5 REST now, other protocols later
- CON-6 cloud-native priority
- CRN-2 Java, Angular, Kafka knowledge

## Inputs deferred but retained for later iterations
These inputs remain relevant but are not the main focus of Iteration 1:
- QA-7 deployability details
- QA-8 monitorability mechanisms
- QA-9 testability mechanisms
- CRN-5 continuous deployment infrastructure
- CON-3 Git platform hosting details
- Detailed work allocation implications from CRN-3

## Self-Reflection (Step 1)
- Whether only prior knowledge was used: yes — all statements are derived from the provided use cases, quality attributes, concerns, constraints, and allowed design approaches.
- Whether current iteration drivers are addressed: yes — the review identifies the drivers most relevant to establishing the overall system structure for Iteration 1.
- Whether diagrams use correct Mermaid/PlantUML format: yes — no diagrams were required in this step.
- Whether any undeclared assumptions were made: none
