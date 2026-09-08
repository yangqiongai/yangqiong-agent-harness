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

import com.yangqiongai.agent.harness.model.DashScopeChatModel;
import com.yangqiongai.agent.harness.core.model.AgentModel;
import com.yangqiongai.agent.harness.core.model.spi.AgentModelCreationContext;
import com.yangqiongai.agent.harness.core.model.spi.AgentModelProvider;

/**
 * DashScope模型提供者
 * @author yangqiong
 */
public class DashScopeModelProvider implements AgentModelProvider {

    /**
     * DashScope模型名匹配正则（qwen系列）
     */
    private static final Pattern DASHSCOPE_MODEL_PATTERN = Pattern.compile("^(qwen.*|deepseek.*)");

    /**
     * DashScope默认基础地址
     */
    private static final String DEFAULT_BASE_URL = "https://dashscope.aliyuncs.com";

    @Override
    public String providerId() {
        return "dashscope";
    }

    @Override
    public boolean supports(String provider, String modelName) {
        if ("dashscope".equals(provider)) {
            return true;
        }
        return DASHSCOPE_MODEL_PATTERN.matcher(modelName).matches();
    }

    @Override
    public AgentModel create(String provider, String modelName, AgentModelCreationContext context) {
        String baseUrl = context.getBaseUrl() != null ? context.getBaseUrl() : DEFAULT_BASE_URL;
        Integer timeoutSeconds = context.getTimeoutSeconds() != null ? context.getTimeoutSeconds() : 60;
        return new DashScopeChatModel(
                baseUrl,
                context.getApiKey(),
                modelName,
                null,
                timeoutSeconds
        );
    }
}
