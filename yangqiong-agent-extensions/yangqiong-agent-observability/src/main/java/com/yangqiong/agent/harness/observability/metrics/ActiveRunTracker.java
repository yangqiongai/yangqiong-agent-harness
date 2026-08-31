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

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 活跃运行追踪器
 * <p>
 * 维护 AGENT_START 到终态之间的运行状态，支撑运行耗时与审批等待时长指标。
 * 事件流暂无会话标识：ERROR/INTERRUPTED 到 AGENT_END 在同一运行管道内相邻串行发射，
 * 因此采用相邻标志位法标记最近一次失败/中断归属（并发同名Agent运行时为 best-effort）。
 * </p>
 * @author yangqiong
 */
final class ActiveRunTracker {

    /**
     * 活跃运行状态
     */
    private final ConcurrentHashMap<String, RunState> activeRuns = new ConcurrentHashMap<>();

    /**
     * 相邻ERROR标志：下一个结束的运行以error归因
     */
    private volatile boolean errorFlag;

    /**
     * 相邻INTERRUPTED标志：下一个结束的运行以interrupted归因
     */
    private volatile boolean interruptedFlag;

    /**
     * 运行结束结果
     * @param agent Agent名
     * @param result 结果标签（completed/error/interrupted）
     * @param durationNanos 运行耗时纳秒
     * @param approvalWaitNanos 审批等待耗时纳秒，未发生审批为0
     */
    record RunEnd(String agent, String result, long durationNanos, long approvalWaitNanos) {
    }

    /**
     * 单个运行状态
     */
    private static final class RunState {

        /**
         * 运行开始纳秒
         */
        private final long startNanos;

        /**
         * 审批等待起始纳秒，0表示未发生审批
         */
        private volatile long awaitingApprovalSince;

        private RunState(long startNanos) {
            this.startNanos = startNanos;
        }
    }

    /**
     * 记录运行开始，同名Agent重入时新运行覆盖旧悬置状态
     * @param agent
     */
    void onRunStart(String agent) {
        activeRuns.put(agent, new RunState(System.nanoTime()));
    }

    /**
     * 标记相邻ERROR，归因到下一个结束的运行
     */
    void onError() {
        errorFlag = true;
    }

    /**
     * 标记相邻INTERRUPTED，归因到下一个结束的运行
     */
    void onInterrupted() {
        interruptedFlag = true;
    }

    /**
     * 标记全部活跃运行进入审批等待
     */
    void onApprovalRequested() {
        long now = System.nanoTime();
        for (RunState state : activeRuns.values()) {
            if (state.awaitingApprovalSince == 0L) {
                state.awaitingApprovalSince = now;
            }
        }
    }

    /**
     * 获取当前活跃Agent名列表
     * @return
     */
    List<String> activeAgents() {
        return List.copyOf(activeRuns.keySet());
    }

    /**
     * 取走运行结束结果，无活跃运行返回null
     * @param agent
     * @return
     */
    RunEnd takeRunEnd(String agent) {
        RunState state = agent != null ? activeRuns.remove(agent) : null;
        if (state == null) {
            return null;
        }
        String result = errorFlag ? "error" : interruptedFlag ? "interrupted" : "completed";
        errorFlag = false;
        interruptedFlag = false;
        long now = System.nanoTime();
        long approvalWait = state.awaitingApprovalSince > 0L ? now - state.awaitingApprovalSince : 0L;
        return new RunEnd(agent, result, now - state.startNanos, approvalWait);
    }

    /**
     * 活跃运行数
     * @return
     */
    int activeCount() {
        return activeRuns.size();
    }
}
