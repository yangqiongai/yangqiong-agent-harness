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

import java.util.Map;

/**
 * 工具调用前置上下文
 * <p>
 * 携带调用身份与入参，供守卫做放行判定与审计留痕，字段不可变。
 * </p>
 * @author yangqiong
 */
public class ToolInvocation {

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
     * 工具入参
     */
    private final Map<String, Object> input;

    public ToolInvocation(String agentCode, String toolName, String toolUseId, String scopeId, String userId,
                          String runId, Map<String, Object> input) {
        this.agentCode = agentCode;
        this.toolName = toolName;
        this.toolUseId = toolUseId;
        this.scopeId = scopeId;
        this.userId = userId;
        this.runId = runId;
        this.input = input;
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

    public Map<String, Object> getInput() {
        return input;
    }
}
