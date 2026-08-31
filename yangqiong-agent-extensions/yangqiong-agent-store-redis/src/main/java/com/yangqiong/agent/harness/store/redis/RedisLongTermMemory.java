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
import java.util.Map;
import java.util.Set;

import com.yangqiong.agent.harness.core.memory.AgentLongTermMemory;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.resps.Tuple;

/**
 * 长期记忆Redis实现
 * <p>
 * 记忆按 (scopeId, userId) 分桶，命中次数作为有序集合分值累计并作为检索排序依据，
 * 检索时以检索词在既有记忆中的命中次数倒序返回。
 * </p>
 * @author yangqiong
 */
public class RedisLongTermMemory implements AgentLongTermMemory {

    private static final String MEMORY_SET = "harness:%s:memory:%s";
    private static final String MEMORY_KEY = "harness:memory:data:%s";

    /**
     * Redis客户端（线程安全）
     */
    private final JedisPooled jedis;

    /**
     * 构建长期记忆
     * @param jedis
     */
    public RedisLongTermMemory(JedisPooled jedis) {
        this.jedis = jedis;
    }

    @Override
    public void store(String userId, String sessionId, String content, Map<String, Object> metadata) {
        store(null, userId, sessionId, content, metadata);
    }

    @Override
    public void store(String scopeId, String userId, String sessionId,
                      String content, Map<String, Object> metadata) {
        if (userId == null || content == null) {
            return;
        }
        String memoryId = newMemoryId(userId);
        jedis.set(memoryKey(memoryId), content);
        jedis.zadd(memorySet(scopeId, userId), 1, memoryId);
    }

    @Override
    public List<String> search(String userId, String query, int limit) {
        return search(null, userId, query, limit);
    }

    @Override
    public List<String> search(String scopeId, String userId, String query, int limit) {
        if (userId == null) {
            return List.of();
        }
        List<Tuple> tuples = jedis.zrevrangeWithScores(memorySet(scopeId, userId), 0, -1);
        return tuples.stream()
                .filter(t -> matches(t.getElement(), query))
                .sorted((a, b) -> Double.compare(b.getScore(), a.getScore()))
                .limit(limit)
                .map(t -> jedis.get(memoryKey(t.getElement())))
                .filter(s -> s != null)
                .toList();
    }

    @Override
    public void delete(String memoryId) {
        delete(null, memoryId);
    }

    @Override
    public void delete(String scopeId, String memoryId) {
        if (memoryId == null) {
            return;
        }
        if (scopeId != null) {
            Set<String> owners = jedis.keys(String.format(MEMORY_SET, scope(scopeId), "*"));
            boolean owned = owners.stream().anyMatch(key -> jedis.zscore(key, memoryId) != null);
            if (!owned) {
                throw new SecurityException("记忆所有权校验失败: " + memoryId);
            }
        }
        jedis.del(memoryKey(memoryId));
        Set<String> owners = jedis.keys("harness:*:memory:*");
        for (String key : owners) {
            jedis.zrem(key, memoryId);
        }
    }

    /**
     * 判断记忆内容是否命中检索词
     * @param memoryId
     * @param query
     * @return
     */
    private boolean matches(String memoryId, String query) {
        String content = jedis.get(memoryKey(memoryId));
        if (content == null || query == null || query.isEmpty()) {
            return false;
        }
        return content.contains(query);
    }

    /**
     * 生成记忆ID
     * @param userId
     * @return
     */
    private static String newMemoryId(String userId) {
        return userId + ":" + java.util.UUID.randomUUID();
    }

    private static String memorySet(String scopeId, String userId) {
        return String.format(MEMORY_SET, scope(scopeId), userId);
    }

    private static String memoryKey(String memoryId) {
        return String.format(MEMORY_KEY, memoryId);
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