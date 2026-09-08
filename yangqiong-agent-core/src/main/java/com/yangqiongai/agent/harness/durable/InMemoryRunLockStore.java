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
package com.yangqiongai.agent.harness.durable;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存运行锁存储
 * <p>
 * 单进程兜底实现：按 runId 维护持有者与过期时间，超时按系统时钟判断。
 * 同节点重复 tryLock 视为续期，保证引擎多断点场景幂等。
 * </p>
 * @author yangqiong
 */
public class InMemoryRunLockStore implements RunLockStore {

    /**
     * 锁条目
     */
    private static final class LockEntry {

        /**
         * 持有节点ID
         */
        private final String ownerNodeId;

        /**
         * 过期时间戳（毫秒）
         */
        private final long expiresAt;

        LockEntry(String ownerNodeId, long expiresAt) {
            this.ownerNodeId = ownerNodeId;
            this.expiresAt = expiresAt;
        }
    }

    /**
     * runId到锁条目的索引
     */
    private final Map<String, LockEntry> locks = new ConcurrentHashMap<>();

    @Override
    public boolean tryLock(String runId, String ownerNodeId, Duration lockTtl) {
        if (runId == null || ownerNodeId == null || lockTtl == null) {
            return false;
        }
        LockEntry candidate = new LockEntry(ownerNodeId, System.currentTimeMillis() + lockTtl.toMillis());
        return locks.compute(runId, (key, existing) -> {
            if (existing == null || existing.expiresAt <= System.currentTimeMillis()
                    || existing.ownerNodeId.equals(ownerNodeId)) {
                return candidate;
            }
            return existing;
        }) == candidate;
    }

    @Override
    public void renew(String runId, String ownerNodeId, Duration lockTtl) {
        locks.computeIfPresent(runId, (key, existing) -> {
            if (existing.ownerNodeId.equals(ownerNodeId) && existing.expiresAt > System.currentTimeMillis()) {
                return new LockEntry(ownerNodeId, System.currentTimeMillis() + lockTtl.toMillis());
            }
            return existing;
        });
    }

    @Override
    public void unlock(String runId, String ownerNodeId) {
        locks.computeIfPresent(runId, (key, existing) ->
                existing.ownerNodeId.equals(ownerNodeId) ? null : existing);
    }

    @Override
    public Optional<String> owner(String runId) {
        if (runId == null) {
            return Optional.empty();
        }
        LockEntry entry = locks.get(runId);
        if (entry == null || entry.expiresAt <= System.currentTimeMillis()) {
            return Optional.empty();
        }
        return Optional.of(entry.ownerNodeId);
    }
}
