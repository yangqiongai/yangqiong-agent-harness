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
package com.yangqiongai.agent.harness.subagent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import com.yangqiongai.agent.harness.config.*;
import com.yangqiongai.agent.harness.core.AgentRuntimeBuilder;
import com.yangqiongai.agent.harness.core.event.AgentEvent;
import com.yangqiongai.agent.harness.core.interruption.AgentInterruptControl;
import com.yangqiongai.agent.harness.core.middleware.AgentMiddleware;
import com.yangqiongai.agent.harness.core.model.AgentGenerateOptions;
import com.yangqiongai.agent.harness.core.model.AgentModel;
import com.yangqiongai.agent.harness.subagent.orchestration.SubagentDeclaration;
import com.yangqiongai.agent.harness.core.skill.AgentSkillBox;
import com.yangqiongai.agent.harness.core.tool.AgentToolkit;
import com.yangqiongai.agent.harness.subagent.orchestration.SubagentResult;
import com.yangqiongai.agent.harness.core.AgentRuntime;
import com.yangqiongai.agent.harness.engine.AgentRuntimeContext;
import com.yangqiongai.agent.harness.core.AdvancedAgentRuntimeBuilder;
import com.yangqiongai.agent.harness.core.AgentRuntimeFactory;
import com.yangqiongai.agent.harness.core.message.AgentMessage;
import com.yangqiongai.agent.harness.core.message.AgentMessageRole;
import com.yangqiongai.agent.harness.core.message.AgentTextBlock;
import com.yangqiongai.agent.harness.engine.AgentLoop;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

/**
 * 子代理执行器测试
 * @author yangqiong
 */
class SubagentSpawnerTest {

    @Test
    void shouldReturnFailureWhenDeclarationNull() {
        SubagentSpawner spawner = new SubagentSpawner(null, null, null, null);
        SubagentResult result = spawner.spawn(null, "input", AgentRuntimeContext.empty()).block();
        assertThat(result).isNotNull();
        assertThat(result.isFailure()).isTrue();
    }

    @Test
    void shouldReturnFailureWhenFactoryNull() {
        SubagentDeclaration decl = SubagentDeclaration.builder()
                .name("test")
                .description("test agent")
                .build();
        SubagentSpawner spawner = new SubagentSpawner(null, null, null, null);
        SubagentResult result = spawner.spawn(decl, "input", AgentRuntimeContext.empty()).block();
        assertThat(result).isNotNull();
        assertThat(result.isFailure()).isTrue();
        assertThat(result.error()).contains("运行时工厂未注入");
    }

