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
package com.alibaba.cloud.ai.examples.softarchdesigner.report;

import com.alibaba.cloud.ai.examples.softarchdesigner.output.OutputSessionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Merges ADD step markdown files and session summary into an assignment report draft.
 */
@Service
public class ReportExportService {

	private static final String[] STEP_TITLES = {
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

	private final ObjectMapper objectMapper = new ObjectMapper();

	public Path exportReport(Path outputSessionDir, Path logsSessionDir) throws IOException {
		Path reportPath = outputSessionDir.resolve("REPORT.md");
		StringBuilder report = new StringBuilder();
		report.append("# Hotel Pricing System — ADD 3.0 Architecture Design Report\n\n");
		report.append("Generated from session output folder: `").append(outputSessionDir.getFileName()).append("`\n\n");

		Path globalStep1 = outputSessionDir.resolve(OutputSessionService.GLOBAL_STEP1_FILE);
		report.append("## ADD Step 1: ").append(STEP_TITLES[0]).append(" (Global)\n\n");
		if (Files.exists(globalStep1)) {
			report.append(Files.readString(globalStep1, StandardCharsets.UTF_8)).append("\n\n");
		}
		else {
			report.append("_Step file not found: ").append(OutputSessionService.GLOBAL_STEP1_FILE).append("_\n\n");
		}

		for (int iteration = 1; iteration <= 4; iteration++) {
			report.append("## Iteration ").append(iteration).append(": ")
					.append(ITERATION_GOALS[iteration - 1]).append("\n\n");
			for (int step = 2; step <= 7; step++) {
				Path stepFile = outputSessionDir.resolve("iteration-" + iteration + "/step-" + step + ".md");
				report.append("### ADD Step ").append(step).append(": ").append(STEP_TITLES[step - 1]).append("\n\n");
				if (Files.exists(stepFile)) {
					report.append(Files.readString(stepFile, StandardCharsets.UTF_8)).append("\n\n");
				}
				else {
					report.append("_Step file not found: ").append(stepFile.getFileName()).append("_\n\n");
				}
			}
		}

		report.append("## Interaction Cost Analysis\n\n");
		report.append(buildCostAnalysisTable(logsSessionDir)).append("\n\n");
		report.append("## Personal Reflection\n\n");
		report.append("_To be completed by each team member (see assignment appendix)._\n");

		Files.writeString(reportPath, report.toString(), StandardCharsets.UTF_8);
		return reportPath;
	}

	private String buildCostAnalysisTable(Path logsSessionDir) throws IOException {
		Path summaryFile = logsSessionDir.resolve("session-summary.json");
		if (!Files.exists(summaryFile)) {
			return "| Field | Value |\n|-------|-------|\n| session-summary.json | not found |\n";
		}
		JsonNode root = objectMapper.readTree(summaryFile.toFile());
		StringBuilder table = new StringBuilder();
		table.append("| Field | Value |\n|-------|-------|\n");
		appendRow(table, "AI paradigm", root.path("aiParadigm").asText("N/A"));
		appendRow(table, "LLM model", root.path("llmModel").asText("N/A"));
		appendRow(table, "Human interactions (turns)", root.path("humanInteractionCount").asText("N/A"));
		appendRow(table, "LLM calls", root.path("llmCallCount").asText("N/A"));
		appendRow(table, "Total tokens (K)", root.path("tokenUsage").path("totalTokensK").asText("N/A"));
		appendRow(table, "Session start", root.path("sessionStart").asText("N/A"));
		appendRow(table, "Session end", root.path("sessionEnd").asText("N/A"));
		table.append("\n| Iteration | Duration (ms) | LLM calls | Tokens |\n");
		table.append("|-----------|---------------|-----------|--------|\n");
		JsonNode perIteration = root.path("perIteration");
		if (perIteration.isArray()) {
			for (JsonNode node : perIteration) {
				table.append("| ").append(node.path("iteration").asText())
						.append(" | ").append(node.path("durationMs").asText())
						.append(" | ").append(node.path("llmCalls").asText())
						.append(" | ").append(node.path("tokens").asText())
						.append(" |\n");
			}
		}
		return table.toString();
	}

	private static void appendRow(StringBuilder table, String field, String value) {
		table.append("| ").append(field).append(" | ").append(value).append(" |\n");
	}

	public Path exportLatestSession(Path outputRoot, Path logsRoot) throws IOException {
		try (Stream<Path> sessions = Files.list(outputRoot)) {
			Path latest = sessions.filter(Files::isDirectory)
					.filter(path -> Files.exists(path.resolve(OutputSessionService.GLOBAL_STEP1_FILE))
							|| Files.exists(path.resolve("iteration-1/step-2.md")))
					.max((a, b) -> a.getFileName().toString().compareTo(b.getFileName().toString()))
					.orElseThrow(() -> new IOException("No output session folders under " + outputRoot));
			Path logsSession = logsRoot.resolve(latest.getFileName());
			return exportReport(latest, logsSession);
		}
	}

}
