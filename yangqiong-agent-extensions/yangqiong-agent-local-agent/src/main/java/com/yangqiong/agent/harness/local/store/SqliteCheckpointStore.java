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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangqiong.agent.harness.core.message.AgentContentBlock;
import com.yangqiong.agent.harness.core.message.AgentMessage;
import com.yangqiong.agent.harness.core.message.AgentToolUseBlock;
import com.yangqiong.agent.harness.durable.AgentCheckpoint;
import com.yangqiong.agent.harness.durable.CheckpointStore;
import com.yangqiong.agent.harness.durable.serialization.AgentContentBlockSerializer;
import com.yangqiong.agent.harness.durable.serialization.AgentMessageSerializer;
import com.yangqiong.agent.harness.durable.serialization.HarnessObjectMapper;

/**
 * SQLite检查点存储
 * <p>
 * 以 (run_id, version) 为复合主键落库，save 前校验复合键下版本必须单调递增，
 * 防止旧版本覆盖新版本；消息、待执行工具调用与已完成ID集合以JSON列持久化，
 * latest 按复合键取最近检查点。
 * </p>
 * @author yangqiong
 */
public class SqliteCheckpointStore implements CheckpointStore {

    /**
     * JSON映射器
     */
    private static final ObjectMapper MAPPER = HarnessObjectMapper.get();

    /**
     * 查询复合键下最大检查点版本
     */
    private static final String SQL_MAX_VERSION = """
            SELECT MAX(version) FROM harness_checkpoint
            WHERE scope_id = ? AND session_id = ?""";

    /**
     * 插入检查点
     */
    private static final String SQL_INSERT = """
            INSERT INTO harness_checkpoint (run_id, scope_id, session_id, version, iteration,
                messages, pending_tool_calls, completed_tool_use_ids, checkpoint_time)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""";

    /**
     * 按复合键取最近检查点
     */
    private static final String SQL_LATEST = """
            SELECT * FROM harness_checkpoint
            WHERE scope_id = ? AND session_id = ?
            ORDER BY version DESC
            LIMIT 1""";

    /**
     * 按运行ID列出全部检查点
     */
    private static final String SQL_FIND_BY_RUN_ID = """
            SELECT * FROM harness_checkpoint
            WHERE run_id = ?
            ORDER BY version ASC""";

    /**
     * 清理运行检查点
     */
    private static final String SQL_CLEAR = "DELETE FROM harness_checkpoint WHERE run_id = ?";

    /**
     * 共享数据库连接
     */
    private final Connection connection;

    /**
     * 构造器
     * @param connection
     */
    public SqliteCheckpointStore(Connection connection) {
        this.connection = Objects.requireNonNull(connection, "connection不能为空");
    }

    /**
     * 保存检查点
     * @param checkpoint
     * @return
     */
    @Override
    public void save(AgentCheckpoint checkpoint) {
        if (checkpoint == null || checkpoint.getRunId() == null) {
            throw new IllegalArgumentException("检查点或runId不能为空");
        }
        synchronized (connection) {
            Long latestVersion = SqliteSupport.queryFirst(connection, SQL_MAX_VERSION,
                    statement -> {
                        statement.setString(1, normalizeScope(checkpoint.getScopeId()));
                        statement.setString(2, checkpoint.getSessionId());
                    },
                    resultSet -> resultSet.getLong(1));
            long latest = latestVersion != null ? latestVersion : 0L;
            // 乐观锁：版本必须大于复合键当前最新版本，防止旧版本覆盖新版本
            if (checkpoint.getVersion() <= latest) {
                throw new IllegalStateException("检查点版本冲突: expect>" + latest
                        + " actual=" + checkpoint.getVersion() + ", runId=" + checkpoint.getRunId());
            }
            SqliteSupport.update(connection, SQL_INSERT, statement -> {
                statement.setString(1, checkpoint.getRunId());
                statement.setString(2, normalizeScope(checkpoint.getScopeId()));
                statement.setString(3, checkpoint.getSessionId());
                statement.setLong(4, checkpoint.getVersion());
                statement.setInt(5, checkpoint.getIteration());
                statement.setString(6, messagesToJson(checkpoint.getMessages()));
                statement.setString(7, toolUseToJson(checkpoint.getPendingToolCalls()));
                statement.setString(8, stringListToJson(checkpoint.getCompletedToolUseIds()));
                statement.setLong(9, checkpoint.getTimestamp());
            });
        }
    }

