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
package com.alibaba.cloud.ai.examples.softarchdesigner;

import com.alibaba.cloud.ai.examples.softarchdesigner.hook.ConversationTurnLogHook;
import com.alibaba.cloud.ai.examples.softarchdesigner.hook.OutputSessionHook;
import com.alibaba.cloud.ai.examples.softarchdesigner.interceptor.ConversationLogInterceptor;
import com.alibaba.cloud.ai.examples.softarchdesigner.interceptor.OutputPathInterceptor;
import com.alibaba.cloud.ai.examples.softarchdesigner.interceptor.RateLimitRetryInterceptor;
import com.alibaba.cloud.ai.examples.softarchdesigner.interceptor.SessionPathToolInterceptor;
import com.alibaba.cloud.ai.examples.softarchdesigner.knowledge.Add30Knowledge;
import com.alibaba.cloud.ai.examples.softarchdesigner.knowledge.HotelPricingSystemKnowledge;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.extension.interceptor.FilesystemInterceptor;
import com.alibaba.cloud.ai.graph.agent.hook.toolcalllimit.ToolCallLimitHook;
import com.alibaba.cloud.ai.graph.agent.interceptor.todolist.TodoListInterceptor;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SoftArchDesignAgent {

	public static final String SYSTEM_PROMPT = buildSystemPrompt();

	@Bean
	public MemorySaver memorySaver() {
		return new MemorySaver();
	}

	@Bean
	public TodoListInterceptor todoListInterceptor() {
		return TodoListInterceptor.builder().build();
	}

	@Bean
	public FilesystemInterceptor filesystemInterceptor() {
		return FilesystemInterceptor.builder()
				.readOnly(false)
				.systemPrompt("""
						Use filesystem tools to persist architecture design artifacts.
						Each chat thread has a timestamped session folder (see # Output Session in system prompt).
						ONLY use paths under that session root — never create new folders under output/.
						Global Step 1: {session}/step-1-review-inputs.md (once per session).
						Per-iteration Steps 2-7: {session}/iteration-N/step-M.md (M=2..7).
						Pre-created step files exist — read_file then edit_file to replace __SOFTARCH_PENDING__.
						NEVER use write_file on step-1-review-inputs.md or iteration step-2.md through step-7.md.
						Store Mermaid and PlantUML diagrams inside the step markdown files or as separate .md files in the iteration folder.
						""")
				.build();
	}

	@Bean
	public RateLimitRetryInterceptor rateLimitRetryInterceptor() {
		return RateLimitRetryInterceptor.defaults();
	}

	@Bean
	public ToolCallLimitHook toolCallLimitHook() {
		return ToolCallLimitHook.builder()
				.runLimit(60)
				.build();
	}

	@Bean
	public ReactAgent softArchDesignerAgent(
			ChatModel chatModel,
			TodoListInterceptor todoListInterceptor,
			FilesystemInterceptor filesystemInterceptor,
			ConversationLogInterceptor conversationLogInterceptor,
			OutputPathInterceptor outputPathInterceptor,
			SessionPathToolInterceptor sessionPathToolInterceptor,
			ConversationTurnLogHook conversationTurnLogHook,
			OutputSessionHook outputSessionHook,
			RateLimitRetryInterceptor rateLimitRetryInterceptor,
			ToolCallLimitHook toolCallLimitHook,
			MemorySaver memorySaver) {
		return ReactAgent.builder()
				.name("SoftArchDesigner")
				.model(chatModel)
				.systemPrompt(SYSTEM_PROMPT)
				.enableLogging(true)
				.interceptors(outputPathInterceptor, sessionPathToolInterceptor, todoListInterceptor,
						filesystemInterceptor, conversationLogInterceptor, rateLimitRetryInterceptor)
				.hooks(outputSessionHook, conversationTurnLogHook, toolCallLimitHook)
				.saver(memorySaver)
				.build();
	}

	private static String buildSystemPrompt() {
		String header = """
				# Role
				You are a professional software architect performing architecture design using the ADD 3.0 method.
				You operate as a single agent using sequential reasoning and self-reflection.
				You design the Hotel Pricing System case study following the iteration plan below.

				# Prior Knowledge (ONLY source of domain information)

				""";
		String footer = """
				
				# Hard Constraints (MUST follow)
				1. Use ONLY the prior knowledge above. Do NOT introduce external domain knowledge.
				2. Do NOT reinterpret, extend, or enhance requirements beyond the prior knowledge.
				3. All decision rules MUST be explicitly derived from the prior knowledge and architectural drivers.
				4. Do NOT use few-shot examples or reference external design patterns not stated in prior knowledge.
				5. When selecting patterns or third-party products, they MUST appear in prior knowledge OR be directly required by CRN-2 (Java, Angular, Kafka) or CON-2 (cloud identity service). Cite the ID in your rationale.
				6. All architectural views MUST be expressed as mermaid or plantuml fenced code blocks.
				7. Write outputs in English.

				# Iteration Plan (execute one iteration per user request)
				- Iteration 1: Establish overall system structure
				- Iteration 2: Identify structures supporting primary functionality
				- Iteration 3: Address reliability and availability quality attributes
				- Iteration 4: Address development and operations

				# ADD Workflow (assignment template — Sequential Reasoning)

				## Global Step 1 (once per session)
				- File: step-1-review-inputs.md at session root (pre-created).
				- When the user requests Iteration 1: if step-1-review-inputs.md still contains __SOFTARCH_PENDING__,
				  complete ADD Step 1 first (read_file + edit_file), then proceed to Iteration 1 Steps 2–7.
				- Iterations 2–4: do NOT repeat Step 1. If the user asks, state it is already done and start at Step 2.

				## Per-iteration Steps 2–7
				- Iteration 1: after global Step 1, execute Steps 2–7 → iteration-1/step-2.md … step-7.md.
				- Iterations 2–4: execute Steps 2–7 only → iteration-N/step-2.md … step-7.md.
				- For each step, output a clearly labeled section: "## ADD Step N: <step name>".
				- Include relevant Mermaid or PlantUML diagrams in Step 6.

				# File Handling (IMPORTANT)
				- Each chat thread gets its own timestamped folder (see # Output Session — use ONLY that path).
				- NEVER create new folders under output/; the session folder is pre-created on your first message.
				- Global: step-1-review-inputs.md. Per iteration: step-2.md through step-7.md only (no per-iteration step-1.md).
				- For each step: read_file the step file, then edit_file to replace __SOFTARCH_PENDING__ with completed content.
				- NEVER use write_file on pre-created step files (filesystem tools block this).
				- Minimize tool calls: one read + one edit per step; avoid batch-reading all steps upfront.

				# Self-Reflection Protocol (REQUIRED after each ADD step)
				After completing each ADD step:
				1. Persist content via read_file + edit_file on the step file (global Step 1 or iteration-N/step-M.md).
				2. Append at the END of that same step file a section:
				   ## Self-Reflection (Step M)
				   with the four checklist items below (yes/no + brief explanation).
				3. In the chat, output a short line: "Completed ADD Step M — see <file> for content and self-reflection."
				Then proceed to the next step. Do NOT skip self-reflection for global Step 1 or iteration Steps 2–7.
				Checklist (repeat in each step file reflection section):
				- Whether only prior knowledge was used (yes/no and explanation)
				- Whether current iteration drivers are addressed (yes/no and explanation)
				- Whether diagrams use correct Mermaid/PlantUML format (yes/no and explanation)
				- Whether any undeclared assumptions were made (list or "none")
				When naming a design pattern or technology, cite the prior-knowledge ID it supports (e.g. QA-2, CRN-2, CON-6).

				# Iteration Review (REQUIRED at end of each iteration)
				After completing Step 7 for the current iteration, output a section:
				## Iteration Review
				State whether the iteration goal is achieved (PASS/FAIL) with justification.
				List remaining risks or open issues.

				# Task Planning
				- Iteration 1 (first turn): write_todos for global Step 1 (if pending) + Steps 2–7 (7 or 6 items).
				- Iterations 2–4: write_todos for Steps 2–7 only (6 items).
				Mark todos as completed immediately after each step finishes.
				Persist each step with read_file + edit_file only (no write_file on step files).
				""";
		return header + Add30Knowledge.CONTENT + "\n\n" + HotelPricingSystemKnowledge.CONTENT + footer;
	}

}
