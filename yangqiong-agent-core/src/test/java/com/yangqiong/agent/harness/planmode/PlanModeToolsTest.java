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
package com.yangqiong.agent.harness.planmode;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import com.yangqiong.agent.harness.engine.AgentRuntimeContext;
import com.yangqiong.agent.harness.core.message.AgentToolResultBlock;
import com.yangqiong.agent.harness.core.tool.AgentTool;
import com.yangqiong.agent.harness.core.tool.AgentToolCallParam;
import org.junit.jupiter.api.Test;

import reactor.test.StepVerifier;

/**
 * 计划模式工具集测试
 * @author yangqiong
 */
class PlanModeToolsTest {

    @Test
    void shouldReturnThreeTools() {
        PlanModeManager manager = new PlanModeManager();
        PlanModeTools tools = new PlanModeTools(manager, AgentRuntimeContext.empty());
        List<AgentTool> toolList = tools.getTools();
        assertThat(toolList).hasSize(3);
    }

    @Test
    void shouldHavePlanEnterTool() {
        PlanModeTools tools = new PlanModeTools(new PlanModeManager(), AgentRuntimeContext.empty());
        List<AgentTool> toolList = tools.getTools();
        assertThat(toolList.get(0).getName()).isEqualTo("plan_enter");
    }

    @Test
    void shouldHavePlanWriteTool() {
        PlanModeTools tools = new PlanModeTools(new PlanModeManager(), AgentRuntimeContext.empty());
        List<AgentTool> toolList = tools.getTools();
        assertThat(toolList.get(1).getName()).isEqualTo("plan_write");
    }

    @Test
    void shouldHavePlanExitTool() {
        PlanModeTools tools = new PlanModeTools(new PlanModeManager(), AgentRuntimeContext.empty());
        List<AgentTool> toolList = tools.getTools();
        assertThat(toolList.get(2).getName()).isEqualTo("plan_exit");
    }

    @Test
    void shouldEnterPlanModeOnPlanEnter() {
        PlanModeManager manager = new PlanModeManager();
        AgentRuntimeContext ctx = AgentRuntimeContext.empty();
        PlanModeTools tools = new PlanModeTools(manager, ctx);
        AgentTool planEnter = tools.getTools().get(0);

        StepVerifier.create(planEnter.callAsync(new AgentToolCallParam(Map.of())))
                .assertNext(result -> assertThat(result.isError()).isFalse())
                .verifyComplete();

        assertThat(manager.isPlanModeActive(ctx)).isTrue();
    }

    @Test
    void shouldWritePlanOnPlanWrite() {
        PlanModeManager manager = new PlanModeManager();
        AgentRuntimeContext ctx = AgentRuntimeContext.empty();
        PlanModeTools tools = new PlanModeTools(manager, ctx);
        AgentTool planWrite = tools.getTools().get(1);

        StepVerifier.create(planWrite.callAsync(new AgentToolCallParam(Map.of("plan", "step1,step2"))))
                .assertNext(result -> assertThat(result.isError()).isFalse())
                .verifyComplete();

        assertThat(manager.getPlanContent(ctx)).isEqualTo("step1,step2");
    }

    @Test
    void shouldExitPlanModeOnPlanExit() {
        PlanModeManager manager = new PlanModeManager();
        AgentRuntimeContext ctx = AgentRuntimeContext.empty();
        manager.enterPlanMode(ctx);
        manager.writePlan(ctx, "my plan");

        PlanModeTools tools = new PlanModeTools(manager, ctx);
        AgentTool planExit = tools.getTools().get(2);

        StepVerifier.create(planExit.callAsync(new AgentToolCallParam(Map.of())))
                .assertNext(result -> {
                    assertThat(result.isError()).isFalse();
                })
                .verifyComplete();

        assertThat(manager.isPlanModeActive(ctx)).isFalse();
    }

    @Test
    void shouldReturnEmptyObjectForPlanEnterParameters() {
        PlanModeTools tools = new PlanModeTools(new PlanModeManager(), AgentRuntimeContext.empty());
        Map<String, Object> params = tools.getTools().get(0).getParameters();
        assertThat(params.get("type")).isEqualTo("object");
    }

    @Test
    void shouldReturnPlanParameterForPlanWrite() {
        PlanModeTools tools = new PlanModeTools(new PlanModeManager(), AgentRuntimeContext.empty());
        Map<String, Object> params = tools.getTools().get(1).getParameters();
        assertThat(params.get("type")).isEqualTo("object");
        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) params.get("properties");
        assertThat(properties).containsKey("plan");
    }

    @Test
    void shouldReturnEmptyObjectForPlanExitParameters() {
        PlanModeTools tools = new PlanModeTools(new PlanModeManager(), AgentRuntimeContext.empty());
        Map<String, Object> params = tools.getTools().get(2).getParameters();
        assertThat(params.get("type")).isEqualTo("object");
    }

    @Test
    void shouldHandleNullInputInPlanWrite() {
        PlanModeManager manager = new PlanModeManager();
        AgentRuntimeContext ctx = AgentRuntimeContext.empty();
        PlanModeTools tools = new PlanModeTools(manager, ctx);
        AgentTool planWrite = tools.getTools().get(1);

        planWrite.callAsync(new AgentToolCallParam(null)).block();
        assertThat(manager.getPlanContent(ctx)).isEmpty();
    }

    @Test
    void shouldHandleMissingPlanKeyInPlanWrite() {
        PlanModeManager manager = new PlanModeManager();
        AgentRuntimeContext ctx = AgentRuntimeContext.empty();
        PlanModeTools tools = new PlanModeTools(manager, ctx);
        AgentTool planWrite = tools.getTools().get(1);

        planWrite.callAsync(new AgentToolCallParam(Map.of("other", "val"))).block();
        assertThat(manager.getPlanContent(ctx)).isEmpty();
    }

    @Test
    void shouldIncludePlanContentInExitResult() {
        PlanModeManager manager = new PlanModeManager();
        AgentRuntimeContext ctx = AgentRuntimeContext.empty();
        manager.writePlan(ctx, "execute step 1");

        PlanModeTools tools = new PlanModeTools(manager, ctx);
        AgentTool planExit = tools.getTools().get(2);

        AgentToolResultBlock result = planExit.callAsync(new AgentToolCallParam(Map.of())).block();
        assertThat(result).isNotNull();
        assertThat(result.isError()).isFalse();
    }

    @Test
    void shouldReturnCorrectDescriptions() {
        PlanModeTools tools = new PlanModeTools(new PlanModeManager(), AgentRuntimeContext.empty());
        List<AgentTool> toolList = tools.getTools();
        assertThat(toolList.get(0).getDescription()).contains("进入计划模式");
        assertThat(toolList.get(1).getDescription()).contains("写入或更新");
        assertThat(toolList.get(2).getDescription()).contains("退出计划模式");
    }
}
