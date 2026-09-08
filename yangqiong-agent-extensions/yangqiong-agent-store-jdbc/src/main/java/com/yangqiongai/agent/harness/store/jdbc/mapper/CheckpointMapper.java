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

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yangqiongai.agent.harness.store.jdbc.entity.CheckpointEntity;

/**
 * 运行检查点映射
 * @author yangqiong
 */
public interface CheckpointMapper extends BaseMapper<CheckpointEntity> {

    /**
     * 查询复合键最新版本号
     * @param scopeId
     * @param sessionId
     * @return
     */
    @Select("SELECT COALESCE(MAX(version), 0) FROM harness_checkpoint "
            + "WHERE scope_id = #{scopeId} AND session_id = #{sessionId}")
    Long maxVersion(@Param("scopeId") String scopeId, @Param("sessionId") String sessionId);
}
