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

import java.util.Collections;
import java.util.List;
import java.util.Set;

import com.yangqiongai.agent.harness.config.AgentPermissionContextState;
import com.yangqiongai.agent.harness.config.AgentPermissionDecision;
import com.yangqiongai.agent.harness.config.AgentPermissionMode;
import com.yangqiongai.agent.harness.config.AgentPermissionRule;
import com.yangqiongai.agent.harness.core.tool.AgentTool;

/**
 * 权限引擎
 * @author yangqiong
 */
public class PermissionEngine {

    /**
     * 权限上下文状态
     */
    private final AgentPermissionContextState state;

    /**
     * 需要人工审批的工具名称集合
     */
    private final Set<String> requireApproval;

    public PermissionEngine(AgentPermissionContextState state) {
        this(state, Collections.emptySet());
    }

    public PermissionEngine(AgentPermissionContextState state, Set<String> requireApproval) {
        this.state = state;
        this.requireApproval = requireApproval != null ? requireApproval : Collections.emptySet();
    }

    /**
     * 校验工具是否允许调用
     * @param toolName
     * @return
     */
    public boolean allow(String toolName) {
        return evaluate(toolName) != AgentPermissionDecision.DENY;
    }

    /**
     * 评估工具调用的权限决策
     * @param toolName
     * @return
     */
    public AgentPermissionDecision evaluate(String toolName) {
        return evaluate(toolName, null);
    }

    /**
     * 评估工具调用的权限决策，携带工具实例时使用工具声明的只读属性精确判定
     * @param toolName
     * @param tool
     * @return
     */
    public AgentPermissionDecision evaluate(String toolName, AgentTool tool) {
        if (requireApproval.contains(toolName)) {
            return AgentPermissionDecision.ASK;
        }
        if (state == null || state.getMode() == null) {
            return AgentPermissionDecision.ALLOW;
        }
        AgentPermissionMode mode = state.getMode();
        switch (mode) {
            case BYPASS:
            case DONT_ASK:
                return AgentPermissionDecision.ALLOW;
            case ASK:
                if (isDestructiveTool(toolName)) {
                    return AgentPermissionDecision.ASK;
                }
                return AgentPermissionDecision.ALLOW;
            case EXPLORE:
                return isReadOnlyTool(toolName, tool)
                        ? AgentPermissionDecision.ALLOW : AgentPermissionDecision.DENY;
            case ACCEPT_EDITS:
                if (!isDestructiveTool(toolName)) {
                    return AgentPermissionDecision.ALLOW;
                }
                return hasExplicitAllowRule(toolName)
                        ? AgentPermissionDecision.ALLOW : AgentPermissionDecision.DENY;
            default:
                return matchRules(toolName)
                        ? AgentPermissionDecision.ALLOW : AgentPermissionDecision.DENY;
        }
    }

    /**
     * 规则匹配，DEFAULT模式下已配置规则时按白名单语义只放行命中放行规则的工具
     * @param toolName
     * @return
     */
    private boolean matchRules(String toolName) {
        List<AgentPermissionRule> rules = state.getRules();
        if (rules == null || rules.isEmpty()) {
            return true;
        }
        for (AgentPermissionRule rule : rules) {
            if (toolName != null && toolName.equals(rule.getToolName())) {
                AgentPermissionMode ruleMode = rule.getMode();
                if (ruleMode == AgentPermissionMode.EXPLORE) {
                    return false;
                }
                return true;
            }
        }
        // 未命中任何规则：规则集含放行规则时按白名单语义拒绝，仅含拒绝规则时保持默认放行
        for (AgentPermissionRule rule : rules) {
            if (rule.getMode() != AgentPermissionMode.EXPLORE) {
                return false;
            }
        }
        return true;
    }

    /**
     * 判断是否存在显式放行规则
     * @param toolName
     * @return
     */
    private boolean hasExplicitAllowRule(String toolName) {
        List<AgentPermissionRule> rules = state.getRules();
        if (rules == null || rules.isEmpty()) {
            return false;
        }
        for (AgentPermissionRule rule : rules) {
            if (toolName != null && toolName.equals(rule.getToolName())) {
                AgentPermissionMode ruleMode = rule.getMode();
                if (ruleMode == AgentPermissionMode.DONT_ASK || ruleMode == AgentPermissionMode.BYPASS) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 判断是否为只读工具，优先使用工具实例声明的只读属性，无实例时回退名称前缀启发式
     * @param toolName
     * @param tool
     * @return
     */
    private boolean isReadOnlyTool(String toolName, AgentTool tool) {
        if (tool != null) {
            return tool.isReadOnly();
        }
        if (toolName == null) {
            return false;
        }
        return toolName.startsWith("read_")
                || toolName.startsWith("list_")
                || toolName.startsWith("get_")
                || toolName.startsWith("search_")
                || toolName.startsWith("query_")
                || "glob_files".equals(toolName)
                || "grep_files".equals(toolName);
    }

    /**
     * 判断是否为破坏性工具
     * @param toolName
     * @return
     */
    private boolean isDestructiveTool(String toolName) {
        if (toolName == null) {
            return false;
        }
        return toolName.startsWith("delete_")
                || toolName.startsWith("remove_")
                || toolName.startsWith("drop_")
                || "shell".equals(toolName)
                || toolName.startsWith("exec_");
    }
}
