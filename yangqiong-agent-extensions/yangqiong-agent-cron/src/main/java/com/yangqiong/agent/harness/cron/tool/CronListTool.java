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
package com.yangqiong.agent.harness.cron.tool;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import com.yangqiong.agent.harness.core.message.AgentTextBlock;
import com.yangqiong.agent.harness.core.message.AgentToolResultBlock;
import com.yangqiong.agent.harness.core.tool.AgentTool;
import com.yangqiong.agent.harness.core.tool.AgentToolCallParam;
import com.yangqiong.agent.harness.core.tool.ToolRiskLevel;
import com.yangqiong.agent.harness.cron.CronJob;
import com.yangqiong.agent.harness.cron.CronScheduler;
import reactor.core.publisher.Mono;

/**
 * 定时任务查询工具
 * @author yangqiong
 */
public class CronListTool implements AgentTool {

    /**
     * 工具名称
     */
    private final String name = "cron_list";

    /**
     * 工具描述
     */
    private final String description = "查询全部定时任务，返回任务ID、名称、cron表达式、启停状态、已触发次数与下次触发时间。";

    /**
     * 绑定的调度器
     */
    private final CronScheduler scheduler;

    /**
     * 时间格式化器
     */
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    /**
     * 构造
     * @param scheduler
     */
    public CronListTool(CronScheduler scheduler) {
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
     * 获取工具参数定义，无参数
     * @return
     */
    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> schema = new java.util.LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", new java.util.LinkedHashMap<String, Object>());
        return schema;
    }

    /**
     * 查询全部定时任务
     * @param param
     * @return
     */
    @Override
    public Mono<AgentToolResultBlock> callAsync(AgentToolCallParam param) {
        return Mono.fromCallable(() -> execute());
    }

    /**
     * 只读查询，标记为低风险
     * @return
     */
    @Override
    public boolean isReadOnly() {
        return true;
    }

    /**
     * 同步查询并组装结果
     * @return
     */
    private AgentToolResultBlock execute() {
        List<CronJob> jobs = scheduler.list();
        if (jobs.isEmpty()) {
            return AgentToolResultBlock.of(List.of(AgentTextBlock.builder().text("暂无定时任务").build()));
        }
        StringBuilder text = new StringBuilder("共 " + jobs.size() + " 条定时任务:\n");
        for (CronJob job : jobs) {
            text.append("- id=").append(job.getId())
                    .append(", name=").append(nullSafe(job.getName()))
                    .append(", cron=").append(job.getCronExpression())
                    .append(", enabled=").append(job.isEnabled())
                    .append(", runCount=").append(job.getRunCount())
                    .append(", nextRun=").append(formatTime(job.getNextRunAtMillis()))
                    .append("\n");
        }
        return AgentToolResultBlock.of(List.of(AgentTextBlock.builder().text(text.toString()).build()));
    }

    /**
     * 空值兜底
     * @param value
     * @return
     */
    private static String nullSafe(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    /**
     * 格式化毫秒时间戳
     * @param millis
     * @return
     */
    private static String formatTime(long millis) {
        return millis > 0 ? FORMATTER.format(Instant.ofEpochMilli(millis)) : "-";
    }
}
