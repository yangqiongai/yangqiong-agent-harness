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
package com.yangqiongai.agent.harness.store.jdbc;

import javax.sql.DataSource;

import com.yangqiongai.agent.harness.core.memory.AgentLongTermMemory;
import com.yangqiongai.agent.harness.core.memory.SessionMemory;
import com.yangqiongai.agent.harness.durable.AgentRunStore;
import com.yangqiongai.agent.harness.durable.ApprovalStore;
import com.yangqiongai.agent.harness.durable.CheckpointStore;
import com.yangqiongai.agent.harness.durable.DistributedStores;
import com.yangqiongai.agent.harness.durable.RunLockStore;
import com.yangqiongai.agent.harness.store.jdbc.mapper.ApprovalMapper;
import com.yangqiongai.agent.harness.store.jdbc.mapper.CheckpointMapper;
import com.yangqiongai.agent.harness.store.jdbc.mapper.LongTermMemoryMapper;
import com.yangqiongai.agent.harness.store.jdbc.mapper.RunLockMapper;
import com.yangqiongai.agent.harness.store.jdbc.mapper.RunMapper;
import com.yangqiongai.agent.harness.store.jdbc.mapper.SessionEpisodeMapper;
import com.yangqiongai.agent.harness.store.jdbc.mapper.SessionSummaryMapper;
import com.yangqiongai.agent.harness.store.jdbc.mapper.ToolExecutionMapper;
import com.yangqiongai.agent.harness.tool.ToolExecutionStore;

/**
 * JDBC分布式存储聚合实现
 * <p>
 * 收敛共享存储全部组件为一组装配单元，共享同一数据源，供使用方一行装配。
 * </p>
 * @author yangqiong
 */
public class JdbcDistributedStores implements DistributedStores {

    /**
     * 运行记录存储
     */
    private final AgentRunStore runStore;

    /**
     * 检查点存储
     */
    private final CheckpointStore checkpointStore;

    /**
     * 审批存储
     */
    private final ApprovalStore approvalStore;

    /**
     * 会话记忆存储
     */
    private final SessionMemory sessionMemory;

    /**
     * 长期记忆存储
     */
    private final AgentLongTermMemory longTermMemory;

    /**
     * 工具执行记录存储
     */
    private final ToolExecutionStore toolExecutionStore;

    /**
     * 运行锁存储
     */
    private final RunLockStore runLockStore;

    /**
     * 构造器：以数据源装配工厂并组装全部存储组件
     * @param dataSource
     */
    public JdbcDistributedStores(DataSource dataSource) {
        JdbcStoresFactory factory = new JdbcStoresFactory(dataSource,
                RunMapper.class, CheckpointMapper.class, ApprovalMapper.class,
                SessionSummaryMapper.class, SessionEpisodeMapper.class,
                LongTermMemoryMapper.class, RunLockMapper.class, ToolExecutionMapper.class);
        this.runStore = new JdbcAgentRunStore(factory.getMapper(RunMapper.class));
        this.checkpointStore = new JdbcCheckpointStore(factory.getMapper(CheckpointMapper.class));
        this.approvalStore = new JdbcApprovalStore(factory.getMapper(ApprovalMapper.class));
        this.sessionMemory = new JdbcSessionMemory(factory.getMapper(SessionSummaryMapper.class),
                factory.getMapper(SessionEpisodeMapper.class));
        this.longTermMemory = new JdbcLongTermMemory(factory.getMapper(LongTermMemoryMapper.class));
        this.toolExecutionStore = new JdbcToolExecutionStore(factory.getMapper(ToolExecutionMapper.class));
        this.runLockStore = new JdbcRunLockStore(factory.getMapper(RunLockMapper.class));
    }

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