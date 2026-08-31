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
import com.yangqiong.agent.harness.durable.AgentRunRecord;
import com.yangqiong.agent.harness.durable.AgentRunState;
import com.yangqiong.agent.harness.durable.AgentRunStore;
import com.yangqiong.agent.harness.durable.serialization.HarnessObjectMapper;

/**
 * SQLite运行记录存储
 * <p>
 * 以 run_id 为主键落库，saveTransition 按乐观锁版本条件更新保证状态迁移并发安全，
 * 状态迁移历史以JSON列持久化，读取时按迁移序列重放还原运行记录。
 * </p>
 * @author yangqiong
 */
public class SqliteAgentRunStore implements AgentRunStore {

    /**
     * JSON映射器
     */
    private static final ObjectMapper MAPPER = HarnessObjectMapper.get();

    /**
     * 运行记录存在性检查
     */
    private static final String SQL_EXISTS = "SELECT 1 FROM harness_run WHERE run_id = ?";

    /**
     * 插入运行记录
     */
    private static final String SQL_INSERT = """
            INSERT INTO harness_run (run_id, scope_id, session_id, user_id, agent_name,
                created_at, updated_at, version, state, error, transitions)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""";

    /**
     * 按运行ID查询
     */
    private static final String SQL_FIND_BY_RUN_ID = "SELECT * FROM harness_run WHERE run_id = ?";

    /**
     * 按复合键查询会话最近一次运行
     */
    private static final String SQL_FIND_LATEST_BY_SESSION = """
            SELECT * FROM harness_run
            WHERE scope_id = ? AND session_id = ?
            ORDER BY created_at DESC
            LIMIT 1""";

    /**
     * 按复合键列出会话全部运行
     */
    private static final String SQL_FIND_BY_SESSION = """
            SELECT * FROM harness_run
            WHERE scope_id = ? AND session_id = ?
            ORDER BY created_at ASC""";

    /**
     * 乐观锁条件更新状态迁移
     */
    private static final String SQL_SAVE_TRANSITION = """
            UPDATE harness_run
            SET state = ?, updated_at = ?, version = ?, error = ?, transitions = ?
            WHERE run_id = ? AND version = ?""";

    /**
     * 列出全部等待审批运行
     */
    private static final String SQL_FIND_WAITING_ALL = "SELECT * FROM harness_run WHERE state = ?";

    /**
     * 列出租户等待审批运行
     */
    private static final String SQL_FIND_WAITING_BY_SCOPE = """
            SELECT * FROM harness_run
            WHERE state = ? AND scope_id = ?""";

    /**
     * 共享数据库连接
     */
    private final Connection connection;

    /**
     * 构造器
     * @param connection
     */
    public SqliteAgentRunStore(Connection connection) {
        this.connection = Objects.requireNonNull(connection, "connection不能为空");
    }

    /**
     * 创建运行记录
     * @param record
     * @return
     */
    @Override
    public AgentRunRecord create(AgentRunRecord record) {
        if (record == null || record.getRunId() == null) {
            throw new IllegalArgumentException("运行记录或runId不能为空");
        }
        synchronized (connection) {
            // 先查存在性再插入，主键冲突转为明确业务异常
            Integer exists = SqliteSupport.queryFirst(connection, SQL_EXISTS,
                    statement -> statement.setString(1, record.getRunId()), resultSet -> 1);
            if (exists != null) {
                throw new IllegalStateException("运行记录已存在: " + record.getRunId());
            }
            SqliteSupport.update(connection, SQL_INSERT, statement -> {
                statement.setString(1, record.getRunId());
                statement.setString(2, normalizeScope(record.getScopeId()));
                statement.setString(3, record.getSessionId());
                statement.setString(4, record.getUserId());
                statement.setString(5, record.getAgentName());
                statement.setLong(6, record.getCreatedAt());
                statement.setLong(7, record.getUpdatedAt());
                statement.setLong(8, record.getVersion());
                statement.setString(9, record.getState().name());
                statement.setString(10, record.getError());
                statement.setString(11, transitionsToJson(record));
            });
        }
        return record;
    }

    /**
     * 按运行ID查询
     * @param runId
     * @return
     */
    @Override
    public Optional<AgentRunRecord> findByRunId(String runId) {
        if (runId == null) {
            return Optional.empty();
        }
        synchronized (connection) {
            AgentRunRecord record = SqliteSupport.queryFirst(connection, SQL_FIND_BY_RUN_ID,
                    statement -> statement.setString(1, runId), this::fromEntity);
            return Optional.ofNullable(record);
        }
    }

    /**
     * 按复合键查询会话最近一次运行
     * @param scopeId
     * @param sessionId
     * @return
     */
    @Override
    public Optional<AgentRunRecord> findLatestBySession(String scopeId, String sessionId) {
        synchronized (connection) {
            AgentRunRecord record = SqliteSupport.queryFirst(connection, SQL_FIND_LATEST_BY_SESSION,
                    statement -> {
                        statement.setString(1, normalizeScope(scopeId));
                        statement.setString(2, sessionId);
                    }, this::fromEntity);
            return Optional.ofNullable(record);
        }
    }

