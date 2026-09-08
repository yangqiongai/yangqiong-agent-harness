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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.yangqiongai.agent.harness.core.model.AgentModel;
import com.yangqiongai.agent.harness.core.model.spi.AgentModelCreationContext;

/**
 * 模型提供者单元测试
 * <p>
 * 覆盖OpenAI、DashScope、Ollama、Anthropic四种Provider的providerId、supports、create方法。
 * </p>
 * @author yangqiong
 */
class ModelProvidersTest {

    @Nested
    @DisplayName("OpenAIModelProvider")
    class OpenAIProviderTest {

        private final OpenAIModelProvider provider = new OpenAIModelProvider();

        @Test
        @DisplayName("providerId返回openai")
        void providerId_shouldReturnOpenAI() {
            assertThat(provider.providerId()).isEqualTo("openai");
        }

        @Test
        @DisplayName("显式provider为openai时支持任意模型名")
        void supports_explicitOpenAIProvider_shouldSupportAnyModel() {
            assertThat(provider.supports("openai", "gpt-4o")).isTrue();
            assertThat(provider.supports("openai", "any-model")).isTrue();
        }

        @Test
        @DisplayName("默认provider场景下通过模型名匹配gpt系列")
        void supports_gptModels_shouldMatchPattern() {
            assertThat(provider.supports(null, "gpt-4o")).isTrue();
            assertThat(provider.supports(null, "gpt-4o-mini")).isTrue();
            assertThat(provider.supports(null, "gpt-3.5-turbo")).isTrue();
        }

        @Test
        @DisplayName("默认provider场景下通过模型名匹配o系列")
        void supports_oSeriesModels_shouldMatchPattern() {
            assertThat(provider.supports(null, "o1-mini")).isTrue();
            assertThat(provider.supports(null, "o3-mini")).isTrue();
        }

        @Test
        @DisplayName("默认provider场景下通过模型名匹配chatgpt系列")
        void supports_chatgptModels_shouldMatchPattern() {
            assertThat(provider.supports(null, "chatgpt-4o-latest")).isTrue();
        }

        @Test
        @DisplayName("非OpenAI模型名且provider不匹配时返回false")
        void supports_nonMatchingModel_shouldReturnFalse() {
            assertThat(provider.supports(null, "claude-3")).isFalse();
            assertThat(provider.supports(null, "qwen-max")).isFalse();
            assertThat(provider.supports("anthropic", "claude-3")).isFalse();
        }

        @Test
        @DisplayName("create方法返回非null模型实例")
        void create_shouldReturnNonNullModel() {
            AgentModelCreationContext context = AgentModelCreationContext.builder()
                    .apiKey("sk-test")
                    .baseUrl("https://api.openai.com/v1")
                    .timeoutSeconds(30)
                    .build();
            AgentModel model = provider.create("openai", "gpt-4o", context);
            assertThat(model).isNotNull();
        }

        @Test
        @DisplayName("create方法baseUrl为null时使用默认OpenAI地址")
        void create_nullBaseUrl_shouldUseDefault() {
            AgentModelCreationContext context = AgentModelCreationContext.builder()
                    .apiKey("sk-test")
                    .build();
            AgentModel model = provider.create("openai", "gpt-4o", context);
            assertThat(model).isNotNull();
        }

        @Test
        @DisplayName("create方法timeout为null时使用默认60秒")
        void create_nullTimeout_shouldUseDefault60() {
            AgentModelCreationContext context = AgentModelCreationContext.builder()
                    .apiKey("sk-test")
                    .build();
            AgentModel model = provider.create("openai", "gpt-4o", context);
            assertThat(model).isNotNull();
        }
    }

    @Nested
    @DisplayName("DashScopeModelProvider")
    class DashScopeProviderTest {

        private final DashScopeModelProvider provider = new DashScopeModelProvider();

        @Test
        @DisplayName("providerId返回dashscope")
        void providerId_shouldReturnDashScope() {
            assertThat(provider.providerId()).isEqualTo("dashscope");
        }

        @Test
        @DisplayName("显式provider为dashscope时支持任意模型名")
        void supports_explicitDashScopeProvider_shouldSupportAnyModel() {
            assertThat(provider.supports("dashscope", "qwen-max")).isTrue();
            assertThat(provider.supports("dashscope", "any-model")).isTrue();
        }

        @Test
        @DisplayName("默认provider场景下通过模型名匹配qwen系列")
        void supports_qwenModels_shouldMatchPattern() {
            assertThat(provider.supports(null, "qwen-max")).isTrue();
            assertThat(provider.supports(null, "qwen-turbo")).isTrue();
            assertThat(provider.supports(null, "qwen-plus")).isTrue();
        }

        @Test
        @DisplayName("默认provider场景下通过模型名匹配deepseek系列")
        void supports_deepseekModels_shouldMatchPattern() {
            assertThat(provider.supports(null, "deepseek-chat")).isTrue();
            assertThat(provider.supports(null, "deepseek-v4-flash")).isTrue();
        }

        @Test
        @DisplayName("非DashScope模型名且provider不匹配时返回false")
        void supports_nonMatchingModel_shouldReturnFalse() {
            assertThat(provider.supports(null, "gpt-4o")).isFalse();
            assertThat(provider.supports(null, "claude-3")).isFalse();
            assertThat(provider.supports("openai", "gpt-4o")).isFalse();
        }

