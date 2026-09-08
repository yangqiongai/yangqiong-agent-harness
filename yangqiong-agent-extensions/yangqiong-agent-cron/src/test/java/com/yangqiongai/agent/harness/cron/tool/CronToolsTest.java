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

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import com.yangqiongai.agent.harness.core.message.AgentToolResultBlock;
import com.yangqiongai.agent.harness.core.tool.AgentToolCallParam;
import com.yangqiongai.agent.harness.cron.CronJob;
import com.yangqiongai.agent.harness.cron.CronScheduler;
import com.yangqiongai.agent.harness.cron.JsonFileCronJobStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 定时任务工具集测试
 * @author yangqiong
 */
class CronToolsTest {

    @TempDir
    Path tempDir;

    @Test
    void schedulerExposesFiveTools() {
        assertThat(newScheduler().tools()).hasSize(5);
    }

    @Test
    void scheduleCreatesJobAndPersists() {
        CronScheduler scheduler = newScheduler();
        CronScheduleTool tool = new CronScheduleTool(scheduler);

        AgentToolResultBlock result = tool.callAsync(new AgentToolCallParam(
                        Map.of("expression", "0 9 * * *", "instruction", "每日汇总"), "scope-1", "user-1"))
                .block(Duration.ofSeconds(5));

        assertThat(result).isNotNull();
        assertThat(result.isError()).isFalse();
        assertThat(result.getTextContent()).contains("定时任务已创建");
        assertThat(scheduler.list()).hasSize(1);
        CronJob job = scheduler.list().get(0);
        assertThat(job.getId()).isNotBlank();
        assertThat(job.getScopeId()).isEqualTo("scope-1");
        assertThat(job.getCronExpression()).isEqualTo("0 9 * * *");
        assertThat(job.getNextRunAtMillis()).isGreaterThan(0);
    }

    @Test
    void scheduleReturnsErrorWhenInstructionMissing() {
        CronScheduleTool tool = new CronScheduleTool(newScheduler());

        AgentToolResultBlock result = tool.callAsync(new AgentToolCallParam(Map.of("expression", "0 9 * * *")))
                .block(Duration.ofSeconds(5));

        assertThat(result).isNotNull();
        assertThat(result.isError()).isTrue();
        assertThat(result.getTextContent()).contains("instruction");
    }

    @Test
    void scheduleReturnsErrorForInvalidCron() {
        CronScheduleTool tool = new CronScheduleTool(newScheduler());

        AgentToolResultBlock result = tool.callAsync(
                        new AgentToolCallParam(Map.of("expression", "bad", "instruction", "执行指令")))
                .block(Duration.ofSeconds(5));

        assertThat(result).isNotNull();
        assertThat(result.isError()).isTrue();
        assertThat(result.getTextContent()).contains("失败");
    }

    @Test
    void listShowsEmptyWhenNoJobs() {
        CronListTool tool = new CronListTool(newScheduler());

        AgentToolResultBlock result = tool.callAsync(new AgentToolCallParam(Map.of()))
                .block(Duration.ofSeconds(5));

        assertThat(result).isNotNull();
        assertThat(result.isError()).isFalse();
        assertThat(result.getTextContent()).contains("暂无定时任务");
    }

    @Test
    void listShowsScheduledJobs() {
        CronScheduler scheduler = newScheduler();
        scheduler.schedule(List.of(CronJob.builder()
                .id("job-1")
                .name("任务A")
                .cronExpression("0 9 * * *")
                .instruction("执行指令")
                .build()));
        CronListTool tool = new CronListTool(scheduler);

        AgentToolResultBlock result = tool.callAsync(new AgentToolCallParam(Map.of()))
                .block(Duration.ofSeconds(5));

        assertThat(result).isNotNull();
        assertThat(result.getTextContent()).contains("job-1").contains("任务A").contains("0 9 * * *");
    }

    @Test
    void cancelRemovesJob() {
        CronScheduler scheduler = newScheduler();
        scheduler.schedule(List.of(CronJob.builder()
                .id("job-1")
                .cronExpression("0 9 * * *")
                .instruction("执行指令")
                .build()));
        CronCancelTool tool = new CronCancelTool(scheduler);

        AgentToolResultBlock result = tool.callAsync(new AgentToolCallParam(Map.of("id", "job-1")))
                .block(Duration.ofSeconds(5));

        assertThat(result).isNotNull();
        assertThat(result.isError()).isFalse();
        assertThat(result.getTextContent()).contains("已取消");
        assertThat(scheduler.list()).isEmpty();
    }

    @Test
    void cancelReturnsNoticeWhenJobMissing() {
        CronCancelTool tool = new CronCancelTool(newScheduler());

        AgentToolResultBlock result = tool.callAsync(new AgentToolCallParam(Map.of("id", "missing")))
                .block(Duration.ofSeconds(5));

        assertThat(result.getTextContent()).contains("不存在");
    }

    @Test
    void cancelReturnsErrorWhenIdMissing() {
        CronCancelTool tool = new CronCancelTool(newScheduler());

        AgentToolResultBlock result = tool.callAsync(new AgentToolCallParam(Map.of()))
                .block(Duration.ofSeconds(5));

        assertThat(result.isError()).isTrue();
    }

    @Test
    void pauseAndResumeToggleEnabled() {
        CronScheduler scheduler = newScheduler();
        scheduler.schedule(List.of(CronJob.builder()
                .id("job-1")
                .cronExpression("0 9 * * *")
                .instruction("执行指令")
                .build()));
        CronPauseTool pause = new CronPauseTool(scheduler);
        CronResumeTool resume = new CronResumeTool(scheduler);

        AgentToolResultBlock paused = pause.callAsync(new AgentToolCallParam(Map.of("id", "job-1")))
                .block(Duration.ofSeconds(5));

        assertThat(paused.getTextContent()).contains("已暂停");
        assertThat(scheduler.list().get(0).isEnabled()).isFalse();

        AgentToolResultBlock resumed = resume.callAsync(new AgentToolCallParam(Map.of("id", "job-1")))
                .block(Duration.ofSeconds(5));

        assertThat(resumed.getTextContent()).contains("已恢复");
        assertThat(scheduler.list().get(0).isEnabled()).isTrue();
    }

    /**
     * 构造带临时存储的调度器
     * @return
     */
    private CronScheduler newScheduler() {
        return CronScheduler.builder()
                .store(new JsonFileCronJobStore(tempDir.resolve("jobs.json")))
                .build();
    }
}
