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

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import com.yangqiongai.agent.harness.core.event.ModelCallInfo;
import com.yangqiongai.agent.harness.core.event.ModelCallResult;
import com.yangqiongai.agent.harness.core.message.AgentToolResultBlock;
import com.yangqiongai.agent.harness.core.message.AgentToolUseBlock;

/**
 * 调用中悬置状态配对器
 * <p>
 * 通过 START/END 事件载荷中的稳定键（模型调用为 agent+model+iteration，
 * 工具调用为 toolUseId）配对计算耗时。事件流暂无会话标识，
 * 同名Agent并发运行时配对为 best-effort，配对失败仅跳过耗时统计不影响计数。
 * </p>
 * @author yangqiong
 */
final class PendingCallTracker {

    /**
     * 悬置表容量上限，超过时清空防止中断路径泄漏
     */
    private static final int MAX_PENDING = 1024;

    /**
     * 悬置模型调用：键 agent|model|iteration -> 开始纳秒
     */
    private final ConcurrentHashMap<String, AtomicLong> pendingModelCalls = new ConcurrentHashMap<>();

    /**
     * 悬置工具调用：键 toolUseId -> 工具调用起始状态
     */
    private final ConcurrentHashMap<String, ToolTiming> pendingToolCalls = new ConcurrentHashMap<>();

    /**
     * 工具调用配对结果
     * @param toolName 工具名，未配对到START时为unknown
     * @param durationNanos 耗时纳秒，未配对时为-1
     * @param error 是否错误结果
     */
    record ToolOutcome(String toolName, long durationNanos, boolean error) {
    }

    /**
     * 工具调用起始状态
     * @param toolName 工具名
     * @param startNanos 开始纳秒
     */
    private record ToolTiming(String toolName, long startNanos) {
    }

    /**
     * 记录模型调用开始
     * @param info
     */
    void onModelCallStart(ModelCallInfo info) {
        if (info == null) {
            return;
        }
        evictIfNeeded(pendingModelCalls);
        pendingModelCalls.put(modelKey(info.getAgentName(), info.getModelName(), info.getIteration()),
                new AtomicLong(System.nanoTime()));
    }

    /**
     * 取走模型调用耗时纳秒，未配对返回null
     * @param result
     * @return
     */
    Long takeModelDurationNanos(ModelCallResult result) {
        if (result == null) {
            return null;
        }
        AtomicLong start = pendingModelCalls.remove(
                modelKey(result.getAgentName(), result.getModelName(), result.getIteration()));
        return start != null ? System.nanoTime() - start.get() : null;
    }

    /**
     * 记录一批工具调用开始
     * @param toolCalls
     */
    void onToolCallStart(List<AgentToolUseBlock> toolCalls) {
        if (toolCalls == null || toolCalls.isEmpty()) {
            return;
        }
        evictIfNeeded(pendingToolCalls);
        long now = System.nanoTime();
        for (AgentToolUseBlock block : toolCalls) {
            if (block.getToolUseId() != null) {
                pendingToolCalls.put(block.getToolUseId(),
                        new ToolTiming(block.getToolName(), now));
            }
        }
    }

    /**
     * 取走一批工具调用配对结果，未配对到START的仅返回计数信息
     * @param results
     * @return
     */
    List<ToolOutcome> takeToolOutcomes(List<AgentToolResultBlock> results) {
        if (results == null || results.isEmpty()) {
            return List.of();
        }
        long now = System.nanoTime();
        java.util.ArrayList<ToolOutcome> outcomes = new java.util.ArrayList<>(results.size());
        for (AgentToolResultBlock block : results) {
            String toolUseId = block.getToolUseId();
            ToolTiming timing = toolUseId != null ? pendingToolCalls.remove(toolUseId) : null;
            if (timing != null) {
                outcomes.add(new ToolOutcome(timing.toolName(), now - timing.startNanos(), block.isError()));
            } else {
                outcomes.add(new ToolOutcome("unknown", -1L, block.isError()));
            }
        }
        return outcomes;
    }

    /**
     * 悬置表超限时整体清空，防止中断路径的悬置项泄漏
     * @param pendingMap
     */
    private static void evictIfNeeded(ConcurrentHashMap<?, ?> pendingMap) {
        if (pendingMap.size() >= MAX_PENDING) {
            pendingMap.clear();
        }
    }

    /**
     * 模型调用配对键
     * @param agentName
     * @param modelName
     * @param iteration
     * @return
     */
    private static String modelKey(String agentName, String modelName, int iteration) {
        return agentName + "|" + modelName + "|" + iteration;
    }
}
