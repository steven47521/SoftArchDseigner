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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Runs one scripted ADD iteration on startup when {@code softarch.mock.auto-run=true}.
 */
@Component
@Profile("mock")
@ConditionalOnProperty(name = "softarch.mock.auto-run", havingValue = "true")
public class MockSessionRunner implements ApplicationListener<ApplicationReadyEvent> {

	private static final Logger log = LoggerFactory.getLogger(MockSessionRunner.class);

	private final ReactAgent softArchDesignerAgent;

	private final OutputSessionService outputSessionService;

	private final FakeAddChatModel fakeAddChatModel;

	private final String threadId;

	public MockSessionRunner(
			ReactAgent softArchDesignerAgent,
			OutputSessionService outputSessionService,
			FakeAddChatModel fakeAddChatModel,
			@Value("${softarch.mock.thread-id:mock-integration-thread}") String threadId) {
		this.softArchDesignerAgent = softArchDesignerAgent;
		this.outputSessionService = outputSessionService;
		this.fakeAddChatModel = fakeAddChatModel;
		this.threadId = threadId;
	}

	@Override
	public void onApplicationEvent(ApplicationReadyEvent event) {
		try {
			log.info("Mock profile: running scripted Iteration 1 for thread {}", threadId);
			RunnableConfig config = RunnableConfig.builder().threadId(threadId).build();
			softArchDesignerAgent.invoke(
					"Execute ADD Iteration 1: Establish overall system structure",
					config);
			printVerificationReport();
		}
		catch (Exception e) {
			log.error("Mock integration run failed", e);
		}
	}

	private void printVerificationReport() throws Exception {
		OutputSessionService.SessionInfo session = outputSessionService.getSession(threadId);
		Path outputDir = session.outputDir();
		Path logsDir = session.logsDir();
		Path step1 = outputDir.resolve(OutputSessionService.GLOBAL_STEP1_FILE);
		String step1Content = Files.readString(step1);

		boolean outputOk = Files.isDirectory(outputDir)
				&& Files.exists(outputDir.resolve("session-info.md"))
				&& Files.exists(step1)
				&& !step1Content.contains(OutputSessionService.PENDING_PLACEHOLDER)
				&& step1Content.contains("mock LLM");
		boolean logsOk = Files.isDirectory(logsDir)
				&& Files.exists(logsDir.resolve("conversation-turns.jsonl"))
				&& Files.exists(logsDir.resolve("llm-calls.log"))
				&& Files.exists(logsDir.resolve("session-summary.json"))
				&& Files.exists(logsDir.resolve("system-prompt.txt"));
		boolean timestampAligned = session.outputPath().endsWith(session.timestamp())
				&& session.logsPath().endsWith(session.timestamp());

		System.out.println("\n========================================");
		System.out.println("MOCK INTEGRATION TEST REPORT");
		System.out.println("========================================");
		System.out.println("Thread ID       : " + threadId);
		System.out.println("Timestamp       : " + session.timestamp());
		System.out.println("Output path     : " + session.outputPath());
		System.out.println("Logs path       : " + session.logsPath());
		System.out.println("LLM call count  : " + fakeAddChatModel.getCallCount());
		System.out.println("Output folder OK: " + outputOk);
		System.out.println("Logs folder OK  : " + logsOk);
		System.out.println("Timestamp align : " + timestampAligned);
		System.out.println("Step-1 preview  : " + step1Content.lines().findFirst().orElse("(empty)"));
		System.out.println("OVERALL         : " + (outputOk && logsOk && timestampAligned ? "PASS" : "FAIL"));
		System.out.println("========================================\n");
	}

}
