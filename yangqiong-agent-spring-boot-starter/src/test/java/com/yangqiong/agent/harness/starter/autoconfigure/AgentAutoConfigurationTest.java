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
package com.yangqiong.agent.harness.starter.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.yangqiong.agent.harness.HarnessAgentRuntime;
import com.yangqiong.agent.harness.HarnessRuntimeBuilder;
import com.yangqiong.agent.harness.core.AgentRuntime;
import com.yangqiong.agent.harness.core.memory.AgentLongTermMemory;
import com.yangqiong.agent.harness.core.memory.SessionMemory;
import com.yangqiong.agent.harness.core.model.AgentModel;
import com.yangqiong.agent.harness.core.model.registry.AgentModelRegistry;
import com.yangqiong.agent.harness.core.trace.TraceEmitter;
import com.yangqiong.agent.harness.durable.AgentRunStore;
import com.yangqiong.agent.harness.durable.ApprovalStore;
import com.yangqiong.agent.harness.durable.CheckpointStore;
import com.yangqiong.agent.harness.durable.DistributedStores;
import com.yangqiong.agent.harness.durable.MemoryDistributedStores;
import com.yangqiong.agent.harness.durable.RunLockStore;
import com.yangqiong.agent.harness.model.DashScopeChatModel;
import com.yangqiong.agent.harness.model.HarnessModelFactory;
import com.yangqiong.agent.harness.model.HarnessModelProperties;
import com.yangqiong.agent.harness.starter.trace.LoggingTraceEmitter;
import com.yangqiong.agent.harness.tool.ToolExecutionStore;

/**
 * Starter自动装配测试
 * @author yangqiong
 */
class AgentAutoConfigurationTest {

    /**
     * 上下文运行器，装配全部内置自动配置类
     */
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    AgentModelAutoConfiguration.class,
                    AgentStoreAutoConfiguration.class,
                    AgentTracingAutoConfiguration.class,
                    AgentRuntimeAutoConfiguration.class
            ));

    /**
     * 默认配置下模型、存储、运行时全部就绪
     */
    @Test
    void shouldAssembleDefaultRuntime() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(HarnessModelProperties.class);
            assertThat(context).hasSingleBean(AgentModelRegistry.class);
            assertThat(context).hasSingleBean(HarnessModelFactory.class);
            assertThat(context).hasSingleBean(AgentModel.class);
            assertThat(context).hasSingleBean(DistributedStores.class);
            assertThat(context).hasSingleBean(MemoryDistributedStores.class);
            assertThat(context).hasSingleBean(HarnessRuntimeBuilder.class);
            assertThat(context).hasSingleBean(AgentRuntime.class);
            assertThat(context.getBean(AgentRuntime.class)).isInstanceOf(HarnessAgentRuntime.class);
        });
    }

    /**
     * 默认模型注册表包含全部内置协议提供方
     */
    @Test
    void shouldRegisterAllBuiltinProviders() {
        contextRunner.run(context -> {
            AgentModelRegistry registry = context.getBean(AgentModelRegistry.class);
            assertThat(registry.getRegisteredProviders())
                    .containsExactlyInAnyOrder("openai", "anthropic", "dashscope", "ollama", "gemini");
        });
    }

    /**
     * 自定义provider配置生效并路由到对应协议
     */
    @Test
    void shouldRespectCustomModelConfig() {
        contextRunner
                .withPropertyValues(
                        "ai.harness.model.default-provider=dashscope",
                        "ai.harness.model.model-name=deepseek-v3",
                        "ai.harness.model.providers.dashscope.api-key=test-key"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(AgentModel.class);
                    assertThat(context.getBean(AgentModel.class)).isInstanceOf(DashScopeChatModel.class);
                    assertThat(context).hasSingleBean(AgentRuntime.class);
                });
    }

    /**
     * 模型关闭时运行时一并跳过
     */
    @Test
    void shouldSkipRuntimeWhenModelDisabled() {
        contextRunner
                .withPropertyValues("ai.harness.model.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(AgentModel.class);
                    assertThat(context).doesNotHaveBean(AgentModelRegistry.class);
                    assertThat(context).doesNotHaveBean(AgentRuntime.class);
                    assertThat(context).doesNotHaveBean(HarnessRuntimeBuilder.class);
                });
    }

    /**
     * 运行时开关关闭时仅跳过运行时装配
     */
    @Test
    void shouldSkipRuntimeWhenRuntimeDisabled() {
        contextRunner
                .withPropertyValues("ai.harness.runtime.enabled=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(AgentModel.class);
                    assertThat(context).doesNotHaveBean(AgentRuntime.class);
                    assertThat(context).doesNotHaveBean(HarnessRuntimeBuilder.class);
                });
    }

    /**
     * 启用追踪时装配日志追踪导出器
     */
    @Test
    void shouldAssembleTracingWhenEnabled() {
        contextRunner
                .withPropertyValues("ai.harness.tracing.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(TraceEmitter.class);
                    assertThat(context.getBean(TraceEmitter.class)).isInstanceOf(LoggingTraceEmitter.class);
                });
    }

    /**
     * 未启用追踪时默认不装配追踪导出器
     */
    @Test
    void shouldSkipTracingByDefault() {
        contextRunner.run(context -> assertThat(context).doesNotHaveBean(TraceEmitter.class));
    }

    /**
     * 使用方自定义存储时内存存储不重复装配
     */
    @Test
    void shouldRespectCustomStoreBean() {
        contextRunner
                .withBean(DistributedStores.class, CustomStores::new)
                .run(context -> {
                    assertThat(context).doesNotHaveBean(MemoryDistributedStores.class);
                    assertThat(context).hasSingleBean(AgentRuntime.class);
                });
    }

    /**
     * 自定义存储桩
     */
    static class CustomStores implements DistributedStores {

        @Override
        public AgentRunStore runStore() {
            return null;
        }

        @Override
        public CheckpointStore checkpointStore() {
            return null;
        }

        @Override
        public ApprovalStore approvalStore() {
            return null;
        }

        @Override
        public SessionMemory sessionMemory() {
            return null;
        }

        @Override
        public AgentLongTermMemory longTermMemory() {
            return null;
        }

        @Override
        public ToolExecutionStore toolExecutionStore() {
            return null;
        }

        @Override
        public RunLockStore runLockStore() {
            return null;
        }
    }
}
