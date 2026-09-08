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

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.yangqiongai.agent.harness.core.memory.SessionSummary;
import com.yangqiongai.agent.harness.core.message.AgentMessage;
import com.yangqiongai.agent.harness.core.message.AgentMessageRole;
import com.yangqiongai.agent.harness.core.message.AgentTextBlock;
import com.yangqiongai.agent.harness.durable.AgentRunRecord;
import com.yangqiongai.agent.harness.durable.AgentRunState;
import com.yangqiongai.agent.harness.durable.DistributedStores;

/**
 * JDBC分布式存储聚合实现测试
 * @author yangqiong
 */
class JdbcDistributedStoresTest {

    /**
     * 数据源
     */
    private DataSource dataSource;

    /**
     * 聚合存储
     */
    private DistributedStores stores;

    @BeforeEach
    void setUp() {
        dataSource = JdbcTestSupport.newDataSource();
        stores = new JdbcDistributedStores(dataSource);
    }

    /**
     * 聚合暴露全部组件且共享同一数据源
     */
    @Test
    void exposeAllComponents() {
        assertThat(stores.runStore()).isNotNull();
        assertThat(stores.checkpointStore()).isNotNull();
        assertThat(stores.approvalStore()).isNotNull();
        assertThat(stores.sessionMemory()).isNotNull();
        assertThat(stores.longTermMemory()).isNotNull();
        assertThat(stores.toolExecutionStore()).isNotNull();
        assertThat(stores.runLockStore()).isNotNull();
    }

    /**
     * 聚合组件可协同完成一次运行全流程
     */
    @Test
    void endToEndFlow() {
        AgentRunRecord record = new AgentRunRecord("run1", "scope1", "session1", "user1", "agent1");
        stores.runStore().create(record);
        stores.runStore().saveTransition(record.transitionTo(AgentRunState.RUNNING, "启动"));

        assertThat(stores.runStore().findByRunId("run1")).isPresent();
        assertThat(stores.runStore().findByRunId("run1").orElseThrow().getState())
                .isEqualTo(AgentRunState.RUNNING);

        stores.sessionMemory().saveSummary("scope1", "session1", "会话摘要", 5);
        SessionSummary summary = stores.sessionMemory().loadSummary("scope1", "session1");
        assertThat(summary.getSummary()).isEqualTo("会话摘要");

        stores.runLockStore().tryLock("run1", "node1", java.time.Duration.ofMinutes(5));
        assertThat(stores.runLockStore().owner("run1")).hasValue("node1");
    }
}