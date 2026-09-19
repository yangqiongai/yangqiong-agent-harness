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
package com.yangqiongai.agent.harness.model;

import java.util.List;

/**
 * 上下文快照
 * @author yangqiong
 */
public class ContextSnapshot {

    /**
     * 任务ID
     */
    private final String taskId;

    /**
     * 追踪ID
     */
    private final String traceId;

    /**
     * 会话ID
     */
    private final String sessionId;

    /**
     * 代理编码
     */
    private final String agentCode;

    /**
     * 隔离范围ID
     */
    private final String scopeId;

    /**
     * 模型编码
     */
    private final String modelCode;

    /**
     * 本次任务内的模型调用序号（从1开始）
     */
    private final int callSeq;

    /**
     * 本次调用注入模型的上下文消息列表
     */
    private final List<ContextMessage> messages;

    public ContextSnapshot(String taskId, String traceId, String sessionId, String agentCode,
                           String scopeId, String modelCode, int callSeq, List<ContextMessage> messages) {
        this.taskId = taskId;
        this.traceId = traceId;
        this.sessionId = sessionId;
        this.agentCode = agentCode;
        this.scopeId = scopeId;
        this.modelCode = modelCode;
        this.callSeq = callSeq;
        this.messages = messages;
    }

    public String getTaskId() {
        return taskId;
    }

    public String getTraceId() {
        return traceId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getAgentCode() {
        return agentCode;
    }

    public String getScopeId() {
        return scopeId;
    }

    public String getModelCode() {
        return modelCode;
    }

    public int getCallSeq() {
        return callSeq;
    }

    public List<ContextMessage> getMessages() {
        return messages;
    }
}
