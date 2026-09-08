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
package com.yangqiongai.agent.harness.planmode;

import java.util.List;

import com.yangqiongai.agent.harness.engine.AgentRuntimeContext;
import com.yangqiongai.agent.harness.core.middleware.AgentMiddleware;
import com.yangqiongai.agent.harness.core.tool.AgentTool;

/**
 * 计划模式中间件
 * @author yangqiong
 */
public class PlanModeMiddleware implements AgentMiddleware {

    /**
     * 上下文属性键：计划模式工具列表
     */
    public static final String ATTR_PLAN_MODE_TOOLS = "_planModeTools";

    /**
     * 计划模式管理器
     */
    private final PlanModeManager manager;

    /**
     * 工具注册标志
     */
    private volatile boolean toolsRegistered = false;

    public PlanModeMiddleware() {
        this(new PlanModeManager());
    }

    public PlanModeMiddleware(PlanModeManager manager) {
        this.manager = manager;
    }

    /**
     * 获取计划模式管理器
     * @return
     */
    public PlanModeManager getManager() {
        return manager;
    }

    /**
     * 系统提示词钩子：注入计划模式指引 + 注册工具
     * @param prompt
     * @param ctx
     * @return
     */
    @Override
    public String onSystemPrompt(String prompt, AgentRuntimeContext ctx) {
        if (!toolsRegistered) {
            PlanModeTools tools = new PlanModeTools(manager, ctx);
            List<AgentTool> planTools = tools.getTools();
            ctx.getAttributes().put(ATTR_PLAN_MODE_TOOLS, planTools);
            toolsRegistered = true;
        }
        String planModeGuide = "\n\n## 计划模式\n"
                + "对于复杂任务，建议先使用计划模式制定执行计划：\n"
                + "- 调用 plan_enter 进入计划模式\n"
                + "- 调用 plan_write 写入执行计划\n"
                + "- 调用 plan_exit 退出计划模式，按计划执行\n";
        return prompt + planModeGuide;
    }
}
