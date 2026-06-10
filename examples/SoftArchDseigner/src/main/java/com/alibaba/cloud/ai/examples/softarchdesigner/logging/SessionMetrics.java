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
package com.alibaba.cloud.ai.examples.softarchdesigner.logging;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Aggregated metrics for a single chat session (thread).
 */
public class SessionMetrics {

	private final String threadId;

	private final AtomicInteger llmCallCounter = new AtomicInteger(0);

	private final AtomicInteger turnCounter = new AtomicInteger(0);

	private Instant sessionStart;

	private Instant sessionEnd;

	private String systemPrompt;

	private boolean systemPromptRecorded;

	private int promptTokens;

	private int completionTokens;

	private int totalTokens;

	private int selfReflectionCount;

	private final List<ConversationTurn> conversationTurns = new ArrayList<>();

	private ConversationTurn currentTurn;

	public SessionMetrics(String threadId) {
		this.threadId = threadId;
	}

	public String getThreadId() {
		return threadId;
	}

	public int nextLlmCallId() {
		return llmCallCounter.incrementAndGet();
	}

	public int nextTurnId() {
		return turnCounter.incrementAndGet();
	}

	public Instant getSessionStart() {
		return sessionStart;
	}

	public void setSessionStart(Instant sessionStart) {
		this.sessionStart = sessionStart;
	}

	public Instant getSessionEnd() {
		return sessionEnd;
	}

	public void setSessionEnd(Instant sessionEnd) {
		this.sessionEnd = sessionEnd;
	}

	public String getSystemPrompt() {
		return systemPrompt;
	}

	public void setSystemPrompt(String systemPrompt) {
		this.systemPrompt = systemPrompt;
		this.systemPromptRecorded = systemPrompt != null && !systemPrompt.isBlank();
	}

	public boolean isSystemPromptRecorded() {
		return systemPromptRecorded;
	}

	public int getPromptTokens() {
		return promptTokens;
	}

	public int getCompletionTokens() {
		return completionTokens;
	}

	public int getTotalTokens() {
		return totalTokens;
	}

	public void addTokenUsage(int prompt, int completion, int total) {
		this.promptTokens += prompt;
		this.completionTokens += completion;
		this.totalTokens += total;
	}

	public int getSelfReflectionCount() {
		return selfReflectionCount;
	}

	public void incrementSelfReflectionCount() {
		this.selfReflectionCount++;
	}

	public List<ConversationTurn> getConversationTurns() {
		return conversationTurns;
	}

	public int getLlmCallCount() {
		return llmCallCounter.get();
	}

	public int getHumanInteractionCount() {
		return conversationTurns.size();
	}

	public ConversationTurn getCurrentTurn() {
		return currentTurn;
	}

	public void setCurrentTurn(ConversationTurn currentTurn) {
		this.currentTurn = currentTurn;
	}

	public void addTurn(ConversationTurn turn) {
		this.conversationTurns.add(turn);
	}

	public long getTotalDurationMs() {
		if (sessionStart == null || sessionEnd == null) {
			return 0;
		}
		return sessionEnd.toEpochMilli() - sessionStart.toEpochMilli();
	}

}
