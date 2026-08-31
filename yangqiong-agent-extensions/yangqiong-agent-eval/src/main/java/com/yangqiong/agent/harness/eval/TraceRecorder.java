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
package com.yangqiong.agent.harness.eval;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.yangqiong.agent.harness.core.event.AgentEvent;
import com.yangqiong.agent.harness.core.event.AgentEventType;
import com.yangqiong.agent.harness.core.event.AgentResultEvent;
import com.yangqiong.agent.harness.core.event.ModelCallResult;
import com.yangqiong.agent.harness.core.message.AgentChatUsage;
import com.yangqiong.agent.harness.core.message.AgentMessage;
import com.yangqiong.agent.harness.core.message.AgentToolUseBlock;

/**
 * 事件轨迹记录
 * @author yangqiong
 */
public final class TraceRecorder {

    /**
     * 实际调用的工具名称列表
     */
    private final List<String> toolNames = new ArrayList<>();

    /**
     * 实际调用的工具调用块列表（含输入参数）
     */
    private final List<AgentToolUseBlock> toolCalls = new ArrayList<>();

    /**
     * 最终答案文本
     */
    private String finalAnswer;

    /**
     * 模型调用次数（即ReAct迭代轮次）
     */
    private int modelCallCount;

    /**
     * 累计Token用量
     */
    private AgentChatUsage usage;

    /**
     * 累计成本（美元），可空
     */
    private Double totalCostUsd;

    /**
     * 记录单个事件，收集工具调用、最终答案与效率指标
     * @param event
     * @return
     */
    public TraceRecorder record(AgentEvent event) {
        if (event == null) {
            return this;
        }
        if (event.getType() == AgentEventType.TOOL_CALL_START && event.getPayload() instanceof List) {
            for (Object item : (List<?>) event.getPayload()) {
                if (item instanceof AgentToolUseBlock) {
                    AgentToolUseBlock block = (AgentToolUseBlock) item;
                    if (block.getToolName() != null) {
                        toolNames.add(block.getToolName());
                        toolCalls.add(block);
                    }
                }
            }
        }
        if (event.getType() == AgentEventType.MODEL_CALL_END && event.getPayload() instanceof ModelCallResult) {
            modelCallCount++;
            ModelCallResult callResult = (ModelCallResult) event.getPayload();
            usage = AgentChatUsage.merge(usage, callResult.getUsage());
        }
        if (event instanceof AgentResultEvent) {
            AgentResultEvent resultEvent = (AgentResultEvent) event;
            AgentMessage result = resultEvent.getResult();
            if (result != null) {
                finalAnswer = result.getTextContent();
            }
            // 结果事件携带累计用量时以它为准，避免与逐次合并重复计数
            if (resultEvent.getUsage() != null) {
                usage = resultEvent.getUsage();
            }
            if (resultEvent.getTotalCostUsd() != null) {
                totalCostUsd = resultEvent.getTotalCostUsd();
            }
        }
        return this;
    }

    /**
     * 获取实际调用的工具名称列表（只读视图）
     * @return
     */
    public List<String> getToolNames() {
        return Collections.unmodifiableList(toolNames);
    }

    /**
     * 获取实际调用的工具调用块列表（只读视图，含输入参数）
     * @return
     */
    public List<AgentToolUseBlock> getToolCalls() {
        return Collections.unmodifiableList(toolCalls);
    }

    /**
     * 获取最终答案文本
     * @return
     */
    public String getFinalAnswer() {
        return finalAnswer;
    }

    /**
     * 获取模型调用次数（即ReAct迭代轮次）
     * @return
     */
    public int getModelCallCount() {
        return modelCallCount;
    }

    /**
     * 获取累计Token用量，无数据时返回null
     * @return
     */
    public AgentChatUsage getUsage() {
        return usage;
    }

    /**
     * 获取累计成本（美元），无数据时返回null
     * @return
     */
    public Double getTotalCostUsd() {
        return totalCostUsd;
    }
}
