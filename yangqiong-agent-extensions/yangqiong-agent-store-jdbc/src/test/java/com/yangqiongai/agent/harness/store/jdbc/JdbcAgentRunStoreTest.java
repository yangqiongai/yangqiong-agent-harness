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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.yangqiongai.agent.harness.durable.AgentRunRecord;
import com.yangqiongai.agent.harness.durable.AgentRunState;
import com.yangqiongai.agent.harness.durable.AgentRunStore;
import com.yangqiongai.agent.harness.store.jdbc.mapper.RunMapper;

/**
 * 运行存储JDBC实现测试
 * @author yangqiong
 */
class JdbcAgentRunStoreTest {

    /**
     * 运行存储
     */
    private AgentRunStore store;

    @BeforeEach
    void setUp() {
        store = new JdbcAgentRunStore(JdbcTestSupport.newFactory().getMapper(RunMapper.class));
    }

    /**
     * 创建并查询运行记录
     */
    @Test
    void createAndFindByRunId() {
        AgentRunRecord record = new AgentRunRecord("run1", "scope1", "session1", "user1", "agent1");
        store.create(record);

        AgentRunRecord found = store.findByRunId("run1").orElseThrow();
        assertThat(found.getRunId()).isEqualTo("run1");
        assertThat(found.getScopeId()).isEqualTo("scope1");
        assertThat(found.getSessionId()).isEqualTo("session1");
        assertThat(found.getUserId()).isEqualTo("user1");
        assertThat(found.getAgentName()).isEqualTo("agent1");
        assertThat(found.getState()).isEqualTo(AgentRunState.CREATED);
        assertThat(found.getVersion()).isEqualTo(0L);
    }

    /**
     * 重复创建相同runId抛出异常
     */
    @Test
    void createDuplicateRunIdThrows() {
        store.create(new AgentRunRecord("run1", "scope1", "session1", "user1", "agent1"));

        assertThatThrownBy(() -> store.create(new AgentRunRecord("run1", "scope1", "session1", "user1", "agent1")))
                .isInstanceOf(IllegalStateException.class);
    }

    /**
     * 按会话查询运行列表与最近运行
     */
    @Test
    void findBySessionAndLatest() throws InterruptedException {
        store.create(new AgentRunRecord("run1", "scope1", "session1", "user1", "agent1"));
        Thread.sleep(2);
        store.create(new AgentRunRecord("run2", "scope1", "session1", "user1", "agent1"));
        store.create(new AgentRunRecord("run3", "scope1", "session2", "user1", "agent1"));

        assertThat(store.findBySession("scope1", "session1")).hasSize(2);
        assertThat(store.findBySession("scope1", "session2")).hasSize(1);
        assertThat(store.findBySession("scope2", "session1")).isEmpty();

        AgentRunRecord latest = store.findLatestBySession("scope1", "session1").orElseThrow();
        assertThat(latest.getRunId()).isEqualTo("run2");
    }

    /**
     * 状态迁移成功并持久化最新状态与版本
     */
    @Test
    void saveTransitionSuccess() {
        store.create(new AgentRunRecord("run1", "scope1", "session1", "user1", "agent1"));

        AgentRunRecord loaded = store.findByRunId("run1").orElseThrow();
        AgentRunRecord migrated = loaded.transitionTo(AgentRunState.RUNNING, "启动运行");
        store.saveTransition(migrated);

        AgentRunRecord found = store.findByRunId("run1").orElseThrow();
        assertThat(found.getState()).isEqualTo(AgentRunState.RUNNING);
        assertThat(found.getVersion()).isEqualTo(1L);
        assertThat(found.getTransitions()).hasSize(2);
    }

    /**
     * 乐观锁冲突时状态迁移抛异常
     */
    @Test
    void saveTransitionVersionConflictThrows() {
        store.create(new AgentRunRecord("run1", "scope1", "session1", "user1", "agent1"));

        AgentRunRecord first = new AgentRunRecord("run1", "scope1", "session1", "user1", "agent1");
        AgentRunRecord second = new AgentRunRecord("run1", "scope1", "session1", "user1", "agent1");
        store.saveTransition(second.transitionTo(AgentRunState.RUNNING, "并发迁移B"));

        // 旧副本基于过期版本0再次迁移应冲突
        assertThatThrownBy(() -> store.saveTransition(first.transitionTo(AgentRunState.RUNNING, "并发迁移A")))
                .isInstanceOf(IllegalStateException.class);
    }

    /**
     * 查询等待审批的运行
     */
    @Test
    void findWaitingApproval() {
        AgentRunRecord waiting1 = new AgentRunRecord("run1", "scope1", "session1", "user1", "agent1");
        store.create(waiting1);
        store.saveTransition(waiting1.transitionTo(AgentRunState.RUNNING, "启动"));
        store.saveTransition(waiting1.transitionTo(AgentRunState.WAITING_APPROVAL, "待审批"));

        AgentRunRecord waiting2 = new AgentRunRecord("run2", "scope2", "session1", "user1", "agent1");
        store.create(waiting2);
        store.saveTransition(waiting2.transitionTo(AgentRunState.RUNNING, "启动"));
        store.saveTransition(waiting2.transitionTo(AgentRunState.WAITING_APPROVAL, "待审批"));

        AgentRunRecord running = new AgentRunRecord("run3", "scope1", "session1", "user1", "agent1");
        store.create(running);
        store.saveTransition(running.transitionTo(AgentRunState.RUNNING, "启动"));

        assertThat(store.findWaitingApproval("scope1")).hasSize(1);
        assertThat(store.findWaitingApproval("scope1").get(0).getRunId()).isEqualTo("run1");
        assertThat(store.findWaitingApproval(null)).hasSize(2);
    }

    /**
     * 容量边界：多次状态迁移保持版本与迁移历史一致
     */
    @Test
    void transitionCapacityBoundary() {
        AgentRunRecord record = new AgentRunRecord("run1", "scope1", "session1", "user1", "agent1");
        store.create(record);
        saveSteps(record, AgentRunState.RUNNING, AgentRunState.WAITING_APPROVAL,
                AgentRunState.RUNNING, AgentRunState.SUCCEEDED);

        AgentRunRecord found = store.findByRunId("run1").orElseThrow();
        assertThat(found.getState()).isEqualTo(AgentRunState.SUCCEEDED);
        assertThat(found.getVersion()).isEqualTo(4L);
        assertThat(found.getTransitions()).hasSize(5);
    }

    /**
     * 逐个状态迁移并落库，模拟引擎单次迁移后保存
     * @param record
     * @param states
     */
    private void saveSteps(AgentRunRecord record, AgentRunState... states) {
        for (AgentRunState state : states) {
            store.saveTransition(record.transitionTo(state, "迁移至" + state));
        }
    }
}