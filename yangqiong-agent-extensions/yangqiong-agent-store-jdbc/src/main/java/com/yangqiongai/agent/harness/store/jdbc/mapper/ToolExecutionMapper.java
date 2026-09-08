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
package com.yangqiongai.agent.harness.store.jdbc.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yangqiongai.agent.harness.store.jdbc.entity.ToolExecutionEntity;

/**
 * 工具执行记录映射
 * @author yangqiong
 */
public interface ToolExecutionMapper extends BaseMapper<ToolExecutionEntity> {

    /**
     * 忽略式插入：同键首次结果落地后重复记录不覆盖
     * @param idempotencyKey
     * @param result
     * @return
     */
    @Insert("INSERT IGNORE INTO harness_tool_execution (idempotency_key, result) VALUES (#{key}, #{result})")
    int insertIgnore(@Param("key") String idempotencyKey, @Param("result") String result);
}
