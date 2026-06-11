# ADD Step 2: Establish Iteration Goal by Selecting Drivers

## Iteration 1

## Iteration goal
Establish the preliminary overall system structure for the greenfield Hotel Pricing System, including its external context, primary internal building blocks, and key boundaries needed to support browser-based access, secure login, price changes, price queries, and future extension of query protocols.

## Drivers selected for Iteration 1

### Primary drivers
- CRN-1: establish preliminary overall system structure.
- HPS-2: change prices and publish them for query and external consumption.
- HPS-3: query prices via UI and API.
- QA-5: secure login through frontend with validation against the cloud identity service and authorized function visibility.
- CON-1: browser-based access across devices and operating systems.
- CON-2: cloud provider identity service and cloud hosting.
- CON-5: initial REST API with possible future alternative protocols.
- CON-6: prioritize cloud-native design.

### Secondary shaping drivers
- QA-1: publication readiness within 100 ms after base price change.
- QA-2: reliable price publication and delivery to channel management.
- QA-3: 99.9% uptime for price query.
- QA-4: scale price queries from 100,000 to 1,000,000 per day with limited latency growth.
- QA-6: add non-REST query protocols without changing core components.
- CRN-2: leverage Java, Angular, and Kafka team knowledge.
- CRN-3: support assignment of work to team members.
- CRN-4: avoid technical debt through explicit boundaries.

## Explicit scope for this iteration
This iteration will:
- define the system context and major external actors/systems;
- choose an initial decomposition for the Hotel Pricing System;
- define coarse responsibilities and interfaces for major elements;
- sketch initial structural views.

This iteration will not yet fully design:
- detailed reliability tactics such as transactional outbox behavior, retries, or circuit breaker rules, although it may leave placeholders justified by QA-2;
- detailed runtime deployment topology for availability and deployability;
- detailed observability and test mechanisms.

## Decision focus
The structure chosen in this iteration must especially make room for:
- a web frontend using Angular (CRN-2, CON-1);
- secure identity integration with the cloud provider identity service (CON-2, QA-5);
- a core pricing capability supporting price changes and queries (HPS-2, HPS-3);
- external integration with the channel management system and external price consumers;
- protocol isolation so REST is supported now and other query protocols can be added later without changing core components (QA-6, CON-5).

## Self-Reflection (Step 2)
- Whether only prior knowledge was used: yes — the goal and driver selection use only the specified requirements, constraints, concerns, and allowed approaches.
- Whether current iteration drivers are addressed: yes — the selected drivers directly target establishing the overall system structure in Iteration 1.
- Whether diagrams use correct Mermaid/PlantUML format: yes — no diagrams were required in this step.
- Whether any undeclared assumptions were made: none
