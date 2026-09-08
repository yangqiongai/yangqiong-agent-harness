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
import java.util.Optional;

import com.yangqiongai.agent.harness.core.message.AgentTextBlock;
import com.yangqiongai.agent.harness.core.message.AgentToolResultBlock;
import com.yangqiongai.agent.harness.core.tool.AgentTool;
import com.yangqiongai.agent.harness.core.tool.AgentToolCallParam;
import com.yangqiongai.agent.harness.core.tool.ToolRiskLevel;
import com.yangqiongai.agent.harness.cron.CronJob;
import com.yangqiongai.agent.harness.cron.CronScheduler;
import reactor.core.publisher.Mono;

/**
 * 定时任务取消工具
 * @author yangqiong
 */
public class CronCancelTool implements AgentTool {

    /**
     * 工具名称
     */
    private final String name = "cron_cancel";

    /**
     * 工具描述
     */
    private final String description = "按任务ID取消（删除）一条定时任务，取消后不再触发。";

    /**
     * 绑定的调度器
     */
    private final CronScheduler scheduler;

    /**
     * 构造
     * @param scheduler
     */
    public CronCancelTool(CronScheduler scheduler) {
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
        Map<String, Object> idProp = new LinkedHashMap<>();
        idProp.put("type", "string");
        idProp.put("description", "要取消的定时任务ID");

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("id", idProp);

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of("id"));
        return schema;
    }

    /**
     * 取消定时任务
     * @param param
     * @return
     */
    @Override
    public Mono<AgentToolResultBlock> callAsync(AgentToolCallParam param) {
        return Mono.fromCallable(() -> execute(param));
    }

    /**
     * 取消任务具有副作用，标记为中风险
     * @return
     */
    @Override
    public ToolRiskLevel getRiskLevel() {
        return ToolRiskLevel.MEDIUM;
    }

    /**
     * 同步取消定时任务并组装结果
     * @param param
     * @return
     */
    private AgentToolResultBlock execute(AgentToolCallParam param) {
        Map<String, Object> input = param != null ? param.getInput() : Map.of();
        Object idObj = input.get("id");
        if (idObj == null || idObj.toString().isBlank()) {
            return AgentToolResultBlock.error("id 参数缺失");
        }
        Optional<CronJob> removed = scheduler.cancel(idObj.toString().trim());
        String text = removed.isPresent()
                ? "定时任务已取消: id=" + removed.get().getId() + ", name=" + removed.get().getName()
                : "定时任务不存在，无需取消: id=" + idObj;
        return AgentToolResultBlock.of(List.of(AgentTextBlock.builder().text(text).build()));
    }
}
