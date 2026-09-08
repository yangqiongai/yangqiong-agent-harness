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
package com.yangqiongai.agent.harness.observability.metrics;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * Agent指标集
 * <p>
 * 全部Agent运行指标的定义与登记入口，由 MetricsEventListener 驱动写入。
 * 会话级高基维度（sessionId/userId）不进tag防止tag爆炸，仅agent/model/tool/kind进入。
 * </p>
 * @author yangqiong
 */
final class AgentMetrics {

    /**
     * 空值统一回退标签
     */
    private static final String UNKNOWN = "unknown";

    /**
     * 指标注册表
     */
    private final MeterRegistry registry;

    /**
     * 活跃会话数，gauge保持强引用防GC回收
     */
    private final AtomicLong activeSessions = new AtomicLong();

    /**
     * 构造并登记活跃会话gauge
     * @param registry
     */
    AgentMetrics(MeterRegistry registry) {
        this.registry = registry;
        registry.gauge("agent.sessions.active", activeSessions);
    }

    /**
     * 运行开始：活跃会话数加一
     */
    void onRunStart() {
        activeSessions.incrementAndGet();
    }

    /**
     * 运行结束：计数、耗时与活跃会话数
     * @param agent
     * @param result
     * @param durationNanos
     */
    void onRunEnd(String agent, String result, long durationNanos) {
        registry.counter("agent.run.total", "agent", tag(agent), "result", tag(result)).increment();
        Timer.builder("agent.run.duration")
                .tag("agent", tag(agent))
                .register(registry)
                .record(durationNanos, TimeUnit.NANOSECONDS);
        activeSessions.decrementAndGet();
    }

    /**
     * 模型调用结束：调用计数
     * @param agent
     * @param model
     */
    void onModelCallEnd(String agent, String model) {
        registry.counter("agent.model.calls", "agent", tag(agent), "model", tag(model)).increment();
    }

    /**
     * 模型调用延迟
     * @param agent
     * @param model
     * @param durationNanos
     */
    void onModelLatency(String agent, String model, long durationNanos) {
        Timer.builder("agent.model.latency")
                .tag("agent", tag(agent))
                .tag("model", tag(model))
                .register(registry)
                .record(durationNanos, TimeUnit.NANOSECONDS);
    }

    /**
     * Token用量
     * @param agent
     * @param model
     * @param kind prompt或completion
     * @param tokens
     */
    void onTokens(String agent, String model, String kind, long tokens) {
        DistributionSummary.builder("agent.tokens")
                .tag("agent", tag(agent))
                .tag("model", tag(model))
                .tag("kind", kind)
                .register(registry)
                .record(tokens);
    }

    /**
     * 成本累计（美元）
     * @param agent
     * @param model
     * @param costUsd
     */
    void onCost(String agent, String model, double costUsd) {
        if (costUsd <= 0.0) {
            return;
        }
        registry.counter("agent.cost.usd", "agent", tag(agent), "model", tag(model)).increment(costUsd);
    }

    /**
     * 工具调用结果：计数与耗时
     * @param tool
     * @param error
     * @param durationNanos 配对失败时传-1跳过耗时
     */
    void onToolCall(String tool, boolean error, long durationNanos) {
        if (error) {
            registry.counter("agent.tool.errors", "tool", tag(tool)).increment();
        } else {
            registry.counter("agent.tool.calls", "tool", tag(tool)).increment();
        }
        if (durationNanos >= 0) {
            Timer.builder("agent.tool.duration")
                    .tag("tool", tag(tool))
                    .register(registry)
                    .record(durationNanos, TimeUnit.NANOSECONDS);
        }
    }

    /**
     * 审批请求计数
     * @param agent
     */
    void onApprovalRequested(String agent) {
        registry.counter("agent.approval.requested", "agent", tag(agent)).increment();
    }

    /**
     * 审批等待耗时
     * @param agent
     * @param durationNanos
     */
    void onApprovalWait(String agent, long durationNanos) {
        Timer.builder("agent.approval.wait.duration")
                .tag("agent", tag(agent))
                .register(registry)
                .record(durationNanos, TimeUnit.NANOSECONDS);
    }

    /**
     * 预算越限计数
     * @param kind token或cost
     * @param level warn或exceeded
     */
    void onBudgetEvent(String kind, String level) {
        registry.counter("agent.budget.events", "kind", tag(kind), "level", tag(level)).increment();
    }

    /**
     * 标签值清洗，null/空白回退unknown
     * @param value
     * @return
     */
    private static String tag(String value) {
        return value == null || value.isBlank() ? UNKNOWN : value;
    }
}
