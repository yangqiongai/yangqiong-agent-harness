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
package com.yangqiongai.agent.harness.core.message;

import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Agent工具调用块
 * @author yangqiong
 */
public final class AgentToolUseBlock implements AgentContentBlock {

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

    @JsonCreator
    public AgentToolUseBlock(@JsonProperty("toolName") String toolName,
                             @JsonProperty("toolUseId") String toolUseId,
                             @JsonProperty("input") Map<String, Object> input) {
        this.toolName = toolName;
        this.toolUseId = toolUseId;
        this.input = input != null ? Map.copyOf(input) : Map.of();
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
        AgentToolUseBlock that = (AgentToolUseBlock) o;
        return Objects.equals(toolName, that.toolName)
                && Objects.equals(toolUseId, that.toolUseId)
                && Objects.equals(input, that.input);
    }

    @Override
    public int hashCode() {
        return Objects.hash(toolName, toolUseId, input);
    }

    @Override
    public String toString() {
        return "AgentToolUseBlock{toolName='" + toolName + "', toolUseId='" + toolUseId + "'}";
    }
}
