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

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportExportServiceTest {

	@Test
	void exportsReportFromExistingSession() throws Exception {
		Path projectDir = Paths.get("").toAbsolutePath().normalize();
		Path outputSession = projectDir.resolve("output/2026-06-10_170539");
		Path logsSession = projectDir.resolve("logs/2026-06-10_170539");
		if (!Files.isDirectory(outputSession) || !Files.isDirectory(logsSession)) {
			return;
		}

		ReportExportService service = new ReportExportService();
		Path reportPath = service.exportReport(outputSession, logsSession);

		assertTrue(Files.exists(reportPath));
		String content = Files.readString(reportPath);
		assertTrue(content.contains("Interaction Cost Analysis"));
		assertTrue(content.contains("ADD Step 1"));
		assertTrue(content.contains("(Global)"));
		assertTrue(content.contains("ADD Step 2"));
		assertTrue(content.contains("humanInteractionCount") || content.contains("Human interactions"));
	}

}