        @Test
        @DisplayName("create方法返回非null模型实例")
        void create_shouldReturnNonNullModel() {
            AgentModelCreationContext context = AgentModelCreationContext.builder()
                    .apiKey("sk-dashscope")
                    .baseUrl("https://dashscope.aliyuncs.com")
                    .timeoutSeconds(60)
                    .build();
            AgentModel model = provider.create("dashscope", "qwen-max", context);
            assertThat(model).isNotNull();
        }

        @Test
        @DisplayName("create方法baseUrl为null时使用默认DashScope地址")
        void create_nullBaseUrl_shouldUseDefault() {
            AgentModelCreationContext context = AgentModelCreationContext.builder()
                    .apiKey("sk-test")
                    .build();
            AgentModel model = provider.create("dashscope", "qwen-max", context);
            assertThat(model).isNotNull();
        }
    }

    @Nested
    @DisplayName("OllamaModelProvider")
    class OllamaProviderTest {

        private final OllamaModelProvider provider = new OllamaModelProvider();

        @Test
        @DisplayName("providerId返回ollama")
        void providerId_shouldReturnOllama() {
            assertThat(provider.providerId()).isEqualTo("ollama");
        }

        @Test
        @DisplayName("显式provider为ollama时支持任意模型名")
        void supports_explicitOllamaProvider_shouldSupportAnyModel() {
            assertThat(provider.supports("ollama", "llama3")).isTrue();
            assertThat(provider.supports("ollama", "mistral")).isTrue();
            assertThat(provider.supports("ollama", "any-model")).isTrue();
        }

        @Test
        @DisplayName("非ollama provider时返回false")
        void supports_nonOllamaProvider_shouldReturnFalse() {
            assertThat(provider.supports(null, "llama3")).isFalse();
            assertThat(provider.supports("openai", "llama3")).isFalse();
        }

        @Test
        @DisplayName("create方法返回非null模型实例")
        void create_shouldReturnNonNullModel() {
            AgentModelCreationContext context = AgentModelCreationContext.builder()
                    .baseUrl("http://localhost:11434")
                    .timeoutSeconds(120)
                    .build();
            AgentModel model = provider.create("ollama", "llama3", context);
            assertThat(model).isNotNull();
        }

        @Test
        @DisplayName("create方法baseUrl为null时使用默认本地地址")
        void create_nullBaseUrl_shouldUseDefaultLocal() {
            AgentModelCreationContext context = AgentModelCreationContext.builder().build();
            AgentModel model = provider.create("ollama", "llama3", context);
            assertThat(model).isNotNull();
        }

        @Test
        @DisplayName("create方法timeout为null时使用默认120秒")
        void create_nullTimeout_shouldUseDefault120() {
            AgentModelCreationContext context = AgentModelCreationContext.builder().build();
            AgentModel model = provider.create("ollama", "llama3", context);
            assertThat(model).isNotNull();
        }
    }

    @Nested
    @DisplayName("AnthropicModelProvider")
    class AnthropicProviderTest {

        private final AnthropicModelProvider provider = new AnthropicModelProvider();

        @Test
        @DisplayName("providerId返回anthropic")
        void providerId_shouldReturnAnthropic() {
            assertThat(provider.providerId()).isEqualTo("anthropic");
        }

        @Test
        @DisplayName("显式provider为anthropic时支持任意模型名")
        void supports_explicitAnthropicProvider_shouldSupportAnyModel() {
            assertThat(provider.supports("anthropic", "claude-3")).isTrue();
            assertThat(provider.supports("anthropic", "any-model")).isTrue();
        }

        @Test
        @DisplayName("默认provider场景下通过模型名匹配claude系列")
        void supports_claudeModels_shouldMatchPattern() {
            assertThat(provider.supports(null, "claude-3-opus")).isTrue();
            assertThat(provider.supports(null, "claude-3-5-sonnet")).isTrue();
            assertThat(provider.supports(null, "claude-3-haiku")).isTrue();
        }

        @Test
        @DisplayName("非Anthropic模型名且provider不匹配时返回false")
        void supports_nonMatchingModel_shouldReturnFalse() {
            assertThat(provider.supports(null, "gpt-4o")).isFalse();
            assertThat(provider.supports(null, "qwen-max")).isFalse();
            assertThat(provider.supports("openai", "gpt-4o")).isFalse();
        }

        @Test
        @DisplayName("create方法返回非null模型实例")
        void create_shouldReturnNonNullModel() {
            AgentModelCreationContext context = AgentModelCreationContext.builder()
                    .apiKey("sk-ant-test")
                    .baseUrl("https://api.anthropic.com")
                    .timeoutSeconds(120)
                    .build();
            AgentModel model = provider.create("anthropic", "claude-3", context);
            assertThat(model).isNotNull();
        }

        @Test
        @DisplayName("create方法baseUrl为null时使用默认Anthropic地址")
        void create_nullBaseUrl_shouldUseDefault() {
            AgentModelCreationContext context = AgentModelCreationContext.builder()
                    .apiKey("sk-ant-test")
                    .build();
            AgentModel model = provider.create("anthropic", "claude-3", context);
            assertThat(model).isNotNull();
        }

        @Test
        @DisplayName("create方法timeout为null时使用默认120秒")
        void create_nullTimeout_shouldUseDefault120() {
            AgentModelCreationContext context = AgentModelCreationContext.builder()
                    .apiKey("sk-ant-test")
                    .build();
            AgentModel model = provider.create("anthropic", "claude-3", context);
            assertThat(model).isNotNull();
        }
    }
}
