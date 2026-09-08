/*
 * Copyright 2026 yangqiongai.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.yangqiongai.agent.harness.model.provider;

import java.util.regex.Pattern;

import com.yangqiongai.agent.harness.model.GeminiChatModel;
import com.yangqiongai.agent.harness.core.model.AgentModel;
import com.yangqiongai.agent.harness.core.model.spi.AgentModelCreationContext;
import com.yangqiongai.agent.harness.core.model.spi.AgentModelProvider;

/**
 * Gemini模型提供者
 * @author yangqiong
 */
public class GeminiModelProvider implements AgentModelProvider {

    /**
     * Gemini模型名匹配正则
     */
    private static final Pattern GEMINI_MODEL_PATTERN = Pattern.compile("^(gemini-.*)");

    /**
     * Gemini OpenAI兼容模式默认基础地址
     */
    private static final String DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/openai";

    @Override
    public String providerId() {
        return "gemini";
    }

    @Override
    public boolean supports(String provider, String modelName) {
        if ("gemini".equals(provider)) {
            return true;
        }
        // 默认provider场景下通过模型名匹配
        return GEMINI_MODEL_PATTERN.matcher(modelName).matches();
    }

    @Override
    public AgentModel create(String provider, String modelName, AgentModelCreationContext context) {
        String baseUrl = context.getBaseUrl() != null ? context.getBaseUrl() : DEFAULT_BASE_URL;
        Integer timeoutSeconds = context.getTimeoutSeconds() != null ? context.getTimeoutSeconds() : 60;
        return new GeminiChatModel(
                baseUrl,
                context.getApiKey(),
                modelName,
                null,
                timeoutSeconds
        );
    }
}
