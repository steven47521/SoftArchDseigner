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
package com.alibaba.cloud.ai.examples.softarchdesigner.hook;

import com.alibaba.cloud.ai.examples.softarchdesigner.interceptor.ConversationLogInterceptor;
import com.alibaba.cloud.ai.examples.softarchdesigner.logging.ConversationLogService;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.hook.AgentHook;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Component
public class ConversationTurnLogHook extends AgentHook {

	private final ConversationLogService logService;

	private final ConversationLogInterceptor logInterceptor;

	public ConversationTurnLogHook(ConversationLogService logService,
			ConversationLogInterceptor logInterceptor) {
		this.logService = logService;
		this.logInterceptor = logInterceptor;
	}

	@Override
	public String getName() {
		return "conversation_turn_log_hook";
	}

	@Override
	public CompletableFuture<Map<String, Object>> beforeAgent(OverAllState state, RunnableConfig config) {
		String threadId = config.threadId().orElse("default");
		logInterceptor.setCurrentThreadId(threadId);
		String input = extractInput(state);
		logService.beginTurn(threadId, input);
		return CompletableFuture.completedFuture(Map.of());
	}

	@Override
	public CompletableFuture<Map<String, Object>> afterAgent(OverAllState state, RunnableConfig config) {
		String threadId = config.threadId().orElse("default");
		String output = extractOutput(state);
		logService.endTurn(threadId, output);
		logInterceptor.clearCurrentThreadId();
		return CompletableFuture.completedFuture(Map.of());
	}

	private String extractInput(OverAllState state) {
		if (state.value("input").isPresent()) {
			return state.value("input").get().toString();
		}
		return extractLastUserMessage(state);
	}

	private String extractOutput(OverAllState state) {
		if (state.value("messages").isPresent()) {
			Object messagesObj = state.value("messages").get();
			if (messagesObj instanceof List) {
				List<?> messages = (List<?>) messagesObj;
				for (int i = messages.size() - 1; i >= 0; i--) {
					Object msg = messages.get(i);
					if (msg instanceof AssistantMessage) {
						AssistantMessage assistantMessage = (AssistantMessage) msg;
						return assistantMessage.getText() != null ? assistantMessage.getText() : "";
					}
				}
			}
		}
		return state.data().entrySet().stream()
				.filter(e -> e.getKey().endsWith("_output"))
				.map(Map.Entry::getValue)
				.filter(v -> v instanceof AssistantMessage)
				.map(v -> ((AssistantMessage) v).getText())
				.findFirst()
				.orElse("");
	}

	private String extractLastUserMessage(OverAllState state) {
		if (state.value("messages").isPresent()) {
			Object messagesObj = state.value("messages").get();
			if (messagesObj instanceof List) {
				List<?> messages = (List<?>) messagesObj;
				for (int i = messages.size() - 1; i >= 0; i--) {
					Object msg = messages.get(i);
					if (msg instanceof UserMessage) {
						UserMessage userMessage = (UserMessage) msg;
						return userMessage.getText() != null ? userMessage.getText() : "";
					}
				}
			}
		}
		return "";
	}

}
