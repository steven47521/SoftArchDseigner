# ADD Step 2: Establish Iteration Goal by Selecting Drivers

## Iteration 1

### Iteration Goal

**Establish the preliminary overall system structure** — Identify the top-level decomposition of the Hotel Pricing System into major architectural elements, their boundaries, and their interactions. This iteration addresses CRN-1 directly and lays the foundation for all subsequent refinements.

### Drivers Selected for This Iteration

The following drivers are selected as shaping the overall system decomposition. Each is cited with its rationale for inclusion:

| Driver | Inclusion Rationale |
|--------|-------------------|
| **CRN-1** | This is the explicit goal: establish preliminary overall system structure. |
| **QA-5 (Security)** | The authentication and authorization boundary must be established at the top level. All user interactions flow through login (HPS-1); users may only access authorized hotels/functions afterward. This drives separation of a frontend/auth boundary from core business logic. |
| **QA-4 (Scalability) + QA-1 (Performance)** | Scaling queries 10× with ≤20% latency increase (QA-4) while maintaining 100ms write-path publication latency (QA-1) drives separation of read and write concerns at the topmost structural level. The read path can scale independently of the write path. |
| **QA-6 (Modifiability)** | Support for protocols other than REST (e.g., gRPC) without changing core components drives a ports-and-adapters boundary around the core business logic. |
| **QA-2 (Reliability)** | 100% publication guarantee influences the interaction between the pricing core and the channel management system — asynchronous, reliable messaging is needed. |
| **CRN-2 (Java, Angular, Kafka)** | Team skills constrain technology choices and influence how elements are assigned to implementation technologies. Angular → frontend; Java → backend services; Kafka → async messaging backbone. |
| **CON-2 + CON-6 (Cloud-native, Cloud identity)** | Cloud-native approach and cloud-hosted identity service influence the deployment topology, service boundaries, and integration with the cloud provider's identity service. |
| **CON-1 (Browser access)** | Users interact via web browsers across platforms, driving a web-based frontend element. |
| **CON-5 (REST initially)** | Initial REST API constrains the external-facing protocol of the query API, consistent with a ports-and-adapters approach. |

### Drivers Deferred

These drivers, while important, are deferred to later iterations since they refine rather than shape the top-level structure:

| Driver | Reason for Deferral |
|--------|-------------------|
| QA-3 (Availability) | Multi-AZ deployment and health checks are deployment/infrastructure concerns (Iteration 3). |
| QA-7 (Deployability) | Environment-independent configuration is a deployment concern (Iteration 4). |
| QA-8 (Monitorability) | Metrics collection sits alongside existing elements (Iteration 3/4). |
| QA-9 (Testability) | Test isolation strategies are detailed in Iteration 4. |
| CRN-3 (Team assignment) | Work allocation follows structural decisions (Iteration 4). |
| CRN-5 (CI/CD) | Continuous deployment infrastructure is an Iteration 4 concern. |
| CON-3 (Git platform) | Version control hosting does not shape architecture (Iteration 4). |
| CON-4 (6-month/2-month timelines) | Schedule constraint influences scope but not structure; MVP scope is a project management concern. |

---

## Self-Reflection (Step 2)

- **Whether only prior knowledge was used**: Yes. All drivers, constraints, and concerns are cited directly from the prior knowledge with their IDs. No external patterns or technologies were introduced.
- **Whether current iteration drivers are addressed**: Yes. The iteration goal explicitly targets CRN-1, and all selected drivers directly shape the top-level system decomposition. Deferred drivers are justified with clear rationale for why they do not influence the structural decomposition at this stage.
- **Whether diagrams use correct Mermaid/PlantUML format**: No diagrams are required in Step 2 — this is a goal-setting step.
- **Whether any undeclared assumptions were made**: None. The selection and deferral decisions follow directly from driver attributes (importance, difficulty) and their relevance to top-level structure vs. detailed design or infrastructure concerns.