    /**
     * 按复合键取最近检查点
     * @param scopeId
     * @param sessionId
     * @return
     */
    @Override
    public Optional<AgentCheckpoint> latest(String scopeId, String sessionId) {
        synchronized (connection) {
            AgentCheckpoint checkpoint = SqliteSupport.queryFirst(connection, SQL_LATEST,
                    statement -> {
                        statement.setString(1, normalizeScope(scopeId));
                        statement.setString(2, sessionId);
                    }, this::fromEntity);
            return Optional.ofNullable(checkpoint);
        }
    }

    /**
     * 按运行ID列出全部检查点
     * @param runId
     * @return
     */
    @Override
    public List<AgentCheckpoint> findByRunId(String runId) {
        if (runId == null) {
            return List.of();
        }
        synchronized (connection) {
            return SqliteSupport.query(connection, SQL_FIND_BY_RUN_ID,
                    statement -> statement.setString(1, runId), this::fromEntity);
        }
    }

    /**
     * 清理运行检查点
     * @param runId
     * @return
     */
    @Override
    public void clear(String runId) {
        if (runId == null) {
            return;
        }
        synchronized (connection) {
            SqliteSupport.update(connection, SQL_CLEAR,
                    statement -> statement.setString(1, runId));
        }
    }

    /**
     * 结果行转检查点
     * @param resultSet
     * @return
     */
    private AgentCheckpoint fromEntity(ResultSet resultSet) throws SQLException {
        return new AgentCheckpoint(
                resultSet.getString("run_id"),
                resultSet.getString("scope_id"),
                resultSet.getString("session_id"),
                resultSet.getInt("iteration"),
                messagesFromJson(resultSet.getString("messages")),
                toolUseFromJson(resultSet.getString("pending_tool_calls")),
                stringListFromJson(resultSet.getString("completed_tool_use_ids")),
                resultSet.getLong("checkpoint_time"),
                resultSet.getLong("version"));
    }

    /**
     * 消息列表转JSON
     * @param messages
     * @return
     */
    private String messagesToJson(List<AgentMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return "[]";
        }
        return AgentMessageSerializer.listToJson(messages);
    }

    /**
     * JSON转消息列表
     * @param json
     * @return
     */
    private List<AgentMessage> messagesFromJson(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        return AgentMessageSerializer.listFromJson(json);
    }

    /**
     * 待执行工具调用列表转JSON
     * @param blocks
     * @return
     */
    private String toolUseToJson(List<AgentToolUseBlock> blocks) {
        List<AgentContentBlock> contentBlocks = new ArrayList<>();
        if (blocks != null) {
            contentBlocks.addAll(blocks);
        }
        return AgentContentBlockSerializer.listToJson(contentBlocks);
    }

    /**
     * JSON转待执行工具调用列表
     * @param json
     * @return
     */
    private List<AgentToolUseBlock> toolUseFromJson(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        return AgentContentBlockSerializer.listFromJson(json).stream()
                .map(block -> (AgentToolUseBlock) block)
                .toList();
    }

    /**
     * 字符串集合转JSON
     * @param values
     * @return
     */
    private String stringListToJson(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "[]";
        }
        try {
            return MAPPER.writeValueAsString(values);
        } catch (Exception e) {
            throw new IllegalStateException("序列化已完成工具调用ID失败", e);
        }
    }

    /**
     * JSON转字符串集合
     * @param json
     * @return
     */
    private List<String> stringListFromJson(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<String> values = MAPPER.readValue(json, new TypeReference<List<String>>() {
            });
            return values != null ? values : List.of();
        } catch (Exception e) {
            throw new IllegalStateException("反序列化已完成工具调用ID失败", e);
        }
    }

    /**
     * 空scope归一为空串以适配非空列，与复合键语义一致
     * @param scopeId
     * @return
     */
    private String normalizeScope(String scopeId) {
        return scopeId != null ? scopeId : "";
    }
}
