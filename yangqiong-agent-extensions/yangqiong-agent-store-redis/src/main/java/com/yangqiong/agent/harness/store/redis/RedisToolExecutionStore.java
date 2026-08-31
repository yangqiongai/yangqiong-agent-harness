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

import com.yangqiong.agent.harness.core.message.AgentToolResultBlock;
import com.yangqiong.agent.harness.durable.serialization.AgentContentBlockSerializer;
import com.yangqiong.agent.harness.tool.ToolExecutionStore;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.params.SetParams;

/**
 * 工具执行记录存储Redis实现
 * <p>
 * 幂等键首次记录以 SET NX 落地，重复记录保持首份结果不变，
 * 恢复场景跨节点副作用零重复执行。
 * </p>
 * @author yangqiong
 */
public class RedisToolExecutionStore implements ToolExecutionStore {

    private static final String RESULT_KEY = "harness:tool:result:%s";

    /**
     * Redis客户端（线程安全）
     */
    private final JedisPooled jedis;

    /**
     * 构建工具执行记录存储
     * @param jedis
     */
    public RedisToolExecutionStore(JedisPooled jedis) {
        this.jedis = jedis;
    }

    @Override
    public void record(String idempotencyKey, AgentToolResultBlock result) {
        if (idempotencyKey == null || result == null) {
            return;
        }
        jedis.set(resultKey(idempotencyKey), AgentContentBlockSerializer.toJson(result),
                SetParams.setParams().nx());
    }

    @Override
    public boolean isCompleted(String idempotencyKey) {
        return idempotencyKey != null && jedis.exists(resultKey(idempotencyKey));
    }

    @Override
    public AgentToolResultBlock getResult(String idempotencyKey) {
        if (idempotencyKey == null) {
            return null;
        }
        String json = jedis.get(resultKey(idempotencyKey));
        return json == null ? null : (AgentToolResultBlock) AgentContentBlockSerializer.fromJson(json);
    }

    private static String resultKey(String idempotencyKey) {
        return String.format(RESULT_KEY, idempotencyKey);
    }
}