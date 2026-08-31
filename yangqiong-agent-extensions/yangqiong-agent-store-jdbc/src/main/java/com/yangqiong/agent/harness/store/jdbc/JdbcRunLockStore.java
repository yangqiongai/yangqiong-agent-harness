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
package com.yangqiong.agent.harness.store.jdbc;

import java.time.Duration;
import java.util.Optional;

import org.apache.ibatis.exceptions.PersistenceException;

import com.yangqiong.agent.harness.durable.RunLockStore;
import com.yangqiong.agent.harness.store.jdbc.mapper.RunLockMapper;

/**
 * 运行锁存储JDBC实现
 * <p>
 * 按 runId 维护带过期时间的分布式锁：tryLock 先插入再条件接管，无锁记录直接占位，
 * 主键冲突时仅当原持有者为本节点或原锁已过期才可接管；过期时间戳超出系统时钟即视为可接管。
 * </p>
 * @author yangqiong
 */
public class JdbcRunLockStore implements RunLockStore {

    /**
     * 运行锁映射器
     */
    private final RunLockMapper mapper;

    /**
     * 构造器
     * @param mapper
     */
    public JdbcRunLockStore(RunLockMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public boolean tryLock(String runId, String ownerNodeId, Duration lockTtl) {
        if (runId == null || ownerNodeId == null || lockTtl == null) {
            return false;
        }
        long expiresAt = System.currentTimeMillis() + lockTtl.toMillis();
        try {
            // 无锁记录时直接插入占位
            mapper.insertLock(runId, ownerNodeId, expiresAt);
            return true;
        } catch (PersistenceException e) {
            // 已有锁记录：仅当原持有者为本节点或原锁已过期时条件更新接管
            return mapper.updateTakeover(runId, ownerNodeId, expiresAt, System.currentTimeMillis()) == 1;
        }
    }

    @Override
    public void renew(String runId, String ownerNodeId, Duration lockTtl) {
        if (runId == null || ownerNodeId == null || lockTtl == null) {
            return;
        }
        long expiresAt = System.currentTimeMillis() + lockTtl.toMillis();
        mapper.renewLock(runId, ownerNodeId, expiresAt, System.currentTimeMillis());
    }

    @Override
    public void unlock(String runId, String ownerNodeId) {
        if (runId == null || ownerNodeId == null) {
            return;
        }
        mapper.deleteByOwner(runId, ownerNodeId);
    }

    @Override
    public Optional<String> owner(String runId) {
        if (runId == null) {
            return Optional.empty();
        }
        String owner = mapper.selectOwner(runId, System.currentTimeMillis());
        return owner != null ? Optional.of(owner) : Optional.empty();
    }
}
