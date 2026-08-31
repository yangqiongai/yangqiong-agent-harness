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
package com.yangqiong.agent.harness.store.redis;

import com.yangqiong.agent.harness.core.memory.AgentLongTermMemory;
import com.yangqiong.agent.harness.core.memory.SessionMemory;
import com.yangqiong.agent.harness.durable.ApprovalStore;
import com.yangqiong.agent.harness.durable.AgentRunStore;
import com.yangqiong.agent.harness.durable.CheckpointStore;
import com.yangqiong.agent.harness.durable.DistributedStores;
import com.yangqiong.agent.harness.durable.RunLockStore;
import com.yangqiong.agent.harness.tool.ToolExecutionStore;
import redis.clients.jedis.JedisPooled;

/**
 * 分布式存储Redis聚合实现
 * <p>
 * 收敛共享存储三件套、记忆、工具执行记录与运行锁为一组装配单元，
 * 使用方一行装配全部共享存储。
 * </p>
 * @author yangqiong
 */
public class RedisDistributedStores implements DistributedStores {

    /**
     * Redis客户端（线程安全）
     */
    private final JedisPooled jedis;

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
     * 构建聚合存储
     * @param jedis
     */
    public RedisDistributedStores(JedisPooled jedis) {
        this.jedis = jedis;
        this.runStore = new RedisAgentRunStore(jedis);
        this.checkpointStore = new RedisCheckpointStore(jedis);
        this.approvalStore = new RedisApprovalStore(jedis);
        this.sessionMemory = new RedisSessionMemory(jedis);
        this.longTermMemory = new RedisLongTermMemory(jedis);
        this.toolExecutionStore = new RedisToolExecutionStore(jedis);
        this.runLockStore = new RedisRunLockStore(jedis);
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

    /**
     * 获取底层Redis客户端
     * @return
     */
    public JedisPooled jedis() {
        return jedis;
    }
}