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

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

/**
 * Appends a lightweight assignment-oriented check section to completed ADD step files.
 */
@Service
public class StepOutputCheckService {

	private static final String CHECK_HEADER = "## Step Output Check";

	private static final Pattern ADD_STEP_TITLE = Pattern.compile("(?m)^#+\\s*ADD Step\\s+\\d+\\b");

	private static final Pattern DRIVER_ID = Pattern.compile("\\b(?:HPS|QA|CRN|CON)-\\d+\\b");

	private static final Pattern DIAGRAM_BLOCK = Pattern.compile("(?i)```\\s*(mermaid|plantuml)\\b");

	private static final Pattern CHECK_SECTION = Pattern.compile("(?s)\\n## Step Output Check\\n.*$");

	public void checkSession(Path outputSessionDir) {
		checkFile(outputSessionDir.resolve(OutputSessionService.GLOBAL_STEP1_FILE), false);
		for (int iteration = 1; iteration <= 4; iteration++) {
			for (int step = 2; step <= 7; step++) {
				Path stepFile = outputSessionDir.resolve("iteration-" + iteration).resolve("step-" + step + ".md");
				checkFile(stepFile, step == 6);
			}
		}
	}

	private void checkFile(Path stepFile, boolean diagramRequired) {
		if (!Files.exists(stepFile)) {
			return;
		}
		try {
			String content = Files.readString(stepFile, StandardCharsets.UTF_8);
			if (content.contains(OutputSessionService.PENDING_PLACEHOLDER)) {
				return;
			}
			String baseContent = stripExistingCheck(content);
			String checkedContent = baseContent.stripTrailing() + "\n\n" + buildCheckSection(baseContent, diagramRequired);
			if (!checkedContent.equals(content)) {
				Files.writeString(stepFile, checkedContent, StandardCharsets.UTF_8);
			}
		}
		catch (IOException e) {
			throw new IllegalStateException("Failed to check ADD step output: " + stepFile, e);
		}
	}

	private String stripExistingCheck(String content) {
		return CHECK_SECTION.matcher(content).replaceFirst("");
	}

	private String buildCheckSection(String content, boolean diagramRequired) {
		boolean hasStepTitle = ADD_STEP_TITLE.matcher(content).find();
		boolean hasSelfReflection = content.contains("## Self-Reflection");
		boolean hasDriverId = DRIVER_ID.matcher(content).find();
		boolean hasDiagram = DIAGRAM_BLOCK.matcher(content).find();

		StringBuilder sb = new StringBuilder();
		sb.append(CHECK_HEADER).append("\n\n");
		sb.append("| Check | Result |\n");
		sb.append("|-------|--------|\n");
		appendRow(sb, "ADD Step title present", hasStepTitle ? "PASS" : "FAIL");
		appendRow(sb, "Self-Reflection present", hasSelfReflection ? "PASS" : "FAIL");
		appendRow(sb, "Driver ID present (HPS/QA/CRN/CON)", hasDriverId ? "PASS" : "FAIL");
		appendRow(sb, "Step 6 diagram block present", diagramRequired ? (hasDiagram ? "PASS" : "FAIL") : "N/A");
		return sb.toString();
	}

	private void appendRow(StringBuilder sb, String check, String result) {
		sb.append("| ").append(check).append(" | ").append(result).append(" |\n");
	}

}
