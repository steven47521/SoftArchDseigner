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
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelInterceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelResponse;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class ConversationLogInterceptor extends ModelInterceptor {

	private final ConversationLogService logService;

	private final ThreadLocal<String> currentThreadId = new ThreadLocal<>();

	public ConversationLogInterceptor(ConversationLogService logService) {
		this.logService = logService;
	}

	public void setCurrentThreadId(String threadId) {
		currentThreadId.set(threadId);
	}

	public void clearCurrentThreadId() {
		currentThreadId.remove();
	}

	@Override
	public String getName() {
		return "conversation_log_interceptor";
	}

	@Override
	public ModelResponse interceptModel(ModelRequest request, ModelCallHandler handler) {
		long startNanos = System.nanoTime();
		String threadId = resolveThreadId(request);
		String systemPrompt = request.getSystemMessage() != null ? request.getSystemMessage().getText() : null;
		List<Message> messages = request.getMessages();

		ModelResponse response = handler.call(request);

		Object messagePayload = response.getMessage();
		if (messagePayload instanceof Flux) {
			Flux<ChatResponse> chatFlux = castChatResponseFlux((Flux<?>) messagePayload);
			AtomicReference<StringBuilder> textBuilder = new AtomicReference<>(new StringBuilder());
			AtomicReference<ChatResponse> lastResponse = new AtomicReference<>();
			AtomicReference<AssistantMessage> lastAssistant = new AtomicReference<>();

			Flux<ChatResponse> loggedFlux = chatFlux.doOnNext(chunk -> {
				if (chunk == null || chunk.getResult() == null) {
					return;
				}
				AssistantMessage output = chunk.getResult().getOutput();
				if (output != null) {
					String text = output.getText();
					if (text != null && !text.isEmpty()) {
						textBuilder.get().append(text);
					}
					if (output.hasToolCalls()) {
						lastAssistant.set(output);
					}
				}
				lastResponse.set(chunk);
			}).doOnComplete(() -> {
				long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
				String responseText = formatResponseText(textBuilder.get().toString(),
						lastResponse.get(), lastAssistant.get());
				logService.logLlmCall(threadId, messages, systemPrompt, responseText,
						lastResponse.get(), durationMs);
			});

			return ModelResponse.of(loggedFlux);
		}

		long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
		ChatResponse chatResponse = response.getChatResponse();
		String responseText = formatResponseText(extractResponseText(messagePayload), chatResponse, null);
		logService.logLlmCall(threadId, messages, systemPrompt, responseText, chatResponse, durationMs);
		return response;
	}

	static String formatResponseText(String accumulatedText, ChatResponse chatResponse,
			AssistantMessage streamingAssistant) {
		if (accumulatedText != null && !accumulatedText.isBlank()) {
			return appendToolCallsSection(accumulatedText, resolveAssistant(chatResponse, streamingAssistant));
		}
		AssistantMessage assistant = resolveAssistant(chatResponse, streamingAssistant);
		if (assistant == null) {
			return "";
		}
		return formatAssistantMessage(assistant);
	}

	private static AssistantMessage resolveAssistant(ChatResponse chatResponse, AssistantMessage streamingAssistant) {
		if (streamingAssistant != null && streamingAssistant.hasToolCalls()) {
			return streamingAssistant;
		}
		if (chatResponse != null && chatResponse.getResult() != null) {
			return chatResponse.getResult().getOutput();
		}
		return streamingAssistant;
	}

	static String formatAssistantMessage(AssistantMessage assistant) {
		StringBuilder sb = new StringBuilder();
		if (assistant.getText() != null && !assistant.getText().isBlank()) {
			sb.append(assistant.getText());
		}
		appendToolCallsSection(sb, assistant);
		return sb.toString();
	}

	private static String appendToolCallsSection(String text, AssistantMessage assistant) {
		StringBuilder sb = new StringBuilder(text != null ? text : "");
		appendToolCallsSection(sb, assistant);
		return sb.toString();
	}

	private static void appendToolCallsSection(StringBuilder sb, AssistantMessage assistant) {
		if (assistant == null || !assistant.hasToolCalls()) {
			return;
		}
		if (sb.length() > 0) {
			sb.append("\n\n");
		}
		sb.append("[tool_calls only]\n");
		for (AssistantMessage.ToolCall toolCall : assistant.getToolCalls()) {
			sb.append("- ").append(toolCall.name()).append(": ").append(toolCall.arguments()).append('\n');
		}
	}

	private String resolveThreadId(ModelRequest request) {
		if (request != null && request.getContext() != null) {
			Object fromContext = request.getContext().get(SessionPathToolInterceptor.CONTEXT_THREAD_ID);
			if (fromContext instanceof String id && !id.isBlank()) {
				return id;
			}
		}
		String threadId = currentThreadId.get();
		return threadId != null ? threadId : "default";
	}

	private String extractResponseText(Object messagePayload) {
		if (messagePayload instanceof AssistantMessage assistantMessage) {
			return formatAssistantMessage(assistantMessage);
		}
		if (messagePayload instanceof Message message) {
			return message.getText() != null ? message.getText() : message.toString();
		}
		return messagePayload != null ? messagePayload.toString() : "";
	}

	@SuppressWarnings("unchecked")
	private Flux<ChatResponse> castChatResponseFlux(Flux<?> flux) {
		return (Flux<ChatResponse>) flux;
	}

}
