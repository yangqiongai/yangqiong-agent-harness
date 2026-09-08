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
package com.yangqiongai.agent.harness.observability.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import com.yangqiongai.agent.harness.core.event.AgentEvent;
import com.yangqiongai.agent.harness.core.event.AgentEventType;
import com.yangqiongai.agent.harness.core.event.ModelCallInfo;
import com.yangqiongai.agent.harness.core.event.ModelCallResult;
import com.yangqiongai.agent.harness.core.event.RequireUserConfirmEvent;
import com.yangqiongai.agent.harness.core.message.AgentChatUsage;
import com.yangqiongai.agent.harness.core.message.AgentToolUseBlock;
import com.yangqiongai.agent.harness.core.message.MessageFactory;
import com.yangqiongai.agent.harness.model.ModelPricing;
import com.yangqiongai.agent.harness.model.ModelPricingRegistry;

/**
 * 指标采集事件监听器测试
 * <p>
 * 以SimpleMeterRegistry驱动完整事件序列（运行-模型-工具-审批-结束），
 * 断言全量指标的计数、耗时与配对正确性，无真实网络交互。
 * </p>
 * @author yangqiong
 */
class MetricsEventListenerTest {

    /**
     * 测试指标注册表
     */
    private SimpleMeterRegistry registry;

    /**
     * 被测监听器
     */
    private MetricsEventListener listener;

