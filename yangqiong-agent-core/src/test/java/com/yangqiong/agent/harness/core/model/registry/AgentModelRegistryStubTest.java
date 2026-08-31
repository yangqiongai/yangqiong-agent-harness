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
package com.yangqiong.agent.harness.core.model.registry;

import java.util.List;

import com.yangqiong.agent.harness.core.model.AgentGenerateOptions;
import com.yangqiong.agent.harness.core.model.AgentModel;
import com.yangqiong.agent.harness.core.model.spi.AgentModelCreationContext;
import com.yangqiong.agent.harness.core.model.spi.AgentModelProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 模型注册表核心行为测试
 * <p>
 * 使用桩provider覆盖注册、解析、缓存与异常路径，不依赖任何具体模型提供者实现。
 * </p>
 * @author yangqiong
 */
class AgentModelRegistryStubTest {

    /**
     * 可计数的桩provider，固定支持全部模型名
     */
    private static final class StubProvider implements AgentModelProvider {

        private final String id;

        private int createCount;

        private StubProvider(String id) {
            this.id = id;
        }

        @Override
        public String providerId() {
            return id;
        }

        @Override
        public boolean supports(String provider, String modelName) {
            return true;
        }

        @Override
        public AgentModel create(String provider, String modelName, AgentModelCreationContext context) {
            createCount++;
            return new AgentModel() {
                @Override
                public com.yangqiong.agent.harness.core.model.AgentChatResponse generate(
                        java.util.List<com.yangqiong.agent.harness.core.message.AgentMessage> messages,
                        java.util.List<java.util.Map<String, Object>> tools,
                        AgentGenerateOptions options) {
                    return null;
                }

                @Override
                public reactor.core.publisher.Flux<com.yangqiong.agent.harness.core.model.AgentChatResponse> stream(
                        java.util.List<com.yangqiong.agent.harness.core.message.AgentMessage> messages,
                        java.util.List<java.util.Map<String, Object>> tools,
                        AgentGenerateOptions options) {
                    return reactor.core.publisher.Flux.empty();
                }
            };
        }
    }

    /**
     * 构建默认桩注册表
     * @return
     */
    private AgentModelRegistry stubRegistry() {
        AgentModelRegistry registry = new AgentModelRegistry("openai");
        registry.registerProvider(new StubProvider("openai"));
        return registry;
    }

    /**
     * 验证默认provider回退到openai
     */
    @Test
    @DisplayName("defaultProvider为null或空时回退openai")
    void defaultProvider_blank_shouldFallbackToOpenAI() {
        AgentModelRegistry registry = new AgentModelRegistry(null);
        registry.registerProvider(new StubProvider("openai"));
        assertThat(registry.resolve("stub-model")).isNotNull();
    }

    /**
     * 验证无前缀模型ID按默认provider解析
     */
    @Test
    @DisplayName("无前缀模型ID按默认provider解析")
    void resolve_noPrefix_shouldUseDefaultProvider() {
        assertThat(stubRegistry().resolve("stub-model")).isNotNull();
    }

    /**
     * 验证缓存命中返回同一实例
     */
    @Test
    @DisplayName("缓存命中返回同一实例")
    void resolve_cached_shouldReturnSameInstance() {
        AgentModelRegistry registry = stubRegistry();
        assertThat(registry.resolve("m1")).isSameAs(registry.resolve("m1"));
    }

    /**
     * 验证DISABLED策略跳过缓存
     */
    @Test
    @DisplayName("CachePolicy.DISABLED时每次新建实例")
    void resolve_cacheDisabled_shouldCreateEachTime() {
        AgentModelRegistry registry = stubRegistry();
        AgentModelCreationContext context = AgentModelCreationContext.builder()
                .cachePolicy(AgentModelCreationContext.CachePolicy.DISABLED)
                .build();
        assertThat(registry.resolve("m1", context)).isNotSameAs(registry.resolve("m1", context));
    }

    /**
     * 验证不同apiKey产生不同缓存键
     */
    @Test
    @DisplayName("不同apiKey产生不同缓存键")
    void resolve_differentApiKey_shouldNotShareCache() {
        AgentModelRegistry registry = stubRegistry();
        AgentModel first = registry.resolve("m1", AgentModelCreationContext.builder().apiKey("k1").build());
        AgentModel second = registry.resolve("m1", AgentModelCreationContext.builder().apiKey("k2").build());
        assertThat(first).isNotSameAs(second);
    }

    /**
     * 验证clearCache清空后重建
     */
    @Test
    @DisplayName("clearCache后重新创建实例")
    void clearCache_shouldRecreateInstances() {
        AgentModelRegistry registry = stubRegistry();
        AgentModel first = registry.resolve("m1");
        registry.clearCache();
        assertThat(registry.resolve("m1")).isNotSameAs(first);
    }

    /**
     * 验证未注册provider抛出明确异常
     */
    @Test
    @DisplayName("未注册provider抛出异常")
    void resolve_unregisteredProvider_shouldThrow() {
        AgentModelRegistry registry = new AgentModelRegistry("openai");
        assertThatThrownBy(() -> registry.resolve("openai:gpt"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("未注册的模型 provider");
    }

    /**
     * 验证重复注册同ID provider时替换
     */
    @Test
    @DisplayName("重复注册同ID provider时替换")
    void registerProvider_duplicateId_shouldReplace() {
        AgentModelRegistry registry = new AgentModelRegistry("openai");
        registry.registerProvider(new StubProvider("openai"));
        registry.registerProvider(new StubProvider("openai"));
        assertThat(registry.getRegisteredProviders()).containsExactly("openai");
    }

    /**
     * 验证getRegisteredProviders返回不可变副本
     */
    @Test
    @DisplayName("getRegisteredProviders返回不可变副本")
    void getRegisteredProviders_shouldBeUnmodifiable() {
        List<String> providers = stubRegistry().getRegisteredProviders();
        assertThatThrownBy(() -> providers.add("x"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
