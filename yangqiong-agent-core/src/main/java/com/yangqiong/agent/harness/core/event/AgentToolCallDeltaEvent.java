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
package com.yangqiong.agent.harness.core.event;

import java.util.Map;
import java.util.Objects;

/**
 * Agent工具调用增量事件
 * @author yangqiong
 */
public final class AgentToolCallDeltaEvent extends AgentEvent {

    /**
     * 工具名称
     */
    private final String toolName;

    /**
     * 工具调用ID
     */
    private final String toolUseId;

    /**
     * 工具调用输入参数
     */
    private final Map<String, Object> input;

    public AgentToolCallDeltaEvent() {
        super(AgentEventType.TOOL_CALL_DELTA, null);
        this.toolName = null;
        this.toolUseId = null;
        this.input = null;
    }

    /**
     * 构造携带工具调用数据的增量事件
     * @param toolName
     * @param toolUseId
     * @param input
     */
    public AgentToolCallDeltaEvent(String toolName, String toolUseId, Map<String, Object> input) {
        super(AgentEventType.TOOL_CALL_DELTA, toolName);
        this.toolName = toolName;
        this.toolUseId = toolUseId;
        this.input = input;
    }

    /**
     * 获取工具名称
     * @return
     */
    public String getToolName() {
        return toolName;
    }

    /**
     * 获取工具调用ID
     * @return
     */
    public String getToolUseId() {
        return toolUseId;
    }

    /**
     * 获取工具调用输入参数
     * @return
     */
    public Map<String, Object> getInput() {
        return input;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        AgentToolCallDeltaEvent that = (AgentToolCallDeltaEvent) o;
        return Objects.equals(toolName, that.toolName)
                && Objects.equals(toolUseId, that.toolUseId)
                && Objects.equals(input, that.input);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), toolName, toolUseId, input);
    }

    @Override
    public String toString() {
        return "AgentToolCallDeltaEvent{toolName='" + toolName + "', toolUseId='" + toolUseId + "'}";
    }
}
