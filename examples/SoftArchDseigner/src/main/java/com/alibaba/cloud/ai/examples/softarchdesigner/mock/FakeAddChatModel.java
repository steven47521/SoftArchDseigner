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
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Scripted ChatModel that drives a minimal ADD Iteration 1 flow for integration testing.
 */
public class FakeAddChatModel implements ChatModel {

	private final AtomicInteger callCounter = new AtomicInteger();

	@Override
	public ChatResponse call(Prompt prompt) {
		return buildResponse(prompt);
	}

	@Override
	public Flux<ChatResponse> stream(Prompt prompt) {
		return Flux.just(buildResponse(prompt));
	}

	public int getCallCount() {
		return callCounter.get();
	}

	private ChatResponse buildResponse(Prompt prompt) {
		int call = callCounter.incrementAndGet();
		int toolResponses = countToolResponses(prompt.getInstructions());
		AssistantMessage message = switch (toolResponses) {
			case 0 -> toolCallMessage("call-todos", "write_todos", """
					{"todos":[{"content":"Complete ADD Step 1","status":"in_progress","activeForm":"Completing ADD Step 1"}]}\
					""");
			case 1 -> toolCallMessage("call-read-1", "read_file", """
					{"file_path":"%s"}\
					""".formatted(OutputSessionService.GLOBAL_STEP1_FILE));
			case 2 -> toolCallMessage("call-edit-1", "edit_file", """
					{"file_path":"%s","old_string":"%s","new_string":"# ADD Step 1 completed by mock LLM\\n\\nMock integration test content."}\
					""".formatted(OutputSessionService.GLOBAL_STEP1_FILE, OutputSessionService.PENDING_PLACEHOLDER));
			default -> new AssistantMessage("""
					## Self-Reflection
					- Whether only prior knowledge was used: yes (mock test)
					- Whether current iteration drivers are addressed: yes (mock test)
					- Whether diagrams use correct format: N/A for mock
					- Undeclared assumptions: none

					## Iteration Review
					Iteration 1: PASS (mock integration test)
					""");
		};

		Usage usage = new SimpleUsage(100 + call * 10, 20 + call * 5, 120 + call * 15);
		ChatResponseMetadata metadata = ChatResponseMetadata.builder().usage(usage).build();
		return new ChatResponse(List.of(new Generation(message)), metadata);
	}

	private static AssistantMessage toolCallMessage(String id, String toolName, String arguments) {
		return AssistantMessage.builder()
				.toolCalls(List.of(new AssistantMessage.ToolCall(id, "function", toolName, arguments.strip())))
				.build();
	}

	private static int countToolResponses(List<Message> messages) {
		if (messages == null) {
			return 0;
		}
		return (int) messages.stream().filter(ToolResponseMessage.class::isInstance).count();
	}

	private static final class SimpleUsage implements Usage {

		private final Integer promptTokens;

		private final Integer completionTokens;

		private final Integer totalTokens;

		private SimpleUsage(int promptTokens, int completionTokens, int totalTokens) {
			this.promptTokens = promptTokens;
			this.completionTokens = completionTokens;
			this.totalTokens = totalTokens;
		}

		@Override
		public Integer getPromptTokens() {
			return promptTokens;
		}

		@Override
		public Integer getCompletionTokens() {
			return completionTokens;
		}

		@Override
		public Integer getTotalTokens() {
			return totalTokens;
		}

		@Override
		public Map<String, Object> getNativeUsage() {
			return Map.of(
					"prompt_tokens", promptTokens,
					"completion_tokens", completionTokens,
					"total_tokens", totalTokens);
		}
	}

}
