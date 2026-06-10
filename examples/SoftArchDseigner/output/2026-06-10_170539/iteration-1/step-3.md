# ADD Step 3: Choose One or More System Elements to Refine (Iteration 1)

## Greenfield Development Context

This is a greenfield project. The design activity begins with no existing architectural elements. Following ADD 3.0 guidance for greenfield systems, we must first establish the **system context** — identifying all external entities that interact with the system — and then select the system itself as the sole element to decompose.

## Element Selected for Refinement

**System Element: Hotel Pricing System (the system as a whole)**

This is the only available element at this stage. The goal is to decompose it into top-level architectural elements that define the overall structure.

## External Entities (System Context)

Before decomposing, we identify all external actors and systems from the requirements:

| External Entity | Type | Interaction | Source |
|-----------------|------|-------------|--------|
| **Business User** | Human Actor | Logs in, changes prices, queries prices via browser | HPS-1, HPS-2, HPS-3 |
| **Administrator** | Human Actor | Logs in, manages hotels/rates/users via browser | HPS-1, HPS-4, HPS-5, HPS-6 |
| **User Identity Service** | External System (Cloud Provider) | Validates user credentials | HPS-1, CON-2 |
| **Channel Management System** | External System | Receives published price changes | HPS-2, QA-2 |
| **External Systems (Query API Consumers)** | External System(s) | Query hotel prices via API | HPS-3, QA-4 |
| **Web Browser** | Client Platform | Rendering platform for UI (Windows, OSX, Linux) | CON-1 |

## Decomposition Strategy

From the system context and selected drivers (cloud-native, team expertise, protocol flexibility), the decomposition should recognize:

1. **User-facing concerns** vs. **API-facing concerns** — browsers (CON-1) and external API consumers are distinct interaction channels.
2. **Core business logic** — price calculation, hotel/rate management — should be isolated from interaction protocols (QA-6).
3. **Cloud-native** (CON-6) and **team expertise** (CRN-2: Java backend, Angular frontend, Kafka) suggest a service-based decomposition with distinct frontend and backend tiers.
4. **External integrations** (identity service, channel management) require dedicated integration elements.

The system will be refined in Step 4 by selecting appropriate design concepts and in Step 5 by instantiating specific elements.