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
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.yangqiong.agent.harness.durable.ApprovalRecord;
import com.yangqiong.agent.harness.durable.ApprovalStore;

/**
 * SQLite审批存储
 * <p>
 * 以 approval_id 为主键、tool_call_id 唯一索引落库，resolve 幂等：
 * 仅PENDING状态可落定，重复落定返回当前状态且不再改写原始审批方向。
 * </p>
 * @author yangqiong
 */
public class SqliteApprovalStore implements ApprovalStore {

    /**
     * 审批记录存在性检查
     */
    private static final String SQL_EXISTS = "SELECT 1 FROM harness_approval WHERE approval_id = ?";

    /**
     * 插入审批记录
     */
    private static final String SQL_INSERT = """
            INSERT INTO harness_approval (approval_id, run_id, tool_call_id, tool_name,
                scope_id, approver_id, state, reason, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""";

    /**
     * 按审批ID查询
     */
    private static final String SQL_FIND_BY_APPROVAL_ID = "SELECT * FROM harness_approval WHERE approval_id = ?";

    /**
     * 按工具调用ID查询
     */
    private static final String SQL_FIND_BY_TOOL_CALL_ID = "SELECT * FROM harness_approval WHERE tool_call_id = ?";

    /**
     * 条件落定待审批记录
     */
    private static final String SQL_RESOLVE = """
            UPDATE harness_approval
            SET state = ?, reason = ?
            WHERE approval_id = ? AND state = 'PENDING'""";

    /**
     * 列出全部待审批记录
     */
    private static final String SQL_FIND_PENDING_ALL = "SELECT * FROM harness_approval WHERE state = ?";

    /**
     * 列出租户待审批记录
     */
    private static final String SQL_FIND_PENDING_BY_SCOPE = """
            SELECT * FROM harness_approval
            WHERE state = ? AND scope_id = ?""";

    /**
     * 列出运行的全部审批记录
     */
    private static final String SQL_FIND_BY_RUN_ID = "SELECT * FROM harness_approval WHERE run_id = ?";

    /**
     * 共享数据库连接
     */
    private final Connection connection;

    /**
     * 构造器
     * @param connection
     */
    public SqliteApprovalStore(Connection connection) {
        this.connection = Objects.requireNonNull(connection, "connection不能为空");
    }

    /**
     * 保存待审批记录
     * @param record
     * @return
     */
    @Override
    public ApprovalRecord create(ApprovalRecord record) {
        if (record == null || record.getApprovalId() == null) {
            throw new IllegalArgumentException("审批记录或approvalId不能为空");
        }
        synchronized (connection) {
            // 先查存在性再插入，主键冲突转为明确业务异常
            Integer exists = SqliteSupport.queryFirst(connection, SQL_EXISTS,
                    statement -> statement.setString(1, record.getApprovalId()), resultSet -> 1);
            if (exists != null) {
                throw new IllegalStateException("审批记录已存在: " + record.getApprovalId());
            }
            SqliteSupport.update(connection, SQL_INSERT, statement -> {
                statement.setString(1, record.getApprovalId());
                statement.setString(2, record.getRunId());
                statement.setString(3, record.getToolCallId());
                statement.setString(4, record.getToolName());
                statement.setString(5, normalizeScope(record.getScopeId()));
                statement.setString(6, record.getApproverId());
                statement.setString(7, record.getState().name());
                statement.setString(8, record.getReason());
                statement.setLong(9, record.getCreatedAt());
            });
        }
        return record;
    }

    /**
     * 按审批ID查询
     * @param approvalId
     * @return
     */
    @Override
    public Optional<ApprovalRecord> findByApprovalId(String approvalId) {
        if (approvalId == null) {
            return Optional.empty();
        }
        synchronized (connection) {
            ApprovalRecord record = SqliteSupport.queryFirst(connection, SQL_FIND_BY_APPROVAL_ID,
                    statement -> statement.setString(1, approvalId), this::fromEntity);
            return Optional.ofNullable(record);
        }
    }

    /**
     * 按工具调用ID查询最近审批
     * @param toolCallId
     * @return
     */
    @Override
    public Optional<ApprovalRecord> findByToolCallId(String toolCallId) {
        if (toolCallId == null) {
            return Optional.empty();
        }
        synchronized (connection) {
            ApprovalRecord record = SqliteSupport.queryFirst(connection, SQL_FIND_BY_TOOL_CALL_ID,
                    statement -> statement.setString(1, toolCallId), this::fromEntity);
            return Optional.ofNullable(record);
        }
    }

    /**
     * 审批落定
     * @param approvalId
     * @param approved
     * @param reason
     * @return
     */
    @Override
    public ApprovalRecord resolve(String approvalId, boolean approved, String reason) {
        synchronized (connection) {
            ApprovalRecord record = findByApprovalId(approvalId).orElse(null);
            if (record == null) {
                throw new IllegalStateException("审批记录不存在: " + approvalId);
            }
            // 已决记录幂等返回当前状态，不再改写审批方向
            if (record.getState() != ApprovalRecord.ApprovalState.PENDING) {
                return record;
            }
            ApprovalRecord resolved = record.resolve(approved, reason);
            SqliteSupport.update(connection, SQL_RESOLVE, statement -> {
                statement.setString(1, resolved.getState().name());
                statement.setString(2, resolved.getReason());
                statement.setString(3, approvalId);
            });
            return resolved;
        }
    }

    /**
     * 列出租户待审批记录
     * @param scopeId
     * @return
     */
    @Override
    public List<ApprovalRecord> findPending(String scopeId) {
        synchronized (connection) {
            String pendingState = ApprovalRecord.ApprovalState.PENDING.name();
            if (scopeId == null) {
                return SqliteSupport.query(connection, SQL_FIND_PENDING_ALL,
                        statement -> statement.setString(1, pendingState), this::fromEntity);
            }
            return SqliteSupport.query(connection, SQL_FIND_PENDING_BY_SCOPE, statement -> {
                statement.setString(1, pendingState);
                statement.setString(2, scopeId);
            }, this::fromEntity);
        }
    }

    /**
     * 列出运行的全部审批记录
     * @param runId
     * @return
     */
    @Override
    public List<ApprovalRecord> findByRunId(String runId) {
        if (runId == null) {
            return List.of();
        }
        synchronized (connection) {
            return SqliteSupport.query(connection, SQL_FIND_BY_RUN_ID,
                    statement -> statement.setString(1, runId), this::fromEntity);
        }
    }

    /**
     * 结果行转审批记录
     * @param resultSet
     * @return
     */
    private ApprovalRecord fromEntity(ResultSet resultSet) throws SQLException {
        return new ApprovalRecord(
                resultSet.getString("approval_id"),
                resultSet.getString("run_id"),
                resultSet.getString("tool_call_id"),
                resultSet.getString("tool_name"),
                resultSet.getString("scope_id"),
                resultSet.getString("approver_id"),
                ApprovalRecord.ApprovalState.valueOf(resultSet.getString("state")),
                resultSet.getString("reason"),
                resultSet.getLong("created_at"));
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
