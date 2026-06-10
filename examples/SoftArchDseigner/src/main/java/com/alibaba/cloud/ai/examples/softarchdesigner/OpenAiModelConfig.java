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

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!mock")
public class OpenAiModelConfig {

	public static final String BASE_URL = "https://api.ppio.com/openai";

	public static final String MODEL_NAME = "deepseek/deepseek-v4-pro";

	@Bean
	@Primary
	public ChatModel chatModel(
			@Value("${spring.ai.openai.api-key}") String apiKey,
			@Value("${spring.ai.openai.base-url:" + BASE_URL + "}") String baseUrl,
			@Value("${spring.ai.openai.chat.options.model:" + MODEL_NAME + "}") String model) {
		if (apiKey == null || apiKey.isBlank()) {
			throw new IllegalStateException(
					"spring.ai.openai.api-key is required. Configure it in application.yml.");
		}

		OpenAiApi openAiApi = OpenAiApi.builder()
				.baseUrl(baseUrl)
				.apiKey(apiKey)
				.build();

		return OpenAiChatModel.builder()
				.openAiApi(openAiApi)
				.defaultOptions(OpenAiChatOptions.builder().model(model).build())
				.build();
	}

}
