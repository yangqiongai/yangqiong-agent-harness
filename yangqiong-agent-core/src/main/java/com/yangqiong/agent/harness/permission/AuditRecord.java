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
package com.yangqiong.agent.harness.permission;

/**
 * 权限审计记录
 * <p>
 * 记录权限判定与状态迁移等敏感动作，字段不可变，作为不可篡改审计凭据的载体。
 * </p>
 * @author yangqiong
 */
public class AuditRecord {

    /**
     * 审计时间戳
     */
    private final long timestamp;

    /**
     * 租户标识
     */
    private final String scopeId;

    /**
     * 运行ID
     */
    private final String runId;

    /**
     * 工具调用ID
     */
    private final String toolCallId;

    /**
     * 动作（如 TOOL_PERMISSION_DECISION / RUN_STATE_TRANSITION）
     */
    private final String action;

    /**
     * 判定结果
     */
    private final String decision;

    /**
     * 原因说明（已脱敏）
     */
    private final String reason;

    public AuditRecord(long timestamp, String scopeId, String runId, String toolCallId,
                       String action, String decision, String reason) {
        this.timestamp = timestamp;
        this.scopeId = scopeId;
        this.runId = runId;
        this.toolCallId = toolCallId;
        this.action = action;
        this.decision = decision;
        this.reason = reason;
    }

    /**
     * 构造工具权限判定审计
     * @param scopeId
     * @param runId
     * @param toolCallId
     * @param toolName
     * @param decision
     * @return
     */
    public static AuditRecord toolDecision(String scopeId, String runId, String toolCallId,
                                           String toolName, String decision) {
        return new AuditRecord(System.currentTimeMillis(), scopeId, runId, toolCallId,
                "TOOL_PERMISSION_DECISION", decision, toolName);
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getScopeId() {
        return scopeId;
    }

    public String getRunId() {
        return runId;
    }

    public String getToolCallId() {
        return toolCallId;
    }

    public String getAction() {
        return action;
    }

    public String getDecision() {
        return decision;
    }

    public String getReason() {
        return reason;
    }

    @Override
    public String toString() {
        return "AuditRecord{" + timestamp + ", scope=" + scopeId + ", run=" + runId
                + ", toolCall=" + toolCallId + ", action=" + action
                + ", decision=" + decision + ", reason=" + reason + "}";
    }
}
