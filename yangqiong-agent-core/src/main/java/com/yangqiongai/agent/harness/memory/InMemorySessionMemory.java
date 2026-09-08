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
package com.yangqiongai.agent.harness.memory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import com.yangqiongai.agent.harness.core.memory.SessionMemory;
import com.yangqiongai.agent.harness.core.memory.SessionSummary;
import com.yangqiongai.agent.harness.core.message.AgentMessage;
import com.yangqiongai.agent.harness.durable.ScopeKeys;

/**
 * 会话级短期记忆内存默认实现
 * <p>
 * 按(scopeId, sessionId)复合键隔离存储运行摘要与情节消息，线程安全。
 * 作为L2默认实现，外部可替换为Redis/DB等持久化实现。
 * </p>
 * @author yangqiong
 */
public class InMemorySessionMemory implements SessionMemory {

    /**
     * 最大会话数，超限淘汰一个已有会话键，防止内存无界增长
     */
    public static final int MAX_SESSIONS = 1000;

    /**
     * 单会话最大情节消息数，超限淘汰最旧情节
     */
    public static final int MAX_EPISODES_PER_SESSION = 200;

    /**
     * 按复合键隔离的运行摘要
     */
    private final Map<String, SessionSummary> summaries = new ConcurrentHashMap<>();

    /**
     * 按复合键隔离的情节消息
     */
    private final Map<String, List<AgentMessage>> episodes = new ConcurrentHashMap<>();

    @Override
    public void saveSummary(String sessionId, String summary, int summarizedMessageCount) {
        saveSummary(null, sessionId, summary, summarizedMessageCount);
    }

    @Override
    public void saveSummary(String scopeId, String sessionId, String summary, int summarizedMessageCount) {
        if (sessionId == null) {
            return;
        }
        String key = ScopeKeys.key(scopeId, sessionId);
        if (!summaries.containsKey(key)) {
            evictOverflow(key);
        }
        summaries.put(key, new SessionSummary(summary, summarizedMessageCount));
    }

    /**
     * 会话数超限时淘汰一个已有会话键（摘要与情节同步清理）
     * @param protectedKey 本次写入的会话键，禁止作为淘汰对象
     */
    private void evictOverflow(String protectedKey) {
        if (summaries.size() + episodes.size() < MAX_SESSIONS) {
            return;
        }
        String victim = episodes.keySet().stream()
                .filter(k -> !k.equals(protectedKey))
                .findFirst()
                .orElseGet(() -> summaries.keySet().stream()
                        .filter(k -> !k.equals(protectedKey))
                        .findFirst()
                        .orElse(null));
        if (victim != null) {
            summaries.remove(victim);
            episodes.remove(victim);
        }
    }

    @Override
    public SessionSummary loadSummary(String sessionId) {
        return loadSummary(null, sessionId);
    }

    @Override
    public SessionSummary loadSummary(String scopeId, String sessionId) {
        if (sessionId == null) {
            return null;
        }
        return summaries.get(ScopeKeys.key(scopeId, sessionId));
    }

    @Override
    public void saveEpisode(String sessionId, AgentMessage episode) {
        saveEpisode(null, sessionId, episode);
    }

    /**
     * 按租户作用域保存一条关键情节消息
     * @param scopeId
     * @param sessionId
     * @param episode
     */
    public void saveEpisode(String scopeId, String sessionId, AgentMessage episode) {
        if (sessionId == null || episode == null) {
            return;
        }
        String key = ScopeKeys.key(scopeId, sessionId);
        if (!episodes.containsKey(key)) {
            evictOverflow(key);
        }
        List<AgentMessage> list = episodes.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>());
        list.add(episode);
        // 单会话情节超限淘汰最旧条目
        if (list.size() > MAX_EPISODES_PER_SESSION) {
            list.remove(0);
        }
    }

    @Override
    public List<AgentMessage> listEpisodes(String sessionId, int limit) {
        return listEpisodes(null, sessionId, limit);
    }

    /**
     * 按租户作用域列出会话最近的情节消息
     * @param scopeId
     * @param sessionId
     * @param limit
     * @return
     */
    public List<AgentMessage> listEpisodes(String scopeId, String sessionId, int limit) {
        if (sessionId == null) {
            return Collections.emptyList();
        }
        List<AgentMessage> list = episodes.get(ScopeKeys.key(scopeId, sessionId));
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }
        int size = list.size();
        int from = limit > 0 ? Math.max(0, size - limit) : 0;
        return new ArrayList<>(list.subList(from, size));
    }

    @Override
    public void clear(String sessionId) {
        clear(null, sessionId);
    }

    @Override
    public void clear(String scopeId, String sessionId) {
        if (sessionId == null) {
            return;
        }
        String key = ScopeKeys.key(scopeId, sessionId);
        summaries.remove(key);
        episodes.remove(key);
    }
}
