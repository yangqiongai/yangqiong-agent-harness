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
package com.yangqiongai.agent.harness.engine;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import com.yangqiongai.agent.harness.HarnessRuntimeBuilder;
import com.yangqiongai.agent.harness.core.AgentRuntime;
import com.yangqiongai.agent.harness.core.event.AgentEvent;
import com.yangqiongai.agent.harness.core.event.AgentResultEvent;
import com.yangqiongai.agent.harness.core.message.AgentMessage;
import com.yangqiongai.agent.harness.core.message.MessageFactory;
import com.yangqiongai.agent.harness.core.model.AgentChatResponse;
import com.yangqiongai.agent.harness.core.model.AgentGenerateOptions;
import com.yangqiongai.agent.harness.core.model.AgentModel;
import com.yangqiongai.agent.harness.durable.InMemoryAgentRunStore;
import com.yangqiongai.agent.harness.model.ModelPricing;
import com.yangqiongai.agent.harness.model.ModelPricingRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 引擎上下文引擎服务暴露测试
 * @author yangqiong
 */
class EngineContextServicesTest {

    /**
     * 测试桩模型
     */
    private static final class StubModel implements AgentModel {

        @Override
        public AgentChatResponse generate(List<AgentMessage> messages, List<Map<String, Object>> tools, AgentGenerateOptions options) {
            return null;
        }

        @Override
        public Flux<AgentChatResponse> stream(List<AgentMessage> messages, List<Map<String, Object>> tools, AgentGenerateOptions options) {
            return Flux.empty();
        }
    }

    /**
     * 记录引擎服务暴露情况的执行循环桩实现
     */
    private static final class ServiceRecordingLoop implements AgentLoop {

        /**
         * 模型调用器是否非空
         */
        private final AtomicBoolean modelCallerPresent = new AtomicBoolean(false);

        /**
         * 工具执行器是否非空
         */
        private final AtomicBoolean toolExecutorPresent = new AtomicBoolean(false);

        /**
         * 模型响应解析器是否非空
         */
        private final AtomicBoolean responseParserPresent = new AtomicBoolean(false);

        /**
         * 模型定价注册表是否非空
         */
        private final AtomicBoolean pricingRegistryPresent = new AtomicBoolean(false);

        /**
         * 持久执行状态跟踪器是否非空
         */
        private final AtomicBoolean durableTrackerPresent = new AtomicBoolean(false);

        /**
         * 记录到的模型编码
         */
        private volatile String modelCodeValue;

        @Override
        public Flux<AgentEvent> run(List<AgentMessage> inputs, EngineContext context) {
            modelCallerPresent.set(context.getModelCaller() != null);
            toolExecutorPresent.set(context.getToolExecutor() != null);
            responseParserPresent.set(context.getResponseParser() != null);
            pricingRegistryPresent.set(context.getModelPricingRegistry() != null);
            durableTrackerPresent.set(context.getDurableTracker() != null);
            modelCodeValue = context.getModelCode();
            return Flux.just(new AgentResultEvent(MessageFactory.createUserMessage("done")));
        }
    }

    /**
     * 验证自定义执行循环可通过引擎上下文获取模型调用器、工具执行器与模型响应解析器
     */
    @Test
    @DisplayName("自定义执行循环可获取模型调用器、工具执行器与响应解析器")
    void customLoopShouldReceiveEngineServices() {
        ServiceRecordingLoop loop = new ServiceRecordingLoop();
        AgentRuntime runtime = new HarnessRuntimeBuilder()
                .model(new StubModel())
                .agentLoop(loop)
                .build();
        List<AgentEvent> events = runtime.stream(
                List.of(MessageFactory.createUserMessage("hi")),
                AgentRuntimeContext.empty()).collectList().block();
        assertThat(events).isNotNull();
        assertThat(events.get(events.size() - 1)).isInstanceOf(AgentResultEvent.class);
        assertThat(loop.modelCallerPresent.get()).isTrue();
        assertThat(loop.toolExecutorPresent.get()).isTrue();
        assertThat(loop.responseParserPresent.get()).isTrue();
    }

    /**
     * 验证旧构造器未传引擎服务时保持兼容，三个服务均为null
     */
    @Test
    @DisplayName("旧构造器构建的上下文引擎服务为null")
    void legacyConstructorShouldLeaveServicesNull() {
        EngineContext ctx = new EngineContext(null, null, AgentRuntimeContext.empty(), null, 10, null);
        assertThat(ctx.getModelCaller()).isNull();
        assertThat(ctx.getToolExecutor()).isNull();
        assertThat(ctx.getResponseParser()).isNull();
    }

    /**
     * 验证自定义执行循环可通过引擎上下文获取模型定价注册表、模型编码与持久执行状态跟踪器
     */
    @Test
    @DisplayName("自定义执行循环可获取定价注册表、模型编码与持久跟踪器")
    void customLoopShouldReceiveNewEngineComponents() {
        ServiceRecordingLoop loop = new ServiceRecordingLoop();
        AgentRuntime runtime = new HarnessRuntimeBuilder()
                .model(new StubModel())
                .agentLoop(loop)
                .modelCode("test-model")
                .modelPricing(new ModelPricing("test-model", 1.0, 2.0))
                .agentRunStore(new InMemoryAgentRunStore())
                .build();
        List<AgentEvent> events = runtime.stream(
                List.of(MessageFactory.createUserMessage("hi")),
                AgentRuntimeContext.empty()).collectList().block();
        assertThat(events).isNotNull();
        assertThat(events.get(events.size() - 1)).isInstanceOf(AgentResultEvent.class);
        assertThat(loop.pricingRegistryPresent.get()).isTrue();
        assertThat(loop.modelCodeValue).isEqualTo("test-model");
        assertThat(loop.durableTrackerPresent.get()).isTrue();
    }

    /**
     * 验证引擎配置可透传定价注册表、模型编码与持久执行状态跟踪器到引擎上下文
     */
    @Test
    @DisplayName("引擎配置透传新增三组件到引擎上下文")
    void engineConfigShouldPassNewComponentsToContext() {
        ModelPricingRegistry registry = new ModelPricingRegistry();
        DurableExecutionTracker tracker = new DurableExecutionTracker(null, null, null);
        EngineConfig config = new EngineConfig("agent", null, 5, null, null, 1)
                .modelPricingRegistry(registry)
                .modelCode("test-model")
                .durableTracker(tracker);
        EngineContext ctx = config.toEngineContext(AgentRuntimeContext.empty());
        assertThat(ctx.getModelPricingRegistry()).isSameAs(registry);
        assertThat(ctx.getModelCode()).isEqualTo("test-model");
        assertThat(ctx.getDurableTracker()).isSameAs(tracker);
    }
}
