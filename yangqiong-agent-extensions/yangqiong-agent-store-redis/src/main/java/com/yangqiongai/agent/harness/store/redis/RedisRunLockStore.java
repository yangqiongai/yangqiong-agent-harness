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
package com.yangqiongai.agent.harness.store.redis;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import com.yangqiongai.agent.harness.durable.RunLockStore;
import redis.clients.jedis.JedisPooled;

/**
 * 运行锁存储Redis实现
 * <p>
 * 基于 SET key owner NX EX ttl 原语，续期与解锁通过持有者校验的Lua脚本原子执行，
 * 同节点重复加锁自动续期保证引擎多断点场景幂等。
 * </p>
 * @author yangqiong
 */
public class RedisRunLockStore implements RunLockStore {

    private static final String LOCK_KEY = "harness:lock:%s";

    private static final String TRYLOCK_SCRIPT =
            "local current = redis.call('GET', KEYS[1]) "
                    + "if current == ARGV[1] then "
                    + "return redis.call('PEXPIRE', KEYS[1], ARGV[2]) end "
                    + "if current == false then "
                    + "return redis.call('SET', KEYS[1], ARGV[1], 'PX', ARGV[2], 'NX') and 1 or 0 end "
                    + "return 0";

    private static final String RENEW_SCRIPT =
            "if redis.call('GET', KEYS[1]) == ARGV[1] then "
                    + "return redis.call('PEXPIRE', KEYS[1], ARGV[2]) end "
                    + "return 0";

    private static final String UNLOCK_SCRIPT =
            "if redis.call('GET', KEYS[1]) == ARGV[1] then "
                    + "return redis.call('DEL', KEYS[1]) end "
                    + "return 0";

    /**
     * Redis客户端（线程安全）
     */
    private final JedisPooled jedis;

    /**
     * 构建运行锁存储
     * @param jedis
     */
    public RedisRunLockStore(JedisPooled jedis) {
        this.jedis = jedis;
    }

    @Override
    public boolean tryLock(String runId, String ownerNodeId, Duration lockTtl) {
        if (runId == null || ownerNodeId == null || lockTtl == null || lockTtl.isNegative()) {
            return false;
        }
        // 同节点重复加锁视为续期（多断点恢复场景幂等），异节点竞争时NX拒绝
        Object result = jedis.eval(TRYLOCK_SCRIPT, List.of(lockKey(runId)),
                List.of(ownerNodeId, String.valueOf(lockTtl.toMillis())));
        return "1".equals(String.valueOf(result));
    }

    @Override
    public void renew(String runId, String ownerNodeId, Duration lockTtl) {
        if (runId == null || ownerNodeId == null || lockTtl == null) {
            return;
        }
        jedis.eval(RENEW_SCRIPT, List.of(lockKey(runId)),
                List.of(ownerNodeId, String.valueOf(lockTtl.toMillis())));
    }

    @Override
    public void unlock(String runId, String ownerNodeId) {
        if (runId == null || ownerNodeId == null) {
            return;
        }
        jedis.eval(UNLOCK_SCRIPT, List.of(lockKey(runId)), List.of(ownerNodeId));
    }

    @Override
    public Optional<String> owner(String runId) {
        if (runId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(jedis.get(lockKey(runId)));
    }

    private static String lockKey(String runId) {
        return String.format(LOCK_KEY, runId);
    }
}