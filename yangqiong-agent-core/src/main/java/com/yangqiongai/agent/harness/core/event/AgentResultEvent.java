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
package com.yangqiongai.agent.harness.core.event;

import java.util.Objects;

import com.yangqiongai.agent.harness.core.message.AgentChatUsage;
import com.yangqiongai.agent.harness.core.message.AgentMessage;

/**
 * Agent结果事件
 * @author yangqiong
 */
public final class AgentResultEvent extends AgentEvent {

    /**
     * Agent消息结果
     */
    private final AgentMessage result;

    /**
     * 累计Token用量
     */
    private final AgentChatUsage usage;

    /**
     * 累计成本（美元），可空
     */
    private final Double totalCostUsd;

    public AgentResultEvent(AgentMessage result) {
        this(result, null, null);
    }

    public AgentResultEvent(AgentMessage result, AgentChatUsage usage) {
        this(result, usage, null);
    }

    public AgentResultEvent(AgentMessage result, AgentChatUsage usage, Double totalCostUsd) {
        super(AgentEventType.AGENT_RESULT, result);
        this.result = result;
        this.usage = usage;
        this.totalCostUsd = totalCostUsd;
    }

    /**
     * 获取Agent消息结果
     * @return
     */
    public AgentMessage getResult() {
        return result;
    }

    /**
     * 获取累计Token用量
     * @return
     */
    public AgentChatUsage getUsage() {
        return usage;
    }

    /**
     * 获取累计成本（美元）
     * @return
     */
    public Double getTotalCostUsd() {
        return totalCostUsd;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        AgentResultEvent that = (AgentResultEvent) o;
        return Objects.equals(result, that.result) && Objects.equals(usage, that.usage)
                && Objects.equals(totalCostUsd, that.totalCostUsd);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), result, usage, totalCostUsd);
    }

    @Override
    public String toString() {
        return "AgentResultEvent{result=" + result + ", usage=" + usage
                + ", totalCostUsd=" + totalCostUsd + "}";
    }
}
