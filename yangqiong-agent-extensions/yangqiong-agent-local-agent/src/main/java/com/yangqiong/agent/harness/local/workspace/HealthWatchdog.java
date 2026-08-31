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
package com.yangqiong.agent.harness.local.workspace;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.yangqiong.agent.harness.core.event.AgentEvent;
import com.yangqiong.agent.harness.core.event.AgentEventType;
import com.yangqiong.agent.harness.event.AgentEventListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 监控告警与自愈
 * <p>
 * 作为运行时事件监听器旁路消费异常与完成事件：连续异常达到阈值触发自愈回调，
 * 同时以周期自检检测执行停滞（心跳超时），异常时派发子智能体介入处理，
 * 实现无人值守的异常自愈闭环。
 * </p>
 * @author yangqiong
 */
public class HealthWatchdog implements AgentEventListener {

    /**
     * 日志
     */
    private static final Logger log = LoggerFactory.getLogger(HealthWatchdog.class);

    /**
     * 自检线程名
     */
    private static final String THREAD_NAME = "health-watchdog";

    /**
     * 视为异常的Agent事件类型
     */
    private static final Set<AgentEventType> ALARM_TYPES = Set.of(
            AgentEventType.ERROR,
            AgentEventType.TOKEN_BUDGET_EXCEEDED,
            AgentEventType.COST_BUDGET_EXCEEDED);

    /**
     * 视为健康心跳的事件类型
     */
    private static final Set<AgentEventType> HEARTBEAT_TYPES = Set.of(
            AgentEventType.AGENT_START,
            AgentEventType.AGENT_RESULT,
            AgentEventType.COMPLETED,
            AgentEventType.INTERRUPTED);

    /**
     * 触发自愈的连续异常阈值
     */
    private final int alarmThreshold;

    /**
     * 心跳超时时长，超过视为执行停滞
     */
    private final Duration stallTimeout;

    /**
     * 周期自检间隔
     */
    private final Duration checkInterval;

    /**
     * 自愈回调，宿主可派发子智能体介入处理
     */
    private volatile Runnable recoveryAction;

    /**
     * 连续异常计数
     */
    private final AtomicInteger consecutiveAlarms = new AtomicInteger();

    /**
     * 已触发的自愈次数
     */
    private final AtomicInteger recoveryCount = new AtomicInteger();

    /**
     * 最近心跳时间（纳秒）
     */
    private volatile long lastHeartbeatNanos = System.nanoTime();

    /**
     * 运行中的并发状态
     */
    private volatile boolean running;

    /**
     * 周期自检调度器
     */
    private volatile ScheduledExecutorService scheduler;

    /**
     * 构造
     * @param alarmThreshold
     * @param stallTimeout
     * @param checkInterval
     */
    public HealthWatchdog(int alarmThreshold, Duration stallTimeout, Duration checkInterval) {
        if (alarmThreshold <= 0) {
            throw new IllegalArgumentException("异常阈值必须为正数: " + alarmThreshold);
        }
        if (stallTimeout == null || stallTimeout.isZero() || stallTimeout.isNegative()) {
            throw new IllegalArgumentException("停滞超时必须为正时长");
        }
        if (checkInterval == null || checkInterval.isZero() || checkInterval.isNegative()) {
            throw new IllegalArgumentException("自检间隔必须为正时长");
        }
        this.alarmThreshold = alarmThreshold;
        this.stallTimeout = stallTimeout;
        this.checkInterval = checkInterval;
    }

    /**
     * 设置自愈回调，宿主可在此派发子智能体介入
     * @param recoveryAction
     * @return
     */
    public HealthWatchdog recoveryAction(Runnable recoveryAction) {
        this.recoveryAction = recoveryAction;
        return this;
    }

    /**
     * 启动周期自检线程
     * @return
     */
    public HealthWatchdog start() {
        if (running) {
            return this;
        }
        running = true;
        scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, THREAD_NAME);
            thread.setDaemon(true);
            return thread;
        });
        scheduler.scheduleWithFixedDelay(this::runSelfCheck, checkInterval.toMillis(),
                checkInterval.toMillis(), TimeUnit.MILLISECONDS);
        return this;
    }

    /**
     * 停止周期自检线程
     */
    public void stop() {
        running = false;
        ScheduledExecutorService existing = scheduler;
        scheduler = null;
        if (existing != null) {
            existing.shutdownNow();
        }
    }

    /**
     * 是否关注该类事件：仅消费告警与心跳事件
     * @param type
     * @return
     */
    @Override
    public boolean isInterestedIn(AgentEventType type) {
        return ALARM_TYPES.contains(type) || HEARTBEAT_TYPES.contains(type);
    }

    /**
     * 处理运行时事件：告警事件累计计数并超阈值自愈，心跳事件更新心跳并清零连续异常
     * @param event
     */
    @Override
    public void onEvent(AgentEvent event) {
        if (ALARM_TYPES.contains(event.getType())) {
            int count = consecutiveAlarms.incrementAndGet();
            log.warn("监控捕获异常事件，连续告警数={}: {}", count, event.getType());
            if (count >= alarmThreshold) {
                triggerRecovery();
            }
            return;
        }
        if (HEARTBEAT_TYPES.contains(event.getType())) {
            lastHeartbeatNanos = System.nanoTime();
            consecutiveAlarms.set(0);
        }
    }

    /**
     * 周期自检：心跳超时判定执行停滞并触发自愈
     */
    public void runSelfCheck() {
        if (!running) {
            return;
        }
        if (isStalled()) {
            log.warn("检测到执行停滞，心跳超时，触发自愈");
            triggerRecovery();
        }
    }

    /**
     * 判断当前是否处于停滞状态（心跳超时）
     * @return
     */
    public boolean isStalled() {
        long elapsedNanos = System.nanoTime() - lastHeartbeatNanos;
        return elapsedNanos > stallTimeout.toNanos();
    }

    /**
     * 触发自愈：调用配置的回调，异步执行避免阻塞事件分发线程
     */
    private void triggerRecovery() {
        Runnable action = recoveryAction;
        if (action == null) {
            return;
        }
        recoveryCount.incrementAndGet();
        consecutiveAlarms.set(0);
        Thread thread = new Thread(action, "health-recovery");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * 获取已触发的自愈次数
     * @return
     */
    public int recoveryCount() {
        return recoveryCount.get();
    }

    /**
     * 获取当前连续异常计数
     * @return
     */
    public int consecutiveAlarms() {
        return consecutiveAlarms.get();
    }

    /**
     * 测试专用：把最近心跳回拨到很久以前，模拟停滞
     */
    void simulateDeadlineElapsed() {
        lastHeartbeatNanos -= TimeUnit.DAYS.toNanos(1);
    }
}