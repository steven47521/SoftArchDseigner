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
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallResponse;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolInterceptor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Forces filesystem tool paths to the current chat-thread session folder under {@code output/{timestamp}/}.
 */
@Component
public class SessionPathToolInterceptor extends ToolInterceptor {

	public static final String CONTEXT_THREAD_ID = "softarch.session.threadId";

	public static final String CONTEXT_OUTPUT_PATH = "softarch.session.outputPath";

	public static final String CONTEXT_LOGS_PATH = "softarch.session.logsPath";

	private static final Logger log = LoggerFactory.getLogger(SessionPathToolInterceptor.class);

	private static final Set<String> FILE_PATH_TOOLS = Set.of("read_file", "write_file", "edit_file");

	private static final Set<String> STRING_PATH_TOOLS = Set.of("ls", "glob");

	private static final Pattern OUTPUT_TIMESTAMP_PREFIX = Pattern
			.compile("^output/\\d{4}-\\d{2}-\\d{2}_\\d{6}/(.*)$");

	private static final Pattern OUTPUT_PREFIX = Pattern.compile("^output/(.*)$");

	private static final Pattern ITERATION_STEP_FILE = Pattern.compile("step-[2-7]\\.md$", Pattern.CASE_INSENSITIVE);

	private final OutputSessionService outputSessionService;

	private final ObjectMapper objectMapper = new ObjectMapper();

	public SessionPathToolInterceptor(OutputSessionService outputSessionService) {
		this.outputSessionService = outputSessionService;
	}

	@Override
	public String getName() {
		return "session_path_tool_interceptor";
	}

	@Override
	public ToolCallResponse interceptToolCall(ToolCallRequest request, ToolCallHandler handler) {
		String toolName = request.getToolName();
		if (!isFilesystemTool(toolName)) {
			return handler.call(request);
		}

		Optional<String> sessionPath = resolveSessionPath(request);
		if (sessionPath.isEmpty()) {
			return ToolCallResponse.error(request.getToolCallId(), toolName,
					"No output session bound to this chat thread. Start a new message in the Chat UI first.");
		}

		String sessionRoot = sessionPath.get();
		PathRewriteResult rewriteResult = rewriteArguments(toolName, request.getArguments(), sessionRoot);
		if (rewriteResult.error() != null) {
			return ToolCallResponse.error(request.getToolCallId(), toolName, rewriteResult.error());
		}

		if ("write_file".equals(toolName) && rewriteResult.resolvedPath() != null
				&& isProtectedStepFile(rewriteResult.resolvedPath(), sessionRoot)) {
			return ToolCallResponse.error(request.getToolCallId(), toolName,
					"Use edit_file on " + rewriteResult.resolvedPath() + " to replace "
							+ OutputSessionService.PENDING_PLACEHOLDER);
		}

		if (rewriteResult.rewritten()) {
			log.info("Session path rewrite [{}]: {} -> {}", toolName, rewriteResult.originalPath(),
					rewriteResult.resolvedPath());
		}

		ToolCallRequest rewrittenRequest = ToolCallRequest.builder(request)
				.arguments(rewriteResult.arguments())
				.build();
		return handler.call(rewrittenRequest);
	}

	private boolean isFilesystemTool(String toolName) {
		return FILE_PATH_TOOLS.contains(toolName) || STRING_PATH_TOOLS.contains(toolName) || "grep".equals(toolName);
	}

	private Optional<String> resolveSessionPath(ToolCallRequest request) {
		return request.getExecutionContext().flatMap(ctx -> {
			Object fromContext = ctx.config().context().get(CONTEXT_OUTPUT_PATH);
			if (fromContext instanceof String path && !path.isBlank()) {
				return Optional.of(path);
			}
			String threadId = ctx.threadId().orElse("default");
			return Optional.of(outputSessionService.getSession(threadId).outputPath());
		});
	}

	PathRewriteResult rewriteArguments(String toolName, String arguments, String sessionRoot) {
		try {
			if (FILE_PATH_TOOLS.contains(toolName)) {
				return rewriteFilePathTool(arguments, sessionRoot);
			}
			if ("grep".equals(toolName)) {
				return rewriteGrepTool(arguments, sessionRoot);
			}
			if (STRING_PATH_TOOLS.contains(toolName)) {
				return rewriteStringPathTool(arguments, sessionRoot);
			}
			return PathRewriteResult.unchanged(arguments);
		}
		catch (Exception e) {
			return PathRewriteResult.error("Failed to rewrite filesystem path: " + e.getMessage());
		}
	}

