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
package com.yangqiongai.agent.harness.permission;

import java.util.Map;
import java.util.Set;

import com.yangqiongai.agent.harness.config.AgentPermissionDecision;

/**
 * 工具权限统一策略门
 * <p>
 * 收敛权限判定为唯一出口：权限引擎、白名单、黑名单、审批集合、默认拒绝开关统一在此评估，
 * ReActEngine 与 ToolExecutor 共用同一实例，消除三套语义并存的策略漂移；
 * 每次判定写入审计记录（工具名与参数不落敏感内容）。
 * </p>
 * @author yangqiong
 */
public class ToolPolicyGate {

    /**
     * 权限引擎（可空，空时仅按名单判定）
     */
    private final PermissionEngine permissionEngine;

    /**
     * 工具白名单
     */
    private final Set<String> allowedTools;

    /**
     * 工具黑名单
     */
    private final Set<String> deniedTools;

    /**
     * 默认拒绝开关（未命中任何名单时拒绝，默认拒绝RBAC语义）
     */
    private final boolean defaultDeny;

    /**
     * 审计出口
     */
    private final AuditSink auditSink;

    public ToolPolicyGate(PermissionEngine permissionEngine, Set<String> allowedTools,
                          Set<String> deniedTools, boolean defaultDeny, AuditSink auditSink) {
        this.permissionEngine = permissionEngine;
        this.allowedTools = allowedTools;
        this.deniedTools = deniedTools;
        this.defaultDeny = defaultDeny;
        this.auditSink = auditSink != null ? auditSink : new AuditSink.InMemory();
    }

    /**
     * 统一评估工具调用权限
     * @param toolName
     * @param toolCallId
     * @param scopeId
     * @param runId
     * @return
     */
    public AgentPermissionDecision evaluate(String toolName, String toolCallId,
                                             String scopeId, String runId) {
        return evaluate(toolName, toolCallId, scopeId, runId, null);
    }

    /**
     * 统一评估工具调用权限（携带工具入参）
     * <p>
     * 默认忽略入参按既有名单与权限引擎判定，子类（如AI自动审批门）可基于入参做更精细判定。
     * </p>
     * @param toolName
     * @param toolCallId
     * @param scopeId
     * @param runId
     * @param toolInput 工具调用入参，可为null
     * @return
     */
    public AgentPermissionDecision evaluate(String toolName, String toolCallId,
                                             String scopeId, String runId, Map<String, Object> toolInput) {
        AgentPermissionDecision decision = doEvaluate(toolName);
        audit(toolName, toolCallId, scopeId, runId, decision);
        return decision;
    }

    /**
     * 判定主逻辑：黑名单 > 白名单 > 权限引擎 > 默认拒绝
     * @param toolName
     * @return
     */
    private AgentPermissionDecision doEvaluate(String toolName) {
        if (toolName == null) {
            return AgentPermissionDecision.DENY;
        }
        if (deniedTools != null && deniedTools.contains(toolName)) {
            return AgentPermissionDecision.DENY;
        }
        if (allowedTools != null && !allowedTools.isEmpty() && !allowedTools.contains(toolName)) {
            return AgentPermissionDecision.DENY;
        }
        if (permissionEngine != null) {
            return permissionEngine.evaluate(toolName);
        }
        if (defaultDeny) {
            return AgentPermissionDecision.DENY;
        }
        return AgentPermissionDecision.ALLOW;
    }

    /**
     * 写入审计记录（受保护，供子类在自定义判定后复用审计出口）
     * @param toolName
     * @param toolCallId
     * @param scopeId
     * @param runId
     * @param decision
     */
    protected void audit(String toolName, String toolCallId, String scopeId,
                       String runId, AgentPermissionDecision decision) {
        try {
            auditSink.append(AuditRecord.toolDecision(scopeId, runId, toolCallId,
                    toolName, decision.name()));
        } catch (Exception ignored) {
            // 审计失败不阻断主流程
        }
    }
}
