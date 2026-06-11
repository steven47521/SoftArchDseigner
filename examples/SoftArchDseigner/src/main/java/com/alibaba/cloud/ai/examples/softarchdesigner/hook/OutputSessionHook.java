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

import com.alibaba.cloud.ai.examples.softarchdesigner.interceptor.OutputPathInterceptor;
import com.alibaba.cloud.ai.examples.softarchdesigner.interceptor.SessionPathToolInterceptor;
import com.alibaba.cloud.ai.examples.softarchdesigner.output.OutputSessionService;
import com.alibaba.cloud.ai.examples.softarchdesigner.output.OutputSessionService.SessionInfo;
import com.alibaba.cloud.ai.examples.softarchdesigner.output.StepOutputCheckService;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.hook.AgentHook;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Component
public class OutputSessionHook extends AgentHook {

	private final OutputSessionService outputSessionService;

	private final OutputPathInterceptor outputPathInterceptor;

	private final StepOutputCheckService stepOutputCheckService;

	public OutputSessionHook(OutputSessionService outputSessionService,
			OutputPathInterceptor outputPathInterceptor,
			StepOutputCheckService stepOutputCheckService) {
		this.outputSessionService = outputSessionService;
		this.outputPathInterceptor = outputPathInterceptor;
		this.stepOutputCheckService = stepOutputCheckService;
	}

	@Override
	public String getName() {
		return "output_session_hook";
	}

	@Override
	public CompletableFuture<Map<String, Object>> beforeAgent(OverAllState state, RunnableConfig config) {
		String threadId = config.threadId().orElse("default");
		SessionInfo sessionInfo = outputSessionService.getSession(threadId);
		String sessionPath = sessionInfo.outputPath();
		config.context().put(SessionPathToolInterceptor.CONTEXT_THREAD_ID, threadId);
		config.context().put(SessionPathToolInterceptor.CONTEXT_OUTPUT_PATH, sessionPath);
		config.context().put(SessionPathToolInterceptor.CONTEXT_LOGS_PATH, sessionInfo.logsPath());
		outputPathInterceptor.setCurrentSessionPath(sessionPath);
		// Also publish into agent state so ModelInterceptors on reactor threads can resolve session/thread.
		return CompletableFuture.completedFuture(Map.of(
				SessionPathToolInterceptor.CONTEXT_THREAD_ID, threadId,
				SessionPathToolInterceptor.CONTEXT_OUTPUT_PATH, sessionPath,
				SessionPathToolInterceptor.CONTEXT_LOGS_PATH, sessionInfo.logsPath()));
	}

	@Override
	public CompletableFuture<Map<String, Object>> afterAgent(OverAllState state, RunnableConfig config) {
		try {
			String threadId = config.threadId().orElse("default");
			SessionInfo sessionInfo = outputSessionService.getSession(threadId);
			stepOutputCheckService.checkSession(sessionInfo.outputDir());
		}
		finally {
			outputPathInterceptor.clearCurrentSessionPath();
		}
		return CompletableFuture.completedFuture(Map.of());
	}

}
