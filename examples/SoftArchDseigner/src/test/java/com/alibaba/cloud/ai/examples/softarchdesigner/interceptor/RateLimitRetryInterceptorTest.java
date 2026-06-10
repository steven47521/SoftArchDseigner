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

import com.alibaba.cloud.ai.graph.agent.interceptor.ModelRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelResponse;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateLimitRetryInterceptorTest {

	@Test
	void handlesFluxResponseWithoutClassCastException() {
		RateLimitRetryInterceptor interceptor = RateLimitRetryInterceptor.defaults();
		Flux<ChatResponse> flux = Flux.just(new ChatResponse(List.of(new Generation(new AssistantMessage("ok")))));

		ModelResponse response = interceptor.interceptModel(ModelRequest.builder().build(),
				request -> ModelResponse.of(flux));

		assertInstanceOf(Flux.class, response.getMessage());
	}

	@Test
	void retriesStreaming429() {
		RateLimitRetryInterceptor interceptor = new RateLimitRetryInterceptor(3, 10, 100, 2.0);
		AtomicInteger attempts = new AtomicInteger();

		ModelResponse response = interceptor.interceptModel(ModelRequest.builder().build(), request -> {
			int attempt = attempts.incrementAndGet();
			if (attempt == 1) {
				return ModelResponse.of(Flux.error(tooManyRequests()));
			}
			return ModelResponse.of(Flux.just(new ChatResponse(List.of(new Generation(new AssistantMessage("recovered"))))));
		});

		Flux<ChatResponse> flux = (Flux<ChatResponse>) response.getMessage();
		List<ChatResponse> chunks = flux.collectList().block();
		assertEquals(2, attempts.get());
		assertTrue(chunks != null && !chunks.isEmpty());
		assertEquals("recovered", chunks.get(0).getResult().getOutput().getText());
	}

	private WebClientResponseException tooManyRequests() {
		return WebClientResponseException.create(429, "Too Many Requests", null, null, StandardCharsets.UTF_8);
	}

}
