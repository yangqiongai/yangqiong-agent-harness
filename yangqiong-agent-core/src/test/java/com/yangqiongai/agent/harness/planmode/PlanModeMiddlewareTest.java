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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Collectors;

import com.yangqiongai.agent.harness.engine.AgentRuntimeContext;
import com.yangqiongai.agent.harness.core.tool.AgentTool;
import org.junit.jupiter.api.Test;

/**
 * 计划模式中间件测试
 * @author yangqiong
 */
class PlanModeMiddlewareTest {

    @Test
    void shouldAppendPlanModeGuideToPrompt() {
        PlanModeMiddleware middleware = new PlanModeMiddleware();
        AgentRuntimeContext ctx = AgentRuntimeContext.empty();
        String result = middleware.onSystemPrompt("base prompt", ctx);
        assertThat(result).contains("base prompt");
        assertThat(result).contains("计划模式");
        assertThat(result).contains("plan_enter");
        assertThat(result).contains("plan_write");
        assertThat(result).contains("plan_exit");
    }

    @Test
    void shouldRegisterToolsToContext() {
        PlanModeMiddleware middleware = new PlanModeMiddleware();
        AgentRuntimeContext ctx = AgentRuntimeContext.empty();
        middleware.onSystemPrompt("base", ctx);
        Object tools = ctx.get(PlanModeMiddleware.ATTR_PLAN_MODE_TOOLS);
        assertThat(tools).isInstanceOf(java.util.List.class);
        @SuppressWarnings("unchecked")
        java.util.List<AgentTool> toolList = (java.util.List<AgentTool>) tools;
        assertThat(toolList).hasSize(3);
    }

    @Test
    void shouldNotRegisterToolsTwice() {
        PlanModeMiddleware middleware = new PlanModeMiddleware();
        AgentRuntimeContext ctx = AgentRuntimeContext.empty();
        middleware.onSystemPrompt("base", ctx);
        middleware.onSystemPrompt("base2", ctx);
        Object tools = ctx.get(PlanModeMiddleware.ATTR_PLAN_MODE_TOOLS);
        @SuppressWarnings("unchecked")
        java.util.List<AgentTool> toolList = (java.util.List<AgentTool>) tools;
        assertThat(toolList).hasSize(3);
    }

    @Test
    void shouldExposeManager() {
        PlanModeManager manager = new PlanModeManager();
        PlanModeMiddleware middleware = new PlanModeMiddleware(manager);
        assertThat(middleware.getManager()).isSameAs(manager);
    }

    @Test
    void onSystemPrompt_注入计划模式指引() {
        PlanModeMiddleware middleware = new PlanModeMiddleware();
        AgentRuntimeContext ctx = AgentRuntimeContext.empty();
        String result = middleware.onSystemPrompt("base prompt", ctx);
        assertThat(result).startsWith("base prompt");
        assertThat(result).contains("## 计划模式");
        assertThat(result).contains("进入计划模式");
        assertThat(result).contains("写入执行计划");
        assertThat(result).contains("退出计划模式");
    }

    @Test
    void onSystemPrompt_注册plan_enter_plan_write_plan_exit工具() {
        PlanModeMiddleware middleware = new PlanModeMiddleware();
        AgentRuntimeContext ctx = AgentRuntimeContext.empty();
        middleware.onSystemPrompt("base", ctx);
        Object tools = ctx.get(PlanModeMiddleware.ATTR_PLAN_MODE_TOOLS);
        assertThat(tools).isInstanceOf(List.class);
        @SuppressWarnings("unchecked")
        List<AgentTool> toolList = (List<AgentTool>) tools;
        List<String> toolNames = toolList.stream()
                .map(AgentTool::getName)
                .collect(Collectors.toList());
        assertThat(toolNames).containsExactly("plan_enter", "plan_write", "plan_exit");
    }
}
