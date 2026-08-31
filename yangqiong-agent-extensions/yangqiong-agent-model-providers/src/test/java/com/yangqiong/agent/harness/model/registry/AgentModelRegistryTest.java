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
package com.yangqiong.agent.harness.model.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import com.yangqiong.agent.harness.core.model.spi.AgentModelProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.yangqiong.agent.harness.model.provider.AnthropicModelProvider;
import com.yangqiong.agent.harness.model.provider.DashScopeModelProvider;
import com.yangqiong.agent.harness.model.provider.OllamaModelProvider;
import com.yangqiong.agent.harness.model.provider.OpenAIModelProvider;
import com.yangqiong.agent.harness.core.model.AgentModel;
import com.yangqiong.agent.harness.core.model.registry.AgentModelRegistry;
import com.yangqiong.agent.harness.core.model.registry.ModelIdParser;
import com.yangqiong.agent.harness.core.model.spi.AgentModelCreationContext;

/**
 * 模型注册表与ID解析器单元测试
 * <p>
 * 覆盖AgentModelRegistry的provider注册、模型解析、缓存策略，
 * 以及ModelIdParser的provider前缀解析逻辑。
 * </p>
 * @author yangqiong
 */
class AgentModelRegistryTest {

    @Nested
    @DisplayName("ModelIdParser")
    class ModelIdParserTest {

        @Test
        @DisplayName("解析openai:前缀")
        void parse_openaiPrefix_shouldReturnOpenAIProvider() {
            ModelIdParser.ParsedModelId parsed = ModelIdParser.parse("openai:gpt-4o", "openai");
            assertThat(parsed.provider()).isEqualTo("openai");
            assertThat(parsed.modelName()).isEqualTo("gpt-4o");
        }

        @Test
        @DisplayName("解析dashscope:前缀")
        void parse_dashscopePrefix_shouldReturnDashScopeProvider() {
            ModelIdParser.ParsedModelId parsed = ModelIdParser.parse("dashscope:qwen-max", "openai");
            assertThat(parsed.provider()).isEqualTo("dashscope");
            assertThat(parsed.modelName()).isEqualTo("qwen-max");
        }

        @Test
        @DisplayName("解析ollama:前缀")
        void parse_ollamaPrefix_shouldReturnOllamaProvider() {
            ModelIdParser.ParsedModelId parsed = ModelIdParser.parse("ollama:llama3", "openai");
            assertThat(parsed.provider()).isEqualTo("ollama");
            assertThat(parsed.modelName()).isEqualTo("llama3");
        }

        @Test
        @DisplayName("解析anthropic:前缀")
        void parse_anthropicPrefix_shouldReturnAnthropicProvider() {
            ModelIdParser.ParsedModelId parsed = ModelIdParser.parse("anthropic:claude-3", "openai");
            assertThat(parsed.provider()).isEqualTo("anthropic");
            assertThat(parsed.modelName()).isEqualTo("claude-3");
        }

        @Test
        @DisplayName("无前缀时使用默认provider")
        void parse_noPrefix_shouldUseDefaultProvider() {
            ModelIdParser.ParsedModelId parsed = ModelIdParser.parse("gpt-4o", "openai");
            assertThat(parsed.provider()).isEqualTo("openai");
            assertThat(parsed.modelName()).isEqualTo("gpt-4o");
        }

        @Test
        @DisplayName("未知provider前缀时整个ID作为modelName")
        void parse_unknownPrefix_shouldUseDefaultProviderAndFullIdAsModelName() {
            ModelIdParser.ParsedModelId parsed = ModelIdParser.parse("unknown:model", "openai");
            assertThat(parsed.provider()).isEqualTo("openai");
            assertThat(parsed.modelName()).isEqualTo("unknown:model");
        }

        @Test
        @DisplayName("冒号开头时使用默认provider")
        void parse_leadingColon_shouldUseDefaultProvider() {
            ModelIdParser.ParsedModelId parsed = ModelIdParser.parse(":model", "openai");
            assertThat(parsed.provider()).isEqualTo("openai");
            assertThat(parsed.modelName()).isEqualTo("model");
        }

