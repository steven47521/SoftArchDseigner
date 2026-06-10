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
package com.alibaba.cloud.ai.examples.softarchdesigner.output;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Creates a timestamped output folder per chat thread and pre-scaffolds ADD step files.
 */
@Service
public class OutputSessionService {

	private static final Logger log = LoggerFactory.getLogger(OutputSessionService.class);

	private static final DateTimeFormatter SESSION_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss");

	public static final String PENDING_PLACEHOLDER = "__SOFTARCH_PENDING__";

	public static final String GLOBAL_STEP1_FILE = "step-1-review-inputs.md";

	private static final String[] STEP_NAMES = {
			"Review Inputs",
			"Establish Iteration Goal by Selecting Drivers",
			"Choose System Elements to Refine",
			"Choose Design Concepts",
			"Instantiate Elements, Assign Responsibilities, and Define Interfaces",
			"Sketch Views and Perspectives",
			"Analyze Design and Review Iteration Goal"
	};

	private static final String[] ITERATION_GOALS = {
			"Establish overall system structure",
			"Identify structures supporting primary functionality",
			"Address reliability and availability quality attributes",
			"Address development and operations"
	};

	private final Path outputRoot;

	private final Path logsRoot;

	private final ConcurrentHashMap<String, SessionInfo> sessionByThread = new ConcurrentHashMap<>();

	public OutputSessionService(
			@Value("${softarch.output-dir:output}") String outputDirName,
			@Value("${softarch.logs-dir:logs}") String logsDirName) {
		this.outputRoot = Paths.get(outputDirName).toAbsolutePath().normalize();
		this.logsRoot = Paths.get(logsDirName).toAbsolutePath().normalize();
	}

	/**
	 * Returns the session root path relative to the JVM working directory, e.g.
	 * {@code output/2026-06-10_164530}. Creates output + logs folders on first call per thread.
	 */
	public String prepareSession(String threadId) {
		return getSession(threadId).outputPath();
	}

	public SessionInfo getSession(String threadId) {
		String key = threadId != null && !threadId.isBlank() ? threadId : "default";
		return sessionByThread.computeIfAbsent(key, this::createSession);
	}

	public Path getOutputRoot() {
		return outputRoot;
	}

	public Path getLogsRoot() {
		return logsRoot;
	}

	private SessionInfo createSession(String threadId) {
		String timestamp = SESSION_FORMAT.format(LocalDateTime.now());
		Path outputSessionDir = outputRoot.resolve(timestamp);
		Path logsSessionDir = logsRoot.resolve(timestamp);
		try {
			scaffoldSession(outputSessionDir, logsSessionDir, threadId, timestamp);
			String outputPath = toRelativePathString(outputSessionDir);
			String logsPath = toRelativePathString(logsSessionDir);
			log.info("Created session output={}, logs={} for thread {}", outputPath, logsPath, threadId);
			return new SessionInfo(threadId, timestamp, outputPath, logsPath, outputSessionDir, logsSessionDir);
		}
		catch (IOException e) {
			throw new IllegalStateException("Failed to scaffold session under " + outputSessionDir, e);
		}
	}

	public record SessionInfo(
			String threadId,
			String timestamp,
			String outputPath,
			String logsPath,
			Path outputDir,
			Path logsDir) {
	}

	private void scaffoldSession(Path sessionDir, Path logsDir, String threadId, String timestamp) throws IOException {
		Files.createDirectories(sessionDir);
		Files.createDirectories(logsDir);

		Files.writeString(sessionDir.resolve(GLOBAL_STEP1_FILE), buildGlobalStep1Template(), StandardCharsets.UTF_8);

		for (int iteration = 1; iteration <= 4; iteration++) {
			Path iterationDir = sessionDir.resolve("iteration-" + iteration);
			Files.createDirectories(iterationDir);
			for (int step = 2; step <= 7; step++) {
				Path stepFile = iterationDir.resolve("step-" + step + ".md");
				String content = buildIterationStepTemplate(iteration, step);
				Files.writeString(stepFile, content, StandardCharsets.UTF_8);
			}
		}

		String logsFolderName = toRelativePathString(logsDir);
		String sessionInfo = """
				# Output Session

				- Created: %s
				- Thread ID: %s
				- Output folder: %s
				- Logs folder: %s

				## Iteration goals

				1. %s
				2. %s
				3. %s
				4. %s

				Global ADD Step 1 (once): `%s`
				Each iteration has pre-created step-2.md through step-7.md files.
				Read a step file, then use edit_file to replace `%s` with the completed content.
				""".formatted(
				timestamp,
				threadId,
				sessionDir.getFileName(),
				logsFolderName,
				ITERATION_GOALS[0],
				ITERATION_GOALS[1],
				ITERATION_GOALS[2],
				ITERATION_GOALS[3],
				GLOBAL_STEP1_FILE,
				PENDING_PLACEHOLDER);

		Files.writeString(sessionDir.resolve("session-info.md"), sessionInfo, StandardCharsets.UTF_8);
	}

	private static String buildGlobalStep1Template() {
		return """
				# ADD Step 1: %s

				%s
				""".formatted(STEP_NAMES[0], PENDING_PLACEHOLDER);
	}

	private static String buildIterationStepTemplate(int iteration, int step) {
		return """
				# ADD Step %d: %s

				## Iteration %d

				%s
				""".formatted(step, STEP_NAMES[step - 1], iteration, PENDING_PLACEHOLDER);
	}

	private static String toRelativePathString(Path absolutePath) {
		Path cwd = Paths.get("").toAbsolutePath().normalize();
		Path normalized = absolutePath.toAbsolutePath().normalize();
		if (normalized.startsWith(cwd)) {
			return cwd.relativize(normalized).toString().replace('\\', '/');
		}
		return normalized.toString().replace('\\', '/');
	}

}
