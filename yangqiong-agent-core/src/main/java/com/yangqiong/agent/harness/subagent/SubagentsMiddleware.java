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
package com.yangqiong.agent.harness.subagent;

import java.util.List;

import com.yangqiong.agent.harness.subagent.orchestration.SubagentDeclaration;
import com.yangqiong.agent.harness.subagent.orchestration.AutoOrchestrateTool;
import com.yangqiong.agent.harness.engine.AgentRuntimeContext;
import com.yangqiong.agent.harness.core.middleware.AgentMiddleware;
import com.yangqiong.agent.harness.core.tool.AgentToolkit;

/**
 * 子代理中间件
 * <p>
 * 在onSystemPrompt时注册agent_spawn工具和auto_orchestrate工具，
 * 同时注入运行时parentCtx（sessionId/userId/interruptControl）。
 * </p>
 * @author yangqiong
 */
public class SubagentsMiddleware implements AgentMiddleware {

    /**
     * 子代理声明列表
     */
    private final List<SubagentDeclaration> declarations;

    /**
     * 子代理执行器
     */
    private final SubagentSpawner spawner;

    /**
     * 父级工具箱（用于将 agent_spawn 工具追加到工具箱）
     */
    private final AgentToolkit parentToolkit;

    /**
     * 自动编排工具（可选，null表示禁用动态编排）
     */
    private final AutoOrchestrateTool autoOrchestrateTool;

    /**
     * 注册标志，避免重复注册
     */
    private volatile boolean registered = false;

    public SubagentsMiddleware(List<SubagentDeclaration> declarations, SubagentSpawner spawner,
                                 AgentToolkit parentToolkit) {
        this(declarations, spawner, parentToolkit, null);
    }

    public SubagentsMiddleware(List<SubagentDeclaration> declarations, SubagentSpawner spawner,
                                 AgentToolkit parentToolkit, AutoOrchestrateTool autoOrchestrateTool) {
        this.declarations = declarations;
        this.spawner = spawner;
        this.parentToolkit = parentToolkit;
        this.autoOrchestrateTool = autoOrchestrateTool;
    }

    /**
     * 系统提示词钩子：注入子代理描述，注册工具，注入运行时上下文
     * @param prompt
     * @param ctx
     * @return
     */
    @Override
    public String onSystemPrompt(String prompt, AgentRuntimeContext ctx) {
        if (!registered) {
            SubagentToolRegistrar.register(parentToolkit, declarations, spawner, ctx);
            if (autoOrchestrateTool != null) {
                parentToolkit.addTool(autoOrchestrateTool);
            }
            registered = true;
        }
        // 每次调用刷新父级上下文，中间件被多会话共享时避免串用过期上下文
        if (autoOrchestrateTool != null) {
            autoOrchestrateTool.setParentCtx(ctx);
        }
        String subagentDesc = buildSubagentDescription(declarations);
        String orchestrateHint = autoOrchestrateTool != null ? buildOrchestrateHint() : "";
        if (subagentDesc.isEmpty() && orchestrateHint.isEmpty()) {
            return prompt;
        }
        return prompt + "\n\n" + subagentDesc + orchestrateHint;
    }

    /**
     * 构建auto_orchestrate工具使用提示词
     * <p>
     * 指定了子代理且动态编排启用时自动注入，调用方无需在系统提示词中手工书写
     * "调用auto_orchestrate工具"的固定话术。
     * </p>
     * @return
     */
    private String buildOrchestrateHint() {
        return "\n\n当任务需要多角色协作或辩论/反思/群聊时，调用 auto_orchestrate 工具自动编排子代理协作，"
                + "并在完成后汇总最终结论完整回答。";
    }

    /**
     * 构建子代理描述文本
     * @param declarations
     * @return
     */
    private String buildSubagentDescription(List<SubagentDeclaration> declarations) {
        if (declarations == null || declarations.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("你可以通过调用 agent_spawn 工具委派以下子代理执行子任务：\n");
        for (SubagentDeclaration decl : declarations) {
            sb.append("- ").append(decl.getName())
              .append(": ").append(decl.getDescription()).append("\n");
        }
        return sb.toString();
    }
}
