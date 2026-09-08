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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.yangqiongai.agent.harness.core.message.MessageFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.core.publisher.Mono;

/**
 * 定时任务调度器测试
 * @author yangqiong
 */
class CronSchedulerTest {

    @TempDir
    Path tempDir;

    @Test
    void firesDueJobOnce() throws Exception {
        AtomicInteger count = new AtomicInteger();
        CountDownLatch latch = new CountDownLatch(1);
        CronScheduler scheduler = scheduler(countingRunner(count, latch));

        // 下次触发时间设为过去，首个巡检周期立即触发；触发后顺延到下月初不再触发
        scheduler.schedule(List.of(pastDueJob("0 0 1 * *")));
        scheduler.start();
        try {
            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(count.get()).isEqualTo(1);
            assertThat(scheduler.list()).hasSize(1);
            assertThat(scheduler.list().get(0).getRunCount()).isEqualTo(1);
        } finally {
            scheduler.close();
        }
    }

    @Test
    void canceledJobDoesNotFireAgain() throws Exception {
        AtomicInteger count = new AtomicInteger();
        CountDownLatch latch = new CountDownLatch(1);
        CronScheduler scheduler = scheduler(countingRunner(count, latch));

        CronJob job = pastDueJob("0 0 1 * *");
        scheduler.schedule(List.of(job));
        scheduler.start();
        try {
            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
            scheduler.cancel(job.getId());
            assertThat(scheduler.list()).isEmpty();
            int fired = count.get();
            Thread.sleep(200);
            assertThat(count.get()).isEqualTo(fired);
        } finally {
            scheduler.close();
        }
    }

    @Test
    void pausedJobDoesNotFireUntilResumed() throws Exception {
        AtomicInteger count = new AtomicInteger();
        CountDownLatch latch = new CountDownLatch(1);
        CronScheduler scheduler = scheduler(countingRunner(count, latch));

        CronJob job = pastDueJob("* * * * * *");
        scheduler.schedule(List.of(job));
        scheduler.pause(job.getId());
        scheduler.start();
        try {
            Thread.sleep(300);
            assertThat(count.get()).isZero();
            scheduler.resume(job.getId());
            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(count.get()).isGreaterThanOrEqualTo(1);
        } finally {
            scheduler.close();
        }
    }

    @Test
    void scheduleRejectsInvalidCron() {
        CronScheduler scheduler = CronScheduler.builder()
                .store(new JsonFileCronJobStore(tempDir.resolve("jobs.json")))
                .build();
        CronJob bad = CronJob.builder()
                .id("bad")
                .cronExpression("not-a-cron")
                .instruction("执行指令")
                .build();

        assertThatThrownBy(() -> scheduler.schedule(List.of(bad)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * 构造短巡检间隔的调度器
     * @param runner
     * @return
     */
    private static CronScheduler scheduler(CronTaskRunner runner) {
        return CronScheduler.builder()
                .store(new JsonFileCronJobStore(Path.of("target", "test-cron-jobs-" + UUID.randomUUID() + ".json")))
                .runner(runner)
                .pollInterval(Duration.ofMillis(50))
                .build();
    }

    /**
     * 构造计数执行器
     * @param count
     * @param latch
     * @return
     */
    private static CronTaskRunner countingRunner(AtomicInteger count, CountDownLatch latch) {
        return job -> {
            count.incrementAndGet();
            latch.countDown();
            return Mono.just(MessageFactory.createUserMessage("done"));
        };
    }

    /**
     * 构造下次触发时间已过期的任务
     * @param cron
     * @return
     */
    private static CronJob pastDueJob(String cron) {
        CronJob job = CronJob.builder()
                .id(UUID.randomUUID().toString())
                .name("测试任务")
                .cronExpression(cron)
                .instruction("执行指令")
                .build();
        job.setNextRunAtMillis(1);
        return job;
    }
}
