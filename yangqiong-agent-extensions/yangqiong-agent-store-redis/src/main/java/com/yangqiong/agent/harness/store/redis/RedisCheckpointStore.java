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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.yangqiong.agent.harness.durable.AgentCheckpoint;
import com.yangqiong.agent.harness.durable.CheckpointStore;
import com.yangqiong.agent.harness.durable.serialization.AgentCheckpointSerializer;
import redis.clients.jedis.JedisPooled;

/**
 * 检查点存储Redis实现
 * <p>
 * 每个运行版本作为有序集合成员按版本号排序存储，最新检查点指针按 (scopeId, sessionId) 隔离，
 * 保存通过版本号Lua比较实现乐观锁，冲突抛IllegalStateException。
 * </p>
 * @author yangqiong
 */
public class RedisCheckpointStore implements CheckpointStore {

    private static final String RUN_SET = "harness:checkpoint:run:%s";
    private static final String DATA_KEY = "harness:checkpoint:data:%s:%s";
    private static final String LATEST_KEY = "harness:%s:checkpoint:latest:%s";

    private static final String SAVE_SCRIPT =
            "local top = redis.call('ZREVRANGE', KEYS[1], 0, 0, 'WITHSCORES') "
                    + "if #top >= 2 and tonumber(top[2]) >= tonumber(ARGV[1]) then return 0 end "
                    + "redis.call('ZADD', KEYS[1], tonumber(ARGV[1]), ARGV[1]) "
                    + "redis.call('SET', KEYS[2], ARGV[2]) "
                    + "redis.call('SET', KEYS[3], ARGV[3]) "
                    + "return 1";

    /**
     * Redis客户端（线程安全）
     */
    private final JedisPooled jedis;

    /**
     * 构建检查点存储
     * @param jedis
     */
    public RedisCheckpointStore(JedisPooled jedis) {
        this.jedis = jedis;
    }

    @Override
    public void save(AgentCheckpoint checkpoint) {
        String runId = checkpoint.getRunId();
        long version = checkpoint.getVersion();
        Object result = jedis.eval(SAVE_SCRIPT,
                List.of(runSet(runId), dataKey(runId, version), latestKey(checkpoint.getScopeId(), checkpoint.getSessionId())),
                List.of(String.valueOf(version), AgentCheckpointSerializer.toJson(checkpoint),
                        runId + ":" + version));
        if (Long.valueOf(1L).equals(result)) {
            return;
        }
        throw new IllegalStateException("检查点版本冲突: runId=" + runId
                + " expectGreaterThan=" + latestVersion(runId) + " actual=" + version);
    }

    @Override
    public Optional<AgentCheckpoint> latest(String scopeId, String sessionId) {
        String pointer = jedis.get(latestKey(scopeId, sessionId));
        if (pointer == null) {
            return Optional.empty();
        }
        int separator = pointer.indexOf(':');
        if (separator < 0) {
            return Optional.empty();
        }
        String runId = pointer.substring(0, separator);
        String version = pointer.substring(separator + 1);
        String json = jedis.get(dataKey(runId, version));
        return json == null ? Optional.empty() : Optional.of(AgentCheckpointSerializer.fromJson(json));
    }

    @Override
    public List<AgentCheckpoint> findByRunId(String runId) {
        List<String> versions = jedis.zrange(runSet(runId), 0, -1);
        if (versions == null || versions.isEmpty()) {
            return List.of();
        }
        List<AgentCheckpoint> checkpoints = new ArrayList<>();
        for (String version : versions) {
            String json = jedis.get(dataKey(runId, version));
            if (json != null) {
                checkpoints.add(AgentCheckpointSerializer.fromJson(json));
            }
        }
        checkpoints.sort(Comparator.comparingLong(AgentCheckpoint::getVersion));
        return checkpoints;
    }

    @Override
    public void clear(String runId) {
        jedis.del(runSet(runId));
        Set<String> dataKeys = jedis.keys(String.format(DATA_KEY, runId, "*"));
        if (dataKeys != null && !dataKeys.isEmpty()) {
            jedis.del(dataKeys.toArray(new String[0]));
        }
    }

    /**
     * 读取运行的最大检查点版本号
     * @param runId
     * @return
     */
    private Long latestVersion(String runId) {
        List<String> scores = jedis.zrevrange(runSet(runId), 0, 0);
        if (scores == null || scores.isEmpty()) {
            return -1L;
        }
        return Long.parseLong(scores.get(0));
    }

    private static String runSet(String runId) {
        return String.format(RUN_SET, runId);
    }

    private static String dataKey(String runId, Object version) {
        return String.format(DATA_KEY, runId, version);
    }

    private static String latestKey(String scopeId, String sessionId) {
        return String.format(LATEST_KEY, scope(scopeId), sessionId == null ? "" : sessionId);
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