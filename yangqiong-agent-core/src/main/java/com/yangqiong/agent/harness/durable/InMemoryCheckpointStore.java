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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 检查点存储内存实现
 * <p>
 * 按复合键 (scopeId, sessionId) 隔离，保存时校验版本单调递增，
 * 并发写冲突抛出异常防止旧版本覆盖新版本。
 * </p>
 * @author yangqiong
 */
public class InMemoryCheckpointStore implements CheckpointStore {

    /**
     * 最大会话键数，超限淘汰进度最旧检查点（连同其runId历史），防止内存无界增长
     */
    public static final int MAX_SESSION_KEYS = 1000;

    /**
     * 最大runId历史数，超限淘汰进度最旧历史（连同latest引用），防止内存无界增长
     */
    public static final int MAX_RUN_HISTORIES = 1000;

    /**
     * 按复合键索引的最近检查点
     */
    private final ConcurrentHashMap<String, AgentCheckpoint> latestByKey = new ConcurrentHashMap<>();

    /**
     * 按runId索引的检查点历史
     */
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<AgentCheckpoint>> historyByRunId = new ConcurrentHashMap<>();

    @Override
    public void save(AgentCheckpoint checkpoint) {
        if (checkpoint == null || checkpoint.getRunId() == null) {
            throw new IllegalArgumentException("检查点或runId不能为空");
        }
        String key = ScopeKeys.key(checkpoint.getScopeId(), checkpoint.getSessionId());
        latestByKey.compute(key, (k, current) -> {
            if (current != null && checkpoint.getVersion() <= current.getVersion()) {
                throw new IllegalStateException("检查点版本冲突: expect>" + current.getVersion()
                        + " actual=" + checkpoint.getVersion() + ", runId=" + checkpoint.getRunId());
            }
            return checkpoint;
        });
        historyByRunId.computeIfAbsent(checkpoint.getRunId(), k -> new CopyOnWriteArrayList<>())
                .add(checkpoint);
        evictOverflow(checkpoint.getRunId());
    }

    /**
     * 容量超限时淘汰进度最旧条目并保持两索引一致
     * @param protectedRunId 本次保存的runId，禁止作为淘汰对象
     */
    private void evictOverflow(String protectedRunId) {
        if (latestByKey.size() > MAX_SESSION_KEYS) {
            latestByKey.values().stream()
                    .filter(c -> !protectedRunId.equals(c.getRunId()))
                    .min(Comparator.comparingLong(AgentCheckpoint::getVersion))
                    .ifPresent(victim -> {
                        latestByKey.remove(ScopeKeys.key(victim.getScopeId(), victim.getSessionId()), victim);
                        historyByRunId.remove(victim.getRunId());
                    });
        }
        if (historyByRunId.size() > MAX_RUN_HISTORIES) {
            historyByRunId.keySet().stream()
                    .filter(runId -> !protectedRunId.equals(runId))
                    .min(Comparator.comparingLong(this::maxVersionOf))
                    .ifPresent(victimRunId -> {
                        historyByRunId.remove(victimRunId);
                        latestByKey.values().stream()
                                .filter(c -> victimRunId.equals(c.getRunId()))
                                .findFirst()
                                .ifPresent(victim -> latestByKey.remove(
                                        ScopeKeys.key(victim.getScopeId(), victim.getSessionId()), victim));
                    });
        }
    }

    /**
     * 读取指定runId历史中的最大版本号
     * @param runId
     * @return
     */
    private long maxVersionOf(String runId) {
        List<AgentCheckpoint> history = historyByRunId.get(runId);
        return history != null && !history.isEmpty()
                ? history.stream().mapToLong(AgentCheckpoint::getVersion).max().orElse(0L) : 0L;
    }

    @Override
    public Optional<AgentCheckpoint> latest(String scopeId, String sessionId) {
        return Optional.ofNullable(latestByKey.get(ScopeKeys.key(scopeId, sessionId)));
    }

    @Override
    public List<AgentCheckpoint> findByRunId(String runId) {
        List<AgentCheckpoint> history = historyByRunId.get(runId);
        if (history == null) {
            return List.of();
        }
        List<AgentCheckpoint> sorted = new ArrayList<>(history);
        sorted.sort(Comparator.comparingLong(AgentCheckpoint::getVersion));
        return sorted;
    }

    @Override
    public void clear(String runId) {
        if (runId == null) {
            return;
        }
        List<AgentCheckpoint> history = historyByRunId.remove(runId);
        if (history != null) {
            for (AgentCheckpoint checkpoint : history) {
                String key = ScopeKeys.key(checkpoint.getScopeId(), checkpoint.getSessionId());
                latestByKey.computeIfPresent(key, (k, current) ->
                        current.getRunId().equals(runId) ? null : current);
            }
        }
    }
}
