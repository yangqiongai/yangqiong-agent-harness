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

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.yangqiong.agent.harness.core.event.AgentEvent;
import com.yangqiong.agent.harness.core.event.AgentEventType;
import com.yangqiong.agent.harness.core.message.AgentMessage;
import com.yangqiong.agent.harness.core.message.AgentToolResultBlock;
import com.yangqiong.agent.harness.core.message.AgentToolUseBlock;
import com.yangqiong.agent.harness.durable.AgentCheckpoint;
import com.yangqiong.agent.harness.durable.AgentRunRecord;
import com.yangqiong.agent.harness.durable.AgentRunState;
import com.yangqiong.agent.harness.durable.ApprovalRecord;
import com.yangqiong.agent.harness.durable.InMemoryAgentRunStore;
import com.yangqiong.agent.harness.durable.InMemoryApprovalStore;
import com.yangqiong.agent.harness.durable.InMemoryCheckpointStore;
import com.yangqiong.agent.harness.core.message.MessageFactory;
import org.junit.jupiter.api.Test;

/**
 * 持久执行状态跟踪器测试
 * @author yangqiong
 */
class DurableExecutionTrackerTest {

    private AgentRuntimeContext newContext() {
        return AgentRuntimeContext.builder()
                .scopeId("scope-1")
                .sessionId("session-1")
                .userId("user-1")
                .build();
    }

    @Test
    void shouldCreateRunAndKeepSameRunId() {
        InMemoryAgentRunStore runStore = new InMemoryAgentRunStore();
        DurableExecutionTracker tracker = new DurableExecutionTracker(runStore, null, null);
        AgentRuntimeContext context = newContext();

        String runId = tracker.ensureRunning(context, "testAgent");
        String runIdAgain = tracker.ensureRunning(context, "testAgent");

        assertThat(runIdAgain).isEqualTo(runId);
        Optional<AgentRunRecord> record = runStore.findByRunId(runId);
        assertThat(record).isPresent();
        assertThat(record.get().getState()).isEqualTo(AgentRunState.RUNNING);
        assertThat(record.get().getScopeId()).isEqualTo("scope-1");
        assertThat(record.get().getAgentName()).isEqualTo("testAgent");
    }

    @Test
    void shouldResumeFromWaitingApproval() {
        InMemoryAgentRunStore runStore = new InMemoryAgentRunStore();
        DurableExecutionTracker tracker = new DurableExecutionTracker(runStore, null, null);
        AgentRuntimeContext context = newContext();

        String runId = tracker.ensureRunning(context, "testAgent");
        runStore.findByRunId(runId).get().transitionTo(AgentRunState.WAITING_APPROVAL, "await");
        String resumed = tracker.ensureRunning(context, "testAgent");

        assertThat(resumed).isEqualTo(runId);
        assertThat(runStore.findByRunId(runId).get().getState()).isEqualTo(AgentRunState.RUNNING);
    }

    @Test
    void shouldPersistWaitingApprovalWithCheckpointAndApprovals() {
        InMemoryAgentRunStore runStore = new InMemoryAgentRunStore();
        InMemoryApprovalStore approvalStore = new InMemoryApprovalStore();
        InMemoryCheckpointStore checkpointStore = new InMemoryCheckpointStore();
        DurableExecutionTracker tracker =
                new DurableExecutionTracker(runStore, checkpointStore, approvalStore);
        AgentRuntimeContext context = newContext();
        String runId = tracker.ensureRunning(context, "testAgent");

        AgentToolUseBlock askCall = new AgentToolUseBlock("send_email", "call-1", Map.of());
        AgentMessage userMsg = MessageFactory.createUserMessage("发送邮件");
        tracker.markWaitingApproval(context, runId, List.of(askCall), List.of(userMsg), 2);

        assertThat(runStore.findByRunId(runId).get().getState())
                .isEqualTo(AgentRunState.WAITING_APPROVAL);
        List<ApprovalRecord> pending = approvalStore.findPending("scope-1");
        assertThat(pending).hasSize(1);
        assertThat(pending.get(0).getToolCallId()).isEqualTo("call-1");
        assertThat(pending.get(0).getRunId()).isEqualTo(runId);

        Optional<AgentCheckpoint> checkpoint = checkpointStore.latest("scope-1", "session-1");
        assertThat(checkpoint).isPresent();
        assertThat(checkpoint.get().getRunId()).isEqualTo(runId);
        assertThat(checkpoint.get().getIteration()).isEqualTo(2);
    }

