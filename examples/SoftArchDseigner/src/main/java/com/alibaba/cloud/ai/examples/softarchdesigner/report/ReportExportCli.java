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

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * CLI entry point: {@code mvn -q exec:java -Dexec.mainClass=... -Dexec.args="output/ts logs/ts"}
 */
public final class ReportExportCli {

	private ReportExportCli() {
	}

	public static void main(String[] args) throws Exception {
		if (args.length < 2) {
			System.err.println("Usage: ReportExportCli <output-session-dir> <logs-session-dir>");
			System.err.println("Example: ReportExportCli output/2026-06-10_170539 logs/2026-06-10_170539");
			System.exit(1);
		}
		Path outputSession = Paths.get(args[0]);
		Path logsSession = Paths.get(args[1]);
		ReportExportService service = new ReportExportService();
		Path report = service.exportReport(outputSession, logsSession);
		System.out.println("Report written to: " + report.toAbsolutePath());
	}

}
