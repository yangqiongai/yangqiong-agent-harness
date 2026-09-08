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

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yangqiongai.agent.harness.core.message.AgentToolResultBlock;
import com.yangqiongai.agent.harness.durable.serialization.AgentContentBlockSerializer;
import com.yangqiongai.agent.harness.store.jdbc.entity.ToolExecutionEntity;
import com.yangqiongai.agent.harness.store.jdbc.mapper.ToolExecutionMapper;
import com.yangqiongai.agent.harness.tool.ToolExecutionStore;

/**
 * 工具执行记录存储JDBC实现
 * <p>
 * 以幂等键为主键，record 采用INSERT IGNORE保证同键首次结果落地后
 * 重复记录不覆盖，实现恢复场景副作用零重复执行。
 * </p>
 * @author yangqiong
 */
public class JdbcToolExecutionStore implements ToolExecutionStore {

    /**
     * 工具执行记录映射器
     */
    private final ToolExecutionMapper mapper;

    /**
     * 构造器
     * @param mapper
     */
    public JdbcToolExecutionStore(ToolExecutionMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void record(String idempotencyKey, AgentToolResultBlock result) {
        if (idempotencyKey == null || result == null) {
            return;
        }
        mapper.insertIgnore(idempotencyKey, AgentContentBlockSerializer.toJson(result));
    }

    @Override
    public boolean isCompleted(String idempotencyKey) {
        if (idempotencyKey == null) {
            return false;
        }
        Long count = mapper.selectCount(Wrappers.<ToolExecutionEntity>lambdaQuery()
                .eq(ToolExecutionEntity::getIdempotencyKey, idempotencyKey));
        return count != null && count > 0;
    }

    @Override
    public AgentToolResultBlock getResult(String idempotencyKey) {
        if (idempotencyKey == null) {
            return null;
        }
        ToolExecutionEntity entity = mapper.selectOne(Wrappers.<ToolExecutionEntity>lambdaQuery()
                .eq(ToolExecutionEntity::getIdempotencyKey, idempotencyKey));
        if (entity == null || entity.getResult() == null) {
            return null;
        }
        return (AgentToolResultBlock) AgentContentBlockSerializer.fromJson(entity.getResult());
    }
}
