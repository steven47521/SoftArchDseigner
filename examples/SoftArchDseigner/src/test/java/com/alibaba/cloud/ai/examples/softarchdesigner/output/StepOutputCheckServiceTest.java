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
package com.alibaba.cloud.ai.examples.softarchdesigner.output;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StepOutputCheckServiceTest {

	@Test
	void appendsChecksToCompletedStepFilesOnly(@TempDir Path tempDir) throws Exception {
		Path outputRoot = tempDir.resolve("output");
		Path logsRoot = tempDir.resolve("logs");
		OutputSessionService sessionService = new OutputSessionService(outputRoot.toString(), logsRoot.toString());
		OutputSessionService.SessionInfo session = sessionService.getSession("thread-check");

		Path step2 = session.outputDir().resolve("iteration-1/step-2.md");
		Files.writeString(step2, """
				# ADD Step 2: Establish Iteration Goal by Selecting Drivers

				| Driver ID | Driver Type | Selection Reason | Design Impact |
				|-----------|-------------|------------------|---------------|
				| QA-1 | Quality Attribute | selected | affects decomposition |

				## Self-Reflection (Step 2)
				- Whether only prior knowledge was used: yes
				""");

		new StepOutputCheckService().checkSession(session.outputDir());

		String checked = Files.readString(step2);
		assertTrue(checked.contains("## Step Output Check"));
		assertTrue(checked.contains("| ADD Step title present | PASS |"));
		assertTrue(checked.contains("| Driver ID present (HPS/QA/CRN/CON) | PASS |"));

		String pending = Files.readString(session.outputDir().resolve("iteration-1/step-3.md"));
		assertFalse(pending.contains("## Step Output Check"));
	}

}
