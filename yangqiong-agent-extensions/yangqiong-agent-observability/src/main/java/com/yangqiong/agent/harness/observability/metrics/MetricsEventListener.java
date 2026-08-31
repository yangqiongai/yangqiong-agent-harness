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
package com.yangqiong.agent.harness.observability.metrics;

import java.util.List;
import java.util.Set;

import io.micrometer.core.instrument.MeterRegistry;

import com.yangqiong.agent.harness.core.event.AgentEvent;
import com.yangqiong.agent.harness.core.event.AgentEventType;
import com.yangqiong.agent.harness.core.event.ModelCallInfo;
import com.yangqiong.agent.harness.core.event.ModelCallResult;
import com.yangqiong.agent.harness.core.event.RequireUserConfirmEvent;
import com.yangqiong.agent.harness.core.message.AgentChatUsage;
import com.yangqiong.agent.harness.core.message.AgentToolResultBlock;
import com.yangqiong.agent.harness.core.message.AgentToolUseBlock;
import com.yangqiong.agent.harness.event.AgentEventListener;
import com.yangqiong.agent.harness.model.ModelPricing;
import com.yangqiong.agent.harness.model.ModelPricingRegistry;

/**
 * 指标采集事件监听器
 * <p>
 * 注册到EventBus后旁路消费Agent事件流，驱动运行/模型/工具/成本/审批全量指标。
 * 与主流程完全隔离：EventBus已保证监听器异常不影响Agent执行。
 * </p>
 * @author yangqiong
 */
public final class MetricsEventListener implements AgentEventListener {

    /**
     * 关心的事件类型白名单
     */
    private static final Set<AgentEventType> INTERESTED = Set.of(
            AgentEventType.AGENT_START, AgentEventType.AGENT_END,
            AgentEventType.MODEL_CALL_START, AgentEventType.MODEL_CALL_END,
            AgentEventType.TOOL_CALL_START, AgentEventType.TOOL_CALL_END,
            AgentEventType.REQUIRE_USER_CONFIRM,
            AgentEventType.INTERRUPTED, AgentEventType.ERROR,
            AgentEventType.TOKEN_BUDGET_WARN, AgentEventType.TOKEN_BUDGET_EXCEEDED,
            AgentEventType.COST_BUDGET_WARN, AgentEventType.COST_BUDGET_EXCEEDED);

    /**
     * 指标集
     */
    private final AgentMetrics metrics;

    /**
     * 调用中悬置配对器
     */
    private final PendingCallTracker pendingCalls = new PendingCallTracker();

    /**
     * 活跃运行追踪器
     */
    private final ActiveRunTracker activeRuns = new ActiveRunTracker();

    /**
     * 模型定价注册表，null时跳过成本统计
     */
    private final ModelPricingRegistry pricingRegistry;

    /**
     * 构造监听器
     * @param meterRegistry Micrometer指标注册表
     * @param pricingRegistry 模型定价注册表，null时跳过成本指标
     */
    public MetricsEventListener(MeterRegistry meterRegistry, ModelPricingRegistry pricingRegistry) {
        this.metrics = new AgentMetrics(meterRegistry);
        this.pricingRegistry = pricingRegistry;
    }

    /**
     * 仅处理白名单事件
     * @param type
     * @return
     */
    @Override
    public boolean isInterestedIn(AgentEventType type) {
        return INTERESTED.contains(type);
    }

    /**
     * 事件分发到各指标采集路径
     * @param event
     */
    @Override
    public void onEvent(AgentEvent event) {
        if (event == null) {
            return;
        }
        switch (event.getType()) {
            case AGENT_START -> onAgentStart(event);
            case AGENT_END -> onAgentEnd(event);
            case MODEL_CALL_START -> onModelCallStart(event);
            case MODEL_CALL_END -> onModelCallEnd(event);
            case TOOL_CALL_START -> onToolCallStart(event);
            case TOOL_CALL_END -> onToolCallEnd(event);
            case REQUIRE_USER_CONFIRM -> onApprovalRequested();
            case INTERRUPTED -> activeRuns.onInterrupted();
            case ERROR -> activeRuns.onError();
            case TOKEN_BUDGET_WARN -> metrics.onBudgetEvent("token", "warn");
            case TOKEN_BUDGET_EXCEEDED -> metrics.onBudgetEvent("token", "exceeded");
            case COST_BUDGET_WARN -> metrics.onBudgetEvent("cost", "warn");
            case COST_BUDGET_EXCEEDED -> metrics.onBudgetEvent("cost", "exceeded");
            default -> {
            }
        }
    }

    /**
     * 运行开始事件，载荷为Agent名称
     * @param event
     */
    private void onAgentStart(AgentEvent event) {
        if (event.getPayload() instanceof String agentName) {
            activeRuns.onRunStart(agentName);
            metrics.onRunStart();
        }
    }

