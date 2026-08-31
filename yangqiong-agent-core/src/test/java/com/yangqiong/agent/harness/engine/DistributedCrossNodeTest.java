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
package com.yangqiong.agent.harness.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import com.yangqiong.agent.harness.core.event.AgentEvent;
import com.yangqiong.agent.harness.core.event.AgentEventType;
import com.yangqiong.agent.harness.core.message.AgentMessage;
import com.yangqiong.agent.harness.core.message.AgentTextBlock;
import com.yangqiong.agent.harness.core.message.AgentToolResultBlock;
import com.yangqiong.agent.harness.core.message.AgentToolUseBlock;
import com.yangqiong.agent.harness.durable.AgentRunState;
import com.yangqiong.agent.harness.durable.InMemoryAgentRunStore;
import com.yangqiong.agent.harness.durable.InMemoryApprovalStore;
import com.yangqiong.agent.harness.durable.InMemoryCheckpointStore;
import com.yangqiong.agent.harness.durable.InMemoryRunLockStore;
import com.yangqiong.agent.harness.durable.RunLockStore;
import com.yangqiong.agent.harness.core.message.MessageFactory;
import com.yangqiong.agent.harness.tool.InMemoryToolExecutionStore;
import org.junit.jupiter.api.Test;

/**
 * 跨节点语义模拟测试
 * <p>
 * 两个节点（持久执行状态跟踪器实例）共享同一套存储实现，
 * 验证会话续跑、审批跨实例衔接、工具执行去重、运行锁互斥。
 * </p>
 * @author yangqiong
 */
class DistributedCrossNodeTest {

    private AgentRuntimeContext newContext(String sessionId) {
        return AgentRuntimeContext.builder()
                .scopeId("scope-1")
                .sessionId(sessionId)
                .userId("user-1")
                .build();
    }

    @Test
    void shouldRunLockBeMutuallyExclusiveAcrossNodes() {
        RunLockStore lockStore = new InMemoryRunLockStore();
        DurableExecutionTracker nodeA =
                new DurableExecutionTracker(null, null, null, lockStore, "node-A", Duration.ofSeconds(30));
        DurableExecutionTracker nodeB =
                new DurableExecutionTracker(null, null, null, lockStore, "node-B", Duration.ofSeconds(30));

        String runId = nodeA.ensureRunning(newContext("session-1"), "agent");
        // 节点B接管同一run：恢复runId到上下文后抢锁，节点A持有则抢锁失败
        AgentRuntimeContext ctxB = newContext("session-1");
        ctxB.put(DurableExecutionTracker.ATTR_RUN_ID, runId);
        assertThat(lockStore.owner(runId)).contains("node-A");
        nodeB.ensureRunning(ctxB, "agent");
        assertThat(lockStore.owner(runId)).contains("node-A");

        // 运行到达终态，节点A释放锁后节点B以同一runId接管
        nodeA.onEvent(AgentEvent.of(AgentEventType.AGENT_RESULT, null), runId);
        assertThat(lockStore.owner(runId)).isEmpty();
        nodeB.ensureRunning(ctxB, "agent");
        assertThat(lockStore.owner(runId)).contains("node-B");
    }

    @Test
    void shouldSessionResumeAcrossNodesWithSameRunId() {
        InMemoryAgentRunStore runStore = new InMemoryAgentRunStore();
        DurableExecutionTracker nodeA =
                new DurableExecutionTracker(runStore, null, null);
        DurableExecutionTracker nodeB =
                new DurableExecutionTracker(runStore, null, null);

        AgentRuntimeContext ctxA = newContext("session-1");
        AgentRuntimeContext ctxB = newContext("session-1");
        String runIdA = nodeA.ensureRunning(ctxA, "agent");
        // 节点B从共享运行记录存储恢复runId后续跑
        ctxB.put(DurableExecutionTracker.ATTR_RUN_ID, runIdA);
        String runIdB = nodeB.ensureRunning(ctxB, "agent");

        assertThat(runIdB).isEqualTo(runIdA);
        assertThat(runStore.findByRunId(runIdB).get().getState()).isEqualTo(AgentRunState.RUNNING);
        assertThat(runStore.findByRunId(runIdB).get().getScopeId()).isEqualTo("scope-1");
    }

    @Test
    void shouldApprovalResolveAcrossInstances() {
        InMemoryAgentRunStore runStore = new InMemoryAgentRunStore();
        InMemoryApprovalStore approvalStore = new InMemoryApprovalStore();
        InMemoryCheckpointStore checkpointStore = new InMemoryCheckpointStore();
        DurableExecutionTracker nodeA =
                new DurableExecutionTracker(runStore, checkpointStore, approvalStore);
        DurableExecutionTracker nodeB =
                new DurableExecutionTracker(runStore, checkpointStore, approvalStore);

        AgentRuntimeContext ctx = newContext("session-2");
        String runId = nodeA.ensureRunning(ctx, "agent");
        AgentToolUseBlock call = new AgentToolUseBlock("send_email", "call-1", Map.of());
        List<AgentMessage> msgs = List.of(MessageFactory.createUserMessage("发送邮件"));
        nodeA.markWaitingApproval(ctx, runId, List.of(call), msgs, 1);

        assertThat(approvalStore.findByToolCallId("call-1")).isPresent();
        assertThat(approvalStore.findByToolCallId("call-1").get().getState().name()).isEqualTo("PENDING");

        // 节点B对节点A创建的审批落定
        nodeB.resolveApproval(ctx, "call-1", true, "批准");
        assertThat(approvalStore.findByToolCallId("call-1").get().getState().name()).isEqualTo("APPROVED");
    }

    @Test
    void shouldToolResultDeduplicateAcrossNodes() {
        InMemoryToolExecutionStore sharedStore = new InMemoryToolExecutionStore();
        AgentToolResultBlock first = AgentToolResultBlock.of("call-7",
                List.of(AgentTextBlock.builder().text("节点A执行结果").build()));

        // 节点A执行工具并记录
        sharedStore.record("call-7", first);
        // 节点B恢复时命中共享记录，不重复执行
        assertThat(sharedStore.isCompleted("call-7")).isTrue();
        assertThat(sharedStore.getResult("call-7").getTextContent()).isEqualTo("节点A执行结果");
    }
}