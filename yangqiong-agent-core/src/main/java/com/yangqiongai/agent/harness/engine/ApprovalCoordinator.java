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
package com.yangqiongai.agent.harness.engine;

import java.util.ArrayList;
import java.util.List;

import com.yangqiongai.agent.harness.config.AgentPermissionDecision;
import com.yangqiongai.agent.harness.core.event.ConfirmResult;
import com.yangqiongai.agent.harness.core.message.AgentMessage;
import com.yangqiongai.agent.harness.core.message.AgentToolResultBlock;
import com.yangqiongai.agent.harness.core.message.AgentToolUseBlock;
import com.yangqiongai.agent.harness.core.message.MessageFactory;
import com.yangqiongai.agent.harness.permission.ToolPolicyGate;
import com.yangqiongai.agent.harness.permission.PermissionEngine;

/**
 * 审批协调器
 * <p>
 * ReActEngine审批节点独立抽取：权限三路分拣（ASK/ALLOW/DENY）与审批结果精确匹配
 * 统一在此实现。分拣优先走{@link ToolPolicyGate}统一策略门（含审计），
 * 未配置时回退既有PermissionEngine语义；匹配以toolCallId为精确键，空时回退toolName。
 * </p>
 * @author yangqiong
 */
public class ApprovalCoordinator {

    /**
     * 统一策略门（可空，空时回退权限引擎）
     */
    private final ToolPolicyGate policyGate;

    /**
     * 权限引擎（可空）
     */
    private final PermissionEngine permissionEngine;

    public ApprovalCoordinator(ToolPolicyGate policyGate, PermissionEngine permissionEngine) {
        this.policyGate = policyGate;
        this.permissionEngine = permissionEngine;
    }

    /**
     * 是否启用审批处理（策略门或权限引擎任一存在）
     * @return
     */
    public boolean isEnabled() {
        return policyGate != null || permissionEngine != null;
    }

    /**
     * 对模型产出的工具调用做权限三路分拣
     * @param toolCalls
     * @param context
     * @param runId
     * @return
     */
    public TriageResult triage(List<AgentToolUseBlock> toolCalls, AgentRuntimeContext context, String runId) {
        TriageResult result = new TriageResult();
        for (AgentToolUseBlock call : toolCalls) {
            AgentPermissionDecision decision = evaluate(call, context, runId);
            if (decision == AgentPermissionDecision.ASK) {
                result.askCalls.add(call);
            } else if (decision == AgentPermissionDecision.DENY) {
                result.deniedMessages.add(MessageFactory.createToolMessage(
                        AgentToolResultBlock.error(call.getToolUseId(),
                                "权限拒绝: " + call.getToolName())));
            } else {
                result.allowedCalls.add(call);
            }
        }
        return result;
    }

    /**
     * 评估单次工具调用权限：策略门优先，回退权限引擎
     * @param toolName
     * @param toolCallId
     * @param context
     * @param runId
     * @return
     */
    public AgentPermissionDecision evaluate(String toolName, String toolCallId,
                                             AgentRuntimeContext context, String runId) {
        return evaluate(toolName, toolCallId, context, runId, null);
    }

    /**
     * 评估单次工具调用权限：策略门优先（携带工具入参供AI审批等精细判定），回退权限引擎
     * @param toolName
     * @param toolCallId
     * @param context
     * @param runId
     * @param toolInput 工具调用入参，可为null
     * @return
     */
    public AgentPermissionDecision evaluate(String toolName, String toolCallId,
                                             AgentRuntimeContext context, String runId,
                                             java.util.Map<String, Object> toolInput) {
        if (policyGate != null) {
            String scopeId = context != null ? context.getScopeId() : null;
            return policyGate.evaluate(toolName, toolCallId, scopeId, runId, toolInput);
        }
        if (permissionEngine != null) {
            return permissionEngine.evaluate(toolName);
        }
        return AgentPermissionDecision.ALLOW;
    }

    /**
     * 评估单次工具调用权限：携带完整工具调用块（含入参）
     * @param call
     * @param context
     * @param runId
     * @return
     */
    public AgentPermissionDecision evaluate(AgentToolUseBlock call,
                                             AgentRuntimeContext context, String runId) {
        return evaluate(call.getToolName(), call.getToolUseId(), context, runId,
                call.getInput());
    }

    /**
     * 为待确认的工具调用匹配审批结果
     * <p>
     * toolCallId精确匹配优先（并行同名工具互不干扰），
     * 审批结果未携带toolCallId时回退按toolName匹配（兼容既有调用方）。
     * </p>
     * @param call
     * @param confirmResults
     * @return
     */
    public ConfirmResult matchConfirm(AgentToolUseBlock call, List<ConfirmResult> confirmResults) {
        if (confirmResults == null || confirmResults.isEmpty()) {
            return null;
        }
        String toolCallId = call.getToolUseId();
        if (toolCallId != null && !toolCallId.isEmpty()) {
            for (ConfirmResult result : confirmResults) {
                if (result.getToolCallId() != null && toolCallId.equals(result.getToolCallId())) {
                    return result;
                }
            }
        }
        for (ConfirmResult result : confirmResults) {
            if (result.getToolCallId() == null
                    && call.getToolName() != null && call.getToolName().equals(result.getToolName())) {
                return result;
            }
        }
        return null;
    }

    /**
     * 权限三路分拣结果
     * @author yangqiong
     */
    public static class TriageResult {

        /**
         * 需人工确认的工具调用
         */
        final List<AgentToolUseBlock> askCalls = new ArrayList<>();

        /**
         * 直接放行的工具调用
         */
        final List<AgentToolUseBlock> allowedCalls = new ArrayList<>();

        /**
         * 权限拒绝生成的错误结果消息
         */
        final List<AgentMessage> deniedMessages = new ArrayList<>();

        public List<AgentToolUseBlock> getAskCalls() {
            return askCalls;
        }

        public List<AgentToolUseBlock> getAllowedCalls() {
            return allowedCalls;
        }

        public List<AgentMessage> getDeniedMessages() {
            return deniedMessages;
        }
    }
}