	private PathRewriteResult rewriteFilePathTool(String arguments, String sessionRoot) throws Exception {
		JsonNode node = objectMapper.readTree(arguments);
		if (!node.isObject() || !node.has("file_path")) {
			return PathRewriteResult.unchanged(arguments);
		}
		String original = node.get("file_path").asText();
		String resolved = resolveUnderSession(original, sessionRoot);
		if (!isUnderSession(resolved, sessionRoot)) {
			return PathRewriteResult.error("Path must stay under " + sessionRoot + "/. Got: " + original);
		}
		if (resolved.equals(original)) {
			return new PathRewriteResult(arguments, false, original, resolved);
		}
		((ObjectNode) node).put("file_path", resolved);
		return new PathRewriteResult(objectMapper.writeValueAsString(node), true, original, resolved);
	}

	private PathRewriteResult rewriteGrepTool(String arguments, String sessionRoot) throws Exception {
		JsonNode node = objectMapper.readTree(arguments);
		if (!node.isObject() || !node.has("path") || node.get("path").isNull()) {
			return PathRewriteResult.unchanged(arguments);
		}
		String original = node.get("path").asText();
		if (original.isBlank()) {
			return PathRewriteResult.unchanged(arguments);
		}
		String resolved = resolveUnderSession(original, sessionRoot);
		if (!isUnderSession(resolved, sessionRoot)) {
			return PathRewriteResult.error("Path must stay under " + sessionRoot + "/. Got: " + original);
		}
		if (resolved.equals(original)) {
			return new PathRewriteResult(arguments, false, original, resolved);
		}
		((ObjectNode) node).put("path", resolved);
		return new PathRewriteResult(objectMapper.writeValueAsString(node), true, original, resolved);
	}

	private PathRewriteResult rewriteStringPathTool(String arguments, String sessionRoot) throws Exception {
		String original = parseStringArgument(arguments);
		if (original == null || original.isBlank()) {
			return PathRewriteResult.unchanged(arguments);
		}
		String resolved = resolveUnderSession(original, sessionRoot);
		if (!isUnderSession(resolved, sessionRoot)) {
			return PathRewriteResult.error("Path must stay under " + sessionRoot + "/. Got: " + original);
		}
		if (resolved.equals(original)) {
			return new PathRewriteResult(arguments, false, original, resolved);
		}
		return new PathRewriteResult(objectMapper.writeValueAsString(resolved), true, original, resolved);
	}

	String resolveUnderSession(String rawPath, String sessionRoot) {
		String normalized = rawPath.replace('\\', '/').trim();
		while (normalized.startsWith("./")) {
			normalized = normalized.substring(2);
		}

		if (normalized.equals(sessionRoot) || normalized.startsWith(sessionRoot + "/")) {
			return normalized;
		}

		String suffix;
		var timestampMatcher = OUTPUT_TIMESTAMP_PREFIX.matcher(normalized);
		if (timestampMatcher.matches()) {
			suffix = timestampMatcher.group(1);
		}
		else {
			var outputMatcher = OUTPUT_PREFIX.matcher(normalized);
			if (outputMatcher.matches()) {
				suffix = outputMatcher.group(1);
			}
			else {
				suffix = normalized;
			}
		}

		if (suffix.isEmpty()) {
			return sessionRoot;
		}
		return sessionRoot + "/" + suffix;
	}

	private boolean isUnderSession(String path, String sessionRoot) {
		return path.equals(sessionRoot) || path.startsWith(sessionRoot + "/");
	}

	private boolean isProtectedStepFile(String resolvedPath, String sessionRoot) {
		if (resolvedPath == null) {
			return false;
		}
		String normalized = resolvedPath.replace('\\', '/');
		if (normalized.equals(sessionRoot + "/" + OutputSessionService.GLOBAL_STEP1_FILE)) {
			return true;
		}
		return ITERATION_STEP_FILE.matcher(normalized).find();
	}

	private String parseStringArgument(String arguments) throws Exception {
		if (arguments == null || arguments.isBlank()) {
			return null;
		}
		String trimmed = arguments.trim();
		if (trimmed.startsWith("\"")) {
			return objectMapper.readValue(trimmed, String.class);
		}
		return trimmed;
	}

	record PathRewriteResult(String arguments, boolean rewritten, String originalPath, String resolvedPath, String error) {

		static PathRewriteResult unchanged(String arguments) {
			return new PathRewriteResult(arguments, false, null, null, null);
		}

		static PathRewriteResult error(String message) {
			return new PathRewriteResult(null, false, null, null, message);
		}

		PathRewriteResult(String arguments, boolean rewritten, String originalPath, String resolvedPath) {
			this(arguments, rewritten, originalPath, resolvedPath, null);
		}
	}

}
