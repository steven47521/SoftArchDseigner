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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OutputSessionServiceTest {

	@Test
	void createsTimestampedSessionWithAllStepFiles(@TempDir Path tempDir) throws Exception {
		Path outputRoot = tempDir.resolve("output");
		Path logsRoot = tempDir.resolve("logs");
		OutputSessionService service = new OutputSessionService(outputRoot.toString(), logsRoot.toString());

		OutputSessionService.SessionInfo session = service.getSession("thread-abc");
		String timestamp = session.timestamp();
		Path outputSessionDir = outputRoot.resolve(timestamp);
		Path logsSessionDir = logsRoot.resolve(timestamp);

		assertTrue(Files.isDirectory(outputSessionDir));
		assertTrue(Files.isDirectory(logsSessionDir));
		assertTrue(Files.exists(outputSessionDir.resolve("session-info.md")));
		assertTrue(Files.exists(outputSessionDir.resolve(OutputSessionService.GLOBAL_STEP1_FILE)));
		assertFalse(Files.exists(outputSessionDir.resolve("iteration-1/step-1.md")));
		assertTrue(Files.exists(outputSessionDir.resolve("iteration-1/step-2.md")));
		assertTrue(Files.exists(outputSessionDir.resolve("iteration-4/step-7.md")));
		assertTrue(session.outputPath().replace('\\', '/').endsWith("output/" + timestamp));
		assertTrue(session.logsPath().replace('\\', '/').endsWith("logs/" + timestamp));

		String globalStep1 = Files.readString(outputSessionDir.resolve(OutputSessionService.GLOBAL_STEP1_FILE));
		assertTrue(globalStep1.contains("ADD Step 1"));
		assertTrue(globalStep1.contains(OutputSessionService.PENDING_PLACEHOLDER));

		// Same thread reuses session
		assertEquals(session.outputPath(), service.prepareSession("thread-abc"));
	}

}
