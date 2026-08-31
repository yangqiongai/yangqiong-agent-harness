/*
 * Copyright 2026 yangqiongtech.com
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
package com.yangqiong.agent.harness.model.provider;

import com.yangqiong.agent.harness.model.OllamaChatModel;
import com.yangqiong.agent.harness.core.model.AgentModel;
import com.yangqiong.agent.harness.core.model.spi.AgentModelCreationContext;
import com.yangqiong.agent.harness.core.model.spi.AgentModelProvider;

/**
 * Ollama模型提供者
 * @author yangqiong
 */
public class OllamaModelProvider implements AgentModelProvider {

    /**
     * Ollama默认基础地址
     */
    private static final String DEFAULT_BASE_URL = "http://localhost:11434";

    @Override
    public String providerId() {
        return "ollama";
    }

    @Override
    public boolean supports(String provider, String modelName) {
        // Ollama模型名无固定模式，仅通过provider前缀识别
        return "ollama".equals(provider);
    }

    @Override
    public AgentModel create(String provider, String modelName, AgentModelCreationContext context) {
        String baseUrl = context.getBaseUrl() != null ? context.getBaseUrl() : DEFAULT_BASE_URL;
        Integer timeoutSeconds = context.getTimeoutSeconds() != null ? context.getTimeoutSeconds() : 120;
        // Ollama本地部署无需API Key
        String apiKey = context.getApiKey();
        return new OllamaChatModel(
                baseUrl,
                apiKey,
                modelName,
                null,
                timeoutSeconds
        );
    }
}
