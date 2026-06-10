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
 * ADD 3.0 (Attribute-Driven Design) prior knowledge embedded in the system prompt.
 */
public final class Add30Knowledge {

	private Add30Knowledge() {
	}

	public static final String CONTENT = """
			## ADD 3.0 Method (Attribute-Driven Design)

			ADD 3.0 is a method for designing software architectures driven by quality attributes
			and functional requirements. Each design iteration follows seven steps:

			### Step 1: Review Inputs
			Review all inputs and determine which requirements will be treated as architectural drivers.
			Inputs include functional requirements, quality attribute scenarios, constraints, and concerns.

			### Step 2: Establish Iteration Goal by Selecting Drivers
			A design cycle typically consists of a series of design iterations, each focused on
			achieving a specific goal. Such a goal usually involves designing to satisfy a subset
			of the drivers identified in Step 1.

			### Step 3: Choose One or More System Elements to Refine
			This is where core design activity begins. Select elements that participate in satisfying
			the specific drivers for this iteration. For greenfield development, start by establishing
			the system context, then select the only available element (the system itself) to decompose.
			For subsequent iterations, refine elements identified in prior iterations.

			### Step 4: Choose One or More Design Concepts to Satisfy the Selected Drivers
			Identify alternative design concepts from the design concept catalog that can achieve
			the iteration goal, and select one alternative.

			### Step 5: Instantiate Architectural Elements, Assign Responsibilities, and Define Interfaces
			Instantiate architectural elements based on the selected design concepts. Assign
			responsibilities to each element and define their interfaces.

			### Step 6: Sketch Views and Perspectives
			Create architectural views to document the design decisions made in this iteration.
			Views must be expressed as Mermaid or PlantUML code blocks.

			### Step 7: Analyze Design and Review Iteration Goal
			Perform analysis of the current design. Review whether the iteration goal has been
			achieved and identify any remaining risks or open issues before proceeding.
			""";

}
