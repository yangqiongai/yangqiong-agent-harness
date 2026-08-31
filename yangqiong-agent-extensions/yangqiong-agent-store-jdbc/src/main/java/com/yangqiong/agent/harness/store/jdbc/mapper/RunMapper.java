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
package com.yangqiong.agent.harness.store.jdbc.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yangqiong.agent.harness.store.jdbc.entity.RunEntity;

/**
 * 运行记录映射
 * @author yangqiong
 */
public interface RunMapper extends BaseMapper<RunEntity> {

    /**
     * 乐观锁保存状态迁移：期望DB版本为当前版本减一，防并发覆盖
     * @param runId
     * @param state
     * @param updatedAt
     * @param version
     * @param error
     * @param transitions
     * @param expectVersion
     * @return
     */
    @Update("UPDATE harness_run SET state = #{state}, updated_at = #{updatedAt}, version = #{version}, "
            + "error = #{error}, transitions = #{transitions} "
            + "WHERE run_id = #{runId} AND version = #{expectVersion}")
    int saveTransition(@Param("runId") String runId, @Param("state") String state,
                       @Param("updatedAt") long updatedAt, @Param("version") long version,
                       @Param("error") String error, @Param("transitions") String transitions,
                       @Param("expectVersion") long expectVersion);
}