    /**
     * 运行结束事件，载荷为Agent名称
     * @param event
     */
    private void onAgentEnd(AgentEvent event) {
        if (!(event.getPayload() instanceof String agentName)) {
            return;
        }
        ActiveRunTracker.RunEnd runEnd = activeRuns.takeRunEnd(agentName);
        if (runEnd != null) {
            metrics.onRunEnd(runEnd.agent(), runEnd.result(), runEnd.durationNanos());
            if (runEnd.approvalWaitNanos() > 0) {
                metrics.onApprovalWait(runEnd.agent(), runEnd.approvalWaitNanos());
            }
        }
    }

    /**
     * 模型调用开始事件，载荷为ModelCallInfo
     * @param event
     */
    private void onModelCallStart(AgentEvent event) {
        if (event.getPayload() instanceof ModelCallInfo info) {
            pendingCalls.onModelCallStart(info);
        }
    }

    /**
     * 模型调用结束事件，载荷为ModelCallResult，含Token用量与成本换算
     * @param event
     */
    private void onModelCallEnd(AgentEvent event) {
        if (!(event.getPayload() instanceof ModelCallResult result) || result.getUsage() == null) {
            return;
        }
        metrics.onModelCallEnd(result.getAgentName(), result.getModelName());
        Long duration = pendingCalls.takeModelDurationNanos(result);
        if (duration != null) {
            metrics.onModelLatency(result.getAgentName(), result.getModelName(), duration);
        }
        AgentChatUsage usage = result.getUsage();
        metrics.onTokens(result.getAgentName(), result.getModelName(), "prompt", usage.getPromptTokens());
        metrics.onTokens(result.getAgentName(), result.getModelName(), "completion", usage.getCompletionTokens());
        recordCost(result);
    }

    /**
     * 按定价表换算并累计本次调用成本
     * @param result
     */
    private void recordCost(ModelCallResult result) {
        if (pricingRegistry == null || result.getUsage() == null) {
            return;
        }
        ModelPricing pricing = pricingRegistry.priceOf(result.getModelName());
        double cost = result.getUsage().getPromptTokens() / 1000.0 * pricing.promptUsdPer1k()
                + result.getUsage().getCompletionTokens() / 1000.0 * pricing.completionUsdPer1k();
        metrics.onCost(result.getAgentName(), result.getModelName(), cost);
    }

    /**
     * 工具调用开始事件，载荷为AgentToolUseBlock列表
     * @param event
     */
    private void onToolCallStart(AgentEvent event) {
        if (event.getPayload() instanceof List<?> list && !list.isEmpty()
                && list.get(0) instanceof AgentToolUseBlock) {
            @SuppressWarnings("unchecked")
            List<AgentToolUseBlock> blocks = (List<AgentToolUseBlock>) list;
            pendingCalls.onToolCallStart(blocks);
        }
    }

    /**
     * 工具调用结束事件，兼容两种载荷：AgentToolResultBlock列表（历史约定）
     * 与AgentMessage列表（引擎实际载荷，工具结果块位于消息content中）
     * @param event
     */
    private void onToolCallEnd(AgentEvent event) {
        List<AgentToolResultBlock> blocks = extractToolResultBlocks(event.getPayload());
        if (blocks.isEmpty()) {
            return;
        }
        for (PendingCallTracker.ToolOutcome outcome : pendingCalls.takeToolOutcomes(blocks)) {
            metrics.onToolCall(outcome.toolName(), outcome.error(), outcome.durationNanos());
        }
    }

    /**
     * 从事件载荷中提取工具结果块列表
     * @param payload
     * @return
     */
    private List<AgentToolResultBlock> extractToolResultBlocks(Object payload) {
        if (!(payload instanceof List<?> list) || list.isEmpty()) {
            return List.of();
        }
        Object first = list.get(0);
        if (first instanceof AgentToolResultBlock) {
            @SuppressWarnings("unchecked")
            List<AgentToolResultBlock> blocks = (List<AgentToolResultBlock>) list;
            return blocks;
        }
        if (first instanceof com.yangqiong.agent.harness.core.message.AgentMessage) {
            List<AgentToolResultBlock> blocks = new java.util.ArrayList<>();
            for (Object item : list) {
                com.yangqiong.agent.harness.core.message.AgentMessage message =
                        (com.yangqiong.agent.harness.core.message.AgentMessage) item;
                if (message == null || message.getContent() == null) {
                    continue;
                }
                for (com.yangqiong.agent.harness.core.message.AgentContentBlock block : message.getContent()) {
                    if (block instanceof AgentToolResultBlock resultBlock) {
                        blocks.add(resultBlock);
                    }
                }
            }
            return blocks;
        }
        return List.of();
    }

    /**
     * 审批请求事件：计数并开启等待计时（载荷可能为RequireUserConfirmEvent或列表）
     */
    private void onApprovalRequested() {
        activeRuns.onApprovalRequested();
        List<String> agents = activeRuns.activeAgents();
        if (agents.isEmpty()) {
            metrics.onApprovalRequested("unknown");
        } else {
            agents.forEach(metrics::onApprovalRequested);
        }
    }
}
