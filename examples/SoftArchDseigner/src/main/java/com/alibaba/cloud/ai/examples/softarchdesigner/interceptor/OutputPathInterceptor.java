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
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelInterceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelResponse;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.stereotype.Component;

@Component
public class OutputPathInterceptor extends ModelInterceptor {

	private final ThreadLocal<String> currentSessionPath = new ThreadLocal<>();

	public void setCurrentSessionPath(String sessionPath) {
		currentSessionPath.set(sessionPath);
	}

	public void clearCurrentSessionPath() {
		currentSessionPath.remove();
	}

	@Override
	public String getName() {
		return "output_path_interceptor";
	}

	@Override
	public ModelResponse interceptModel(ModelRequest request, ModelCallHandler handler) {
		String sessionPath = resolveSessionPath(request);
		if (sessionPath == null || sessionPath.isBlank()) {
			return handler.call(request);
		}

		String sessionPrompt = """
				
				# Output Session (this conversation)
				All artifacts for this chat thread MUST be written under: %s/
				Global Step 1 (once): %s/%s
				Per-iteration Steps 2-7: %s/iteration-N/step-M.md (N=1..4, M=2..7).
				NEVER create new folders under output/ — the session folder is already created for you.
				ONLY use paths under this session root (filesystem tools enforce this automatically).
				For each step:
				- read_file: %s/%s or %s/iteration-1/step-2.md (adjust iteration/step numbers)
				- edit_file: same path to replace `%s` with completed content
				Do NOT use write_file on pre-created step files (use edit_file only).
				Do NOT write to paths outside this session folder.
				""".formatted(sessionPath, sessionPath, OutputSessionService.GLOBAL_STEP1_FILE, sessionPath,
				sessionPath, OutputSessionService.GLOBAL_STEP1_FILE, sessionPath,
				OutputSessionService.PENDING_PLACEHOLDER);

		SystemMessage enhancedSystemMessage;
		if (request.getSystemMessage() == null) {
			enhancedSystemMessage = new SystemMessage(sessionPrompt.strip());
		}
		else {
			enhancedSystemMessage = new SystemMessage(request.getSystemMessage().getText() + sessionPrompt);
		}

		ModelRequest enhancedRequest = ModelRequest.builder(request)
				.systemMessage(enhancedSystemMessage)
				.build();
		return handler.call(enhancedRequest);
	}

	private String resolveSessionPath(ModelRequest request) {
		if (request != null && request.getContext() != null) {
			Object fromContext = request.getContext().get(SessionPathToolInterceptor.CONTEXT_OUTPUT_PATH);
			if (fromContext instanceof String path && !path.isBlank()) {
				return path;
			}
		}
		return currentSessionPath.get();
	}

}
