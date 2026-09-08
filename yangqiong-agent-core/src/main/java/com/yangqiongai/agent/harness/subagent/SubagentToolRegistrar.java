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

import java.util.List;

import com.yangqiongai.agent.harness.subagent.orchestration.SubagentDeclaration;
import com.yangqiongai.agent.harness.engine.AgentRuntimeContext;
import com.yangqiongai.agent.harness.core.tool.AgentToolkit;
import com.yangqiongai.agent.harness.core.tool.AgentTool;

/**
 * 子代理工具注册器
 * @author yangqiong
 */
public final class SubagentToolRegistrar {

    private SubagentToolRegistrar() {
    }

    /**
     * 注册 agent_spawn 工具到工具箱
     * @param toolkit
     * @param declarations
     * @param spawner
     * @param ctx
     */
    public static void register(AgentToolkit toolkit, List<SubagentDeclaration> declarations,
                                  SubagentSpawner spawner, AgentRuntimeContext ctx) {
        if (toolkit == null || declarations == null || declarations.isEmpty()) {
            return;
        }
        for (SubagentDeclaration decl : declarations) {
            if (decl == null || decl.getName() == null) {
                continue;
            }
            AgentTool tool = new AgentSpawnTool(decl, spawner, ctx);
            toolkit.addTool(tool);
        }
    }
}
