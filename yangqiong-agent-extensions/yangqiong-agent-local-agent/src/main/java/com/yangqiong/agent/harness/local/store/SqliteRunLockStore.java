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
package com.yangqiong.agent.harness.local.store;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

import com.yangqiong.agent.harness.durable.RunLockStore;

/**
 * SQLite运行锁存储
 * <p>
 * 按 runId 维护带过期时间戳的租约锁：无锁记录直接插入占位，
 * 已有锁时仅当原持有者为本节点或租约已过期才可接管，
 * 过期时间戳超出系统时钟即视为可接管，仅持有者可续期与解锁。
 * </p>
 * @author yangqiong
 */
public class SqliteRunLockStore implements RunLockStore {

    /**
     * 单语句原子加锁：插入或接管，仅当租约过期或持有者为本节点时更新
     */
    private static final String SQL_UPSERT_LOCK = """
            INSERT INTO harness_run_lock (run_id, owner_node_id, expires_at)
            VALUES (?, ?, ?)
            ON CONFLICT(run_id) DO UPDATE SET
                owner_node_id = excluded.owner_node_id,
                expires_at = excluded.expires_at
            WHERE harness_run_lock.expires_at <= ? OR harness_run_lock.owner_node_id = excluded.owner_node_id""";

    /**
     * 持有者条件续期
     */
    private static final String SQL_RENEW = """
            UPDATE harness_run_lock
            SET expires_at = ?
            WHERE run_id = ? AND owner_node_id = ? AND expires_at > ?""";

    /**
     * 持有者条件解锁
     */
    private static final String SQL_UNLOCK = """
            DELETE FROM harness_run_lock
            WHERE run_id = ? AND owner_node_id = ?""";

    /**
     * 查询未过期的当前持有者
     */
    private static final String SQL_OWNER = """
            SELECT owner_node_id FROM harness_run_lock
            WHERE run_id = ? AND expires_at > ?""";

    /**
     * 共享数据库连接
     */
    private final Connection connection;

    /**
     * 构造器
     * @param connection
     */
    public SqliteRunLockStore(Connection connection) {
        this.connection = Objects.requireNonNull(connection, "connection不能为空");
    }

    /**
     * 尝试加锁：无锁、锁已过期或当前持有者为本节点时成功
     * <p>
     * 采用单语句 UPSERT 原子完成读-判-写，返回影响行数>0 即获取成功，
     * 避免多进程并发下读-改-写的竞态窗口。
     * </p>
     * @param runId
     * @param ownerNodeId
     * @param lockTtl
     * @return
     */
    @Override
    public boolean tryLock(String runId, String ownerNodeId, Duration lockTtl) {
        if (runId == null || ownerNodeId == null || lockTtl == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        long expiresAt = now + lockTtl.toMillis();
        synchronized (connection) {
            int updated = SqliteSupport.update(connection, SQL_UPSERT_LOCK, statement -> {
                statement.setString(1, runId);
                statement.setString(2, ownerNodeId);
                statement.setLong(3, expiresAt);
                statement.setLong(4, now);
            });
            return updated > 0;
        }
    }

    /**
     * 持有者续期
     * @param runId
     * @param ownerNodeId
     * @param lockTtl
     */
    @Override
    public void renew(String runId, String ownerNodeId, Duration lockTtl) {
        if (runId == null || ownerNodeId == null || lockTtl == null) {
            return;
        }
        long now = System.currentTimeMillis();
        synchronized (connection) {
            SqliteSupport.update(connection, SQL_RENEW, statement -> {
                statement.setLong(1, now + lockTtl.toMillis());
                statement.setString(2, runId);
                statement.setString(3, ownerNodeId);
                statement.setLong(4, now);
            });
        }
    }

    /**
     * 运行结束/释放时解锁，仅持有者可解锁
     * @param runId
     * @param ownerNodeId
     */
    @Override
    public void unlock(String runId, String ownerNodeId) {
        if (runId == null || ownerNodeId == null) {
            return;
        }
        synchronized (connection) {
            SqliteSupport.update(connection, SQL_UNLOCK, statement -> {
                statement.setString(1, runId);
                statement.setString(2, ownerNodeId);
            });
        }
    }

    /**
     * 查询当前持有者
     * @param runId
     * @return
     */
    @Override
    public Optional<String> owner(String runId) {
        if (runId == null) {
            return Optional.empty();
        }
        synchronized (connection) {
            String owner = SqliteSupport.queryFirst(connection, SQL_OWNER, statement -> {
                statement.setString(1, runId);
                statement.setLong(2, System.currentTimeMillis());
            }, resultSet -> resultSet.getString("owner_node_id"));
            return Optional.ofNullable(owner);
        }
    }
}
