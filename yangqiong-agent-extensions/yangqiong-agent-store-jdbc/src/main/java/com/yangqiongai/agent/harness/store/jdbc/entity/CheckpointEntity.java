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
package com.yangqiongai.agent.harness.store.jdbc.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 运行检查点
 * @author yangqiong
 */
@TableName("harness_checkpoint")
public class CheckpointEntity {

    /**
     * 所属运行ID（复合主键之一）
     */
    @TableId(value = "run_id", type = IdType.INPUT)
    private String runId;

    /**
     * 租户标识
     */
    private String scopeId;

    /**
     * 会话ID
     */
    private String sessionId;

    /**
     * 乐观锁版本号（复合主键之一）
     */
    private Long version;

    /**
     * 迭代轮次
     */
    private Integer iteration;

    /**
     * 对话历史快照（JSON）
     */
    private String messages;

    /**
     * 待执行工具调用（JSON）
     */
    private String pendingToolCalls;

    /**
     * 已完成工具调用ID（JSON）
     */
    private String completedToolUseIds;

    /**
     * 快照时间戳（毫秒）
     */
    private Long checkpointTime;

    public String getRunId() {
        return runId;
    }

    public void setRunId(String runId) {
        this.runId = runId;
    }

    public String getScopeId() {
        return scopeId;
    }

    public void setScopeId(String scopeId) {
        this.scopeId = scopeId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public Integer getIteration() {
        return iteration;
    }

    public void setIteration(Integer iteration) {
        this.iteration = iteration;
    }

    public String getMessages() {
        return messages;
    }

    public void setMessages(String messages) {
        this.messages = messages;
    }

    public String getPendingToolCalls() {
        return pendingToolCalls;
    }

    public void setPendingToolCalls(String pendingToolCalls) {
        this.pendingToolCalls = pendingToolCalls;
    }

    public String getCompletedToolUseIds() {
        return completedToolUseIds;
    }

    public void setCompletedToolUseIds(String completedToolUseIds) {
        this.completedToolUseIds = completedToolUseIds;
    }

    public Long getCheckpointTime() {
        return checkpointTime;
    }

    public void setCheckpointTime(Long checkpointTime) {
        this.checkpointTime = checkpointTime;
    }
}
