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
import com.alibaba.cloud.ai.examples.softarchdesigner.report.ReportExportService;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

/**
 * Simulates four ADD iterations in one chat thread (mock LLM) to verify session + logging pipeline.
 */
@SpringBootTest
@ActiveProfiles("mock")
class FourIterationMockIntegrationTest {

	private static final Path OUTPUT_ROOT = Paths.get("target/mock-4iter-output");

	private static final Path LOGS_ROOT = Paths.get("target/mock-4iter-logs");

	private static final String[] ITERATION_PROMPTS = {
			"Execute ADD Iteration 1: Establish overall system structure",
			"Execute ADD Iteration 2: Identify structures supporting primary functionality",
			"Execute ADD Iteration 3: Address reliability and availability quality attributes",
			"Execute ADD Iteration 4: Address development and operations"
	};

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
	void fourIterationsShareSessionAndProduceLogs() throws Exception {
		String threadId = "mock-four-iter-thread";
		RunnableConfig config = RunnableConfig.builder().threadId(threadId).build();

		for (String prompt : ITERATION_PROMPTS) {
			softArchDesignerAgent.invoke(prompt, config);
		}

		OutputSessionService.SessionInfo session = outputSessionService.getSession(threadId);
		Path outputDir = session.outputDir();
		Path logsDir = session.logsDir();

		assertEquals(session.timestamp(), outputDir.getFileName().toString());
		assertEquals(session.timestamp(), logsDir.getFileName().toString());
		assertTrue(Files.exists(outputDir.resolve(OutputSessionService.GLOBAL_STEP1_FILE)));
		assertTrue(Files.exists(outputDir.resolve("iteration-4/step-7.md")));
		assertFalse(Files.exists(outputDir.resolve("iteration-4/step-1.md")));
		assertTrue(Files.exists(logsDir.resolve("conversation-turns.jsonl")));

		JsonNode summary = new ObjectMapper().readTree(Files.readString(logsDir.resolve("session-summary.json")));
		assertEquals(4, summary.path("humanInteractionCount").asInt());
		assertTrue(summary.path("llmCallCount").asInt() >= 4);

		String llmLog = Files.readString(logsDir.resolve("llm-calls.log"));
		assertTrue(llmLog.contains("[tool_calls only]"));

		Path report = new ReportExportService().exportReport(outputDir, logsDir);
		assertTrue(Files.exists(report));
		assertTrue(Files.readString(report).contains("Interaction Cost Analysis"));
		assertTrue(fakeAddChatModel.getCallCount() >= 4);
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
					// best effort
				}
			});
		}
	}

}