        @Test
        @DisplayName("defaultProvider为null时使用openai")
        void parse_nullDefaultProvider_shouldUseOpenAI() {
            ModelIdParser.ParsedModelId parsed = ModelIdParser.parse("gpt-4o", null);
            assertThat(parsed.provider()).isEqualTo("openai");
        }

        @Test
        @DisplayName("defaultProvider为空字符串时使用openai")
        void parse_blankDefaultProvider_shouldUseOpenAI() {
            ModelIdParser.ParsedModelId parsed = ModelIdParser.parse("gpt-4o", "");
            assertThat(parsed.provider()).isEqualTo("openai");
        }

        @Test
        @DisplayName("modelId为null抛出异常")
        void parse_nullModelId_shouldThrowException() {
            assertThatThrownBy(() -> ModelIdParser.parse(null, "openai"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("不能为空");
        }

        @Test
        @DisplayName("modelId为空字符串抛出异常")
        void parse_blankModelId_shouldThrowException() {
            assertThatThrownBy(() -> ModelIdParser.parse("", "openai"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("不能为空");
        }

        @Test
        @DisplayName("冒号后modelName为空抛出异常")
        void parse_emptyModelNameAfterColon_shouldThrowException() {
            assertThatThrownBy(() -> ModelIdParser.parse("openai:", "openai"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("不能为空");
        }

        @Test
        @DisplayName("冒号开头且modelName为空抛出异常")
        void parse_leadingColonWithEmptyName_shouldThrowException() {
            assertThatThrownBy(() -> ModelIdParser.parse(":", "openai"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("不能为空");
        }
    }

    @Nested
    @DisplayName("AgentModelRegistry")
    class RegistryTest {

        private AgentModelRegistry registry;

        @BeforeEach
        void setUp() {
            registry = new AgentModelRegistry("openai");
            registry.registerProvider(new OpenAIModelProvider());
            registry.registerProvider(new DashScopeModelProvider());
            registry.registerProvider(new OllamaModelProvider());
            registry.registerProvider(new AnthropicModelProvider());
        }

        @Test
        @DisplayName("构造时defaultProvider为null使用openai")
        void constructor_nullDefaultProvider_shouldUseOpenAI() {
            AgentModelRegistry r = new AgentModelRegistry(null);
            r.registerProvider(new OpenAIModelProvider());
            AgentModel model = resolve(r, "gpt-4o");
            assertThat(model).isNotNull();
        }

        @Test
        @DisplayName("构造时defaultProvider为空字符串使用openai")
        void constructor_blankDefaultProvider_shouldUseOpenAI() {
            AgentModelRegistry r = new AgentModelRegistry("");
            r.registerProvider(new OpenAIModelProvider());
            AgentModel model = resolve(r, "gpt-4o");
            assertThat(model).isNotNull();
        }

        @Test
        @DisplayName("注册OpenAI provider")
        void registerProvider_openAI_shouldBeRegistered() {
            assertThat(registry.getRegisteredProviders()).contains("openai");
        }

        @Test
        @DisplayName("注册所有4个provider")
        void registerProvider_allFour_shouldBeRegistered() {
            assertThat(registry.getRegisteredProviders())
                    .containsExactlyInAnyOrder("openai", "dashscope", "ollama", "anthropic");
        }

        @Test
        @DisplayName("注册null provider被忽略")
        void registerProvider_null_shouldBeIgnored() {
            int sizeBefore = registry.getRegisteredProviders().size();
            registry.registerProvider(null);
            assertThat(registry.getRegisteredProviders()).hasSize(sizeBefore);
        }

        @Test
        @DisplayName("批量注册provider列表")
        void registerProviders_list_shouldRegisterAll() {
            AgentModelRegistry r = new AgentModelRegistry("openai");
            r.registerProviders(List.of(
                    new OpenAIModelProvider(),
                    new DashScopeModelProvider()));
            assertThat(r.getRegisteredProviders()).containsExactlyInAnyOrder("openai", "dashscope");
        }

        @Test
        @DisplayName("批量注册null列表被忽略")
        void registerProviders_nullList_shouldBeIgnored() {
            int sizeBefore = registry.getRegisteredProviders().size();
            registry.registerProviders(null);
            assertThat(registry.getRegisteredProviders()).hasSize(sizeBefore);
        }

        @Test
        @DisplayName("注册相同providerId的provider会替换旧的")
        void registerProvider_duplicateId_shouldReplaceExisting() {
            OpenAIModelProvider newProvider = new OpenAIModelProvider();
            registry.registerProvider(newProvider);
            // 应该只有一个openai provider
            long count = registry.getRegisteredProviders().stream()
                    .filter("openai"::equals).count();
            assertThat(count).isEqualTo(1);
        }

        @Test
        @DisplayName("resolve通过openai:前缀解析OpenAI模型")
        void resolve_openAIPrefix_shouldReturnOpenAIModel() {
            AgentModel model = resolve(registry, "openai:gpt-4o");
            assertThat(model).isNotNull();
        }

        @Test
        @DisplayName("resolve通过dashscope:前缀解析DashScope模型")
        void resolve_dashscopePrefix_shouldReturnDashScopeModel() {
            AgentModel model = resolve(registry, "dashscope:qwen-max");
            assertThat(model).isNotNull();
        }

        @Test
        @DisplayName("resolve通过ollama:前缀解析Ollama模型")
        void resolve_ollamaPrefix_shouldReturnOllamaModel() {
            AgentModel model = resolve(registry, "ollama:llama3");
            assertThat(model).isNotNull();
        }

        @Test
        @DisplayName("resolve通过anthropic:前缀解析Anthropic模型")
        void resolve_anthropicPrefix_shouldReturnAnthropicModel() {
            AgentModel model = resolve(registry, "anthropic:claude-3");
            assertThat(model).isNotNull();
        }

        @Test
        @DisplayName("resolve无前缀时使用默认provider通过模型名匹配")
        void resolve_noPrefix_shouldMatchByModelName() {
            AgentModel model = resolve(registry, "gpt-4o");
            assertThat(model).isNotNull();
        }

        @Test
        @DisplayName("resolve缓存命中返回同一实例")
        void resolve_sameModelId_shouldReturnCachedInstance() {
            AgentModel model1 = resolve(registry, "openai:gpt-4o");
            AgentModel model2 = resolve(registry, "openai:gpt-4o");
            assertThat(model1).isSameAs(model2);
        }

        @Test
        @DisplayName("resolve不同modelId返回不同实例")
        void resolve_differentModelId_shouldReturnDifferentInstances() {
            AgentModel model1 = resolve(registry, "openai:gpt-4o");
            AgentModel model2 = resolve(registry, "openai:gpt-4o-mini");
            assertThat(model1).isNotSameAs(model2);
        }

        @Test
        @DisplayName("CachePolicy.DISABLED时不缓存，每次返回新实例")
        void resolve_cacheDisabled_shouldReturnNewInstanceEachTime() {
            AgentModelCreationContext context = AgentModelCreationContext.builder()
                    .apiKey("sk-test")
                    .cachePolicy(AgentModelCreationContext.CachePolicy.DISABLED)
                    .build();
            AgentModel model1 = registry.resolve("openai:gpt-4o", context);
            AgentModel model2 = registry.resolve("openai:gpt-4o", context);
            assertThat(model1).isNotSameAs(model2);
        }

        @Test
        @DisplayName("不同apiKey产生不同缓存键")
        void resolve_differentApiKey_shouldReturnDifferentInstances() {
            AgentModelCreationContext ctx1 = AgentModelCreationContext.builder()
                    .apiKey("sk-key1").build();
            AgentModelCreationContext ctx2 = AgentModelCreationContext.builder()
                    .apiKey("sk-key2").build();
            AgentModel model1 = registry.resolve("openai:gpt-4o", ctx1);
            AgentModel model2 = registry.resolve("openai:gpt-4o", ctx2);
            assertThat(model1).isNotSameAs(model2);
        }

        @Test
        @DisplayName("不同baseUrl产生不同缓存键")
        void resolve_differentBaseUrl_shouldReturnDifferentInstances() {
            AgentModelCreationContext ctx1 = AgentModelCreationContext.builder()
                    .apiKey("sk-test").baseUrl("https://api1.openai.com").build();
            AgentModelCreationContext ctx2 = AgentModelCreationContext.builder()
                    .apiKey("sk-test").baseUrl("https://api2.openai.com").build();
            AgentModel model1 = registry.resolve("openai:gpt-4o", ctx1);
            AgentModel model2 = registry.resolve("openai:gpt-4o", ctx2);
            assertThat(model1).isNotSameAs(model2);
        }

        @Test
        @DisplayName("自定义cacheId优先作为缓存键")
        void resolve_customCacheId_shouldUseCacheIdAsKey() {
            AgentModelCreationContext ctx1 = AgentModelCreationContext.builder()
                    .apiKey("sk-test").cacheId("my-cache").build();
            AgentModelCreationContext ctx2 = AgentModelCreationContext.builder()
                    .apiKey("sk-different").cacheId("my-cache").build();
            AgentModel model1 = registry.resolve("openai:gpt-4o", ctx1);
            AgentModel model2 = registry.resolve("openai:gpt-4o", ctx2);
            // 相同cacheId命中同一缓存
            assertThat(model1).isSameAs(model2);
        }

        @Test
        @DisplayName("未注册的provider抛出异常")
        void resolve_unregisteredProvider_shouldThrowException() {
            AgentModelRegistry r = new AgentModelRegistry("openai");
            // 没有注册任何provider
            assertThatThrownBy(() -> resolve(r, "openai:gpt-4o"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("未注册的模型 provider");
        }

        @Test
        @DisplayName("provider不支持模型名抛出异常")
        void resolve_unsupportedModel_shouldThrowException() {
            // 注册providerId为openai但supports返回false的自定义provider
            AgentModelRegistry r = new AgentModelRegistry("openai");
            r.registerProvider(new AgentModelProvider() {
                @Override
                public String providerId() {
                    return "openai";
                }

                @Override
                public boolean supports(String provider, String modelName) {
                    return false;
                }

                @Override
                public AgentModel create(String provider, String modelName, AgentModelCreationContext context) {
                    return null;
                }
            });
            assertThatThrownBy(() -> resolve(r, "openai:gpt-4o"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("不支持模型");
        }

        @Test
        @DisplayName("clearCache后重新创建模型")
        void clearCache_shouldClearAllCachedModels() {
            AgentModel model1 = resolve(registry, "openai:gpt-4o");
            registry.clearCache();
            AgentModel model2 = resolve(registry, "openai:gpt-4o");
            assertThat(model1).isNotSameAs(model2);
        }

        @Test
        @DisplayName("getRegisteredProviders返回不可变副本")
        void getRegisteredProviders_shouldReturnUnmodifiableCopy() {
            List<String> providers = registry.getRegisteredProviders();
            assertThatThrownBy(() -> providers.add("new-provider"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("resolve无参重载使用空context")
        void resolve_noContext_shouldUseEmptyContext() {
            AgentModel model = registry.resolve("openai:gpt-4o");
            assertThat(model).isNotNull();
        }
    }

    /**
     * 解析模型，使用基础context
     * @param registry
     * @param modelId
     * @return
     */
    private AgentModel resolve(AgentModelRegistry registry, String modelId) {
        AgentModelCreationContext context = AgentModelCreationContext.builder()
                .apiKey("sk-test")
                .build();
        return registry.resolve(modelId, context);
    }
}
