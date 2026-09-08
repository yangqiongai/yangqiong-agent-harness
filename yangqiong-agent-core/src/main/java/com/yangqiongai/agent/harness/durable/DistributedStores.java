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

import com.yangqiongai.agent.harness.core.memory.AgentLongTermMemory;
import com.yangqiongai.agent.harness.core.memory.SessionMemory;
import com.yangqiongai.agent.harness.tool.ToolExecutionStore;

/**
 * 分布式存储聚合接口
 * <p>
 * 收敛共享存储三件套 + 记忆 + 工具执行记录 + 运行锁为一组装配单元，
 * 扩展模块提供各自组合实现，供使用方一行装配。
 * </p>
 * @author yangqiong
 */
public interface DistributedStores {

    /**
     * 运行记录存储
     * @return
     */
    AgentRunStore runStore();

    /**
     * 检查点存储
     * @return
     */
    CheckpointStore checkpointStore();

    /**
     * 审批存储
     * @return
     */
    ApprovalStore approvalStore();

    /**
     * 会话记忆存储
     * @return
     */
    SessionMemory sessionMemory();

    /**
     * 长期记忆存储
     * @return
     */
    AgentLongTermMemory longTermMemory();

    /**
     * 工具执行记录存储
     * @return
     */
    ToolExecutionStore toolExecutionStore();

    /**
     * 运行锁存储
     * @return
     */
    RunLockStore runLockStore();
}
