# ADD Step 2: Establish Iteration Goal by Selecting Drivers (Iteration 1)

## Iteration Goal
**Establish the preliminary overall system structure** — define the high-level decomposition of the Hotel Pricing System, its system context (external actors and systems), and the primary architectural style that will guide further refinement. This iteration answers: _What are the major parts of the system and how do they relate to each other and to external entities?_

## Selected Architectural Drivers

The following drivers are selected as the focus for this iteration because they directly shape the overall structure:

| Priority | Driver | Justification |
|----------|--------|---------------|
| **Primary** | CRN-1 | This is the explicit goal: "Establish preliminary overall system structure." |
| **Primary** | CON-6 | Cloud-native approach must be prioritized when designing the system. This directly influences the deployment topology and service decomposition. |
| **Primary** | CON-1 | Users interact via web browsers on Windows, OSX, Linux. This means a browser-based frontend is required, establishing a clear frontend/backend split. |
| **Secondary** | CON-2 | Users managed via cloud provider identity service; resources hosted in the cloud. Shapes integration with external identity and cloud deployment model. |
| **Secondary** | CRN-2 | Team knowledge of Java, Angular, Kafka. These technology anchors guide component technology choices within the structure. |
| **Secondary** | QA-6 | Support new protocols (gRPC) without changing core components. This drives clean separation between API protocols and core business logic from the start. |
| **Secondary** | QA-7 | Move between non-production environments without code changes. Externalized configuration must be part of the structural design. |
| **Secondary** | CON-4 | MVP demo within 2 months. The initial structure must be simple enough to deliver quickly yet allow incremental growth. |

## Drivers Deferred to Later Iterations

| Driver | Reason for Deferral |
|--------|---------------------|
| QA-1 (Performance) | Internal component-level design; addressed in Iteration 2. |
| QA-2 (Reliability) | Detailed mechanisms needed; addressed in Iteration 3. |
| QA-3 (Availability) | Deployment topology detail; addressed in Iteration 3. |
| QA-4 (Scalability) | Scaling strategy; partially influenced by structure but detailed in Iteration 2. |
| QA-5 (Security) | Authorization mechanisms; addressed when refining internal elements. |
| QA-8 (Monitorability) | Metrics infrastructure; addressed in Iteration 4. |
| QA-9 (Testability) | Integration testing patterns; addressed in Iteration 4. |
| CRN-3, CRN-4, CRN-5 | Development process concerns; addressed in Iteration 4. |
| CON-3, CON-5 | Infrastructure/platform concerns; noted but not structural drivers now. |

## Success Criteria for This Iteration
1. System context is defined (all external actors and systems identified).
2. A high-level decomposition of the system into 3–7 top-level architectural elements is produced.
3. The primary architectural style/pattern is selected and justified.
4. Responsibilities of each top-level element are clearly stated.
5. Technology choices for major elements are aligned with team expertise (CRN-2) and cloud-native (CON-6).