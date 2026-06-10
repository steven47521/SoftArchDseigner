/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.cloud.ai.examples.softarchdesigner.knowledge;

/**
 * Hotel Pricing System case study prior knowledge embedded in the system prompt.
 */
public final class HotelPricingSystemKnowledge {

	private HotelPricingSystemKnowledge() {
	}

	public static final String CONTENT = """
			## Case Study: Hotel Pricing System (Greenfield)

			### Design Purpose
			This project is a greenfield development involving complete replacement of an existing system.
			The design activity aims to make initial decisions supporting construction of the system from scratch.

			### Use Cases

			[HPS-1: Login]
			A user (business user or administrator) provides credentials in the login window. The system
			checks credentials against the user identity service. On success, access is granted. After login,
			users may only query and modify hotels they are authorized for.

			[HPS-2: Change Prices]
			A user selects a specific hotel they are authorized to change prices for, and selects dates for
			base price or fixed price changes. All derived rate prices calculated from base prices are computed
			at this time. The system allows simulation before actual price changes. When prices change, they
			are pushed to the channel management system and can be queried by external systems.

			[HPS-3: Query Prices]
			A user or external system queries prices for a specific hotel via the user interface or query API.

			[HPS-4: Manage Hotels]
			An administrator adds, changes, or modifies hotel information, including tax rates, available
			rates, and room types.

			[HPS-5: Manage Rates]
			An administrator adds, changes, or modifies rates, including business rules for calculating
			different rates.

			[HPS-6: Manage Users]
			An administrator changes permissions for specific users.

			### Quality Attributes
			Format: [ID | Attribute] Scenario | Related Use Case | Importance | Implementation Difficulty

			[QA-1 | Performance] During normal operation, when base prices are changed for a specific hotel
			and date, all rate and room type prices for that hotel are published (ready for query) within
			100 milliseconds. [HPS-2 | High | High]

			[QA-2 | Reliability] A user performs multiple price changes for a given hotel; 100% of price
			changes are successfully published (queryable) and received by the channel management system.
			[HPS-2 | High | High]

			[QA-3 | Availability] Price query uptime SLA must reach 99.9% (outside maintenance windows).
			[All | High | High]

			[QA-4 | Scalability] The system must initially support at least 100,000 price queries per day
			via API, scale to 1,000,000, with average latency increase not exceeding 20%. [HPS-3 | High | High]

			[QA-5 | Security] Users log in via the frontend. Credentials are validated against the user
			identity service. After login, only authorized functions are shown. [All | High | Medium]

			[QA-6 | Modifiability] Support for price query endpoints using protocols other than REST
			(e.g., gRPC) is added without changing core components. [All | Medium | Medium]

			[QA-7 | Deployability] Applications move between non-production environments without code changes.
			[All | Medium | Medium]

			[QA-8 | Monitorability] System operators measure price publication performance and reliability.
			The system provides a mechanism to collect 100% of these metrics as needed. [HPS-2 | Medium | Medium]

			[QA-9 | Testability] 100% of system elements support integration testing independent of
			external systems. [All | Medium | Medium]

			### Architectural Concerns

			[CRN-1] Establish preliminary overall system structure.
			[CRN-2] Leverage team knowledge of Java, Angular framework, and Kafka.
			[CRN-3] Assign work to development team members.
			[CRN-4] Avoid introducing technical debt.
			[CRN-5] Set up continuous deployment infrastructure.

			### Constraints

			[CON-1] Users must interact via web browsers on Windows, OSX, and Linux across different devices.
			[CON-2] Users are managed via cloud provider identity service; resources hosted in the cloud.
			[CON-3] Code must be hosted on a proprietary Git-based platform used by other company projects.
			[CON-4] Initial system version within 6 months; MVP demo to internal stakeholders within 2 months.
			[CON-5] System initially interacts via REST API; may need other protocols later.
			[CON-6] Cloud-native approach should be prioritized when designing the system.

			### Allowed Design Approaches (traceable extensions — cite driver ID when used)

			The following may be used ONLY when justified by a specific driver or constraint above:

			- **Service-based / modular decomposition** — CRN-1, QA-6
			- **Ports and adapters (hexagonal) boundaries** — QA-6, QA-9, CON-5
			- **REST API** — CON-5, HPS-3; **future gRPC** — QA-6
			- **OAuth2 / token-based login via cloud identity service** — CON-2, QA-5, HPS-1
			- **Kafka for asynchronous price publication** — CRN-2, HPS-2, QA-2
			- **Transactional outbox for reliable publication** — QA-2
			- **Read/write separation or caching for query latency** — QA-1, QA-4
			- **Multi-AZ deployment and health checks** — QA-3, CON-6
			- **Metrics collection (e.g. Micrometer/Prometheus-style)** — QA-8
			- **CI/CD pipeline and container deployment** — CRN-5, QA-7, CON-3
			- **Integration testing with stubs/containers** — QA-9
			- **Circuit breaker / retry on external integration** — QA-2, HPS-2
			""";

}
