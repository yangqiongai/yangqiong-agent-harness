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
import java.util.Objects;

import com.yangqiong.agent.harness.core.message.AgentToolResultBlock;
import com.yangqiong.agent.harness.durable.serialization.AgentContentBlockSerializer;
import com.yangqiong.agent.harness.tool.ToolExecutionStore;

/**
 * SQLite工具执行记录
 * <p>
 * 以幂等键为主键，record 采用 INSERT OR IGNORE 保证同键首次结果落地后
 * 重复记录不覆盖，实现恢复场景副作用零重复执行。
 * </p>
 * @author yangqiong
 */
public class SqliteToolExecutionStore implements ToolExecutionStore {

    /**
     * 同键首次结果落地，重复插入静默忽略
     */
    private static final String SQL_INSERT_IGNORE = """
            INSERT OR IGNORE INTO harness_tool_execution (idempotency_key, result)
            VALUES (?, ?)""";

    /**
     * 幂等键存在性检查
     */
    private static final String SQL_EXISTS = "SELECT 1 FROM harness_tool_execution WHERE idempotency_key = ?";

    /**
     * 按幂等键查询既有结果
     */
    private static final String SQL_FIND_RESULT = """
            SELECT result FROM harness_tool_execution
            WHERE idempotency_key = ?""";

    /**
     * 共享数据库连接
     */
    private final Connection connection;

    /**
     * 构造器
     * @param connection
     */
    public SqliteToolExecutionStore(Connection connection) {
        this.connection = Objects.requireNonNull(connection, "connection不能为空");
    }

    /**
     * 记录幂等键已完成
     * @param idempotencyKey
     * @param result
     */
    @Override
    public void record(String idempotencyKey, AgentToolResultBlock result) {
        if (idempotencyKey == null || result == null) {
            return;
        }
        synchronized (connection) {
            SqliteSupport.update(connection, SQL_INSERT_IGNORE, statement -> {
                statement.setString(1, idempotencyKey);
                statement.setString(2, AgentContentBlockSerializer.toJson(result));
            });
        }
    }

    /**
     * 查询幂等键是否已完成
     * @param idempotencyKey
     * @return
     */
    @Override
    public boolean isCompleted(String idempotencyKey) {
        if (idempotencyKey == null) {
            return false;
        }
        synchronized (connection) {
            Integer exists = SqliteSupport.queryFirst(connection, SQL_EXISTS,
                    statement -> statement.setString(1, idempotencyKey), resultSet -> 1);
            return exists != null;
        }
    }

    /**
     * 取幂等键的既有结果
     * @param idempotencyKey
     * @return
     */
    @Override
    public AgentToolResultBlock getResult(String idempotencyKey) {
        if (idempotencyKey == null) {
            return null;
        }
        synchronized (connection) {
            String json = SqliteSupport.queryFirst(connection, SQL_FIND_RESULT,
                    statement -> statement.setString(1, idempotencyKey),
                    resultSet -> resultSet.getString("result"));
            if (json == null) {
                return null;
            }
            return (AgentToolResultBlock) AgentContentBlockSerializer.fromJson(json);
        }
    }
}
