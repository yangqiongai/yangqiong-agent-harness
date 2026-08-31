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
package com.yangqiong.agent.harness.observability.metrics;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micrometer.core.instrument.MeterRegistry;

import com.yangqiong.agent.harness.durable.ApprovalRecord;
import com.yangqiong.agent.harness.durable.ApprovalStore;

/**
 * 跨进程审批等待监控
 * <p>
 * 定期扫描 ApprovalStore 待审批记录，输出跨进程维度的当前等待状态指标：
 * pending计数、最大/平均已等待秒数（自createdAt起算，跨天/跨进程审批可见）。
 * ApprovalRecord 未记录落定时间戳，已完成的审批耗时由进程内 MetricsEventListener
 * 的 agent.approval.wait.duration 覆盖，两者互补。
 * </p>
 * @author yangqiong
 */
public final class ApprovalPendingMonitor implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ApprovalPendingMonitor.class);

    /**
     * 审批存储
     */
    private final ApprovalStore approvalStore;

    /**
     * 指标注册表
     */
    private final MeterRegistry registry;

    /**
     * 监控的租户范围列表
     */
    private final List<String> scopes;

    /**
     * 定时刷新调度器，refreshIntervalMs为0时不创建
     */
    private final ScheduledExecutorService scheduler;

    /**
     * 各scope的pending计数gauge持有（强引用防GC）
     */
    private final Map<String, AtomicLong> pendingCounts = new ConcurrentHashMap<>();

    /**
     * 各scope的最大等待秒数gauge持有
     */
    private final Map<String, AtomicLong> maxWaitSeconds = new ConcurrentHashMap<>();

    /**
     * 各scope的平均等待秒数gauge持有
     */
    private final Map<String, AtomicLong> avgWaitSeconds = new ConcurrentHashMap<>();

    /**
     * 构造审批等待监控
     * @param approvalStore 审批存储
     * @param registry 指标注册表
     * @param scopes 监控的租户范围列表，空列表时不采集
     * @param refreshIntervalMs 定时刷新间隔毫秒，0表示仅手动refresh
     */
    public ApprovalPendingMonitor(ApprovalStore approvalStore, MeterRegistry registry,
                                  Collection<String> scopes, long refreshIntervalMs) {
        this.approvalStore = approvalStore;
        this.registry = registry;
        this.scopes = scopes != null ? List.copyOf(scopes) : List.of();
        if (refreshIntervalMs > 0 && !this.scopes.isEmpty()) {
            this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "agent-approval-monitor");
                thread.setDaemon(true);
                return thread;
            });
            this.scheduler.scheduleWithFixedDelay(this::safeRefresh, refreshIntervalMs,
                    refreshIntervalMs, TimeUnit.MILLISECONDS);
        } else {
            this.scheduler = null;
        }
    }

    /**
     * 立即扫描全部scope并更新gauge
     */
    public void refresh() {
        long now = System.currentTimeMillis();
        for (String scope : scopes) {
            List<ApprovalRecord> pending = safeFindPending(scope);
            gauge(pendingCounts, scope, "agent.approval.pending.count").set(pending.size());
            gauge(maxWaitSeconds, scope, "agent.approval.pending.wait.seconds.max")
                    .set(maxWaitSeconds(pending, now));
            gauge(avgWaitSeconds, scope, "agent.approval.pending.wait.seconds.avg")
                    .set(avgWaitSeconds(pending, now));
        }
    }

    /**
     * 关闭监控：停掉定时任务，幂等
     */
    @Override
    public void close() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    /**
     * 注册或获取scope对应的gauge AtomicLong
     * @param holder gauge持有Map
     * @param scope
     * @param name 指标名
     * @return
     */
    private AtomicLong gauge(Map<String, AtomicLong> holder, String scope, String name) {
        return holder.computeIfAbsent(scope, s -> {
            AtomicLong value = new AtomicLong();
            registry.gauge(name, io.micrometer.core.instrument.Tags.of("scope", s), value);
            return value;
        });
    }

    /**
     * 计算最大已等待秒数
     * @param pending
     * @param now
     * @return
     */
    private static long maxWaitSeconds(List<ApprovalRecord> pending, long now) {
        long max = 0L;
        for (ApprovalRecord record : pending) {
            max = Math.max(max, now - record.getCreatedAt());
        }
        return max / 1000L;
    }

    /**
     * 计算平均已等待秒数
     * @param pending
     * @param now
     * @return
     */
    private static long avgWaitSeconds(List<ApprovalRecord> pending, long now) {
        if (pending.isEmpty()) {
            return 0L;
        }
        long total = 0L;
        for (ApprovalRecord record : pending) {
            total += Math.max(0L, now - record.getCreatedAt());
        }
        return total / pending.size() / 1000L;
    }

    /**
     * 查询待审批记录，异常时返回空列表保证调度循环不被终止
     * @param scope
     * @return
     */
    private List<ApprovalRecord> safeFindPending(String scope) {
        try {
            return approvalStore.findPending(scope);
        } catch (Exception e) {
            log.warn("[ApprovalPendingMonitor] 扫描待审批记录失败, scope={}: {}", scope, e.getMessage());
            return List.of();
        }
    }

    /**
     * 定时任务入口：捕获一切异常防止调度任务被终止
     */
    private void safeRefresh() {
        try {
            refresh();
        } catch (Exception e) {
            log.warn("[ApprovalPendingMonitor] 定时刷新异常: {}", e.getMessage());
        }
    }

    /**
     * 监控的scope列表（不可变快照，供测试断言）
     * @return
     */
    List<String> monitoredScopes() {
        return new ArrayList<>(scopes);
    }
}
