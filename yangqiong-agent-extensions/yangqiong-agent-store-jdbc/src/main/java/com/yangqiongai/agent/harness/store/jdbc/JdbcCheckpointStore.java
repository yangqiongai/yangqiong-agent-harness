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

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yangqiongai.agent.harness.durable.AgentCheckpoint;
import com.yangqiongai.agent.harness.durable.CheckpointStore;
import com.yangqiongai.agent.harness.store.jdbc.entity.CheckpointEntity;
import com.yangqiongai.agent.harness.store.jdbc.mapper.CheckpointMapper;

/**
 * 检查点存储JDBC实现
 * <p>
 * 以 (run_id, version) 为复合主键，save 采用版本乐观锁：目标版本必须大于当前
 * 复合键最新版本，否则抛异常防止旧版本覆盖新版本；latest 按复合键实时计算。
 * </p>
 * @author yangqiong
 */
public class JdbcCheckpointStore implements CheckpointStore {

    /**
     * 检查点映射器
     */
    private final CheckpointMapper mapper;

    /**
     * 构造器
     * @param mapper
     */
    public JdbcCheckpointStore(CheckpointMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void save(AgentCheckpoint checkpoint) {
        if (checkpoint == null || checkpoint.getRunId() == null) {
            throw new IllegalArgumentException("检查点或runId不能为空");
        }
        Long latestVersion = mapper.maxVersion(checkpoint.getScopeId(), checkpoint.getSessionId());
        long latest = latestVersion != null ? latestVersion : 0L;
        if (checkpoint.getVersion() <= latest) {
            throw new IllegalStateException("检查点版本冲突: expect>" + latest
                    + " actual=" + checkpoint.getVersion() + ", runId=" + checkpoint.getRunId());
        }
        mapper.insert(toEntity(checkpoint));
    }

    @Override
    public Optional<AgentCheckpoint> latest(String scopeId, String sessionId) {
        CheckpointEntity entity = mapper.selectOne(Wrappers.<CheckpointEntity>lambdaQuery()
                .eq(CheckpointEntity::getScopeId, scopeId)
                .eq(CheckpointEntity::getSessionId, sessionId)
                .orderByDesc(CheckpointEntity::getVersion)
                .last("LIMIT 1"));
        return entity != null ? Optional.of(fromEntity(entity)) : Optional.empty();
    }

    @Override
    public List<AgentCheckpoint> findByRunId(String runId) {
        if (runId == null) {
            return List.of();
        }
        List<CheckpointEntity> entities = mapper.selectList(Wrappers.<CheckpointEntity>lambdaQuery()
                .eq(CheckpointEntity::getRunId, runId)
                .orderByAsc(CheckpointEntity::getVersion));
        return entities.stream().map(this::fromEntity).toList();
    }

    @Override
    public void clear(String runId) {
        if (runId == null) {
            return;
        }
        mapper.delete(Wrappers.<CheckpointEntity>lambdaQuery().eq(CheckpointEntity::getRunId, runId));
    }

    /**
     * 检查点转实体
     * @param checkpoint
     * @return
     */
    private CheckpointEntity toEntity(AgentCheckpoint checkpoint) {
        CheckpointEntity entity = new CheckpointEntity();
        entity.setRunId(checkpoint.getRunId());
        entity.setScopeId(checkpoint.getScopeId());
        entity.setSessionId(checkpoint.getSessionId());
        entity.setVersion(checkpoint.getVersion());
        entity.setIteration(checkpoint.getIteration());
        entity.setMessages(AgentMessageSerializerUtil.messagesToJson(checkpoint.getMessages()));
        entity.setPendingToolCalls(AgentMessageSerializerUtil.toolUseToJson(checkpoint.getPendingToolCalls()));
        entity.setCompletedToolUseIds(
                AgentMessageSerializerUtil.stringListToJson(checkpoint.getCompletedToolUseIds()));
        entity.setCheckpointTime(checkpoint.getTimestamp());
        return entity;
    }

    /**
     * 实体转检查点
     * @param entity
     * @return
     */
    private AgentCheckpoint fromEntity(CheckpointEntity entity) {
        return new AgentCheckpoint(
                entity.getRunId(),
                entity.getScopeId(),
                entity.getSessionId(),
                entity.getIteration(),
                AgentMessageSerializerUtil.messagesFromJson(entity.getMessages()),
                AgentMessageSerializerUtil.toolUseFromJson(entity.getPendingToolCalls()),
                AgentMessageSerializerUtil.stringListFromJson(entity.getCompletedToolUseIds()),
                entity.getCheckpointTime(),
                entity.getVersion());
    }
}
