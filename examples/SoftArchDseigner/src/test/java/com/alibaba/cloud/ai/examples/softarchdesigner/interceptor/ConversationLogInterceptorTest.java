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

import com.alibaba.cloud.ai.examples.softarchdesigner.logging.ConversationLogService;
import com.alibaba.cloud.ai.examples.softarchdesigner.output.OutputSessionService;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import reactor.core.publisher.Flux;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ConversationLogInterceptorTest {

	@TempDir
	Path tempDir;

	private ConversationLogService logService;

	private ConversationLogInterceptor interceptor;

	@BeforeEach
	void setUp() {
		Path outputRoot = tempDir.resolve("output");
		Path logsRoot = tempDir.resolve("logs");
		OutputSessionService sessionService = new OutputSessionService(outputRoot.toString(), logsRoot.toString());
		logService = new ConversationLogService(sessionService);
		interceptor = new ConversationLogInterceptor(logService);
		interceptor.setCurrentThreadId("log-test-thread");
		sessionService.getSession("log-test-thread");
	}

	@Test
	void formatsToolOnlyAssistantMessage() {
		AssistantMessage assistant = AssistantMessage.builder()
				.toolCalls(List.of(new AssistantMessage.ToolCall("c1", "function", "read_file",
						"{\"file_path\":\"iteration-1/step-1.md\"}")))
				.build();
		String formatted = ConversationLogInterceptor.formatAssistantMessage(assistant);
		assertTrue(formatted.contains("[tool_calls only]"));
		assertTrue(formatted.contains("read_file"));
	}

	@Test
	void logsStreamingFluxWithToolCallsAndReflection() throws Exception {
		AssistantMessage toolCall = AssistantMessage.builder()
				.toolCalls(List.of(new AssistantMessage.ToolCall("c1", "function", "edit_file", "{}")))
				.build();
		AssistantMessage finalText = new AssistantMessage("""
				## Self-Reflection (Step 1)
				- prior knowledge only: yes
				""");
		Flux<ChatResponse> flux = Flux.just(
				new ChatResponse(List.of(new Generation(toolCall))),
				new ChatResponse(List.of(new Generation(finalText))));

		@SuppressWarnings("unchecked")
		Flux<ChatResponse> resultFlux = (Flux<ChatResponse>) interceptor
				.interceptModel(ModelRequest.builder().build(), request -> ModelResponse.of(flux))
				.getMessage();
		resultFlux.blockLast(Duration.ofSeconds(5));

		String logContent = Files.readString(findLlmCallsLog());
		assertTrue(logContent.contains("[tool_calls only]"));
		assertTrue(logContent.contains("Self-Reflection"));
		assertTrue(logContent.contains("LLM_CALL #1"));
	}

	@Test
	void resolvesThreadIdFromModelRequestContextWithoutThreadLocal() throws Exception {
		interceptor.clearCurrentThreadId();
		String threadId = "context-thread-abc";
		OutputSessionService sessionService = new OutputSessionService(
				tempDir.resolve("output2").toString(), tempDir.resolve("logs2").toString());
		ConversationLogService contextLogService = new ConversationLogService(sessionService);
		ConversationLogInterceptor contextInterceptor = new ConversationLogInterceptor(contextLogService);
		sessionService.getSession(threadId);

		Flux<ChatResponse> flux = Flux.just(new ChatResponse(List.of(new Generation(new AssistantMessage("ok")))));
		ModelRequest request = ModelRequest.builder()
				.context(Map.of(SessionPathToolInterceptor.CONTEXT_THREAD_ID, threadId))
				.build();

		@SuppressWarnings("unchecked")
		Flux<ChatResponse> resultFlux = (Flux<ChatResponse>) contextInterceptor
				.interceptModel(request, req -> ModelResponse.of(flux))
				.getMessage();
		resultFlux.blockLast(Duration.ofSeconds(5));

		try (Stream<Path> walk = Files.walk(tempDir.resolve("logs2"))) {
			long sessionDirs = walk.filter(Files::isDirectory)
					.filter(path -> path.getFileName().toString().matches("\\d{4}-\\d{2}-\\d{2}_\\d{6}"))
					.count();
			assertTrue(sessionDirs == 1, "Expected exactly one logs session directory, got " + sessionDirs);
		}
	}

	@Test
	void streamingLogsRecordsElapsedDuration() throws Exception {
		Flux<ChatResponse> flux = Flux.just(new ChatResponse(List.of(new Generation(new AssistantMessage("hello")))))
				.delayElements(Duration.ofMillis(30));

		@SuppressWarnings("unchecked")
		Flux<ChatResponse> resultFlux = (Flux<ChatResponse>) interceptor
				.interceptModel(ModelRequest.builder().build(), request -> ModelResponse.of(flux))
				.getMessage();
		resultFlux.blockLast(Duration.ofSeconds(5));

		String logContent = Files.readString(findLlmCallsLog());
		assertTrue(logContent.contains("duration="));
	}

	private Path findLlmCallsLog() throws Exception {
		try (Stream<Path> walk = Files.walk(tempDir.resolve("logs"))) {
			return walk.filter(path -> "llm-calls.log".equals(path.getFileName().toString()))
					.findFirst()
					.orElseThrow();
		}
	}

}
