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
package com.yangqiongai.agent.harness.cron.tool;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.yangqiongai.agent.harness.core.message.AgentTextBlock;
import com.yangqiongai.agent.harness.core.message.AgentToolResultBlock;
import com.yangqiongai.agent.harness.core.tool.AgentTool;
import com.yangqiongai.agent.harness.core.tool.AgentToolCallParam;
import com.yangqiongai.agent.harness.core.tool.ToolRiskLevel;
import com.yangqiongai.agent.harness.cron.CronJob;
import com.yangqiongai.agent.harness.cron.CronScheduler;
import reactor.core.publisher.Mono;

/**
 * 定时任务创建工具
 * @author yangqiong
 */
public class CronScheduleTool implements AgentTool {

    /**
     * 工具名称
     */
    private final String name = "cron_schedule";

    /**
     * 工具描述
     */
    private final String description = "创建一条定时自动化任务：给定cron表达式与执行指令，"
            + "到期由调度器自动唤起Agent按指令执行，返回任务ID与首次触发时间。";

    /**
     * 绑定的调度器
     */
    private final CronScheduler scheduler;

    /**
     * 构造
     * @param scheduler
     */
    public CronScheduleTool(CronScheduler scheduler) {
        this.scheduler = scheduler;
    }

    /**
     * 获取工具名称
     * @return
     */
    @Override
    public String getName() {
        return name;
    }

    /**
     * 获取工具描述
     * @return
     */
    @Override
    public String getDescription() {
        return description;
    }

    /**
     * 获取工具参数定义
     * @return
     */
    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> expressionProp = new LinkedHashMap<>();
        expressionProp.put("type", "string");
        expressionProp.put("description", "cron表达式，5段（分 时 日 月 周）或6段（秒 分 时 日 月 周）");

        Map<String, Object> instructionProp = new LinkedHashMap<>();
        instructionProp.put("type", "string");
        instructionProp.put("description", "到期交给Agent执行的指令");

        Map<String, Object> nameProp = new LinkedHashMap<>();
        nameProp.put("type", "string");
        nameProp.put("description", "任务名称，可选");

        Map<String, Object> scopeIdProp = new LinkedHashMap<>();
        scopeIdProp.put("type", "string");
        scopeIdProp.put("description", "作用域标识，缺省取当前会话上下文");

        Map<String, Object> sessionIdProp = new LinkedHashMap<>();
        sessionIdProp.put("type", "string");
        sessionIdProp.put("description", "会话标识，可选");

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("expression", expressionProp);
        properties.put("instruction", instructionProp);
        properties.put("name", nameProp);
        properties.put("scopeId", scopeIdProp);
        properties.put("sessionId", sessionIdProp);

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of("expression", "instruction"));
        return schema;
    }

    /**
     * 创建定时任务
     * @param param
     * @return
     */
    @Override
    public Mono<AgentToolResultBlock> callAsync(AgentToolCallParam param) {
        return Mono.fromCallable(() -> execute(param));
    }

    /**
     * 定时任务创建具有副作用，标记为中风险
     * @return
     */
    @Override
    public ToolRiskLevel getRiskLevel() {
        return ToolRiskLevel.MEDIUM;
    }

    /**
     * 同步创建定时任务并组装结果
     * @param param
     * @return
     */
    private AgentToolResultBlock execute(AgentToolCallParam param) {
        Map<String, Object> input = param != null ? param.getInput() : Map.of();
        String expression = stringValue(input.get("expression"));
        if (expression == null || expression.isBlank()) {
            return AgentToolResultBlock.error("expression 参数缺失");
        }
        String instruction = stringValue(input.get("instruction"));
        if (instruction == null || instruction.isBlank()) {
            return AgentToolResultBlock.error("instruction 参数缺失");
        }
        CronJob job = CronJob.builder()
                .name(stringValue(input.get("name")))
                .cronExpression(expression.trim())
                .instruction(instruction.trim())
                .scopeId(blankToNull(input.get("scopeId"), param != null ? param.getScopeId() : null))
                .sessionId(blankToNull(input.get("sessionId"), null))
                .build();
        try {
            CronJob saved = scheduler.schedule(List.of(job)).get(0);
            String text = "定时任务已创建: id=" + saved.getId() + ", name=" + saved.getName()
                    + ", cron=" + saved.getCronExpression() + ", 首次触发=" + saved.getNextRunAtMillis();
            return AgentToolResultBlock.of(List.of(AgentTextBlock.builder().text(text).build()));
        } catch (IllegalArgumentException e) {
            return AgentToolResultBlock.error("定时任务创建失败: " + e.getMessage());
        }
    }

    /**
     * 提取字符串参数
     * @param value
     * @return
     */
    private static String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    /**
     * 取首个非空字符串
     * @param value
     * @param fallback
     * @return
     */
    private static String blankToNull(Object value, String fallback) {
        String v = stringValue(value);
        if (v == null || v.isBlank()) {
            return fallback;
        }
        return v.trim();
    }
}
