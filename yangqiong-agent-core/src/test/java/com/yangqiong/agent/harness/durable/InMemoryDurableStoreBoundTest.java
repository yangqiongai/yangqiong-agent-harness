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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Durable内存存储容量上限淘汰
 * @author yangqiong
 */
public class InMemoryDurableStoreBoundTest {

    /**
     * 检查点会话键超限时淘汰进度最旧检查点及其runId历史
     */
    @Test
    void checkpointStore_会话键超限淘汰最旧检查点及其历史() {
        InMemoryCheckpointStore store = new InMemoryCheckpointStore();
        // 最早会话使用最低版本，确保成为淘汰目标
        store.save(new AgentCheckpoint("run-old", "tenant-a", "session-0", 1,
                null, null, null, System.currentTimeMillis(), 1L));
        for (int i = 1; i < InMemoryCheckpointStore.MAX_SESSION_KEYS; i++) {
            store.save(new AgentCheckpoint("run-" + i, "tenant-a", "session-" + i, 1,
                    null, null, null, System.currentTimeMillis(), 2L));
        }
        store.save(new AgentCheckpoint("run-new", "tenant-a", "session-new", 1,
                null, null, null, System.currentTimeMillis(), 3L));
        assertThat(store.latest("tenant-a", "session-0")).isEmpty();
        assertThat(store.findByRunId("run-old")).isEmpty();
        assertThat(store.latest("tenant-a", "session-new")).isPresent();
    }

    /**
     * 运行记录超限时淘汰创建时间最旧记录并保持两索引一致
     */
    @Test
    void agentRunStore_运行记录超限淘汰最旧记录() throws InterruptedException {
        InMemoryAgentRunStore store = new InMemoryAgentRunStore();
        store.create(new AgentRunRecord("run-0", "tenant-a", "session-0", "user-0", "agent"));
        // 保证最早记录创建时间严格最小
        Thread.sleep(10L);
        for (int i = 1; i <= InMemoryAgentRunStore.MAX_RUNS; i++) {
            store.create(new AgentRunRecord("run-" + i, "tenant-a", "session-" + i,
                    "user-" + i, "agent"));
        }
        assertThat(store.findByRunId("run-0")).isEmpty();
        assertThat(store.findBySession("tenant-a", "session-0")).isEmpty();
        store.create(new AgentRunRecord("run-last", "tenant-a", "session-last", "user-x", "agent"));
        assertThat(store.findByRunId("run-last")).isPresent();
    }

    /**
     * 审批记录超限时淘汰创建时间最旧记录并保持双索引一致
     */
    @Test
    void approvalStore_审批记录超限淘汰最旧记录() throws InterruptedException {
        InMemoryApprovalStore store = new InMemoryApprovalStore();
        store.create(ApprovalRecord.pending("approval-0", "run-0", "call-0", "tool", "tenant-a", "boss"));
        // 保证最早记录创建时间严格最小
        Thread.sleep(10L);
        for (int i = 1; i <= InMemoryApprovalStore.MAX_RECORDS; i++) {
            store.create(ApprovalRecord.pending("approval-" + i, "run-" + i,
                    "call-" + i, "tool", "tenant-a", "boss"));
        }
        assertThat(store.findByApprovalId("approval-0")).isEmpty();
        assertThat(store.findByToolCallId("call-0")).isEmpty();
        assertThat(store.findByApprovalId("approval-" + InMemoryApprovalStore.MAX_RECORDS)).isPresent();
    }
}
