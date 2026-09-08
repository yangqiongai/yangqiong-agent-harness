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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangqiongai.agent.harness.durable.AgentRunRecord;
import com.yangqiongai.agent.harness.durable.AgentRunState;
import com.yangqiongai.agent.harness.durable.AgentRunStore;
import com.yangqiongai.agent.harness.durable.serialization.HarnessObjectMapper;
import com.yangqiongai.agent.harness.store.jdbc.entity.RunEntity;
import com.yangqiongai.agent.harness.store.jdbc.mapper.RunMapper;

/**
 * 运行存储JDBC实现
 * <p>
 * 以 run_id 为主键，saveTransition 采用乐观锁 UPDATE 保证状态迁移并发安全，
 * 状态迁移历史以JSON列持久化，读取时按迁移序列重放还原运行记录。
 * </p>
 * @author yangqiong
 */
public class JdbcAgentRunStore implements AgentRunStore {

    /**
     * 运行记录映射器
     */
    private final RunMapper mapper;

    /**
     * JSON映射器
     */
    private static final ObjectMapper MAPPER = HarnessObjectMapper.get();

    /**
     * 构造器
     * @param mapper
     */
    public JdbcAgentRunStore(RunMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public AgentRunRecord create(AgentRunRecord record) {
        if (record == null || record.getRunId() == null) {
            throw new IllegalArgumentException("运行记录或runId不能为空");
        }
        try {
            mapper.insert(toEntity(record));
            return record;
        } catch (org.apache.ibatis.exceptions.PersistenceException e) {
            throw new IllegalStateException("运行记录已存在: " + record.getRunId(), e);
        }
    }

    @Override
    public Optional<AgentRunRecord> findByRunId(String runId) {
        if (runId == null) {
            return Optional.empty();
        }
        RunEntity entity = mapper.selectById(runId);
        return entity != null ? Optional.of(fromEntity(entity)) : Optional.empty();
    }

    @Override
    public Optional<AgentRunRecord> findLatestBySession(String scopeId, String sessionId) {
        RunEntity entity = mapper.selectOne(Wrappers.<RunEntity>lambdaQuery()
                .eq(RunEntity::getScopeId, scopeId)
                .eq(RunEntity::getSessionId, sessionId)
                .orderByDesc(RunEntity::getCreatedAt)
                .last("LIMIT 1"));
        return entity != null ? Optional.of(fromEntity(entity)) : Optional.empty();
    }

    @Override
    public List<AgentRunRecord> findBySession(String scopeId, String sessionId) {
        List<RunEntity> entities = mapper.selectList(Wrappers.<RunEntity>lambdaQuery()
                .eq(RunEntity::getScopeId, scopeId)
                .eq(RunEntity::getSessionId, sessionId)
                .orderByAsc(RunEntity::getCreatedAt));
        return entities.stream().map(this::fromEntity).toList();
    }

    @Override
    public AgentRunRecord saveTransition(AgentRunRecord record) {
        if (record == null || record.getRunId() == null) {
            throw new IllegalArgumentException("运行记录或runId不能为空");
        }
        // 乐观锁：期望DB中版本为当前版本减一，防并发覆盖
        int updated = mapper.saveTransition(record.getRunId(), record.getState().name(),
                record.getUpdatedAt(), record.getVersion(), record.getError(),
                transitionsToJson(record), record.getVersion() - 1);
        if (updated == 0) {
            throw new IllegalStateException("运行状态迁移版本冲突: runId=" + record.getRunId());
        }
        return record;
    }

    @Override
    public List<AgentRunRecord> findWaitingApproval(String scopeId) {
        LambdaQueryWrapper<RunEntity> query = Wrappers.<RunEntity>lambdaQuery()
                .eq(RunEntity::getState, AgentRunState.WAITING_APPROVAL.name());
        if (scopeId != null) {
            query.eq(RunEntity::getScopeId, scopeId);
        }
        return mapper.selectList(query).stream().map(this::fromEntity).toList();
    }

    /**
     * 运行记录转实体
     * @param record
     * @return
     */
    private RunEntity toEntity(AgentRunRecord record) {
        RunEntity entity = new RunEntity();
        entity.setRunId(record.getRunId());
        entity.setScopeId(record.getScopeId());
        entity.setSessionId(record.getSessionId());
        entity.setUserId(record.getUserId());
        entity.setAgentName(record.getAgentName());
        entity.setCreatedAt(record.getCreatedAt());
        entity.setUpdatedAt(record.getUpdatedAt());
        entity.setVersion(record.getVersion());
        entity.setState(record.getState().name());
        entity.setError(record.getError());
        entity.setTransitions(transitionsToJson(record));
        return entity;
    }

    /**
     * 实体转运行记录
     * @param entity
     * @return
     */
    private AgentRunRecord fromEntity(RunEntity entity) {
        AgentRunRecord record = new AgentRunRecord(entity.getRunId(), entity.getScopeId(),
                entity.getSessionId(), entity.getUserId(), entity.getAgentName());
        List<TransitionDto> dtoList = transitionsFromJson(entity.getTransitions());
        // 跳过构造器自带的初始CREATED迁移，按存储序列重放还原真实状态
        int from = isCreated(dtoList) ? 1 : 0;
        for (int i = from; i < dtoList.size(); i++) {
            TransitionDto dto = dtoList.get(i);
            record.transitionTo(dto.state, dto.reason);
        }
        return record;
    }

    /**
     * 判断迁移序列首条是否为初始CREATED
     * @param list
     * @return
     */
    private boolean isCreated(List<TransitionDto> list) {
        return !list.isEmpty() && list.get(0).state == AgentRunState.CREATED;
    }

    /**
     * 序列化迁移历史为JSON
     * @param record
     * @return
     */
    private String transitionsToJson(AgentRunRecord record) {
        List<TransitionDto> list = new ArrayList<>();
        for (AgentRunRecord.StateTransition t : record.getTransitions()) {
            list.add(new TransitionDto(t.getState(), t.getTimestamp(), t.getReason()));
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
            return Collections.emptyList();
        }
        try {
            List<TransitionDto> list = MAPPER.readValue(json,
                    MAPPER.getTypeFactory().constructCollectionType(List.class, TransitionDto.class));
            return list != null ? list : Collections.emptyList();
        } catch (Exception e) {
            throw new IllegalStateException("反序列化状态迁移历史失败", e);
        }
    }

    /**
     * 状态迁移传输对象
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
