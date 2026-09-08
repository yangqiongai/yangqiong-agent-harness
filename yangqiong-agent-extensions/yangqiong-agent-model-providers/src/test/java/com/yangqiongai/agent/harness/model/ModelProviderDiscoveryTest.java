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
package com.yangqiongai.agent.harness.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.yangqiongai.agent.harness.core.model.AgentModel;
import com.yangqiongai.agent.harness.core.model.registry.AgentModelRegistry;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 模型提供者SPI自动发现测试
 * <p>
 * 验证META-INF/services注册的五家provider能被AgentModelRegistry.withAutoDiscovery发现并解析模型。
 * </p>
 * @author yangqiong
 */
class ModelProviderDiscoveryTest {

    /**
     * 验证SPI自动发现注册全部五家provider
     */
    @Test
    @DisplayName("withAutoDiscovery发现全部五家内置provider")
    void withAutoDiscovery_shouldRegisterAllBuiltinProviders() {
        AgentModelRegistry registry = AgentModelRegistry.withAutoDiscovery("openai");
        assertThat(registry.getRegisteredProviders())
                .containsExactlyInAnyOrder("openai", "anthropic", "dashscope", "gemini", "ollama");
    }

    /**
     * 验证发现的provider可解析各前缀模型
     */
    @Test
    @DisplayName("自动发现的provider可解析各前缀模型")
    void withAutoDiscovery_shouldResolvePrefixedModels() {
        AgentModelRegistry registry = AgentModelRegistry.withAutoDiscovery("openai");
        assertThat(registry.resolve("openai:gpt-4o")).isNotNull();
        assertThat(registry.resolve("anthropic:claude-3")).isNotNull();
        assertThat(registry.resolve("dashscope:qwen-max")).isNotNull();
        assertThat(registry.resolve("gemini:gemini-pro")).isNotNull();
        assertThat(registry.resolve("ollama:llama3")).isNotNull();
    }
}
