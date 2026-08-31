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
package com.yangqiong.agent.harness.model.routing;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;

/**
 * 模型健康登记
 * @author yangqiong
 */
public class ModelHealthRegistry {

    /**
     * 滑动窗口大小，保留最近20次成败记录
     */
    private static final int WINDOW_SIZE = 20;

    /**
     * 连续失败达到该次数标记不健康
     */
    private static final int UNHEALTHY_CONSECUTIVE_FAILURES = 5;

    /**
     * 默认冷却期毫秒数
     */
    private static final long DEFAULT_COOLDOWN_MILLIS = 60_000L;

    /**
     * 各模型健康状态
     */
    private final ConcurrentHashMap<String, HealthState> states = new ConcurrentHashMap<>();

    /**
     * 不健康冷却期毫秒数
     */
    private final long cooldownMillis;

    /**
     * 时钟提供者，供测试注入
     */
    private final LongSupplier clock;

    public ModelHealthRegistry() {
        this(DEFAULT_COOLDOWN_MILLIS, System::currentTimeMillis);
    }

    /**
     * 指定冷却期与时钟构建健康登记
     * @param cooldownMillis
     * @param clock
     */
    ModelHealthRegistry(long cooldownMillis, LongSupplier clock) {
        this.cooldownMillis = cooldownMillis;
        this.clock = clock;
    }

    /**
     * 登记一次模型调用成功
     * @param modelCode
     */
    public void recordSuccess(String modelCode) {
        stateOf(modelCode).record(true, clock.getAsLong());
    }

    /**
     * 登记一次模型调用失败
     * @param modelCode
     */
    public void recordFailure(String modelCode) {
        stateOf(modelCode).record(false, clock.getAsLong());
    }

    /**
     * 判断模型是否健康，无记录视为健康，连续失败达到阈值进入冷却期，冷却期满恢复可重试
     * @param modelCode
     * @return
     */
    public boolean isHealthy(String modelCode) {
        HealthState state = states.get(modelCode);
        if (state == null) {
            return true;
        }
        return state.isHealthy(clock.getAsLong(), cooldownMillis);
    }

    /**
     * 获取模型健康分（0-1），等于滑动窗口内成功率，无记录返回1.0
     * @param modelCode
     * @return
     */
    public double healthScore(String modelCode) {
        HealthState state = states.get(modelCode);
        if (state == null) {
            return 1.0d;
        }
        return state.successRate();
    }

    /**
     * 获取或创建模型健康状态
     * @param modelCode
     * @return
     */
    private HealthState stateOf(String modelCode) {
        return states.computeIfAbsent(modelCode, key -> new HealthState());
    }

    /**
     * 模型健康状态
     * @author yangqiong
     */
    private static final class HealthState {

        /**
         * 最近成败记录窗口
         */
        private final CopyOnWriteArrayList<Boolean> window = new CopyOnWriteArrayList<>();

        /**
         * 连续失败次数
         */
        private final AtomicInteger consecutiveFailures = new AtomicInteger();

        /**
         * 最近一次失败时间戳
         */
        private volatile long lastFailureTimeMillis;

        /**
         * 记录一次调用结果并维护窗口、连续失败计数与失败时间
         * @param success
         * @param now
         */
        void record(boolean success, long now) {
            window.add(success);
            while (window.size() > WINDOW_SIZE) {
                window.remove(0);
            }
            if (success) {
                consecutiveFailures.set(0);
            } else {
                consecutiveFailures.incrementAndGet();
                lastFailureTimeMillis = now;
            }
        }

        /**
         * 判断是否健康，连续失败达到阈值时进入冷却期，冷却期满后允许重试
         * @param now
         * @param cooldownMillis
         * @return
         */
        boolean isHealthy(long now, long cooldownMillis) {
            if (consecutiveFailures.get() < UNHEALTHY_CONSECUTIVE_FAILURES) {
                return true;
            }
            return now - lastFailureTimeMillis >= cooldownMillis;
        }

        /**
         * 计算窗口内成功率，窗口为空返回1.0
         * @return
         */
        double successRate() {
            List<Boolean> snapshot = window;
            if (snapshot.isEmpty()) {
                return 1.0d;
            }
            long successCount = snapshot.stream().filter(Boolean::booleanValue).count();
            return (double) successCount / snapshot.size();
        }
    }
}
