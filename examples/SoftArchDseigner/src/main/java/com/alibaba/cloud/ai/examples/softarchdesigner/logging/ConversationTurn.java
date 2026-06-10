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

/**
 * Represents one user-visible conversation turn (one message sent in Chat UI).
 */
public class ConversationTurn {

	private final int turnId;

	private final String threadId;

	private final Instant timestampStart;

	private Instant timestampEnd;

	private final String input;

	private String output;

	private Integer iterationHint;

	private long durationMs;

	private int promptTokens;

	private int completionTokens;

	private int totalTokens;

	private final List<Integer> llmCallIds = new ArrayList<>();

	public ConversationTurn(int turnId, String threadId, Instant timestampStart, String input) {
		this.turnId = turnId;
		this.threadId = threadId;
		this.timestampStart = timestampStart;
		this.input = input;
	}

	public int getTurnId() {
		return turnId;
	}

	public String getThreadId() {
		return threadId;
	}

	public Instant getTimestampStart() {
		return timestampStart;
	}

	public Instant getTimestampEnd() {
		return timestampEnd;
	}

	public void setTimestampEnd(Instant timestampEnd) {
		this.timestampEnd = timestampEnd;
	}

	public String getInput() {
		return input;
	}

	public String getOutput() {
		return output;
	}

	public void setOutput(String output) {
		this.output = output;
	}

	public Integer getIterationHint() {
		return iterationHint;
	}

	public void setIterationHint(Integer iterationHint) {
		this.iterationHint = iterationHint;
	}

	public long getDurationMs() {
		return durationMs;
	}

	public void setDurationMs(long durationMs) {
		this.durationMs = durationMs;
	}

	public int getPromptTokens() {
		return promptTokens;
	}

	public void addTokenUsage(int prompt, int completion, int total) {
		this.promptTokens += prompt;
		this.completionTokens += completion;
		this.totalTokens += total;
	}

	public int getCompletionTokens() {
		return completionTokens;
	}

	public int getTotalTokens() {
		return totalTokens;
	}

	public List<Integer> getLlmCallIds() {
		return llmCallIds;
	}

	public void addLlmCallId(int llmCallId) {
		this.llmCallIds.add(llmCallId);
	}

}
