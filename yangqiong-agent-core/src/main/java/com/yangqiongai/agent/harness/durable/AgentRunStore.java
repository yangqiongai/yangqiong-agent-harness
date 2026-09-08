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
package com.yangqiongai.agent.harness.durable;

import java.util.List;
import java.util.Optional;

/**
 * Agent运行存储
 * <p>
 * 持久执行存储三SPI之一：运行记录权威状态源。
 * JDBC为权威实现（外部模块供给），此处定义接口，引擎自带内存兜底实现。
 * 键统一为 (scopeId, sessionId) 复合键语义。
 * </p>
 * @author yangqiong
 */
public interface AgentRunStore {

    /**
     * 创建运行记录
     * @param record
     * @return
     */
    AgentRunRecord create(AgentRunRecord record);

    /**
     * 按运行ID查询
     * @param runId
     * @return
     */
    Optional<AgentRunRecord> findByRunId(String runId);

    /**
     * 按复合键查询会话最近一次运行
     * @param scopeId
     * @param sessionId
     * @return
     */
    Optional<AgentRunRecord> findLatestBySession(String scopeId, String sessionId);

    /**
     * 按复合键列出会话全部运行
     * @param scopeId
     * @param sessionId
     * @return
     */
    List<AgentRunRecord> findBySession(String scopeId, String sessionId);

    /**
     * 保存状态迁移（实现负责并发安全）
     * @param record 已迁移的记录
     * @return 保存后的最新记录
     */
    AgentRunRecord saveTransition(AgentRunRecord record);

    /**
     * 列出等待审批的运行
     * @param scopeId
     * @return
     */
    List<AgentRunRecord> findWaitingApproval(String scopeId);
}