    /**
     * 按复合键列出会话全部运行
     * @param scopeId
     * @param sessionId
     * @return
     */
    @Override
    public List<AgentRunRecord> findBySession(String scopeId, String sessionId) {
        synchronized (connection) {
            return SqliteSupport.query(connection, SQL_FIND_BY_SESSION, statement -> {
                statement.setString(1, normalizeScope(scopeId));
                statement.setString(2, sessionId);
            }, this::fromEntity);
        }
    }

    /**
     * 保存状态迁移
     * @param record
     * @return
     */
    @Override
    public AgentRunRecord saveTransition(AgentRunRecord record) {
        if (record == null || record.getRunId() == null) {
            throw new IllegalArgumentException("运行记录或runId不能为空");
        }
        synchronized (connection) {
            // 乐观锁：期望库中版本为当前版本减一，防并发覆盖
            int updated = SqliteSupport.update(connection, SQL_SAVE_TRANSITION, statement -> {
                statement.setString(1, record.getState().name());
                statement.setLong(2, record.getUpdatedAt());
                statement.setLong(3, record.getVersion());
                statement.setString(4, record.getError());
                statement.setString(5, transitionsToJson(record));
                statement.setString(6, record.getRunId());
                statement.setLong(7, record.getVersion() - 1);
            });
            if (updated == 0) {
                throw new IllegalStateException("运行状态迁移版本冲突: runId=" + record.getRunId());
            }
        }
        return record;
    }

    /**
     * 列出等待审批的运行
     * @param scopeId
     * @return
     */
    @Override
    public List<AgentRunRecord> findWaitingApproval(String scopeId) {
        synchronized (connection) {
            String waitingState = AgentRunState.WAITING_APPROVAL.name();
            if (scopeId == null) {
                return SqliteSupport.query(connection, SQL_FIND_WAITING_ALL,
                        statement -> statement.setString(1, waitingState), this::fromEntity);
            }
            return SqliteSupport.query(connection, SQL_FIND_WAITING_BY_SCOPE, statement -> {
                statement.setString(1, waitingState);
                statement.setString(2, scopeId);
            }, this::fromEntity);
        }
    }

    /**
     * 结果行转运行记录，按迁移历史重放还原状态
     * @param resultSet
     * @return
     */
    private AgentRunRecord fromEntity(ResultSet resultSet) throws SQLException {
        AgentRunRecord record = new AgentRunRecord(
                resultSet.getString("run_id"),
                resultSet.getString("scope_id"),
                resultSet.getString("session_id"),
                resultSet.getString("user_id"),
                resultSet.getString("agent_name"));
        List<TransitionDto> dtoList = transitionsFromJson(resultSet.getString("transitions"));
        // 跳过构造器自带的初始CREATED迁移，按存储序列重放还原真实状态
        int from = !dtoList.isEmpty() && dtoList.get(0).state == AgentRunState.CREATED ? 1 : 0;
        for (int i = from; i < dtoList.size(); i++) {
            record.transitionTo(dtoList.get(i).state, dtoList.get(i).reason);
        }
        return record;
    }

    /**
     * 序列化迁移历史为JSON
     * @param record
     * @return
     */
    private String transitionsToJson(AgentRunRecord record) {
        List<TransitionDto> list = new ArrayList<>();
        for (AgentRunRecord.StateTransition transition : record.getTransitions()) {
            list.add(new TransitionDto(transition.getState(), transition.getTimestamp(), transition.getReason()));
        }
        try {
            return MAPPER.writeValueAsString(list);
        } catch (Exception e) {
            throw new IllegalStateException("序列化状态迁移历史失败", e);
        }
    }

    /**
     * 反序列化迁移历史JSON
     * @param json
     * @return
     */
    private List<TransitionDto> transitionsFromJson(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<TransitionDto> list = MAPPER.readValue(json, new TypeReference<List<TransitionDto>>() {
            });
            return list != null ? list : List.of();
        } catch (Exception e) {
            throw new IllegalStateException("反序列化状态迁移历史失败", e);
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

    /**
     * 状态迁移传输对象
     * @author yangqiong
     */
    private static class TransitionDto {

        /**
         * 迁移后状态
         */
        private AgentRunState state;

        /**
         * 迁移时间戳
         */
        private long timestamp;

        /**
         * 迁移原因
         */
        private String reason;

        TransitionDto() {
        }

        TransitionDto(AgentRunState state, long timestamp, String reason) {
            this.state = state;
            this.timestamp = timestamp;
            this.reason = reason;
        }

        public AgentRunState getState() {
            return state;
        }

        public void setState(AgentRunState state) {
            this.state = state;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(long timestamp) {
            this.timestamp = timestamp;
        }

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }
    }
}
