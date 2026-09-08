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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.yangqiongai.agent.harness.engine.AgentRuntimeContext;
import com.yangqiongai.agent.harness.core.message.AgentTextBlock;
import com.yangqiongai.agent.harness.core.message.AgentToolResultBlock;
import com.yangqiongai.agent.harness.core.tool.AgentTool;
import com.yangqiongai.agent.harness.core.tool.AgentToolCallParam;
import reactor.core.publisher.Mono;

/**
 * 计划模式工具集
 * @author yangqiong
 */
public class PlanModeTools {

    /**
     * 计划模式管理器
     */
    private final PlanModeManager manager;

    /**
     * 运行时上下文（通过构造函数闭包传入）
     */
    private final AgentRuntimeContext ctx;

    public PlanModeTools(PlanModeManager manager, AgentRuntimeContext ctx) {
        this.manager = manager;
        this.ctx = ctx;
    }

    /**
     * 获取计划模式工具列表
     * @return
     */
    public List<AgentTool> getTools() {
        return List.of(
                createPlanEnterTool(),
                createPlanWriteTool(),
                createPlanExitTool()
        );
    }

    /**
     * 创建 plan_enter 工具
     * @return
     */
    private AgentTool createPlanEnterTool() {
        return new AgentTool() {
            @Override
            public String getName() {
                return "plan_enter";
            }

            @Override
            public String getDescription() {
                return "进入计划模式，开始制定任务执行计划";
            }

            @Override
            public Map<String, Object> getParameters() {
                Map<String, Object> params = new LinkedHashMap<>();
                params.put("type", "object");
                params.put("properties", Map.of());
                return params;
            }

            @Override
            public Mono<AgentToolResultBlock> callAsync(AgentToolCallParam param) {
                manager.enterPlanMode(ctx);
                return Mono.just(AgentToolResultBlock.of(List.of(
                        AgentTextBlock.builder().text("已进入计划模式，请制定任务执行计划").build())));
            }
        };
    }

    /**
     * 创建 plan_write 工具
     * @return
     */
    private AgentTool createPlanWriteTool() {
        return new AgentTool() {
            @Override
            public String getName() {
                return "plan_write";
            }

            @Override
            public String getDescription() {
                return "写入或更新任务执行计划";
            }

            @Override
            public Map<String, Object> getParameters() {
                Map<String, Object> params = new LinkedHashMap<>();
                params.put("type", "object");
                Map<String, Object> properties = new LinkedHashMap<>();
                Map<String, Object> planProp = new LinkedHashMap<>();
                planProp.put("type", "string");
                planProp.put("description", "任务执行计划内容");
                properties.put("plan", planProp);
                params.put("properties", properties);
                params.put("required", List.of("plan"));
                return params;
            }

            @Override
            public Mono<AgentToolResultBlock> callAsync(AgentToolCallParam param) {
                String plan = "";
                if (param != null && param.getInput() != null) {
                    Object value = param.getInput().get("plan");
                    if (value != null) {
                        plan = String.valueOf(value);
                    }
                }
                manager.writePlan(ctx, plan);
                return Mono.just(AgentToolResultBlock.of(List.of(
                        AgentTextBlock.builder().text("计划已保存").build())));
            }
        };
    }

    /**
     * 创建 plan_exit 工具
     * @return
     */
    private AgentTool createPlanExitTool() {
        return new AgentTool() {
            @Override
            public String getName() {
                return "plan_exit";
            }

            @Override
            public String getDescription() {
                return "退出计划模式，进入执行阶段";
            }

            @Override
            public Map<String, Object> getParameters() {
                Map<String, Object> params = new LinkedHashMap<>();
                params.put("type", "object");
                params.put("properties", Map.of());
                return params;
            }

            @Override
            public Mono<AgentToolResultBlock> callAsync(AgentToolCallParam param) {
                manager.exitPlanMode(ctx);
                String plan = manager.getPlanContent(ctx);
                return Mono.just(AgentToolResultBlock.of(List.of(
                        AgentTextBlock.builder().text("已退出计划模式，开始按计划执行:\n" + plan).build())));
            }
        };
    }
}
