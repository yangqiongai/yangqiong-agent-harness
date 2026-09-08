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
package com.yangqiongai.agent.harness.store.jdbc;

import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yangqiongai.agent.harness.durable.ApprovalRecord;
import com.yangqiongai.agent.harness.durable.ApprovalStore;
import com.yangqiongai.agent.harness.store.jdbc.entity.ApprovalEntity;
import com.yangqiongai.agent.harness.store.jdbc.mapper.ApprovalMapper;

/**
 * 审批存储JDBC实现
 * <p>
 * 以 approval_id 为主键、tool_call_id 唯一，resolve 幂等：重复落定返回当前状态，
 * 已决记录不再改写原始审批方向。
 * </p>
 * @author yangqiong
 */
public class JdbcApprovalStore implements ApprovalStore {

    /**
     * 审批记录映射器
     */
    private final ApprovalMapper mapper;

    /**
     * 构造器
     * @param mapper
     */
    public JdbcApprovalStore(ApprovalMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public ApprovalRecord create(ApprovalRecord record) {
        if (record == null || record.getApprovalId() == null) {
            throw new IllegalArgumentException("审批记录或approvalId不能为空");
        }
        mapper.insert(toEntity(record));
        return record;
    }

    @Override
    public Optional<ApprovalRecord> findByApprovalId(String approvalId) {
        if (approvalId == null) {
            return Optional.empty();
        }
        ApprovalEntity entity = mapper.selectById(approvalId);
        return entity != null ? Optional.of(fromEntity(entity)) : Optional.empty();
    }

    @Override
    public Optional<ApprovalRecord> findByToolCallId(String toolCallId) {
        if (toolCallId == null) {
            return Optional.empty();
        }
        ApprovalEntity entity = mapper.selectOne(Wrappers.<ApprovalEntity>lambdaQuery()
                .eq(ApprovalEntity::getToolCallId, toolCallId));
        return entity != null ? Optional.of(fromEntity(entity)) : Optional.empty();
    }

    @Override
    public ApprovalRecord resolve(String approvalId, boolean approved, String reason) {
        Optional<ApprovalRecord> existing = findByApprovalId(approvalId);
        if (existing.isEmpty()) {
            throw new IllegalStateException("审批记录不存在: " + approvalId);
        }
        ApprovalRecord record = existing.get();
        // 已决记录幂等返回当前状态，不再改写审批方向
        if (record.getState() != ApprovalRecord.ApprovalState.PENDING) {
            return record;
        }
        ApprovalRecord resolved = record.resolve(approved, reason);
        mapper.resolvePending(approvalId, resolved.getState().name(), resolved.getReason());
        return resolved;
    }

    @Override
    public List<ApprovalRecord> findPending(String scopeId) {
        LambdaQueryWrapper<ApprovalEntity> query = Wrappers.<ApprovalEntity>lambdaQuery()
                .eq(ApprovalEntity::getState, ApprovalRecord.ApprovalState.PENDING.name());
        if (scopeId != null) {
            query.eq(ApprovalEntity::getScopeId, scopeId);
        }
        return mapper.selectList(query).stream().map(this::fromEntity).toList();
    }

    @Override
    public List<ApprovalRecord> findByRunId(String runId) {
        if (runId == null) {
            return List.of();
        }
        return mapper.selectList(Wrappers.<ApprovalEntity>lambdaQuery()
                .eq(ApprovalEntity::getRunId, runId)).stream().map(this::fromEntity).toList();
    }

    /**
     * 审批记录转实体
     * @param record
     * @return
     */
    private ApprovalEntity toEntity(ApprovalRecord record) {
        ApprovalEntity entity = new ApprovalEntity();
        entity.setApprovalId(record.getApprovalId());
        entity.setRunId(record.getRunId());
        entity.setToolCallId(record.getToolCallId());
        entity.setToolName(record.getToolName());
        entity.setScopeId(record.getScopeId());
        entity.setApproverId(record.getApproverId());
        entity.setState(record.getState().name());
        entity.setReason(record.getReason());
        entity.setCreatedAt(record.getCreatedAt());
        return entity;
    }

    /**
     * 实体转审批记录
     * @param entity
     * @return
     */
    private ApprovalRecord fromEntity(ApprovalEntity entity) {
        return new ApprovalRecord(
                entity.getApprovalId(),
                entity.getRunId(),
                entity.getToolCallId(),
                entity.getToolName(),
                entity.getScopeId(),
                entity.getApproverId(),
                ApprovalRecord.ApprovalState.valueOf(entity.getState()),
                entity.getReason(),
                entity.getCreatedAt());
    }
}
