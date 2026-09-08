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
package com.yangqiongai.agent.harness.cron;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * JSON文件定时任务存储测试
 * @author yangqiong
 */
class JsonFileCronJobStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void returnsEmptyListWhenFileMissing() {
        JsonFileCronJobStore store = new JsonFileCronJobStore(tempDir.resolve("jobs.json"));

        assertThat(store.list()).isEmpty();
    }

    @Test
    void persistsJobAndRecoversAfterRestart() {
        Path file = tempDir.resolve("jobs.json");
        JsonFileCronJobStore first = new JsonFileCronJobStore(file);
        first.save(job("job-1"));

        // 模拟重启：同一文件新开存储
        JsonFileCronJobStore second = new JsonFileCronJobStore(file);

        List<CronJob> jobs = second.list();
        assertThat(jobs).hasSize(1);
        assertThat(jobs.get(0).getId()).isEqualTo("job-1");
        assertThat(jobs.get(0).getCronExpression()).isEqualTo("0 9 * * *");
        assertThat(jobs.get(0).getInstruction()).isEqualTo("每日九点汇总");
        assertThat(jobs.get(0).isEnabled()).isTrue();
        assertThat(jobs.get(0).getNextRunAtMillis()).isEqualTo(1000L);
    }

    @Test
    void saveOverwritesJobWithSameId() {
        JsonFileCronJobStore store = new JsonFileCronJobStore(tempDir.resolve("jobs.json"));
        store.save(job("job-1"));
        CronJob updated = job("job-1");
        updated.setInstruction("已更新指令");
        store.save(updated);

        assertThat(store.list()).hasSize(1);
        assertThat(store.get("job-1")).get()
                .extracting(CronJob::getInstruction).isEqualTo("已更新指令");
    }

    @Test
    void deletesJobById() {
        JsonFileCronJobStore store = new JsonFileCronJobStore(tempDir.resolve("jobs.json"));
        store.save(job("job-1"));
        store.save(job("job-2"));

        store.delete("job-1");

        assertThat(store.list()).hasSize(1);
        assertThat(store.get("job-1")).isEmpty();
        assertThat(store.get("job-2")).isPresent();
    }

    @Test
    void getReturnsEmptyForMissingId() {
        JsonFileCronJobStore store = new JsonFileCronJobStore(tempDir.resolve("jobs.json"));

        assertThat(store.get("missing")).isEmpty();
    }

    /**
     * 构造测试任务
     * @param id
     * @return
     */
    private static CronJob job(String id) {
        CronJob job = CronJob.builder()
                .id(id)
                .name("测试任务")
                .cronExpression("0 9 * * *")
                .instruction("每日九点汇总")
                .build();
        job.setNextRunAtMillis(1000L);
        return job;
    }
}
