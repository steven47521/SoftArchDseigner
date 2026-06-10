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

import com.alibaba.cloud.ai.examples.softarchdesigner.OpenAiModelConfig;
import com.alibaba.cloud.ai.examples.softarchdesigner.output.OutputSessionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ConversationLogService {

	private static final DateTimeFormatter ISO_FORMATTER =
			DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX").withZone(ZoneId.systemDefault());

	private static final Pattern ITERATION_PATTERN =
			Pattern.compile("(?i)iteration\\s*(\\d+)|迭代\\s*(\\d+)");

	private static final Pattern SELF_REFLECTION_PATTERN =
			Pattern.compile("(?s)##\\s*Self-Reflection[^\\n]*\\s*(.*?)(?=##\\s*|$)");

	private static final Pattern ITERATION_REVIEW_PATTERN =
			Pattern.compile("(?s)##\\s*Iteration Review\\s*(.*?)(?=##\\s*|$)");

	private static final String LLM_CALLS_LOG_FILE = "llm-calls.log";

	private final OutputSessionService outputSessionService;

	private final ObjectMapper objectMapper;

	private final Map<String, SessionMetrics> sessions = new ConcurrentHashMap<>();

	public ConversationLogService(OutputSessionService outputSessionService) {
		this.outputSessionService = outputSessionService;
		this.objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
	}

	public SessionMetrics getOrCreateSession(String threadId) {
		return sessions.computeIfAbsent(threadId, SessionMetrics::new);
	}

	public void recordSystemPrompt(String threadId, String systemPrompt) {
		SessionMetrics session = getOrCreateSession(threadId);
		if (session.isSystemPromptRecorded()) {
			return;
		}
		session.setSystemPrompt(systemPrompt);
		if (session.getSessionStart() == null) {
			session.setSessionStart(Instant.now());
		}
		writeSystemPromptFile(threadId, systemPrompt);
	}

	public ConversationTurn beginTurn(String threadId, String input) {
		SessionMetrics session = getOrCreateSession(threadId);
		if (session.getSessionStart() == null) {
			session.setSessionStart(Instant.now());
		}
		int turnId = session.nextTurnId();
		ConversationTurn turn = new ConversationTurn(turnId, threadId, Instant.now(), input);
		turn.setIterationHint(parseIterationHint(input));
		session.setCurrentTurn(turn);
		return turn;
	}

	public void endTurn(String threadId, String output) {
		SessionMetrics session = getOrCreateSession(threadId);
		ConversationTurn turn = session.getCurrentTurn();
		if (turn == null) {
			return;
		}
		Instant end = Instant.now();
		turn.setTimestampEnd(end);
		turn.setOutput(output != null ? output : "");
		turn.setDurationMs(end.toEpochMilli() - turn.getTimestampStart().toEpochMilli());
		session.addTurn(turn);
		session.setCurrentTurn(null);
		session.setSessionEnd(end);
		writeTurnLog(turn);
		appendTurnJsonLine(turn);
		writeSessionSummary(threadId);
	}

	public void logLlmCall(
			String threadId,
			List<Message> requestMessages,
			String systemPrompt,
			String responseText,
			ChatResponse chatResponse,
			long durationMs) {
		SessionMetrics session = getOrCreateSession(threadId);
		int llmCallId = session.nextLlmCallId();
		ConversationTurn currentTurn = session.getCurrentTurn();
		if (currentTurn != null) {
			currentTurn.addLlmCallId(llmCallId);
		}

		if (systemPrompt != null && !systemPrompt.isBlank()) {
			recordSystemPrompt(threadId, systemPrompt);
		}

		TokenUsage usage = extractTokenUsage(chatResponse);
		session.addTokenUsage(usage.promptTokens, usage.completionTokens, usage.totalTokens);
		if (currentTurn != null) {
			currentTurn.addTokenUsage(usage.promptTokens, usage.completionTokens, usage.totalTokens);
		}

		String selfReflection = extractSection(responseText, SELF_REFLECTION_PATTERN);
		String iterationReview = extractSection(responseText, ITERATION_REVIEW_PATTERN);
		if (selfReflection != null && !selfReflection.isBlank()) {
			session.incrementSelfReflectionCount();
		}

		writeLlmCallLog(threadId, llmCallId, currentTurn, requestMessages, systemPrompt, responseText,
				usage, durationMs, selfReflection, iterationReview, session);
		writeSessionSummary(threadId);
	}

	private void writeSystemPromptFile(String threadId, String systemPrompt) {
		Path file = sessionLogsDir(threadId).resolve("system-prompt.txt");
		if (Files.exists(file)) {
			return;
		}
		try {
			Files.writeString(file, systemPrompt, StandardCharsets.UTF_8);
		}
		catch (IOException e) {
			throw new IllegalStateException("Failed to write system prompt log", e);
		}
	}

	private Path sessionLogsDir(String threadId) {
		return outputSessionService.getSession(threadId).logsDir();
	}

	private void writeTurnLog(ConversationTurn turn) {
		StringBuilder sb = new StringBuilder();
		sb.append("================================================================================\n");
		sb.append(String.format("[%s] TURN #%d | duration=%dms | iteration=%s%n",
				formatInstant(turn.getTimestampStart()),
				turn.getTurnId(),
				turn.getDurationMs(),
				turn.getIterationHint() != null ? turn.getIterationHint() : "N/A"));
		sb.append("--------------------------------------------------------------------------------\n");
		sb.append("--- INPUT ---\n");
		sb.append(nullToEmpty(turn.getInput())).append("\n\n");
		sb.append("--- OUTPUT ---\n");
		sb.append(nullToEmpty(turn.getOutput())).append("\n\n");
		sb.append("--- TURN_TOKEN_USAGE ---\n");
		sb.append(String.format("prompt=%d, completion=%d, total=%d%n",
				turn.getPromptTokens(), turn.getCompletionTokens(), turn.getTotalTokens()));
		sb.append("================================================================================\n\n");
		appendToFile(sessionLogsDir(turn.getThreadId()).resolve("conversation-turns.log"), sb.toString());
	}

	private void appendTurnJsonLine(ConversationTurn turn) {
		ObjectNode node = objectMapper.createObjectNode();
		node.put("turnId", turn.getTurnId());
		node.put("threadId", turn.getThreadId());
		node.put("timestampStart", formatInstant(turn.getTimestampStart()));
		node.put("timestampEnd", formatInstant(turn.getTimestampEnd()));
		node.put("durationMs", turn.getDurationMs());
		node.put("input", nullToEmpty(turn.getInput()));
		node.put("output", nullToEmpty(turn.getOutput()));
		if (turn.getIterationHint() != null) {
			node.put("iterationHint", turn.getIterationHint());
		}
		ArrayNode llmCallIds = node.putArray("llmCallIds");
		turn.getLlmCallIds().forEach(llmCallIds::add);
		ObjectNode tokenUsage = node.putObject("turnTokenUsage");
		tokenUsage.put("promptTokens", turn.getPromptTokens());
		tokenUsage.put("completionTokens", turn.getCompletionTokens());
		tokenUsage.put("totalTokens", turn.getTotalTokens());
		try {
			appendToFile(sessionLogsDir(turn.getThreadId()).resolve("conversation-turns.jsonl"),
					objectMapper.writeValueAsString(node) + System.lineSeparator());
		}
		catch (IOException e) {
			throw new IllegalStateException("Failed to write conversation turn JSON", e);
		}
	}

	private void writeLlmCallLog(
			String threadId,
			int llmCallId,
			ConversationTurn currentTurn,
			List<Message> requestMessages,
			String systemPrompt,
			String responseText,
			TokenUsage usage,
			long durationMs,
			String selfReflection,
			String iterationReview,
			SessionMetrics session) {
		StringBuilder sb = new StringBuilder();
		sb.append("================================================================================\n");
		sb.append(String.format("[%s] LLM_CALL #%d | turnId=%s | threadId=%s | duration=%dms%n",
				formatInstant(Instant.now()),
				llmCallId,
				currentTurn != null ? String.valueOf(currentTurn.getTurnId()) : "N/A",
				threadId,
				durationMs));
		sb.append("--------------------------------------------------------------------------------\n");

		if (systemPrompt != null && !systemPrompt.isBlank() && llmCallId == 1) {
			sb.append("--- SYSTEM_PROMPT (snapshot on first call) ---\n");
			sb.append(systemPrompt).append("\n\n");
		}

		sb.append("--- REQUEST_MESSAGES (full input to model) ---\n");
		sb.append(formatMessages(requestMessages, systemPrompt)).append("\n");

		sb.append("--- RESPONSE (full output from model) ---\n");
		sb.append(nullToEmpty(responseText)).append("\n\n");

		sb.append("--- TOKEN_USAGE ---\n");
		if (usage.available) {
			sb.append(String.format("prompt=%d, completion=%d, total=%d%n",
					usage.promptTokens, usage.completionTokens, usage.totalTokens));
			sb.append(String.format("cumulative_total=%d%n", session.getTotalTokens()));
		}
		else {
			sb.append("token_usage=unavailable\n");
		}

		sb.append("\n--- SELF_REFLECTION (extracted) ---\n");
		sb.append(selfReflection != null && !selfReflection.isBlank() ? selfReflection.trim() : "N/A").append("\n\n");

		sb.append("--- ITERATION_REVIEW (extracted) ---\n");
		sb.append(iterationReview != null && !iterationReview.isBlank() ? iterationReview.trim() : "N/A").append("\n\n");

		sb.append("--- SESSION_METRICS (running) ---\n");
		sb.append(String.format("human_interactions=%d, llm_calls=%d, elapsed_ms=%d%n",
				session.getHumanInteractionCount(),
				session.getLlmCallCount(),
				session.getTotalDurationMs()));
		sb.append("================================================================================\n\n");

		appendToFile(sessionLogsDir(threadId).resolve(LLM_CALLS_LOG_FILE), sb.toString());
	}

	private synchronized void writeSessionSummary(String threadId) {
		SessionMetrics session = getOrCreateSession(threadId);
		ObjectNode root = objectMapper.createObjectNode();
		root.put("aiParadigm", "Single Agent (Sequential Reasoning + Self-Reflection)");
		root.put("llmModel", OpenAiModelConfig.MODEL_NAME);
		root.put("baseUrl", OpenAiModelConfig.BASE_URL);
		root.put("caseStudy", "Hotel Pricing System");
		OutputSessionService.SessionInfo sessionInfo = outputSessionService.getSession(threadId);
		root.put("sessionTimestamp", sessionInfo.timestamp());
		root.put("outputPath", sessionInfo.outputPath());
		root.put("logsPath", sessionInfo.logsPath());
		if (session.getSessionStart() != null) {
			root.put("sessionStart", formatInstant(session.getSessionStart()));
		}
		if (session.getSessionEnd() != null) {
			root.put("sessionEnd", formatInstant(session.getSessionEnd()));
		}
		root.put("totalDurationMs", session.getTotalDurationMs());
		root.put("humanInteractionCount", session.getHumanInteractionCount());
		root.put("llmCallCount", session.getLlmCallCount());
		root.put("selfReflectionCount", session.getSelfReflectionCount());
		root.put("systemPromptRecorded", session.isSystemPromptRecorded());

		ArrayNode turns = root.putArray("conversationTurns");
		for (ConversationTurn turn : session.getConversationTurns()) {
			ObjectNode turnNode = turns.addObject();
			turnNode.put("turnId", turn.getTurnId());
			turnNode.put("input", nullToEmpty(turn.getInput()));
			turnNode.put("output", nullToEmpty(turn.getOutput()));
			turnNode.put("durationMs", turn.getDurationMs());
			turnNode.put("tokens", turn.getTotalTokens());
			if (turn.getIterationHint() != null) {
				turnNode.put("iteration", turn.getIterationHint());
			}
		}

		ObjectNode tokenUsage = root.putObject("tokenUsage");
		tokenUsage.put("promptTokens", session.getPromptTokens());
		tokenUsage.put("completionTokens", session.getCompletionTokens());
		tokenUsage.put("totalTokens", session.getTotalTokens());
		tokenUsage.put("totalTokensK", Math.round(session.getTotalTokens() / 10.0) / 100.0);

		ArrayNode perIteration = root.putArray("perIteration");
		for (int i = 1; i <= 4; i++) {
			final int iteration = i;
			long duration = session.getConversationTurns().stream()
					.filter(t -> t.getIterationHint() != null && t.getIterationHint() == iteration)
					.mapToLong(ConversationTurn::getDurationMs)
					.sum();
			int calls = session.getConversationTurns().stream()
					.filter(t -> t.getIterationHint() != null && t.getIterationHint() == iteration)
					.mapToInt(t -> t.getLlmCallIds().size())
					.sum();
			int tokens = session.getConversationTurns().stream()
					.filter(t -> t.getIterationHint() != null && t.getIterationHint() == iteration)
					.mapToInt(ConversationTurn::getTotalTokens)
					.sum();
			if (duration > 0 || calls > 0 || tokens > 0) {
				ObjectNode iterNode = perIteration.addObject();
				iterNode.put("iteration", iteration);
				iterNode.put("durationMs", duration);
				iterNode.put("llmCalls", calls);
				iterNode.put("tokens", tokens);
			}
		}

		try {
			Files.writeString(sessionLogsDir(threadId).resolve("session-summary.json"),
					objectMapper.writeValueAsString(root),
					StandardCharsets.UTF_8);
		}
		catch (IOException e) {
			throw new IllegalStateException("Failed to write session summary", e);
		}
	}

	private String formatMessages(List<Message> messages, String systemPrompt) {
		StringBuilder sb = new StringBuilder();
		if (systemPrompt != null && !systemPrompt.isBlank()) {
			sb.append("[system] ").append(systemPrompt).append("\n");
		}
		if (messages != null) {
			for (Message message : messages) {
				sb.append("[").append(messageType(message)).append("] ");
				sb.append(message.getText() != null ? message.getText() : message.toString());
				sb.append("\n");
			}
		}
		return sb.toString();
	}

	private String messageType(Message message) {
		if (message instanceof UserMessage) {
			return "user";
		}
		if (message instanceof AssistantMessage) {
			return "assistant";
		}
		if (message instanceof SystemMessage) {
			return "system";
		}
		if (message instanceof ToolResponseMessage) {
			return "tool";
		}
		return message.getClass().getSimpleName().toLowerCase();
	}

	private TokenUsage extractTokenUsage(ChatResponse chatResponse) {
		if (chatResponse == null || chatResponse.getMetadata() == null) {
			return TokenUsage.unavailable();
		}
		Usage usage = chatResponse.getMetadata().getUsage();
		if (usage == null) {
			return TokenUsage.unavailable();
		}
		return new TokenUsage(
				(int) usage.getPromptTokens(),
				(int) usage.getCompletionTokens(),
				(int) usage.getTotalTokens(),
				true);
	}

	private String extractSection(String text, Pattern pattern) {
		if (text == null || text.isBlank()) {
			return null;
		}
		Matcher matcher = pattern.matcher(text);
		if (matcher.find()) {
			return matcher.group(1).trim();
		}
		return null;
	}

	private Integer parseIterationHint(String input) {
		if (input == null) {
			return null;
		}
		Matcher matcher = ITERATION_PATTERN.matcher(input);
		if (matcher.find()) {
			String group = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
			return Integer.parseInt(group);
		}
		return null;
	}

	private void appendToFile(Path file, String content) {
		try {
			Files.writeString(file, content, StandardCharsets.UTF_8,
					StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		}
		catch (IOException e) {
			throw new IllegalStateException("Failed to append to log file: " + file, e);
		}
	}

	private String formatInstant(Instant instant) {
		return instant != null ? ISO_FORMATTER.format(instant) : "N/A";
	}

	private String nullToEmpty(String value) {
		return value != null ? value : "";
	}

	private record TokenUsage(int promptTokens, int completionTokens, int totalTokens, boolean available) {

		static TokenUsage unavailable() {
			return new TokenUsage(0, 0, 0, false);
		}
	}

}
