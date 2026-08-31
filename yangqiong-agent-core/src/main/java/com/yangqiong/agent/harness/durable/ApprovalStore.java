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
package com.yangqiong.agent.harness.durable;

import java.util.List;
import java.util.Optional;

/**
 * 审批存储
 * <p>
 * 持久执行存储三SPI之三：HITL审批记录权威状态源，跨天/跨进程审批恢复的依据。
 * </p>
 * @author yangqiong
 */
public interface ApprovalStore {

    /**
     * 保存待审批记录
     * @param record
     * @return
     */
    ApprovalRecord create(ApprovalRecord record);

    /**
     * 按审批ID查询
     * @param approvalId
     * @return
     */
    Optional<ApprovalRecord> findByApprovalId(String approvalId);

    /**
     * 按工具调用ID查询最近审批
     * @param toolCallId
     * @return
     */
    Optional<ApprovalRecord> findByToolCallId(String toolCallId);

    /**
     * 审批落定（幂等，重复落定返回当前状态）
     * @param approvalId
     * @param approved
     * @param reason
     * @return
     */
    ApprovalRecord resolve(String approvalId, boolean approved, String reason);

    /**
     * 列出租户待审批记录
     * @param scopeId
     * @return
     */
    List<ApprovalRecord> findPending(String scopeId);

    /**
     * 列出运行的全部审批记录
     * @param runId
     * @return
     */
    List<ApprovalRecord> findByRunId(String runId);
}
