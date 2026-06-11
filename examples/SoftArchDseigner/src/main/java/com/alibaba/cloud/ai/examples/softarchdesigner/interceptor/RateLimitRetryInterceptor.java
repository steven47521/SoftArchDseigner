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

import com.alibaba.cloud.ai.graph.agent.interceptor.ModelCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelInterceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelResponse;

import org.springframework.ai.chat.model.ChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Handles PPIO 429 rate limits for streaming model calls.
 * Replaces {@code ModelRetryInterceptor} from agent-framework 1.1.2.2, which
 * does not support {@link Flux} responses and throws ClassCastException.
 */
public class RateLimitRetryInterceptor extends ModelInterceptor {

	private static final Logger log = LoggerFactory.getLogger(RateLimitRetryInterceptor.class);

	private final int maxAttempts;
	private final long initialDelayMs;
	private final long maxDelayMs;
	private final double backoffMultiplier;

	public RateLimitRetryInterceptor(int maxAttempts, long initialDelayMs, long maxDelayMs, double backoffMultiplier) {
		this.maxAttempts = maxAttempts;
		this.initialDelayMs = initialDelayMs;
		this.maxDelayMs = maxDelayMs;
		this.backoffMultiplier = backoffMultiplier;
	}

	public static RateLimitRetryInterceptor defaults() {
		return new RateLimitRetryInterceptor(5, 5000, 60000, 2.0);
	}

	@Override
	public String getName() {
		return "rate_limit_retry";
	}

	@Override
	public ModelResponse interceptModel(ModelRequest request, ModelCallHandler handler) {
		return retrySync(request, handler, 1, initialDelayMs);
	}

	private ModelResponse retrySync(ModelRequest request, ModelCallHandler handler, int attempt, long delayMs) {
		try {
			if (attempt > 1) {
				log.info("Retry model call, attempt {}/{}", attempt, maxAttempts);
			}
			ModelResponse response = handler.call(request);
			Object payload = response.getMessage();
			if (payload instanceof Flux<?> flux) {
				return ModelResponse.of(withStreamingRetry(request, handler, castChatResponseFlux(flux), attempt, delayMs));
			}
			return response;
		}
		catch (Exception e) {
			log.warn("Model call failed (attempt {}/{}): {}", attempt, maxAttempts, e.getMessage());
			String responseBody = extractResponseBody(e);
			if (responseBody != null) {
				log.warn("PPIO response body: {}", responseBody);
			}
			if (attempt >= maxAttempts || !isRetryable(e)) {
				throw new RuntimeException("Model call failed after " + attempt + " attempt(s)", e);
			}
			sleep(delayMs);
			long nextDelay = Math.min((long) (delayMs * backoffMultiplier), maxDelayMs);
			return retrySync(request, handler, attempt + 1, nextDelay);
		}
	}

	private Flux<ChatResponse> withStreamingRetry(ModelRequest request, ModelCallHandler handler,
			Flux<ChatResponse> flux, int attempt, long delayMs) {
		return Flux.defer(() -> {
			AtomicBoolean chunkEmitted = new AtomicBoolean(false);
			return flux
					.doOnNext(chunk -> chunkEmitted.set(true))
					.onErrorResume(error -> {
						if (chunkEmitted.get()) {
							return Flux.error(error);
						}
						return retryStreaming(request, handler, attempt, delayMs, error);
					});
		});
	}

	private Flux<ChatResponse> retryStreaming(ModelRequest request, ModelCallHandler handler,
			int failedAttempt, long delayMs, Throwable error) {
		Exception exception = error instanceof Exception ex ? ex : new RuntimeException(error);
		log.warn("Streaming model call failed (attempt {}/{}): {}", failedAttempt, maxAttempts, exception.getMessage());
		String responseBody = extractResponseBody(exception);
		if (responseBody != null) {
			log.warn("PPIO response body: {}", responseBody);
		}

		if (failedAttempt >= maxAttempts || !isRetryable(exception)) {
			return Flux.error(new RuntimeException("Streaming model call failed after " + failedAttempt + " attempt(s)", exception));
		}

		int nextAttempt = failedAttempt + 1;
		long nextDelay = Math.min((long) (delayMs * backoffMultiplier), maxDelayMs);
		return Flux.<ChatResponse>empty()
				.delaySubscription(Duration.ofMillis(delayMs))
				.thenMany(Flux.defer(() -> {
					log.info("Retry streaming model call, attempt {}/{}", nextAttempt, maxAttempts);
					try {
						ModelResponse retryResponse = handler.call(request);
						Object payload = retryResponse.getMessage();
						if (payload instanceof Flux<?> flux) {
							return withStreamingRetry(request, handler, castChatResponseFlux(flux), nextAttempt, nextDelay);
						}
						return Flux.error(new IllegalStateException(
								"Expected Flux response on retry but got: " + (payload != null ? payload.getClass().getName() : "null")));
					}
					catch (Exception retryError) {
						return retryStreaming(request, handler, nextAttempt, nextDelay, retryError);
					}
				}));
	}

	private static String extractResponseBody(Throwable error) {
		Throwable current = error;
		while (current != null) {
			try {
				var method = current.getClass().getMethod("getResponseBodyAsString");
				Object body = method.invoke(current);
				if (body instanceof String text && !text.isBlank()) {
					return text;
				}
			}
			catch (ReflectiveOperationException ignored) {
				// Not a WebClientResponseException
			}
			current = current.getCause();
		}
		return null;
	}

	static boolean isRetryable(Throwable error) {
		Throwable current = error;
		while (current != null) {
			if (current.getClass().getName().contains("TooManyRequests")) {
				return true;
			}
			String message = current.getMessage();
			if (message != null) {
				String lower = message.toLowerCase();
				if (lower.contains("429")
						|| lower.contains("too many requests")
						|| lower.contains("rate limit")
						|| lower.contains("rate_limit")
						|| lower.contains("timeout")
						|| lower.contains("connection")
						|| lower.contains("i/o error")
						|| lower.contains("socket")) {
					return true;
				}
			}
			current = current.getCause();
		}
		return false;
	}

	private void sleep(long delayMs) {
		if (delayMs <= 0) {
			return;
		}
		try {
			log.info("Retry after {} ms", delayMs);
			Thread.sleep(delayMs);
		}
		catch (InterruptedException ie) {
			Thread.currentThread().interrupt();
			throw new RuntimeException("Retry interrupted", ie);
		}
	}

	@SuppressWarnings("unchecked")
	private Flux<ChatResponse> castChatResponseFlux(Flux<?> flux) {
		return (Flux<ChatResponse>) flux;
	}

}
