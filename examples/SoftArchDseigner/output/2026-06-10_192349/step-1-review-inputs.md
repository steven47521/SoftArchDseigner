# ADD Step 1: Review Inputs

## 1. Functional Requirements (Use Cases)

The system must support the following use cases:

| ID | Use Case | Summary |
|----|----------|---------|
| HPS-1 | Login | Users authenticate via credentials against the cloud identity service; post-login, only authorized hotels/functions are accessible. |
| HPS-2 | Change Prices | Authorized users select hotels, choose dates for base/fixed price changes; derived rates are computed; simulation is supported; changes are published to the channel management system and queryable by external systems. |
| HPS-3 | Query Prices | Users or external systems query prices for a specific hotel via UI or query API. |
| HPS-4 | Manage Hotels | Administrators add/modify hotel information including tax rates, available rates, and room types. |
| HPS-5 | Manage Rates | Administrators add/modify rates including business rules for calculating derived rates. |
| HPS-6 | Manage Users | Administrators change permissions for specific users. |

## 2. Quality Attribute Scenarios

| ID | Attribute | Scenario | Related UC | Importance | Difficulty |
|----|-----------|----------|------------|------------|------------|
| QA-1 | Performance | Base price changes for a hotel/date: all rate and room type prices published within 100ms. | HPS-2 | High | High |
| QA-2 | Reliability | 100% of price changes are successfully published (queryable) and received by the channel management system. | HPS-2 | High | High |
| QA-3 | Availability | Price query uptime SLA of 99.9% (outside maintenance windows). | All | High | High |
| QA-4 | Scalability | Support 100,000 price queries/day via API initially, scale to 1,000,000, with average latency increase ≤20%. | HPS-3 | High | High |
| QA-5 | Security | Users authenticate via cloud identity service; only authorized functions are shown post-login. | All | High | Medium |
| QA-6 | Modifiability | New query protocols (e.g., gRPC) added without changing core components. | All | Medium | Medium |
| QA-7 | Deployability | Applications move between non-production environments without code changes. | All | Medium | Medium |
| QA-8 | Monitorability | Operators can measure price publication performance and reliability; 100% of metrics collectable. | HPS-2 | Medium | Medium |
| QA-9 | Testability | 100% of system elements support integration testing independent of external systems. | All | Medium | Medium |

## 3. Constraints

| ID | Constraint |
|----|------------|
| CON-1 | Web browser access on Windows, OSX, Linux across different devices. |
| CON-2 | Cloud provider identity service for user management; resources hosted in the cloud. |
| CON-3 | Code hosted on proprietary Git-based platform (same as other company projects). |
| CON-4 | Initial system version in 6 months; MVP demo to internal stakeholders in 2 months. |
| CON-5 | Initially REST API; may need other protocols later. |
| CON-6 | Cloud-native approach prioritized. |

## 4. Architectural Concerns

| ID | Concern |
|----|---------|
| CRN-1 | Establish preliminary overall system structure. |
| CRN-2 | Leverage team knowledge of Java, Angular framework, and Kafka. |
| CRN-3 | Assign work to development team members. |
| CRN-4 | Avoid introducing technical debt. |
| CRN-5 | Set up continuous deployment infrastructure. |

## 5. Architectural Drivers

Architectural drivers are the combination of high-importance quality attributes, constraints, and concerns that most significantly shape the architecture. For this system, the following are identified as primary drivers:

### Primary Drivers (High Importance + High Difficulty)

| Driver | Type | Rationale |
|--------|------|-----------|
| QA-1 (Performance) | Quality Attribute | 100ms publication latency directly constrains the write path architecture, influencing decomposition of pricing computation vs. publication. |
| QA-2 (Reliability) | Quality Attribute | 100% publication guarantee demands transactional integrity across pricing writes and channel management pushes. |
| QA-3 (Availability) | Quality Attribute | 99.9% query uptime requires redundancy and fault tolerance in the query path. |
| QA-4 (Scalability) | Quality Attribute | 10× query growth with bounded latency increase drives separation of read and write paths. |
| QA-5 (Security) | Quality Attribute | Authentication/authorization shapes the entire user-facing boundary. |
| QA-6 (Modifiability) | Quality Attribute | Protocol-independent core enables future gRPC support, influencing boundary design. |

### Supporting Drivers

| Driver | Type | Rationale |
|--------|------|-----------|
| CON-2 (Cloud Identity + Cloud Hosting) | Constraint | Dictates integration approach and deployment target. |
| CON-5 (REST initially, multi-protocol later) | Constraint | Shapes API boundary design toward protocol abstraction. |
| CON-6 (Cloud-native) | Constraint | Influences deployment architecture, service boundaries, infrastructure choices. |
| CRN-1 (Overall system structure) | Concern | This is the explicit goal of Iteration 1. |
| CRN-2 (Java, Angular, Kafka) | Concern | Constrains technology choices and influences component assignment. |
| CRN-4 (Avoid technical debt) | Concern | Promotes clean separation and well-defined interfaces from the start. |

## 6. Driver Prioritization for Iteration 1

For Iteration 1 (establish overall system structure), the prioritized drivers are:

1. **CRN-1** — The explicit goal: establish preliminary overall system structure.
2. **QA-5 (Security)** — Authentication/authorization boundary must be established early.
3. **QA-4 (Scalability) + QA-1 (Performance)** — Drive separation of read and write concerns.
4. **QA-6 (Modifiability)** — Protocol-independent core for future extensibility.
5. **CRN-2** — Java/Angular/Kafka influence component assignment.
6. **CON-2 + CON-6** — Cloud-native, cloud-hosted deployment model.

---

## Self-Reflection (Step 1)

- **Whether only prior knowledge was used**: Yes. All requirements, quality attributes, constraints, and concerns are taken verbatim from the prior knowledge provided in the case study. No external domain knowledge was introduced.
- **Whether current iteration drivers are addressed**: Yes. The architectural drivers have been identified and prioritized specifically for Iteration 1's goal of establishing overall system structure. CRN-1 is explicitly called out as the primary concern.
- **Whether diagrams use correct Mermaid/PlantUML format**: No diagrams are required in Step 1 (review of inputs). This step is purely analytical.
- **Whether any undeclared assumptions were made**: None. All classifications (High/Medium importance, difficulty ratings) are taken directly from the case study tables. The grouping into "primary" vs. "supporting" drivers is based solely on the stated importance/difficulty levels and relevance to Iteration 1's goal.