    @Test
    void shouldSpawnSubagentWithDefaultSystemPrompt() {
        SubagentDeclaration decl = SubagentDeclaration.builder()
                .name("worker")
                .description("工作代理")
                .build();
        AgentRuntimeFactory factory = new StubRuntimeFactory();
        SubagentSpawner spawner = new SubagentSpawner(factory, null, null, null);
        SubagentResult result = spawner.spawn(decl, "hello", AgentRuntimeContext.empty()).block();
        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.output()).isEqualTo("RESPONSE");
    }

    @Test
    void shouldSpawnSubagentWithCustomSystemPrompt() {
        SubagentDeclaration decl = SubagentDeclaration.builder()
                .name("worker")
                .description("工作代理")
                .systemPrompt("你是专家")
                .maxIterations(5)
                .build();
        AgentRuntimeFactory factory = new StubRuntimeFactory();
        SubagentSpawner spawner = new SubagentSpawner(factory, null, null, null);
        SubagentResult result = spawner.spawn(decl, "hi", null).block();
        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.output()).isEqualTo("RESPONSE");
    }

    @Test
    void shouldHandleNullInput() {
        SubagentDeclaration decl = SubagentDeclaration.builder()
                .name("worker")
                .description("工作代理")
                .build();
        AgentRuntimeFactory factory = new StubRuntimeFactory();
        SubagentSpawner spawner = new SubagentSpawner(factory, null, null, null);
        SubagentResult result = spawner.spawn(decl, null, AgentRuntimeContext.empty()).block();
        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.output()).isEqualTo("RESPONSE");
    }

    @Test
    void spawn_继承父会话sessionId() {
        SubagentDeclaration decl = SubagentDeclaration.builder()
                .name("worker")
                .description("工作代理")
                .build();
        StubRuntimeFactory factory = new StubRuntimeFactory();
        SubagentSpawner spawner = new SubagentSpawner(factory, null, null, null);
        AgentRuntimeContext parentCtx = AgentRuntimeContext.builder()
                .sessionId("parent-session-id")
                .build();
        spawner.spawn(decl, "hello", parentCtx).block();
        assertThat(factory.lastRuntime).isNotNull();
        // 子代理sessionId使用命名空间隔离：parentSessionId + ">subagent_" + depth + "_" + name
        assertThat(factory.lastRuntime.getLastContext().getSessionId())
                .isEqualTo("parent-session-id>subagent_1_worker");
    }

    @Test
    void spawn_继承父会话userId() {
        SubagentDeclaration decl = SubagentDeclaration.builder()
                .name("worker")
                .description("工作代理")
                .build();
        StubRuntimeFactory factory = new StubRuntimeFactory();
        SubagentSpawner spawner = new SubagentSpawner(factory, null, null, null);
        AgentRuntimeContext parentCtx = AgentRuntimeContext.builder()
                .userId("parent-user-id")
                .build();
        spawner.spawn(decl, "hello", parentCtx).block();
        assertThat(factory.lastRuntime).isNotNull();
        assertThat(factory.lastRuntime.getLastContext().getUserId()).isEqualTo("parent-user-id");
    }

    @Test
    void spawn_继承父级scopeId保证租户隔离() {
        SubagentDeclaration decl = SubagentDeclaration.builder()
                .name("worker")
                .description("工作代理")
                .build();
        StubRuntimeFactory factory = new StubRuntimeFactory();
        SubagentSpawner spawner = new SubagentSpawner(factory, null, null, null);
        AgentRuntimeContext parentCtx = AgentRuntimeContext.builder()
                .scopeId("tenant-a")
                .sessionId("parent-session-id")
                .build();
        spawner.spawn(decl, "hello", parentCtx).block();
        assertThat(factory.lastRuntime).isNotNull();
        assertThat(factory.lastRuntime.getLastContext().getScopeId()).isEqualTo("tenant-a");
    }

    @Test
    void spawn_子代理执行结果返回文本() {
        SubagentDeclaration decl = SubagentDeclaration.builder()
                .name("worker")
                .description("工作代理")
                .build();
        StubRuntimeFactory factory = new StubRuntimeFactory();
        factory.setResponseText("子代理回复内容");
        SubagentSpawner spawner = new SubagentSpawner(factory, null, null, null);
        SubagentResult result = spawner.spawn(decl, "hello", AgentRuntimeContext.empty()).block();
        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.output()).isEqualTo("子代理回复内容");
    }

    @Test
    void spawn_继承父级中断控制器() {
        SubagentDeclaration decl = SubagentDeclaration.builder()
                .name("worker")
                .description("工作代理")
                .build();
        StubRuntimeFactory factory = new StubRuntimeFactory();
        SubagentSpawner spawner = new SubagentSpawner(factory, null, null, null);
        AgentInterruptControl interruptControl =
                (source, message) -> {};
        AgentRuntimeContext parentCtx = AgentRuntimeContext.builder()
                .sessionId("parent-session")
                .interruptControl(interruptControl)
                .build();
        spawner.spawn(decl, "hello", parentCtx).block();
        assertThat(factory.lastRuntime).isNotNull();
        assertThat(factory.lastRuntime.getLastContext().getInterruptControl()).isSameAs(interruptControl);
    }

    @Test
    void buildRuntime_声明指定范式时应用agentLoop() {
        // 仅run方法为抽象方法，可用lambda表达范式循环桩
        AgentLoop paradigmLoop = (inputs, context) -> reactor.core.publisher.Flux.empty();
        AtomicReference<AgentLoop> applied = new AtomicReference<>();
        RuntimeCapabilityAccessor builder = mock(RuntimeCapabilityAccessor.class,
                withSettings().extraInterfaces(AdvancedAgentRuntimeBuilder.class));
        when(builder.agentLoop(any())).thenAnswer(invocation -> {
            applied.set(invocation.getArgument(0));
            return builder;
        });
        AgentRuntimeFactory factory = () -> (AdvancedAgentRuntimeBuilder) builder;
        SubagentSpawner spawner = new SubagentSpawner(factory, null, null, null);
        SubagentDeclaration decl = SubagentDeclaration.builder()
                .name("worker")
                .description("工作代理")
                .agentLoop(paradigmLoop)
                .build();

        spawner.buildRuntime(decl);

        assertThat(applied.get()).as("声明指定的范式循环应被应用到子代理构建器").isSameAs(paradigmLoop);
    }

    @Test
    void buildRuntime_未指定范式时不注入agentLoop() {
        RuntimeCapabilityAccessor builder = mock(RuntimeCapabilityAccessor.class,
                withSettings().extraInterfaces(AdvancedAgentRuntimeBuilder.class));
        AgentRuntimeFactory factory = () -> (AdvancedAgentRuntimeBuilder) builder;
        SubagentSpawner spawner = new SubagentSpawner(factory, null, null, null);
        SubagentDeclaration decl = SubagentDeclaration.builder()
                .name("worker")
                .description("工作代理")
                .build();

        spawner.buildRuntime(decl);

        verify(builder, never()).agentLoop(any());
    }

    /**
     * 桩运行时工厂
     */
    private static class StubRuntimeFactory implements AgentRuntimeFactory {
        private StubRuntime lastRuntime;

        private String responseText = "RESPONSE";

        void setResponseText(String responseText) {
            this.responseText = responseText;
        }

        @Override
        public AdvancedAgentRuntimeBuilder createBuilder() {
            return new StubBuilder(this);
        }
    }

    /**
     * 桩构建器
     */
    private static class StubBuilder implements AdvancedAgentRuntimeBuilder {
        private final StubRuntimeFactory factory;
        private String name;
        private String systemPrompt;
        private int maxIters = 10;

        StubBuilder(StubRuntimeFactory factory) {
            this.factory = factory;
        }

        @Override
        public AdvancedAgentRuntimeBuilder skillBox(AgentSkillBox skillBox) { return this; }
        @Override
        public AdvancedAgentRuntimeBuilder responseFormat(AgentResponseFormat format) { return this; }
        @Override
        public AdvancedAgentRuntimeBuilder structuredOutputType(Class<?> type) { return this; }
        @Override
        public AdvancedAgentRuntimeBuilder permission(AgentPermissionMode mode, AgentPermissionRule rule) { return this; }
        @Override
        public AdvancedAgentRuntimeBuilder permissionContextState(AgentPermissionContextState state) { return this; }
        @Override
        public AdvancedAgentRuntimeBuilder memoryConfig(AgentMemoryConfig config) { return this; }
        @Override
        public AdvancedAgentRuntimeBuilder compactionConfig(AgentCompactionConfig config) { return this; }
        @Override
        public AdvancedAgentRuntimeBuilder toolResultEvictionConfig(AgentToolResultEvictionConfig config) { return this; }
        @Override
        public AdvancedAgentRuntimeBuilder toolChoice(AgentToolChoice choice) { return this; }
        @Override
        public AgentRuntimeBuilder name(String name) { this.name = name; return this; }
        @Override
        public AgentRuntimeBuilder model(AgentModel model) { return this; }
        @Override
        public AgentRuntimeBuilder systemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; return this; }
        @Override
        public AgentRuntimeBuilder maxIters(int maxIters) { this.maxIters = maxIters; return this; }
        @Override
        public AgentRuntimeBuilder toolkit(AgentToolkit toolkit) { return this; }
        @Override
        public AgentRuntimeBuilder middleware(AgentMiddleware middleware) { return this; }
        @Override
        public AgentRuntimeBuilder generateOptions(AgentGenerateOptions options) { return this; }
        @Override
        public AgentRuntime build() {
            StubRuntime runtime = new StubRuntime(name, systemPrompt, maxIters, factory.responseText);
            factory.lastRuntime = runtime;
            return runtime;
        }
    }

    /**
     * 桩运行时
     */
    private static class StubRuntime implements AgentRuntime {
        private final String name;
        private final String systemPrompt;
        private final int maxIters;
        private final String responseText;

        private AgentRuntimeContext lastContext;

        StubRuntime(String name, String systemPrompt, int maxIters, String responseText) {
            this.name = name;
            this.systemPrompt = systemPrompt;
            this.maxIters = maxIters;
            this.responseText = responseText;
        }

        @Override
        public Mono<AgentMessage> call(List<AgentMessage> inputs, AgentRuntimeContext context) {
            this.lastContext = context;
            return Mono.just(AgentMessage.builder()
                    .role(AgentMessageRole.ASSISTANT)
                    .content(Collections.singletonList(AgentTextBlock.builder().text(responseText).build()))
                    .build());
        }

        AgentRuntimeContext getLastContext() {
            return lastContext;
        }

        @Override
        public reactor.core.publisher.Flux<AgentEvent> stream(List<AgentMessage> inputs, AgentRuntimeContext context) {
            return reactor.core.publisher.Flux.empty();
        }

        @Override
        public String getName() {
            return name;
        }
    }
}
