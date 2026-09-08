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
import com.yangqiongai.agent.harness.store.jdbc.entity.SessionSummaryEntity;

/**
 * 会话运行摘要映射
 * @author yangqiong
 */
public interface SessionSummaryMapper extends BaseMapper<SessionSummaryEntity> {

    /**
     * 幂等保存摘要：复合键存在则更新
     * @param scopeId
     * @param sessionId
     * @param summary
     * @param summarizedMessageCount
     * @return
     */
    @Insert("INSERT INTO harness_session_summary (scope_id, session_id, summary, summarized_message_count) "
            + "VALUES (#{scopeId}, #{sessionId}, #{summary}, #{count}) "
            + "ON DUPLICATE KEY UPDATE summary = VALUES(summary), "
            + "summarized_message_count = VALUES(summarized_message_count)")
    int upsert(@Param("scopeId") String scopeId, @Param("sessionId") String sessionId,
               @Param("summary") String summary, @Param("count") int summarizedMessageCount);
}
