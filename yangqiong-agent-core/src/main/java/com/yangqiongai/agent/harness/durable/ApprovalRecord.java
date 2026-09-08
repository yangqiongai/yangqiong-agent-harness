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
package com.yangqiongai.agent.harness.durable;

/**
 * 人工审批记录
 * <p>
 * 以 toolCallId 为主键精确对应一次工具调用，审批与恢复跨天/跨进程可靠衔接。
 * </p>
 * @author yangqiong
 */
public class ApprovalRecord {

    /**
     * 审批ID
     */
    private final String approvalId;

    /**
     * 所属运行ID
     */
    private final String runId;

    /**
     * 工具调用ID（精确匹配键）
     */
    private final String toolCallId;

    /**
     * 工具名称（辅助排查）
     */
    private final String toolName;

    /**
     * 租户标识
     */
    private final String scopeId;

    /**
     * 审批人用户ID
     */
    private final String approverId;

    /**
     * 审批状态
     */
    private final ApprovalState state;

    /**
     * 审批理由
     */
    private final String reason;

    /**
     * 创建时间戳
     */
    private final long createdAt;

    /**
     * 审批状态
     * @author yangqiong
     */
    public enum ApprovalState {

        /**
         * 待审批
         */
        PENDING,

        /**
         * 已批准
         */
        APPROVED,

        /**
         * 已拒绝
         */
        DENIED
    }

    public ApprovalRecord(String approvalId, String runId, String toolCallId, String toolName,
                          String scopeId, String approverId, ApprovalState state,
                          String reason, long createdAt) {
        this.approvalId = approvalId;
        this.runId = runId;
        this.toolCallId = toolCallId;
        this.toolName = toolName;
        this.scopeId = scopeId;
        this.approverId = approverId;
        this.state = state;
        this.reason = reason;
        this.createdAt = createdAt;
    }

    /**
     * 创建待审批记录
     * @param approvalId
     * @param runId
     * @param toolCallId
     * @param toolName
     * @param scopeId
     * @param approverId
     * @return
     */
    public static ApprovalRecord pending(String approvalId, String runId, String toolCallId,
                                         String toolName, String scopeId, String approverId) {
        return new ApprovalRecord(approvalId, runId, toolCallId, toolName, scopeId,
                approverId, ApprovalState.PENDING, null, System.currentTimeMillis());
    }

    /**
     * 派生已决记录
     * @param approved
     * @param reason
     * @return
     */
    public ApprovalRecord resolve(boolean approved, String reason) {
        ApprovalState target = approved ? ApprovalState.APPROVED : ApprovalState.DENIED;
        return new ApprovalRecord(approvalId, runId, toolCallId, toolName, scopeId,
                approverId, target, reason, createdAt);
    }

    public String getApprovalId() {
        return approvalId;
    }

    public String getRunId() {
        return runId;
    }

    public String getToolCallId() {
        return toolCallId;
    }

    public String getToolName() {
        return toolName;
    }

    public String getScopeId() {
        return scopeId;
    }

    public String getApproverId() {
        return approverId;
    }

    public ApprovalState getState() {
        return state;
    }

    public String getReason() {
        return reason;
    }

    public long getCreatedAt() {
        return createdAt;
    }
}
