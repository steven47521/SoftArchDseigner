# ADD Step 4: Choose One or More Design Concepts (Iteration 1)

## Design Concept Alternatives

### Alternative A: Monolithic Layered Architecture
Single deployable backend with internal horizontal layers (Presentation → Business Logic → Data Access), plus a separate Angular SPA frontend. All business capabilities co-located.

| Driver | Assessment |
|--------|------------|
| CON-6 (Cloud-Native) | ⚠ Weak — single deployable limits independent scaling and cloud-native patterns |
| CON-4 (2-Month MVP) | ✅ Strong — simplest to develop and deploy |
| QA-6 (Protocol Flexibility) | ⚠ Weak — protocol concerns tangled with business logic in presentation layer |
| QA-7 (Deployability) | ✅ Strong — single artifact to move between environments |
| CRN-2 (Java, Angular, Kafka) | ✅ Compatible — Angular frontend, Java monolith, Kafka for external integration |

### Alternative B: Full Microservices Architecture
Each business capability (pricing, hotel management, user management) is an independently deployable service with its own data store. API Gateway routes requests.

| Driver | Assessment |
|--------|------------|
| CON-6 (Cloud-Native) | ✅ Strong — fully aligned with cloud-native, independent scaling |
| CON-4 (2-Month MVP) | ❌ Weak — operational complexity (service discovery, distributed data, CI/CD pipelines) threatens 2-month timeline |
| QA-6 (Protocol Flexibility) | ✅ Strong — each service can expose multiple protocols independently |
| QA-7 (Deployability) | ⚠ Moderate — each service deployable independently but orchestration adds complexity |
| CRN-2 (Java, Angular, Kafka) | ✅ Compatible — each service in Java, Angular frontend, Kafka for inter-service comms |

### Alternative C: Modular Monolith with Hexagonal (Ports & Adapters)
Single deployable backend with strict module boundaries. Core business logic isolated behind ports; adapters handle REST, database, Kafka, etc. Angular SPA frontend.

| Driver | Assessment |
|--------|------------|
| CON-6 (Cloud-Native) | ⚠ Weak — single deployable limits cloud-native benefits but can be containerized |
| CON-4 (2-Month MVP) | ✅ Strong — simple deployment, lower operational overhead |
| QA-6 (Protocol Flexibility) | ✅ Strong — ports isolate core from protocols; new adapters add gRPC without changing core |
| QA-7 (Deployability) | ✅ Strong — single artifact; external config per environment |
| CRN-2 (Java, Angular, Kafka) | ✅ Compatible — Java monolith, Angular frontend, Kafka adapter |

### Alternative D: Service-Based Architecture (3-5 Services)
Moderate decomposition: frontend SPA, API Gateway, Pricing Service, Hotel Management Service. Each service is independently deployable but may share a database initially. Kafka for async integration with channel management.

| Driver | Assessment |
|--------|------------|
| CON-6 (Cloud-Native) | ✅ Strong — multiple services, containerizable, supports independent scaling of pricing vs. management |
| CON-4 (2-Month MVP) | ✅ Moderate-Strong — 3-5 services is manageable; team knowledge of Java reduces risk |
| QA-6 (Protocol Flexibility) | ✅ Strong — API Gateway as protocol boundary; new protocols plug in at gateway |
| QA-7 (Deployability) | ✅ Strong — each service independently deployable; externalized configuration |
| CRN-2 (Java, Angular, Kafka) | ✅ Strong — Angular SPA, Java services, Kafka for channel management integration |

## Selected Alternative: **Alternative D — Service-Based Architecture with Ports & Adapters**

### Rationale

1. **CON-6 (Cloud-Native):** Multiple services enable independent scaling, containerized deployment, and alignment with cloud-native principles without the full complexity of microservices.

2. **CON-4 (2-Month MVP):** 3-5 services is an achievable scope. The team already knows Java, Angular, and Kafka, reducing ramp-up risk.

3. **QA-6 (Protocol Flexibility):** The API Gateway acts as the protocol boundary. Core services expose technology-agnostic interfaces (ports). Adding gRPC requires a new adapter at the gateway — no core changes.

4. **QA-7 (Deployability):** Each service is independently deployable with externalized configuration per environment.

5. **CRN-2 (Team Expertise):** Direct alignment — Angular for the SPA, Java for backend services, Kafka for asynchronous price publication to the channel management system.

6. **QA-3 (Availability):** Service separation allows the pricing query path to be scaled independently from management functions, supporting the 99.9% uptime SLA.

### Key Design Concepts Applied

| Design Concept | Application |
|----------------|-------------|
| **Service-Based Architecture** | 3-5 independently deployable services aligned with business capabilities |
| **Ports & Adapters (Hexagonal)** | Core business logic behind ports; REST, Kafka, DB as adapters |
| **SPA (Single Page Application)** | Angular frontend as browser client (CON-1) |
| **API Gateway** | Single entry point for both browser and external API consumers; protocol boundary |
| **Event-Driven (Kafka)** | Asynchronous price publication to channel management system |
| **Externalized Configuration** | Environment-specific configuration outside deployable artifacts (QA-7) |