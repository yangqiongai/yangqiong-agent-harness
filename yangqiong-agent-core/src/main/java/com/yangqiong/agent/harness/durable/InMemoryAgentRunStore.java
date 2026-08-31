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
package com.yangqiong.agent.harness.durable;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 运行存储内存实现
 * @author yangqiong
 */
public class InMemoryAgentRunStore implements AgentRunStore {

    /**
     * 最大运行记录数，超限淘汰创建时间最旧的记录，防止内存无界增长
     */
    public static final int MAX_RUNS = 1000;

    /**
     * 按runId索引的运行记录
     */
    private final ConcurrentHashMap<String, AgentRunRecord> byRunId = new ConcurrentHashMap<>();

    /**
     * 按复合键索引的运行记录列表
     */
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<AgentRunRecord>> bySession = new ConcurrentHashMap<>();

    @Override
    public AgentRunRecord create(AgentRunRecord record) {
        if (record == null || record.getRunId() == null) {
            throw new IllegalArgumentException("运行记录或runId不能为空");
        }
        if (byRunId.putIfAbsent(record.getRunId(), record) != null) {
            throw new IllegalStateException("运行记录已存在: " + record.getRunId());
        }
        bySession.computeIfAbsent(
                        ScopeKeys.key(record.getScopeId(), record.getSessionId()),
                        k -> new CopyOnWriteArrayList<>())
                .add(record);
        evictOverflow();
        return record;
    }

    /**
     * 容量超限时淘汰创建时间最旧的运行记录并保持两索引一致
     */
    private void evictOverflow() {
        if (byRunId.size() <= MAX_RUNS) {
            return;
        }
        byRunId.values().stream()
                .min(Comparator.comparingLong(AgentRunRecord::getCreatedAt))
                .ifPresent(victim -> {
                    byRunId.remove(victim.getRunId(), victim);
                    String sessionKey = ScopeKeys.key(victim.getScopeId(), victim.getSessionId());
                    List<AgentRunRecord> runs = bySession.get(sessionKey);
                    if (runs != null) {
                        runs.remove(victim);
                        if (runs.isEmpty()) {
                            bySession.remove(sessionKey, runs);
                        }
                    }
                });
    }

    @Override
    public Optional<AgentRunRecord> findByRunId(String runId) {
        return Optional.ofNullable(byRunId.get(runId));
    }

    @Override
    public Optional<AgentRunRecord> findLatestBySession(String scopeId, String sessionId) {
        List<AgentRunRecord> runs = bySession.get(ScopeKeys.key(scopeId, sessionId));
        if (runs == null || runs.isEmpty()) {
            return Optional.empty();
        }
        return runs.stream()
                .max(Comparator.comparingLong(AgentRunRecord::getCreatedAt));
    }

    @Override
    public List<AgentRunRecord> findBySession(String scopeId, String sessionId) {
        List<AgentRunRecord> runs = bySession.get(ScopeKeys.key(scopeId, sessionId));
        return runs != null ? List.copyOf(runs) : List.of();
    }

    @Override
    public AgentRunRecord saveTransition(AgentRunRecord record) {
        AgentRunRecord stored = byRunId.get(record.getRunId());
        if (stored == null) {
            throw new IllegalStateException("运行记录不存在: " + record.getRunId());
        }
        return stored;
    }

    @Override
    public List<AgentRunRecord> findWaitingApproval(String scopeId) {
        return byRunId.values().stream()
                .filter(r -> r.getState() == AgentRunState.WAITING_APPROVAL)
                .filter(r -> scopeId == null || scopeId.equals(r.getScopeId()))
                .toList();
    }
}