    /**
     * 初始化注册表与带定价的监听器
     */
    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        ModelPricingRegistry pricing = new ModelPricingRegistry();
        pricing.register(new ModelPricing("gpt-x", 0.01, 0.02));
        listener = new MetricsEventListener(registry, pricing);
    }

    /**
     * 验证完整事件序列的指标产出
     */
    @Test
    void shouldRecordMetricsForFullRunLifecycle() {
        listener.onEvent(AgentEvent.of(AgentEventType.AGENT_START, "main-agent"));
        listener.onEvent(AgentEvent.of(AgentEventType.MODEL_CALL_START,
                new ModelCallInfo("main-agent", "gpt-x", 1)));
        listener.onEvent(AgentEvent.of(AgentEventType.MODEL_CALL_END,
                new ModelCallResult("main-agent", "gpt-x", 1, new AgentChatUsage(100, 50, 150))));
        listener.onEvent(AgentEvent.of(AgentEventType.TOOL_CALL_START,
                List.of(new AgentToolUseBlock("file_read", "t1", Map.of()))));
        listener.onEvent(AgentEvent.of(AgentEventType.TOOL_CALL_END,
                List.of(com.yangqiongai.agent.harness.core.message.AgentToolResultBlock.of("t1", List.of()))));
        listener.onEvent(new RequireUserConfirmEvent(List.of()));
        listener.onEvent(AgentEvent.of(AgentEventType.AGENT_END, "main-agent"));

        assertThat(registry.get("agent.run.total").tag("agent", "main-agent")
                .tag("result", "completed").counter().count()).isEqualTo(1.0);
        assertThat(registry.get("agent.run.duration").tag("agent", "main-agent").timer().count()).isEqualTo(1L);
        assertThat(registry.get("agent.sessions.active").gauge().value()).isZero();

        assertThat(registry.get("agent.model.calls").tag("model", "gpt-x").counter().count()).isEqualTo(1.0);
        assertThat(registry.get("agent.model.latency").tag("model", "gpt-x").timer().count()).isEqualTo(1L);
        assertThat(registry.get("agent.tokens").tag("kind", "prompt").summary().totalAmount()).isEqualTo(100.0);
        assertThat(registry.get("agent.tokens").tag("kind", "completion").summary().totalAmount()).isEqualTo(50.0);
        assertThat(registry.get("agent.cost.usd").counter().count())
                .isCloseTo(100 / 1000.0 * 0.01 + 50 / 1000.0 * 0.02, within(1e-9));

        assertThat(registry.get("agent.tool.calls").tag("tool", "file_read").counter().count()).isEqualTo(1.0);
        assertThat(registry.get("agent.tool.duration").tag("tool", "file_read").timer().count()).isEqualTo(1L);

        assertThat(registry.get("agent.approval.requested").tag("agent", "main-agent").counter().count())
                .isEqualTo(1.0);
        assertThat(registry.get("agent.approval.wait.duration").tag("agent", "main-agent").timer().count())
                .isEqualTo(1L);
    }

    /**
     * 验证错误工具结果计入error计数且不动成功计数
     */
    @Test
    void shouldSeparateToolErrorCounter() {
        listener.onEvent(AgentEvent.of(AgentEventType.TOOL_CALL_START,
                List.of(new AgentToolUseBlock("shell", "t1", Map.of()))));
        listener.onEvent(AgentEvent.of(AgentEventType.TOOL_CALL_END,
                List.of(com.yangqiongai.agent.harness.core.message.AgentToolResultBlock.error("t1", "boom"))));

        assertThat(registry.get("agent.tool.errors").tag("tool", "shell").counter().count()).isEqualTo(1.0);
        assertThat(registry.find("agent.tool.calls").tag("tool", "shell").counter()).isNull();
        assertThat(registry.get("agent.tool.duration").tag("tool", "shell").timer().count()).isEqualTo(1L);
    }

    /**
     * 验证引擎真实载荷（AgentMessage列表，工具结果块位于消息content中）能正确驱动工具指标
     */
    @Test
    void shouldRecordToolMetricsFromAgentMessagePayload() {
        listener.onEvent(AgentEvent.of(AgentEventType.TOOL_CALL_START,
                List.of(new AgentToolUseBlock("get_weather", "t1", Map.of()))));
        listener.onEvent(AgentEvent.of(AgentEventType.TOOL_CALL_END, List.of(
                MessageFactory.createToolMessage(
                        com.yangqiongai.agent.harness.core.message.AgentToolResultBlock.of("t1", List.of())))));

        assertThat(registry.get("agent.tool.calls").tag("tool", "get_weather").counter().count()).isEqualTo(1.0);
        assertThat(registry.get("agent.tool.duration").tag("tool", "get_weather").timer().count()).isEqualTo(1L);
    }

    /**
     * 验证ERROR标志将运行归因为error结果
     */
    @Test
    void shouldAttributeRunErrorFromAdjacentErrorEvent() {
        listener.onEvent(AgentEvent.of(AgentEventType.AGENT_START, "worker"));
        listener.onEvent(AgentEvent.of(AgentEventType.ERROR, new RuntimeException("模型超时")));
        listener.onEvent(AgentEvent.of(AgentEventType.AGENT_END, "worker"));

        assertThat(registry.get("agent.run.total").tag("result", "error").counter().count()).isEqualTo(1.0);
        assertThat(registry.find("agent.run.total").tag("result", "completed").counter()).isNull();
    }

    /**
     * 验证预算事件计数
     */
    @Test
    void shouldCountBudgetEvents() {
        listener.onEvent(AgentEvent.of(AgentEventType.TOKEN_BUDGET_WARN, null));
        listener.onEvent(AgentEvent.of(AgentEventType.COST_BUDGET_EXCEEDED, null));

        assertThat(registry.get("agent.budget.events").tag("kind", "token").tag("level", "warn")
                .counter().count()).isEqualTo(1.0);
        assertThat(registry.get("agent.budget.events").tag("kind", "cost").tag("level", "exceeded")
                .counter().count()).isEqualTo(1.0);
    }

    /**
     * 验证白名单外事件被isInterestedIn过滤
     */
    @Test
    void shouldIgnoreUninterestedEventTypes() {
        assertThat(listener.isInterestedIn(AgentEventType.MODEL_CALL_END)).isTrue();
        assertThat(listener.isInterestedIn(AgentEventType.TEXT_BLOCK_DELTA)).isFalse();
        assertThat(listener.isInterestedIn(AgentEventType.TOOL_CALL_DELTA)).isFalse();
    }
}
