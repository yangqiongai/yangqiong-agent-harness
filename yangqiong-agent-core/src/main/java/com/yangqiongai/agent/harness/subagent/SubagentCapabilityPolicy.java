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
package com.yangqiongai.agent.harness.subagent;

import com.yangqiongai.agent.harness.subagent.orchestration.SubagentDeclaration;
import com.yangqiongai.agent.harness.tool.HarnessToolkit;
import com.yangqiongai.agent.harness.core.middleware.AgentMiddleware;
import com.yangqiongai.agent.harness.core.skill.AgentSkillBox;
import com.yangqiongai.agent.harness.core.tool.AgentTool;
import com.yangqiongai.agent.harness.core.tool.AgentToolkit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 子代理能力筛选策略
 * <p>
 * 按SubagentDeclaration的inherit系列、allowed、denied字段筛选工具/技能/MCP/中间件，
 * 生成受控的子代理能力配置。危险工具默认进入黑名单。
 * </p>
 * @author yangqiong
 */
public class SubagentCapabilityPolicy {

    private static final Logger log = LoggerFactory.getLogger(SubagentCapabilityPolicy.class);

    /**
     * 内置危险工具黑名单
     */
    private static final Set<String> DEFAULT_DANGEROUS_TOOLS = new HashSet<>(Arrays.asList(
            "shell", "bash", "execute_command",
            "write_file", "edit_file", "delete_file",
            "memory_write", "memory_update"
    ));

    /**
     * 可配置的额外危险工具黑名单
     */
    private String configuredDangerousTools;

    /**
     * 筛选工具箱：按inheritTools/allowedTools/deniedTools/inheritMcp筛选
     * @param sourceToolkit 父级工具箱
     * @param declaration 子代理声明
     * @return 受控的子代理工具箱
     */
    public HarnessToolkit filterToolkit(AgentToolkit sourceToolkit, SubagentDeclaration declaration) {
        if (sourceToolkit == null) {
            return new HarnessToolkit(null);
        }
        Boolean inheritTools = declaration.getInheritTools();
        if (inheritTools != null && !inheritTools) {
            return new HarnessToolkit(null);
        }
        Set<String> denied = mergeDangerousTools(declaration.getDeniedTools());
        Set<String> allowed = declaration.getAllowedTools();
        Boolean inheritMcp = declaration.getInheritMcp();
        HarnessToolkit filtered = new HarnessToolkit(null);
        for (AgentTool tool : sourceToolkit.getTools()) {
            if (tool == null) {
                continue;
            }
            String toolName = tool.getName();
            if (denied.contains(toolName)) {
                log.debug("子代理[{}]工具被黑名单过滤: {}", declaration.getName(), toolName);
                continue;
            }
            if (inheritMcp != null && !inheritMcp && "mcp".equals(tool.getToolCategory())) {
                log.debug("子代理[{}]MCP工具被inheritMcp=false过滤: {}", declaration.getName(), toolName);
                continue;
            }
            if (allowed != null && !allowed.isEmpty() && !allowed.contains(toolName)) {
                log.debug("子代理[{}]工具不在白名单中被过滤: {}", declaration.getName(), toolName);
                continue;
            }
            filtered.addTool(tool);
        }
        return filtered;
    }

    /**
     * 筛选MCP工具：按inheritMcp筛选
     * @param sourceToolkit 父级工具箱
     * @param declaration 子代理声明
     * @return MCP工具列表（仅MCP类别工具）
     */
    public List<AgentTool> filterMcp(AgentToolkit sourceToolkit, SubagentDeclaration declaration) {
        if (sourceToolkit == null) {
            return Collections.emptyList();
        }
        Boolean inheritMcp = declaration.getInheritMcp();
        if (inheritMcp != null && !inheritMcp) {
            return Collections.emptyList();
        }
        return sourceToolkit.getTools().stream()
                .filter(tool -> "mcp".equals(tool.getToolCategory()))
                .collect(Collectors.toList());
    }

    /**
     * 筛选技能箱：按inheritSkills筛选
     * @param sourceSkillBox 父级技能箱
     * @param declaration 子代理声明
     * @return 受控的技能箱（inheritSkills=false返回null）
     */
    public AgentSkillBox filterSkillBox(AgentSkillBox sourceSkillBox, SubagentDeclaration declaration) {
        Boolean inheritSkills = declaration.getInheritSkills();
        if (inheritSkills != null && !inheritSkills) {
            return null;
        }
        return sourceSkillBox;
    }

    /**
     * 筛选中间件链：按inheritMiddlewares筛选
     * @param sourceMiddlewares 父级中间件列表
     * @param declaration 子代理声明
     * @return 受控的中间件列表（inheritMiddlewares=false返回空列表）
     */
    public List<AgentMiddleware> filterMiddlewares(List<AgentMiddleware> sourceMiddlewares,
                                                       SubagentDeclaration declaration) {
        Boolean inheritMiddlewares = declaration.getInheritMiddlewares();
        if (inheritMiddlewares != null && !inheritMiddlewares) {
            return new ArrayList<>();
        }
        if (sourceMiddlewares == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(sourceMiddlewares);
    }

    /**
     * 合并内置危险工具黑名单与声明的deniedTools
     * @param declaredDenied 声明的黑名单
     * @return 合并后的黑名单
     */
    private Set<String> mergeDangerousTools(Set<String> declaredDenied) {
        Set<String> merged = new HashSet<>(DEFAULT_DANGEROUS_TOOLS);
        if (configuredDangerousTools != null && !configuredDangerousTools.isBlank()) {
            merged.addAll(Arrays.asList(configuredDangerousTools.split(",")));
        }
        if (declaredDenied != null) {
            merged.addAll(declaredDenied);
        }
        return merged;
    }

    public String getConfiguredDangerousTools() {
        return configuredDangerousTools;
    }

    public void setConfiguredDangerousTools(String configuredDangerousTools) {
        this.configuredDangerousTools = configuredDangerousTools;
    }
}
