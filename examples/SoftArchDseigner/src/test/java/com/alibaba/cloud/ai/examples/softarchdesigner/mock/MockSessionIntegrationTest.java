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
package com.alibaba.cloud.ai.examples.softarchdesigner.mock;

import com.alibaba.cloud.ai.examples.softarchdesigner.output.OutputSessionService;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("mock")
class MockSessionIntegrationTest {

	private static final Path OUTPUT_ROOT = Paths.get("target/mock-it-output");

	private static final Path LOGS_ROOT = Paths.get("target/mock-it-logs");

	@DynamicPropertySource
	static void overrideDirs(DynamicPropertyRegistry registry) {
		registry.add("softarch.output-dir", () -> OUTPUT_ROOT.toString());
		registry.add("softarch.logs-dir", () -> LOGS_ROOT.toString());
		registry.add("softarch.mock.auto-run", () -> "false");
	}

	@BeforeEach
	void cleanDirs() throws Exception {
		deleteRecursively(OUTPUT_ROOT);
		deleteRecursively(LOGS_ROOT);
	}

	@Autowired
	private ReactAgent softArchDesignerAgent;

	@Autowired
	private OutputSessionService outputSessionService;

	@Autowired
	private FakeAddChatModel fakeAddChatModel;

	@Test
	void createsAlignedOutputAndLogsFolders() throws Exception {
		String threadId = "junit-mock-thread";
		RunnableConfig config = RunnableConfig.builder().threadId(threadId).build();

		softArchDesignerAgent.invoke(
				"Execute ADD Iteration 1: Establish overall system structure",
				config);

		OutputSessionService.SessionInfo session = outputSessionService.getSession(threadId);
		Path outputDir = session.outputDir();
		Path logsDir = session.logsDir();
		Path globalStep1 = outputDir.resolve(OutputSessionService.GLOBAL_STEP1_FILE);
		String step1Content = Files.readString(globalStep1);

		assertTrue(Files.isDirectory(outputDir));
		assertTrue(Files.isDirectory(logsDir));
		assertTrue(Files.exists(outputDir.resolve("session-info.md")));
		assertTrue(Files.exists(logsDir.resolve("conversation-turns.jsonl")));
		assertTrue(Files.exists(logsDir.resolve("llm-calls.log")));
		assertTrue(Files.exists(logsDir.resolve("session-summary.json")));
		assertFalse(step1Content.contains(OutputSessionService.PENDING_PLACEHOLDER));
		assertTrue(step1Content.contains("mock LLM"));
		assertEquals(session.timestamp(), outputDir.getFileName().toString());
		assertEquals(session.timestamp(), logsDir.getFileName().toString());
		assertTrue(fakeAddChatModel.getCallCount() >= 4);

		String llmLog = Files.readString(logsDir.resolve("llm-calls.log"));
		assertTrue(llmLog.contains("LLM_CALL #1"));
		assertTrue(llmLog.contains("[tool_calls only]") || llmLog.contains("Self-Reflection"));
		String summary = Files.readString(logsDir.resolve("session-summary.json"));
		assertTrue(summary.contains("\"llmCallCount\""));
		assertTrue(Integer.parseInt(summary.replaceAll("(?s).*\"llmCallCount\"\\s*:\\s*(\\d+).*", "$1")) >= 4);
	}

	private static void deleteRecursively(Path root) throws Exception {
		if (!Files.exists(root)) {
			return;
		}
		try (var walk = Files.walk(root)) {
			walk.sorted(Comparator.reverseOrder()).forEach(path -> {
				try {
					Files.deleteIfExists(path);
				}
				catch (Exception ignored) {
					// best effort cleanup
				}
			});
		}
	}

}
