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

import com.yangqiongai.agent.harness.core.tool.AgentToolCallParam;
import com.yangqiongai.agent.harness.engine.AgentRuntimeContext;
import com.yangqiongai.agent.harness.core.tool.AgentTool;
import org.junit.jupiter.api.Test;

/**
 * 计划模式管理器测试
 * @author yangqiong
 */
class PlanModeManagerTest {

    @Test
    void shouldEnterPlanMode() {
        PlanModeManager manager = new PlanModeManager();
        AgentRuntimeContext ctx = AgentRuntimeContext.empty();
        manager.enterPlanMode(ctx);
        assertThat(manager.isPlanModeActive(ctx)).isTrue();
    }

    @Test
    void shouldExitPlanMode() {
        PlanModeManager manager = new PlanModeManager();
        AgentRuntimeContext ctx = AgentRuntimeContext.empty();
        manager.enterPlanMode(ctx);
        manager.exitPlanMode(ctx);
        assertThat(manager.isPlanModeActive(ctx)).isFalse();
    }

    @Test
    void shouldWriteAndReadPlan() {
        PlanModeManager manager = new PlanModeManager();
        AgentRuntimeContext ctx = AgentRuntimeContext.empty();
        manager.writePlan(ctx, "step1\nstep2");
        assertThat(manager.getPlanContent(ctx)).isEqualTo("step1\nstep2");
    }

    @Test
    void shouldReturnEmptyWhenNoPlan() {
        PlanModeManager manager = new PlanModeManager();
        AgentRuntimeContext ctx = AgentRuntimeContext.empty();
        assertThat(manager.getPlanContent(ctx)).isEmpty();
    }

    @Test
    void shouldNotActiveByDefault() {
        PlanModeManager manager = new PlanModeManager();
        AgentRuntimeContext ctx = AgentRuntimeContext.empty();
        assertThat(manager.isPlanModeActive(ctx)).isFalse();
    }

    @Test
    void shouldHandleNullPlanContent() {
        PlanModeManager manager = new PlanModeManager();
        AgentRuntimeContext ctx = AgentRuntimeContext.empty();
        manager.writePlan(ctx, null);
        assertThat(manager.getPlanContent(ctx)).isEmpty();
    }

    @Test
    void shouldProvideThreeTools() {
        PlanModeManager manager = new PlanModeManager();
        AgentRuntimeContext ctx = AgentRuntimeContext.empty();
        PlanModeTools tools = new PlanModeTools(manager, ctx);
        java.util.List<AgentTool> toolList = tools.getTools();
        assertThat(toolList).hasSize(3);
        assertThat(toolList.get(0).getName()).isEqualTo("plan_enter");
        assertThat(toolList.get(1).getName()).isEqualTo("plan_write");
        assertThat(toolList.get(2).getName()).isEqualTo("plan_exit");
    }

    @Test
    void shouldExecutePlanEnterTool() {
        PlanModeManager manager = new PlanModeManager();
        AgentRuntimeContext ctx = AgentRuntimeContext.empty();
        PlanModeTools tools = new PlanModeTools(manager, ctx);
        AgentTool enterTool = tools.getTools().get(0);
        enterTool.callAsync(null).block();
        assertThat(manager.isPlanModeActive(ctx)).isTrue();
    }

    @Test
    void shouldExecutePlanWriteTool() {
        PlanModeManager manager = new PlanModeManager();
        AgentRuntimeContext ctx = AgentRuntimeContext.empty();
        PlanModeTools tools = new PlanModeTools(manager, ctx);
        AgentTool writeTool = tools.getTools().get(1);
        writeTool.callAsync(new AgentToolCallParam(
                java.util.Map.of("plan", "my plan"))).block();
        assertThat(manager.getPlanContent(ctx)).isEqualTo("my plan");
    }

    @Test
    void shouldExecutePlanExitTool() {
        PlanModeManager manager = new PlanModeManager();
        AgentRuntimeContext ctx = AgentRuntimeContext.empty();
        manager.enterPlanMode(ctx);
        manager.writePlan(ctx, "my plan");
        PlanModeTools tools = new PlanModeTools(manager, ctx);
        AgentTool exitTool = tools.getTools().get(2);
        exitTool.callAsync(null).block();
        assertThat(manager.isPlanModeActive(ctx)).isFalse();
    }
}
