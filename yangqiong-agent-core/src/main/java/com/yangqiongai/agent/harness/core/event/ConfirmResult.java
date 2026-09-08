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

/**
 * 人工审批确认结果
 * <p>
 * toolCallId 为精确匹配键（并行同名工具审批互不干扰），
 * 兼容保留 toolName 匹配：toolCallId 为空时回退按工具名匹配。
 * </p>
 * @author yangqiong
 */
public class ConfirmResult {

    /**
     * 工具调用ID（精确匹配键，可为空）
     */
    private final String toolCallId;

    /**
     * 工具名称
     */
    private final String toolName;

    /**
     * 是否批准
     */
    private final boolean approved;

    /**
     * 审批理由
     */
    private final String reason;

    public ConfirmResult(String toolName, boolean approved, String reason) {
        this(null, toolName, approved, reason);
    }

    /**
     * 全参构造
     * @param toolCallId
     * @param toolName
     * @param approved
     * @param reason
     */
    public ConfirmResult(String toolCallId, String toolName, boolean approved, String reason) {
        this.toolCallId = toolCallId;
        this.toolName = toolName;
        this.approved = approved;
        this.reason = reason;
    }

    /**
     * 创建按工具调用ID的批准结果
     * @param toolCallId
     * @param toolName
     * @return
     */
    public static ConfirmResult approveCall(String toolCallId, String toolName) {
        return new ConfirmResult(toolCallId, toolName, true, null);
    }

    /**
     * 创建按工具调用ID的拒绝结果
     * @param toolCallId
     * @param toolName
     * @param reason
     * @return
     */
    public static ConfirmResult denyCall(String toolCallId, String toolName, String reason) {
        return new ConfirmResult(toolCallId, toolName, false, reason);
    }

    /**
     * 创建批准结果
     * @param toolName
     * @return
     */
    public static ConfirmResult approve(String toolName) {
        return new ConfirmResult(null, toolName, true, null);
    }

    /**
     * 创建拒绝结果
     * @param toolName
     * @param reason
     * @return
     */
    public static ConfirmResult deny(String toolName, String reason) {
        return new ConfirmResult(null, toolName, false, reason);
    }

    /**
     * 获取工具调用ID
     * @return
     */
    public String getToolCallId() {
        return toolCallId;
    }

    /**
     * 获取工具名称
     * @return
     */
    public String getToolName() {
        return toolName;
    }

    /**
     * 是否批准
     * @return
     */
    public boolean isApproved() {
        return approved;
    }

    /**
     * 获取审批理由
     * @return
     */
    public String getReason() {
        return reason;
    }
}
