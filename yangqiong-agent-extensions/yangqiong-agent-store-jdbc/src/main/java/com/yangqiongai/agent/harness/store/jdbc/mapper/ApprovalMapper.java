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
import org.apache.ibatis.annotations.Update;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yangqiongai.agent.harness.store.jdbc.entity.ApprovalEntity;

/**
 * 人工审批记录映射
 * @author yangqiong
 */
public interface ApprovalMapper extends BaseMapper<ApprovalEntity> {

    /**
     * 幂等审批落定：仅待审批记录可落定，重复落定返回0
     * @param approvalId
     * @param state
     * @param reason
     * @return
     */
    @Update("UPDATE harness_approval SET state = #{state}, reason = #{reason} "
            + "WHERE approval_id = #{approvalId} AND state = 'PENDING'")
    int resolvePending(@Param("approvalId") String approvalId, @Param("state") String state,
                       @Param("reason") String reason);
}
