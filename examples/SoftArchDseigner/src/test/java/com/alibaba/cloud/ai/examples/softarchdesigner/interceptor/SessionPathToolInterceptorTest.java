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
package com.alibaba.cloud.ai.examples.softarchdesigner.interceptor;

import com.alibaba.cloud.ai.examples.softarchdesigner.output.OutputSessionService;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallExecutionContext;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionPathToolInterceptorTest {

	@TempDir
	Path tempDir;

	private SessionPathToolInterceptor interceptor;

	private String sessionPath;

	@BeforeEach
	void setUp() {
		Path outputRoot = tempDir.resolve("output");
		Path logsRoot = tempDir.resolve("logs");
		OutputSessionService service = new OutputSessionService(outputRoot.toString(), logsRoot.toString());
		sessionPath = service.getSession("thread-test").outputPath();
		interceptor = new SessionPathToolInterceptor(service);
	}

	@Test
	void resolvesGlobalStep1Path() {
		assertEquals(sessionPath + "/" + OutputSessionService.GLOBAL_STEP1_FILE,
				interceptor.resolveUnderSession(OutputSessionService.GLOBAL_STEP1_FILE, sessionPath));
	}

	@Test
	void resolvesRelativeIterationPath() {
		assertEquals(sessionPath + "/iteration-1/step-2.md",
				interceptor.resolveUnderSession("iteration-1/step-2.md", sessionPath));
	}

	@Test
	void resolvesOutputPathMissingTimestamp() {
		assertEquals(sessionPath + "/iteration-2/step-3.md",
				interceptor.resolveUnderSession("output/iteration-2/step-3.md", sessionPath));
	}

	@Test
	void rewritesWrongTimestampPrefix() {
		assertEquals(sessionPath + "/iteration-1/step-2.md",
				interceptor.resolveUnderSession("output/2025-01-15_000000/iteration-1/step-2.md", sessionPath));
	}

	@Test
	void keepsCorrectSessionPathUnchanged() {
		String correct = sessionPath + "/iteration-1/step-2.md";
		assertEquals(correct, interceptor.resolveUnderSession(correct, sessionPath));
	}

	@Test
	void rewritesReadFileArguments() {
		var result = interceptor.rewriteArguments("read_file",
				"{\"file_path\":\"output/2025-01-15_000000/iteration-1/step-2.md\"}", sessionPath);
		assertTrue(result.rewritten());
		assertTrue(result.arguments().contains(sessionPath + "/iteration-1/step-2.md"));
	}

	@Test
	void blocksWriteFileOnGlobalStep1() {
		AtomicReference<ToolCallRequest> captured = new AtomicReference<>();
		ToolCallHandler handler = request -> {
			captured.set(request);
			return ToolCallResponse.of(request.getToolCallId(), request.getToolName(), "ok");
		};

		ToolCallRequest request = ToolCallRequest.builder()
				.toolName("write_file")
				.toolCallId("call-global")
				.arguments("{\"file_path\":\"" + OutputSessionService.GLOBAL_STEP1_FILE + "\",\"content\":\"x\"}")
				.executionContext(executionContext("thread-test"))
				.build();

		ToolCallResponse response = interceptor.interceptToolCall(request, handler);

		assertTrue(response.getResult().contains("Use edit_file"));
		assertEquals(null, captured.get());
	}

	@Test
	void blocksWriteFileOnStepFiles() {
		AtomicReference<ToolCallRequest> captured = new AtomicReference<>();
		ToolCallHandler handler = request -> {
			captured.set(request);
			return ToolCallResponse.of(request.getToolCallId(), request.getToolName(), "ok");
		};

		ToolCallRequest request = ToolCallRequest.builder()
				.toolName("write_file")
				.toolCallId("call-1")
				.arguments("{\"file_path\":\"iteration-1/step-2.md\",\"content\":\"x\"}")
				.executionContext(executionContext("thread-test"))
				.build();

		ToolCallResponse response = interceptor.interceptToolCall(request, handler);

		assertTrue(response.getResult().contains("Use edit_file"));
		assertEquals(null, captured.get());
	}

	@Test
	void rewritesLsStringArgument() {
		AtomicReference<ToolCallRequest> captured = new AtomicReference<>();
		ToolCallHandler handler = request -> {
			captured.set(request);
			return ToolCallResponse.of(request.getToolCallId(), request.getToolName(), "ok");
		};

		ToolCallRequest request = ToolCallRequest.builder()
				.toolName("ls")
				.toolCallId("call-2")
				.arguments("\"output/iteration-1\"")
				.executionContext(executionContext("thread-test"))
				.build();

		interceptor.interceptToolCall(request, handler);

		assertEquals("\"" + sessionPath + "/iteration-1" + "\"", captured.get().getArguments());
	}

	private ToolCallExecutionContext executionContext(String threadId) {
		RunnableConfig config = RunnableConfig.builder().threadId(threadId).build();
		config.context().put(SessionPathToolInterceptor.CONTEXT_OUTPUT_PATH, sessionPath);
		return new ToolCallExecutionContext(config, new OverAllState());
	}

}
