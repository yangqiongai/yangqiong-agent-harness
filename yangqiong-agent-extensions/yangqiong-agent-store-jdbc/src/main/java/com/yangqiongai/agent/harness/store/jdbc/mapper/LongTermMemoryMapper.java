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

import java.util.List;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yangqiongai.agent.harness.store.jdbc.entity.LongTermMemoryEntity;

/**
 * 长期记忆映射
 * @author yangqiong
 */
public interface LongTermMemoryMapper extends BaseMapper<LongTermMemoryEntity> {

    /**
     * 按任一分词命中检索记忆
     * @param scopeId
     * @param userId
     * @param tokens
     * @return
     */
    @Select({"<script>",
            "SELECT id, content FROM harness_long_term_memory",
            "WHERE scope_id = #{scopeId} AND user_id = #{userId} AND (",
            "<foreach collection='tokens' item='t' separator=' OR '>",
            "LOWER(content) LIKE CONCAT('%', #{t}, '%')",
            "</foreach>",
            ")",
            "</script>"})
    List<LongTermMemoryEntity> searchByTokens(@Param("scopeId") String scopeId,
                                              @Param("userId") String userId,
                                              @Param("tokens") List<String> tokens);
}
