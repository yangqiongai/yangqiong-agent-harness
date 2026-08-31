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
package com.yangqiong.agent.harness.durable;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.yangqiong.agent.harness.core.message.AgentMessage;
import com.yangqiong.agent.harness.core.message.AgentToolUseBlock;

/**
 * Agent完整运行检查点
 * <p>
 * 保存完整运行状态：消息 + 迭代轮次 + 待执行工具调用 + 已完成工具调用ID + 版本号，
 * 支持任意断点（模型响应后/工具前后/等待审批）精确恢复且不重复副作用。
 * </p>
 * @author yangqiong
 */
public class AgentCheckpoint {

    /**
     * 所属运行ID
     */
    private final String runId;

    /**
     * 租户标识
     */
    private final String scopeId;

    /**
     * 会话ID
     */
    private final String sessionId;

    /**
     * 迭代轮次
     */
    private final int iteration;

    /**
     * 对话历史快照
     */
    private final List<AgentMessage> messages;

    /**
     * 待执行工具调用（已批准未执行或待审批）
     */
    private final List<AgentToolUseBlock> pendingToolCalls;

    /**
     * 已完成执行的工具调用ID（恢复时用于副作用去重）
     */
    private final List<String> completedToolUseIds;

    /**
     * 快照时间戳
     */
    private final long timestamp;

    /**
     * 乐观锁版本号（按runId单调递增）
     */
    private final long version;

    @JsonCreator
    public AgentCheckpoint(@JsonProperty("runId") String runId,
                           @JsonProperty("scopeId") String scopeId,
                           @JsonProperty("sessionId") String sessionId,
                           @JsonProperty("iteration") int iteration,
                           @JsonProperty("messages") List<AgentMessage> messages,
                           @JsonProperty("pendingToolCalls") List<AgentToolUseBlock> pendingToolCalls,
                           @JsonProperty("completedToolUseIds") List<String> completedToolUseIds,
                           @JsonProperty("timestamp") long timestamp,
                           @JsonProperty("version") long version) {
        this.runId = runId;
        this.scopeId = scopeId;
        this.sessionId = sessionId;
        this.iteration = iteration;
        this.messages = messages != null ? List.copyOf(messages) : List.of();
        this.pendingToolCalls = pendingToolCalls != null ? List.copyOf(pendingToolCalls) : List.of();
        this.completedToolUseIds = completedToolUseIds != null ? List.copyOf(completedToolUseIds) : List.of();
        this.timestamp = timestamp;
        this.version = version;
    }

    /**
     * 基于当前检查点派生下一版本
     * @param messages
     * @param pendingToolCalls
     * @param completedToolUseIds
     * @param iteration
     * @return
     */
    public AgentCheckpoint next(List<AgentMessage> messages, List<AgentToolUseBlock> pendingToolCalls,
                                List<String> completedToolUseIds, int iteration) {
        return new AgentCheckpoint(runId, scopeId, sessionId, iteration,
                messages, pendingToolCalls, completedToolUseIds,
                System.currentTimeMillis(), this.version + 1);
    }

    public String getRunId() {
        return runId;
    }

    public String getScopeId() {
        return scopeId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public int getIteration() {
        return iteration;
    }

    public List<AgentMessage> getMessages() {
        return messages;
    }

    public List<AgentToolUseBlock> getPendingToolCalls() {
        return pendingToolCalls;
    }

    public List<String> getCompletedToolUseIds() {
        return completedToolUseIds;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public long getVersion() {
        return version;
    }
}
