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
package com.yangqiongai.agent.harness.spi;

/**
 * 工具调用结果上下文
 * <p>
 * 携带调用结果或失败信息，供守卫做事后审计与异常检测，字段不可变。
 * </p>
 * @author yangqiong
 */
public class ToolInvocationOutcome {

    /**
     * Agent编码
     */
    private final String agentCode;

    /**
     * 工具名称
     */
    private final String toolName;

    /**
     * 工具调用ID
     */
    private final String toolUseId;

    /**
     * 租户标识
     */
    private final String scopeId;

    /**
     * 用户标识
     */
    private final String userId;

    /**
     * 运行ID
     */
    private final String runId;

    /**
     * 是否被守卫拒绝（未进入实际执行）
     */
    private final boolean denied;

    /**
     * 是否执行成功
     */
    private final boolean success;

    /**
     * 摘要信息（已脱敏，失败时为错误摘要）
     */
    private final String summary;

    /**
     * 执行耗时毫秒
     */
    private final long durationMillis;

    public ToolInvocationOutcome(String agentCode, String toolName, String toolUseId, String scopeId, String userId,
                                 String runId, boolean denied, boolean success,
                                 String summary, long durationMillis) {
        this.agentCode = agentCode;
        this.toolName = toolName;
        this.toolUseId = toolUseId;
        this.scopeId = scopeId;
        this.userId = userId;
        this.runId = runId;
        this.denied = denied;
        this.success = success;
        this.summary = summary;
        this.durationMillis = durationMillis;
    }

    public String getAgentCode() {
        return agentCode;
    }

    public String getToolName() {
        return toolName;
    }

    public String getToolUseId() {
        return toolUseId;
    }

    public String getScopeId() {
        return scopeId;
    }

    public String getUserId() {
        return userId;
    }

    public String getRunId() {
        return runId;
    }

    public boolean isDenied() {
        return denied;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getSummary() {
        return summary;
    }

    public long getDurationMillis() {
        return durationMillis;
    }
}
