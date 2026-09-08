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

import com.yangqiongai.agent.harness.model.AnthropicChatModel;
import com.yangqiongai.agent.harness.core.model.AgentModel;
import com.yangqiongai.agent.harness.core.model.spi.AgentModelCreationContext;
import com.yangqiongai.agent.harness.core.model.spi.AgentModelProvider;

/**
 * Anthropic模型提供者
 * @author yangqiong
 */
public class AnthropicModelProvider implements AgentModelProvider {

    /**
     * Anthropic模型名匹配正则（claude系列）
     */
    private static final Pattern ANTHROPIC_MODEL_PATTERN = Pattern.compile("^claude.*");

    /**
     * Anthropic默认基础地址
     */
    private static final String DEFAULT_BASE_URL = "https://api.anthropic.com";

    @Override
    public String providerId() {
        return "anthropic";
    }

    @Override
    public boolean supports(String provider, String modelName) {
        if ("anthropic".equals(provider)) {
            return true;
        }
        return ANTHROPIC_MODEL_PATTERN.matcher(modelName).matches();
    }

    @Override
    public AgentModel create(String provider, String modelName, AgentModelCreationContext context) {
        String baseUrl = context.getBaseUrl() != null ? context.getBaseUrl() : DEFAULT_BASE_URL;
        Integer timeoutSeconds = context.getTimeoutSeconds() != null ? context.getTimeoutSeconds() : 120;
        return new AnthropicChatModel(
                baseUrl,
                context.getApiKey(),
                modelName,
                null,
                timeoutSeconds
        );
    }
}