    @Test
    void shouldResolveApprovalIdempotently() {
        InMemoryApprovalStore approvalStore = new InMemoryApprovalStore();
        DurableExecutionTracker tracker =
                new DurableExecutionTracker(null, null, approvalStore);
        ApprovalRecord record = ApprovalRecord.pending("appr-1", "run-1", "call-1",
                "send_email", "scope-1", "user-1");
        approvalStore.create(record);

        tracker.resolveApproval(null, "call-1", true, "同意");
        tracker.resolveApproval(null, "call-1", false, "重复落定");

        ApprovalRecord resolved = approvalStore.findByToolCallId("call-1").get();
        assertThat(resolved.getState()).isEqualTo(ApprovalRecord.ApprovalState.APPROVED);
    }

    @Test
    void shouldResolveConcurrentApprovalExactlyOnce() throws Exception {
        InMemoryApprovalStore approvalStore = new InMemoryApprovalStore();
        DurableExecutionTracker tracker =
                new DurableExecutionTracker(null, null, approvalStore);
        approvalStore.create(ApprovalRecord.pending("appr-race", "run-1", "call-race",
                "send_email", "scope-1", "user-1"));

        int workers = 16;
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(workers);
        for (int i = 0; i < workers; i++) {
            boolean approved = i % 2 == 0;
            new Thread(() -> {
                try {
                    start.await();
                    tracker.resolveApproval(null, "call-race", approved, approved ? "同意" : "拒绝");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            }).start();
        }
        start.countDown();
        assertThat(done.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();

        // 并发落定后状态必须唯一且为终态，不允许互相覆盖或残留待审批
        ApprovalRecord record = approvalStore.findByToolCallId("call-race").orElseThrow();
        assertThat(record.getState())
                .isIn(ApprovalRecord.ApprovalState.APPROVED, ApprovalRecord.ApprovalState.DENIED);
        assertThat(approvalStore.findPending("scope-1")).isEmpty();
    }

    @Test
    void shouldResumeFromWaitingApprovalConcurrentlyWithoutRegression() throws Exception {
        InMemoryAgentRunStore runStore = new InMemoryAgentRunStore();
        DurableExecutionTracker tracker = new DurableExecutionTracker(runStore, null, null);
        AgentRuntimeContext context = newContext();
        String runId = tracker.ensureRunning(context, "testAgent");
        runStore.findByRunId(runId).get().transitionTo(AgentRunState.WAITING_APPROVAL, "await");

        // 多节点并发续跑同一运行：状态迁移幂等，不允许回退或分裂
        int workers = 8;
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(workers);
        for (int i = 0; i < workers; i++) {
            new Thread(() -> {
                try {
                    start.await();
                    tracker.ensureRunning(context, "testAgent");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            }).start();
        }
        start.countDown();
        assertThat(done.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();

        assertThat(runStore.findByRunId(runId).get().getState()).isEqualTo(AgentRunState.RUNNING);
    }

    @Test
    void shouldRecordCompletedToolCallIds() {
        DurableExecutionTracker tracker = new DurableExecutionTracker(null, null, null);
        AgentRuntimeContext context = newContext();

        tracker.recordCompletedToolCalls(context, List.of(
                MessageFactory.createToolMessage(AgentToolResultBlock.of("call-1", List.of())),
                MessageFactory.createToolMessage(AgentToolResultBlock.of("call-2", List.of()))));

        assertThat(tracker.completedToolIds(context)).containsExactlyInAnyOrder("call-1", "call-2");
    }

    @Test
    void shouldSaveCheckpointsWithMonotonicVersions() {
        InMemoryCheckpointStore checkpointStore = new InMemoryCheckpointStore();
        DurableExecutionTracker tracker =
                new DurableExecutionTracker(null, checkpointStore, null);
        AgentRuntimeContext context = newContext();

        tracker.saveCheckpoint(context, "run-1", 1, List.of(), List.of(), null);
        tracker.saveCheckpoint(context, "run-1", 2, List.of(), List.of(), null);

        Optional<AgentCheckpoint> latest = checkpointStore.latest("scope-1", "session-1");
        assertThat(latest).isPresent();
        assertThat(latest.get().getIteration()).isEqualTo(2);
        assertThat(latest.get().getVersion()).isEqualTo(2L);
        assertThat(checkpointStore.findByRunId("run-1")).hasSize(2);
    }

    @Test
    void shouldTransitionTerminalStatesOnEvent() {
        InMemoryAgentRunStore runStore = new InMemoryAgentRunStore();
        DurableExecutionTracker tracker = new DurableExecutionTracker(runStore, null, null);
        AgentRuntimeContext context = newContext();
        String runId = tracker.ensureRunning(context, "testAgent");

        tracker.onEvent(AgentEvent.of(AgentEventType.ERROR, "模型超时"), runId);
        assertThat(runStore.findByRunId(runId).get().getState()).isEqualTo(AgentRunState.FAILED);

        tracker.onEvent(AgentEvent.of(AgentEventType.AGENT_RESULT, "done"), runId);
        assertThat(runStore.findByRunId(runId).get().getState()).isEqualTo(AgentRunState.FAILED);
    }

    @Test
    void shouldDegradeGracefullyWithoutStores() {
        DurableExecutionTracker tracker = new DurableExecutionTracker(null, null, null);
        AgentRuntimeContext context = newContext();

        assertThat(tracker.isEnabled()).isFalse();
        assertThat(tracker.ensureRunning(context, "testAgent")).isNotNull();
        tracker.markWaitingApproval(context, "run-x", List.of(), List.of(), 1);
        tracker.resolveApproval(context, "call-1", true, "ok");
        tracker.onEvent(AgentEvent.of(AgentEventType.AGENT_RESULT, null), "run-x");
        assertThat(tracker.latestCheckpoint("scope-1", "session-1")).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldClearCheckpointVersionIndexOnTerminalEvent() throws Exception {
        InMemoryCheckpointStore checkpointStore = new InMemoryCheckpointStore();
        DurableExecutionTracker tracker = new DurableExecutionTracker(null, checkpointStore, null);
        AgentRuntimeContext context = newContext();

        tracker.saveCheckpoint(context, "run-1", 1, List.of(), List.of(), null);
        tracker.saveCheckpoint(context, "run-1", 2, List.of(), List.of(), null);
        java.lang.reflect.Field field = DurableExecutionTracker.class.getDeclaredField("lastCheckpointVersion");
        field.setAccessible(true);
        Map<String, Long> index = (Map<String, Long>) field.get(tracker);
        assertThat(index).containsKey("run-1");

        tracker.onEvent(AgentEvent.of(AgentEventType.AGENT_RESULT, "done"), "run-1");
        assertThat(index).doesNotContainKey("run-1");

        // 索引清理后同会话新run版本自存储基线递增，不回退
        tracker.saveCheckpoint(context, "run-2", 1, List.of(), List.of(), null);
        Optional<AgentCheckpoint> latest = checkpointStore.latest("scope-1", "session-1");
        assertThat(latest).isPresent();
        assertThat(latest.get().getVersion()).isEqualTo(3L);
    }
}
