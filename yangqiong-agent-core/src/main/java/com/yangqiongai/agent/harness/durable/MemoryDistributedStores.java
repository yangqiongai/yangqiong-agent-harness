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
import com.yangqiongai.agent.harness.memory.InMemoryLongTermMemory;
import com.yangqiongai.agent.harness.memory.InMemorySessionMemory;
import com.yangqiongai.agent.harness.tool.InMemoryToolExecutionStore;
import com.yangqiongai.agent.harness.tool.ToolExecutionStore;

/**
 * 内存共享存储
 * <p>
 * 全部存储使用内存实现，作为分布式存储的内存兜底，
 * 适合单机演示、测试及未引入持久化扩展时的默认场景。
 * </p>
 * @author yangqiong
 */
public class MemoryDistributedStores implements DistributedStores {

    /**
     * 运行记录存储
     */
    private final AgentRunStore runStore = new InMemoryAgentRunStore();

    /**
     * 检查点存储
     */
    private final CheckpointStore checkpointStore = new InMemoryCheckpointStore();

    /**
     * 审批存储
     */
    private final ApprovalStore approvalStore = new InMemoryApprovalStore();

    /**
     * 会话记忆存储
     */
    private final SessionMemory sessionMemory = new InMemorySessionMemory();

    /**
     * 长期记忆存储
     */
    private final AgentLongTermMemory longTermMemory = new InMemoryLongTermMemory();

    /**
     * 工具执行记录存储
     */
    private final ToolExecutionStore toolExecutionStore = new InMemoryToolExecutionStore();

    /**
     * 运行锁存储
     */
    private final RunLockStore runLockStore = new InMemoryRunLockStore();

    @Override
    public AgentRunStore runStore() {
        return runStore;
    }

    @Override
    public CheckpointStore checkpointStore() {
        return checkpointStore;
    }

    @Override
    public ApprovalStore approvalStore() {
        return approvalStore;
    }

    @Override
    public SessionMemory sessionMemory() {
        return sessionMemory;
    }

    @Override
    public AgentLongTermMemory longTermMemory() {
        return longTermMemory;
    }

    @Override
    public ToolExecutionStore toolExecutionStore() {
        return toolExecutionStore;
    }

    @Override
    public RunLockStore runLockStore() {
        return runLockStore;
    }
}