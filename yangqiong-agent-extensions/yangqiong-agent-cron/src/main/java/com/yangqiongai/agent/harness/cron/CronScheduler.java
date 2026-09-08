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

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.yangqiongai.agent.harness.core.tool.AgentTool;
import com.yangqiongai.agent.harness.cron.tool.CronCancelTool;
import com.yangqiongai.agent.harness.cron.tool.CronListTool;
import com.yangqiongai.agent.harness.cron.tool.CronPauseTool;
import com.yangqiongai.agent.harness.cron.tool.CronResumeTool;
import com.yangqiongai.agent.harness.cron.tool.CronScheduleTool;
import com.yangqiongai.agent.harness.durable.RunLockStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 定时任务调度器
 * <p>
 * 进程内常驻调度器：单线程定时巡检任务存储，到期的启用任务先推进下次触发时间并保存，
 * 再提交到工作线程池异步执行；支持可选运行锁用于多节点去重，关闭时终止全部线程。
 * </p>
 * @author yangqiong
 */
public class CronScheduler implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(CronScheduler.class);

    /**
     * 默认巡检间隔
     */
    private static final Duration DEFAULT_POLL_INTERVAL = Duration.ofSeconds(1);

    /**
     * 默认运行锁持有时间
     */
    private static final Duration DEFAULT_RUN_LOCK_TTL = Duration.ofMinutes(5);

    /**
     * 默认节点标识
     */
    private static final String DEFAULT_NODE_ID = "local-node";

    /**
     * 工作线程数
     */
    private static final int WORKER_COUNT = 4;

    /**
     * 任务存储
     */
    private final CronJobStore store;

    /**
     * 任务执行器，为空时到期任务仅推进不执行
     */
    private final CronTaskRunner runner;

    /**
     * 巡检间隔
     */
    private final Duration pollInterval;

    /**
     * 节点标识，运行锁持有者
     */
    private final String nodeId;

    /**
     * 运行锁存储，为空时不启用多节点去重
     */
    private final RunLockStore runLockStore;

    /**
     * 运行锁持有时间
     */
    private final Duration runLockTtl;

    /**
     * 触发时间计算时区
     */
    private final ZoneId zone;

    /**
     * 巡检线程池
     */
    private ScheduledExecutorService tickExecutor;

    /**
     * 任务执行线程池
     */
    private ExecutorService workExecutor;

    /**
     * 是否已启动
     */
    private volatile boolean running;

    private CronScheduler(Builder builder) {
        this.store = Objects.requireNonNull(builder.store, "任务存储不能为空");
        this.runner = builder.runner;
        this.pollInterval = builder.pollInterval != null ? builder.pollInterval : DEFAULT_POLL_INTERVAL;
        this.nodeId = builder.nodeId != null ? builder.nodeId : DEFAULT_NODE_ID;
        this.runLockStore = builder.runLockStore;
        this.runLockTtl = builder.runLockTtl != null ? builder.runLockTtl : DEFAULT_RUN_LOCK_TTL;
        this.zone = builder.zone != null ? builder.zone : ZoneId.systemDefault();
    }

    /**
     * 创建调度器构建器
     * @return
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 启动调度器，单线程定时巡检任务存储
     */
    public synchronized void start() {
        if (running) {
            return;
        }
        running = true;
        AtomicInteger tickSeq = new AtomicInteger();
        AtomicInteger workSeq = new AtomicInteger();
        tickExecutor = Executors.newSingleThreadScheduledExecutor(
                daemonFactory("cron-tick", tickSeq));
        workExecutor = Executors.newFixedThreadPool(WORKER_COUNT, daemonFactory("cron-worker", workSeq));
        long intervalMillis = Math.max(1, pollInterval.toMillis());
        tickExecutor.scheduleAtFixedRate(this::tick, intervalMillis, intervalMillis, TimeUnit.MILLISECONDS);
        log.info("定时任务调度器已启动: pollInterval={}ms, nodeId={}", intervalMillis, nodeId);
    }

    /**
     * 停止调度器并终止全部线程
     */
    public synchronized void stop() {
        if (!running) {
            return;
        }
        running = false;
        if (tickExecutor != null) {
            tickExecutor.shutdownNow();
        }
        if (workExecutor != null) {
            workExecutor.shutdownNow();
        }
        log.info("定时任务调度器已停止");
    }

    /**
     * 关闭调度器，等价于停止
     */
    @Override
    public void close() {
        stop();
    }

    /**
     * 创建一组定时任务，校验表达式与指令并计算首次触发时间
     * @param jobs
     * @return
     */
    public synchronized List<CronJob> schedule(List<CronJob> jobs) {
        Objects.requireNonNull(jobs, "jobs不能为空");
        List<CronJob> saved = new ArrayList<>();
        long nowMillis = Instant.now().toEpochMilli();
        for (CronJob job : jobs) {
            validate(job);
            if (job.getId() == null || job.getId().isBlank()) {
                job.setId(UUID.randomUUID().toString());
            }
            if (job.getCreatedAtMillis() == 0) {
                job.setCreatedAtMillis(nowMillis);
            }
            if (job.getNextRunAtMillis() == 0) {
                job.setNextRunAtMillis(nextFireAt(job, Instant.now()));
            }
            job.setUpdatedAtMillis(nowMillis);
            store.save(job);
            saved.add(job);
        }
        return saved;
    }

    /**
     * 查询全部定时任务
     * @return
     */
    public List<CronJob> list() {
        return store.list();
    }

    /**
     * 取消（删除）定时任务
     * @param id
     * @return
     */
    public synchronized Optional<CronJob> cancel(String id) {
        if (id == null) {
            return Optional.empty();
        }
        Optional<CronJob> removed = store.get(id);
        store.delete(id);
        return removed;
    }

    /**
     * 暂停定时任务，暂停后不再触发
     * @param id
     * @return
     */
    public synchronized Optional<CronJob> pause(String id) {
        if (id == null) {
            return Optional.empty();
        }
        Optional<CronJob> existing = store.get(id);
        existing.ifPresent(job -> {
            job.setEnabled(false);
            job.setUpdatedAtMillis(Instant.now().toEpochMilli());
            store.save(job);
        });
        return existing;
    }

    /**
     * 恢复定时任务，若下次触发时间已过则顺延到下一次
     * @param id
     * @return
     */
    public synchronized Optional<CronJob> resume(String id) {
        if (id == null) {
            return Optional.empty();
        }
        Optional<CronJob> existing = store.get(id);
        existing.ifPresent(job -> {
            job.setEnabled(true);
            long nowMillis = Instant.now().toEpochMilli();
            if (job.getNextRunAtMillis() <= nowMillis) {
                job.setNextRunAtMillis(nextFireAt(job, Instant.now()));
            }
            job.setUpdatedAtMillis(nowMillis);
            store.save(job);
        });
        return existing;
    }

    /**
     * 创建与调度器绑定的Agent工具集
     * @return
     */
    public List<AgentTool> tools() {
        return List.of(
                new CronScheduleTool(this),
                new CronListTool(this),
                new CronCancelTool(this),
                new CronPauseTool(this),
                new CronResumeTool(this));
    }

    /**
     * 一次巡检：收集到期任务并异步执行
     */
    private void tick() {
        List<CronJob> due;
        synchronized (this) {
            due = collectAndAdvance(Instant.now());
        }
        for (CronJob job : due) {
            fireAsync(job);
        }
    }

    /**
     * 收集到期任务，推进下次触发时间并保存运行状态
     * @param now
     * @return
     */
    private List<CronJob> collectAndAdvance(Instant now) {
        List<CronJob> due = new ArrayList<>();
        long nowMillis = now.toEpochMilli();
        for (CronJob job : store.list()) {
            if (!job.isEnabled() || job.getNextRunAtMillis() > nowMillis) {
                continue;
            }
            job.setNextRunAtMillis(nextFireAt(job, now));
            job.setRunCount(job.getRunCount() + 1);
            job.setLastRunAtMillis(nowMillis);
            job.setUpdatedAtMillis(nowMillis);
            store.save(job);
            due.add(job);
        }
        return due;
    }

    /**
     * 异步执行到期任务，可选运行锁保证多节点只执行一次
     * @param job
     */
    private void fireAsync(CronJob job) {
        if (runner == null) {
            log.warn("定时任务无执行器，跳过本次触发: id={}", job.getId());
            return;
        }
        long fireAtMillis = Instant.now().toEpochMilli();
        String runId = "cron:" + job.getId() + ":" + fireAtMillis;
        if (runLockStore != null && !runLockStore.tryLock(runId, nodeId, runLockTtl)) {
            log.info("定时任务运行锁获取失败，跳过本次触发: runId={}", runId);
            return;
        }
        workExecutor.submit(() -> {
            try {
                runner.run(job).block();
            } catch (Exception e) {
                log.error("定时任务执行失败: id={}, runId={}", job.getId(), runId, e);
            } finally {
                if (runLockStore != null) {
                    runLockStore.unlock(runId, nodeId);
                }
            }
        });
    }

    /**
     * 计算任务下一次触发时间
     * @param job
     * @param after
     * @return
     */
    private long nextFireAt(CronJob job, Instant after) {
        return CronExpression.parse(job.getCronExpression()).nextFireAfter(after, zone).toEpochMilli();
    }

    /**
     * 校验定时任务的表达式与指令
     * @param job
     */
    private void validate(CronJob job) {
        Objects.requireNonNull(job, "job不能为空");
        if (job.getCronExpression() == null || job.getCronExpression().isBlank()) {
            throw new IllegalArgumentException("cron表达式不能为空");
        }
        CronExpression.parse(job.getCronExpression());
        if (job.getInstruction() == null || job.getInstruction().isBlank()) {
            throw new IllegalArgumentException("执行指令不能为空");
        }
    }

    /**
     * 创建守护线程工厂
     * @param prefix
     * @param seq
     * @return
     */
    private static ThreadFactory daemonFactory(String prefix, AtomicInteger seq) {
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + "-" + seq.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    /**
     * 调度器构建器
     * @author yangqiong
     */
    public static class Builder {

        /**
         * 任务存储
         */
        private CronJobStore store;

        /**
         * 任务执行器
         */
        private CronTaskRunner runner;

        /**
         * 巡检间隔
         */
        private Duration pollInterval;

        /**
         * 节点标识
         */
        private String nodeId;

        /**
         * 运行锁存储
         */
        private RunLockStore runLockStore;

        /**
         * 运行锁持有时间
         */
        private Duration runLockTtl;

        /**
         * 触发时间计算时区
         */
        private ZoneId zone;

        /**
         * 设置任务存储
         * @param store
         * @return
         */
        public Builder store(CronJobStore store) {
            this.store = store;
            return this;
        }

        /**
         * 设置任务执行器
         * @param runner
         * @return
         */
        public Builder runner(CronTaskRunner runner) {
            this.runner = runner;
            return this;
        }

        /**
         * 设置巡检间隔
         * @param pollInterval
         * @return
         */
        public Builder pollInterval(Duration pollInterval) {
            this.pollInterval = pollInterval;
            return this;
        }

        /**
         * 设置节点标识
         * @param nodeId
         * @return
         */
        public Builder nodeId(String nodeId) {
            this.nodeId = nodeId;
            return this;
        }

        /**
         * 设置运行锁存储
         * @param runLockStore
         * @return
         */
        public Builder runLockStore(RunLockStore runLockStore) {
            this.runLockStore = runLockStore;
            return this;
        }

        /**
         * 设置运行锁持有时间
         * @param runLockTtl
         * @return
         */
        public Builder runLockTtl(Duration runLockTtl) {
            this.runLockTtl = runLockTtl;
            return this;
        }

        /**
         * 设置触发时间计算时区
         * @param zone
         * @return
         */
        public Builder zone(ZoneId zone) {
            this.zone = zone;
            return this;
        }

        /**
         * 构建调度器
         * @return
         */
        public CronScheduler build() {
            return new CronScheduler(this);
        }
    }
}
