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
import java.util.Optional;

import com.yangqiong.agent.harness.durable.AgentRunRecord;
import com.yangqiong.agent.harness.durable.AgentRunState;
import com.yangqiong.agent.harness.durable.AgentRunStore;
import com.yangqiong.agent.harness.durable.ScopeKeys;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.params.SetParams;

/**
 * 运行存储Redis实现
 * <p>
 * 运行记录按 runId 全局唯一存储，另建会话/作用域有序集合索引，
 * saveTransition 通过版本号Lua比较实现乐观锁并发控制。
 * </p>
 * @author yangqiong
 */
public class RedisAgentRunStore implements AgentRunStore {

    private static final String RECORD_KEY = "harness:run:%s";
    private static final String VERSION_KEY = "harness:run:%s:ver";
    private static final String SESSION_SET = "harness:%s:session:%s:runs";
    private static final String SCOPE_SET = "harness:%s:runs";

    private static final String SAVE_TRANSITION_SCRIPT =
            "local current = tonumber(redis.call('GET', KEYS[2]) or '-1') "
                    + "local incoming = tonumber(ARGV[1]) "
                    + "if incoming <= current then return 0 end "
                    + "redis.call('SET', KEYS[1], ARGV[2]) "
                    + "redis.call('SET', KEYS[2], incoming) "
                    + "return 1";

    /**
     * Redis客户端（线程安全）
     */
    private final JedisPooled jedis;

    /**
     * 构建运行存储
     * @param jedis
     */
    public RedisAgentRunStore(JedisPooled jedis) {
        this.jedis = jedis;
    }

    @Override
    public AgentRunRecord create(AgentRunRecord record) {
        String runId = record.getRunId();
        String recordKey = recordKey(runId);
        if (jedis.set(recordKey, StoreJson.toRun(record), SetParams.setParams().nx()) == null) {
            throw new IllegalStateException("运行记录已存在: " + runId);
        }
        jedis.set(versionKey(runId), String.valueOf(record.getVersion()));
        jedis.zadd(sessionSet(record.getScopeId(), record.getSessionId()), record.getCreatedAt(), runId);
        jedis.zadd(scopeSet(record.getScopeId()), record.getCreatedAt(), runId);
        return record;
    }

    @Override
    public Optional<AgentRunRecord> findByRunId(String runId) {
        if (runId == null) {
            return Optional.empty();
        }
        String json = jedis.get(recordKey(runId));
        return json == null ? Optional.empty() : Optional.of(StoreJson.fromRun(json));
    }

    @Override
    public Optional<AgentRunRecord> findLatestBySession(String scopeId, String sessionId) {
        List<String> runIds = jedis.zrevrange(sessionSet(scopeId, sessionId), 0, 0);
        if (runIds == null || runIds.isEmpty()) {
            return Optional.empty();
        }
        return findByRunId(runIds.get(0));
    }

    @Override
    public List<AgentRunRecord> findBySession(String scopeId, String sessionId) {
        List<String> runIds = jedis.zrange(sessionSet(scopeId, sessionId), 0, -1);
        return decodeAll(runIds);
    }

    @Override
    public AgentRunRecord saveTransition(AgentRunRecord record) {
        String runId = record.getRunId();
        Object result = jedis.eval(SAVE_TRANSITION_SCRIPT, List.of(recordKey(runId), versionKey(runId)),
                List.of(String.valueOf(record.getVersion()), StoreJson.toRun(record)));
        if (Long.valueOf(1L).equals(result)) {
            return record;
        }
        throw new IllegalStateException(
                "运行记录版本冲突: runId=" + runId + " version=" + record.getVersion());
    }

    @Override
    public List<AgentRunRecord> findWaitingApproval(String scopeId) {
        List<String> runIds = jedis.zrange(scopeSet(scopeId), 0, -1);
        return decodeAll(runIds).stream()
                .filter(r -> r.getState() == AgentRunState.WAITING_APPROVAL)
                .filter(r -> scopeId == null || scopeId.equals(r.getScopeId()))
                .toList();
    }

    /**
     * 按runId集合批量反序列化运行记录
     * @param runIds
     * @return
     */
    private List<AgentRunRecord> decodeAll(List<String> runIds) {
        if (runIds == null || runIds.isEmpty()) {
            return List.of();
        }
        return runIds.stream()
                .map(this::findByRunId)
                .flatMap(Optional::stream)
                .toList();
    }

    private static String recordKey(String runId) {
        return String.format(RECORD_KEY, runId);
    }

    private static String versionKey(String runId) {
        return String.format(VERSION_KEY, runId);
    }

    private static String sessionSet(String scopeId, String sessionId) {
        return String.format(SESSION_SET, scope(scopeId), sessionId == null ? "" : sessionId);
    }

    private static String scopeSet(String scopeId) {
        return String.format(SCOPE_SET, scope(scopeId));
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