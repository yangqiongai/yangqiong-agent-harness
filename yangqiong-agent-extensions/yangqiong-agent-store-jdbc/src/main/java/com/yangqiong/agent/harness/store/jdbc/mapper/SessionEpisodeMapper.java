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

import java.util.List;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yangqiong.agent.harness.store.jdbc.entity.SessionEpisodeEntity;

/**
 * 会话关键情节消息映射
 * @author yangqiong
 */
public interface SessionEpisodeMapper extends BaseMapper<SessionEpisodeEntity> {

    /**
     * 按时间倒序取最近情节
     * @param scopeId
     * @param sessionId
     * @param limit
     * @return
     */
    @Select("SELECT * FROM harness_session_episode WHERE scope_id = #{scopeId} AND session_id = #{sessionId} "
            + "ORDER BY id DESC LIMIT #{limit}")
    List<SessionEpisodeEntity> listLatest(@Param("scopeId") String scopeId,
                                          @Param("sessionId") String sessionId,
                                          @Param("limit") int limit);
}
