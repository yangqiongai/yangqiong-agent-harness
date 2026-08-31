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
package com.yangqiong.agent.harness.store.redis;

import java.util.List;

import com.yangqiong.agent.harness.core.memory.SessionMemory;
import com.yangqiong.agent.harness.core.memory.SessionSummary;
import com.yangqiong.agent.harness.core.message.AgentMessage;
import com.yangqiong.agent.harness.durable.serialization.AgentMessageSerializer;
import redis.clients.jedis.JedisPooled;

/**
 * 会话记忆Redis实现
 * <p>
 * 摘要以哈希结构按 (scopeId, sessionId) 隔离存储，情节消息追加到列表，
 * 覆写带scopeId的默认方法实现复合键隔离。
 * </p>
 * @author yangqiong
 */
public class RedisSessionMemory implements SessionMemory {

    private static final String SUMMARY_KEY = "harness:%s:session:%s:summary";
    private static final String SUMMARY_FIELD = "summary";
    private static final String COUNT_FIELD = "count";
    private static final String EPISODE_KEY = "harness:%s:session:%s:episodes";

    /**
     * Redis客户端（线程安全）
     */
    private final JedisPooled jedis;

    /**
     * 构建会话记忆
     * @param jedis
     */
    public RedisSessionMemory(JedisPooled jedis) {
        this.jedis = jedis;
    }

    @Override
    public void saveSummary(String sessionId, String summary, int summarizedMessageCount) {
        saveSummary(null, sessionId, summary, summarizedMessageCount);
    }

    @Override
    public void saveSummary(String scopeId, String sessionId, String summary, int summarizedMessageCount) {
        jedis.hset(summaryKey(scopeId, sessionId), SUMMARY_FIELD, summary == null ? "" : summary);
        jedis.hset(summaryKey(scopeId, sessionId), COUNT_FIELD, String.valueOf(summarizedMessageCount));
    }

    @Override
    public SessionSummary loadSummary(String sessionId) {
        return loadSummary(null, sessionId);
    }

    @Override
    public SessionSummary loadSummary(String scopeId, String sessionId) {
        String summary = jedis.hget(summaryKey(scopeId, sessionId), SUMMARY_FIELD);
        if (summary == null) {
            return null;
        }
        String count = jedis.hget(summaryKey(scopeId, sessionId), COUNT_FIELD);
        return new SessionSummary(summary, count == null ? 0 : Integer.parseInt(count));
    }

    @Override
    public void saveEpisode(String sessionId, AgentMessage episode) {
        jedis.rpush(episodeKey(null, sessionId), AgentMessageSerializer.toJson(episode));
    }

    @Override
    public List<AgentMessage> listEpisodes(String sessionId, int limit) {
        long end = limit > 0 ? limit - 1 : -1;
        List<String> jsonList = jedis.lrange(episodeKey(null, sessionId), 0, end);
        return jsonList.stream()
                .map(AgentMessageSerializer::fromJson)
                .toList();
    }

    @Override
    public void clear(String sessionId) {
        clear(null, sessionId);
    }

    @Override
    public void clear(String scopeId, String sessionId) {
        jedis.del(summaryKey(scopeId, sessionId));
        jedis.del(episodeKey(scopeId, sessionId));
    }

    private static String summaryKey(String scopeId, String sessionId) {
        return String.format(SUMMARY_KEY, scope(scopeId), sessionId == null ? "" : sessionId);
    }

    private static String episodeKey(String scopeId, String sessionId) {
        return String.format(EPISODE_KEY, scope(scopeId), sessionId == null ? "" : sessionId);
    }

    /**
     * 归一化作用域
     * @param scopeId
     * @return
     */
    private static String scope(String scopeId) {
        return scopeId != null ? scopeId : "";
    }
}