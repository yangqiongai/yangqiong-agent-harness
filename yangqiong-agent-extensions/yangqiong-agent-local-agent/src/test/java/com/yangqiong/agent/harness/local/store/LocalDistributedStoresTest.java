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
package com.yangqiong.agent.harness.local.store;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.yangqiong.agent.harness.core.memory.AgentLongTermMemory;
import com.yangqiong.agent.harness.core.memory.SessionSummary;
import com.yangqiong.agent.harness.core.message.AgentMessage;
import com.yangqiong.agent.harness.core.message.AgentMessageRole;
import com.yangqiong.agent.harness.core.message.AgentTextBlock;
import com.yangqiong.agent.harness.core.message.AgentToolResultBlock;
import com.yangqiong.agent.harness.durable.AgentCheckpoint;
import com.yangqiong.agent.harness.durable.AgentRunRecord;
import com.yangqiong.agent.harness.durable.AgentRunState;
import com.yangqiong.agent.harness.durable.ApprovalRecord;
import com.yangqiong.agent.harness.durable.CheckpointStore;
import com.yangqiong.agent.harness.durable.RunLockStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * SQLite本地分布式存储测试
 * @author yangqiong
 */
class LocalDistributedStoresTest {

    /**
     * 并发写检查点的线程数
     */
    private static final int CONCURRENT_THREADS = 20;

    /**
     * 每线程写入的检查点数
     */
    private static final int CHECKPOINTS_PER_THREAD = 5;

    /**
     * 临时存储根目录
     */
    @TempDir
    Path tempDir;

    @Test
    void shouldInitializeSchemaAndProvideAllStoresOnFirstCreate() throws Exception {
        Path dbFile = tempDir.resolve("harness.db");
        try (LocalDistributedStores stores = LocalDistributedStores.create(tempDir)) {
            assertThat(dbFile).exists();
            assertSchemaInitialized(dbFile);

            AgentRunRecord created = stores.runStore()
                    .create(new AgentRunRecord("run-smoke", "scope-1", "session-smoke",
                            "user-1", "travel-agent"));
            assertThat(created.getState()).isEqualTo(AgentRunState.CREATED);
            AgentRunRecord transitioned = stores.runStore().findByRunId("run-smoke").orElseThrow()
                    .transitionTo(AgentRunState.RUNNING, null);
            stores.runStore().saveTransition(transitioned);
            assertThat(stores.runStore().findByRunId("run-smoke").orElseThrow().getState())
                    .isEqualTo(AgentRunState.RUNNING);

            stores.checkpointStore().save(new AgentCheckpoint("run-smoke", "scope-1", "session-smoke", 1,
                    List.of(message(AgentMessageRole.USER, "出差审批")), List.of(), List.of(),
                    System.currentTimeMillis(), 1L));
            assertThat(stores.checkpointStore().latest("scope-1", "session-smoke")).isPresent();

            stores.approvalStore().create(ApprovalRecord.pending("approval-1", "run-smoke",
                    "toolu-1", "bookFlight", "scope-1", "approver-1"));
            assertThat(stores.approvalStore().findPending("scope-1")).hasSize(1);
            assertThat(stores.approvalStore().resolve("approval-1", true, "同意出差").getState())
                    .isEqualTo(ApprovalRecord.ApprovalState.APPROVED);

            stores.sessionMemory().saveSummary("scope-1", "session-smoke", "出差审批会话摘要", 3);
            assertThat(stores.sessionMemory().loadSummary("scope-1", "session-smoke").getSummary())
                    .isEqualTo("出差审批会话摘要");

            stores.longTermMemory().store("scope-1", "user-1", "session-smoke",
                    "用户偏好高铁出行", Map.of("category", "travel"));
            assertThat(stores.longTermMemory().search("scope-1", "user-1", "高铁", 5))
                    .containsExactly("用户偏好高铁出行");

            stores.toolExecutionStore().record("tool-exec-1",
                    AgentToolResultBlock.of("toolu-1", List.of(AgentTextBlock.builder().text("已订票").build())));
            assertThat(stores.toolExecutionStore().isCompleted("tool-exec-1")).isTrue();
            assertThat(stores.toolExecutionStore().getResult("tool-exec-1").getTextContent()).isEqualTo("已订票");

            assertThat(stores.runLockStore().tryLock("run-smoke", "node-1", Duration.ofSeconds(60))).isTrue();
            assertThat(stores.runLockStore().owner("run-smoke")).contains("node-1");
        }
    }

    @Test
    void shouldRecoverLatestCheckpointAfterReopen() {
        String runId = "run-restart";
        String scopeId = "scope-1";
        String sessionId = "session-restart";
        AgentCheckpoint saved = new AgentCheckpoint(runId, scopeId, sessionId, 3,
                List.of(message(AgentMessageRole.USER, "帮我订明天去北京的高铁票")),
                List.of(), List.of("toolu-done-1"), System.currentTimeMillis(), 2L);
        try (LocalDistributedStores stores = LocalDistributedStores.create(tempDir)) {
            stores.checkpointStore().save(saved);
        }

        // 模拟进程重启后基于同一目录重新装配本地存储
        try (LocalDistributedStores reopened = LocalDistributedStores.create(tempDir)) {
            AgentCheckpoint restored = reopened.checkpointStore()
                    .latest(scopeId, sessionId).orElseThrow();
            assertThat(restored.getRunId()).isEqualTo(runId);
            assertThat(restored.getIteration()).isEqualTo(3);
            assertThat(restored.getVersion()).isEqualTo(2L);
            assertThat(restored.getCompletedToolUseIds()).containsExactly("toolu-done-1");
            assertThat(restored.getPendingToolCalls()).isEmpty();
            assertThat(restored.getMessages()).hasSize(1);
            assertThat(restored.getMessages().get(0).getRole()).isEqualTo(AgentMessageRole.USER);
            assertThat(restored.getMessages().get(0).getTextContent()).isEqualTo("帮我订明天去北京的高铁票");
        }
    }

    @Test
    void shouldSaveAndLoadSessionMemory() {
        try (LocalDistributedStores stores = LocalDistributedStores.create(tempDir)) {
            // 情节的复合键隔离方法仅在具体类上提供，需以具体类型操作
            SqliteSessionMemory sessionMemory = (SqliteSessionMemory) stores.sessionMemory();
            sessionMemory.saveSummary("scope-1", "session-mem", "首轮对话聚焦差旅审批", 5);
            SessionSummary summary = sessionMemory.loadSummary("scope-1", "session-mem");
            assertThat(summary.getSummary()).isEqualTo("首轮对话聚焦差旅审批");
            assertThat(summary.getSummarizedMessageCount()).isEqualTo(5);

            // 同键再次保存摘要应覆写而非新增
            sessionMemory.saveSummary("scope-1", "session-mem", "摘要更新为报销流程", 8);
            SessionSummary updated = sessionMemory.loadSummary("scope-1", "session-mem");
            assertThat(updated.getSummary()).isEqualTo("摘要更新为报销流程");
            assertThat(updated.getSummarizedMessageCount()).isEqualTo(8);

            sessionMemory.saveEpisode("scope-1", "session-mem", message(AgentMessageRole.USER, "提交出差申请"));
            sessionMemory.saveEpisode("scope-1", "session-mem", message(AgentMessageRole.ASSISTANT, "已进入主管审批环节"));
            sessionMemory.saveEpisode("scope-1", "session-mem", message(AgentMessageRole.USER, "查看审批进度"));
            List<AgentMessage> episodes = sessionMemory.listEpisodes("scope-1", "session-mem", 10);
            assertThat(episodes).hasSize(3);
            assertThat(episodes.get(0).getTextContent()).isEqualTo("提交出差申请");
            assertThat(episodes.get(2).getTextContent()).isEqualTo("查看审批进度");

            // limit只取最近情节并保持时间正序返回
            List<AgentMessage> limited = sessionMemory.listEpisodes("scope-1", "session-mem", 2);
            assertThat(limited).hasSize(2);
            assertThat(limited.get(0).getTextContent()).isEqualTo("已进入主管审批环节");
            assertThat(limited.get(1).getTextContent()).isEqualTo("查看审批进度");

            // 无作用域重载与作用域隔离互不串扰
            sessionMemory.saveSummary("session-plain", "无作用域摘要", 2);
            assertThat(sessionMemory.loadSummary("session-plain").getSummary()).isEqualTo("无作用域摘要");
            sessionMemory.saveSummary("scope-2", "session-mem", "其他租户摘要", 1);
            assertThat(sessionMemory.loadSummary("scope-2", "session-mem").getSummary()).isEqualTo("其他租户摘要");
            assertThat(sessionMemory.loadSummary("scope-1", "session-mem").getSummary())
                    .isEqualTo("摘要更新为报销流程");
        }
    }

    @Test
    void shouldStoreAndSearchLongTermMemory() {
        try (LocalDistributedStores stores = LocalDistributedStores.create(tempDir)) {
            AgentLongTermMemory memory = stores.longTermMemory();
            memory.store("scope-1", "user-1", "session-1", "用户偏好高铁出行", Map.of("category", "travel"));
            memory.store("scope-1", "user-1", "session-1", "用户所在部门为财务部", Map.of("category", "profile"));
            memory.store("scope-1", "user-2", "session-1", "其他用户也偏好高铁出行", null);

            assertThat(memory.search("scope-1", "user-1", "高铁", 5)).containsExactly("用户偏好高铁出行");

            // 用户桶隔离，检索不泄露其他用户记忆
            assertThat(memory.search("scope-1", "user-2", "高铁", 5)).containsExactly("其他用户也偏好高铁出行");

            // 多词命中数优先排序，双词命中应排在单词命中之前
            memory.store("scope-1", "user-1", "session-1", "高铁出差需走财务部报销流程", null);
            List<String> ranked = memory.search("scope-1", "user-1", "高铁 财务部", 5);
            assertThat(ranked).hasSize(3);
            assertThat(ranked.get(0)).isEqualTo("高铁出差需走财务部报销流程");
        }
    }

    @Test
    void shouldRejectUnexpiredLockAndAllowTakeoverAfterExpiry() throws Exception {
        try (LocalDistributedStores stores = LocalDistributedStores.create(tempDir)) {
            RunLockStore lockStore = stores.runLockStore();
            assertThat(lockStore.tryLock("run-lock", "node-a", Duration.ofSeconds(60))).isTrue();
            // 租约未过期，其他节点加锁失败
            assertThat(lockStore.tryLock("run-lock", "node-b", Duration.ofSeconds(60))).isFalse();
            assertThat(lockStore.owner("run-lock")).contains("node-a");

            // 持有者解锁后其他节点可立即加锁
            lockStore.unlock("run-lock", "node-a");
            assertThat(lockStore.tryLock("run-lock", "node-b", Duration.ofSeconds(60))).isTrue();

            // 极短租约到期后其他节点可接管
            assertThat(lockStore.tryLock("run-lock-expired", "node-a", Duration.ofMillis(50))).isTrue();
            awaitOwnerExpired(lockStore, "run-lock-expired");
            assertThat(lockStore.tryLock("run-lock-expired", "node-b", Duration.ofSeconds(60))).isTrue();
            assertThat(lockStore.owner("run-lock-expired")).contains("node-b");
        }
    }

    @Test
    void shouldSupportConcurrentCheckpointWritesWithoutDeadlockOrLoss() throws Exception {
        try (LocalDistributedStores stores = LocalDistributedStores.create(tempDir)) {
            CheckpointStore checkpointStore = stores.checkpointStore();
            ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_THREADS);
            try {
                List<Future<?>> futures = new ArrayList<>();
                for (int i = 0; i < CONCURRENT_THREADS; i++) {
                    int index = i;
                    futures.add(executor.submit(() -> {
                        String runId = "run-conc-" + index;
                        String sessionId = "session-conc-" + index;
                        List<AgentMessage> messages = List.of(message(AgentMessageRole.USER, "并发检查点-" + index));
                        AgentCheckpoint current = new AgentCheckpoint(runId, "scope-conc", sessionId, 1,
                                messages, List.of(), List.of(), System.currentTimeMillis(), 1L);
                        checkpointStore.save(current);
                        for (int version = 2; version <= CHECKPOINTS_PER_THREAD; version++) {
                            current = current.next(messages, List.of(),
                                    List.of("toolu-conc-" + index + "-" + version), version);
                            checkpointStore.save(current);
                        }
                        return null;
                    }));
                }
                for (Future<?> future : futures) {
                    // 死锁场景下以超时快速失败
                    future.get(30, TimeUnit.SECONDS);
                }
            } finally {
                executor.shutdownNow();
            }

            int total = 0;
            for (int i = 0; i < CONCURRENT_THREADS; i++) {
                List<AgentCheckpoint> checkpoints = checkpointStore.findByRunId("run-conc-" + i);
                assertThat(checkpoints).hasSize(CHECKPOINTS_PER_THREAD);
                total += checkpoints.size();
                AgentCheckpoint latest = checkpointStore
                        .latest("scope-conc", "session-conc-" + i).orElseThrow();
                assertThat(latest.getVersion()).isEqualTo(CHECKPOINTS_PER_THREAD);
            }
            assertThat(total).isEqualTo(CONCURRENT_THREADS * CHECKPOINTS_PER_THREAD);
        }
    }

    @Test
    void shouldReportHealthAndCounts() {
        try (LocalDistributedStores stores = LocalDistributedStores.create(tempDir)) {
            stores.runStore().create(new AgentRunRecord("run-health", "scope-1", "s1", "user-1", "agent"));
            stores.checkpointStore().save(new AgentCheckpoint("run-health", "scope-1", "s1", 1,
                    List.of(), List.of(), List.of(), System.currentTimeMillis(), 1L));
            stores.approvalStore().create(ApprovalRecord.pending("appr-health", "run-health",
                    "tu-1", "tool", "scope-1", "user-1"));
            stores.longTermMemory().store("scope-1", "user-1", "s1", "记忆内容", null);

            assertThat(stores.ping()).isTrue();
            LocalDistributedStores.LocalStoreHealth health = stores.health();
            assertThat(health.ok()).isTrue();
            assertThat(health.runCount()).isEqualTo(1);
            assertThat(health.checkpointCount()).isEqualTo(1);
            assertThat(health.approvalCount()).isEqualTo(1);
            assertThat(health.memoryCount()).isEqualTo(1);
            assertThat(health.dbFile()).isEqualTo(tempDir.resolve("harness.db").toString());
        }
    }

    @Test
    void shouldMutuallyExcludeLockAcrossConnections() {
        try (LocalDistributedStores storesA = LocalDistributedStores.create(tempDir)) {
            assertThat(storesA.runLockStore().tryLock("run-cross", "node-a", Duration.ofSeconds(60))).isTrue();
            try (LocalDistributedStores storesB = LocalDistributedStores.create(tempDir)) {
                // 同一库文件另一连接，未过期锁应被拒绝
                assertThat(storesB.runLockStore().tryLock("run-cross", "node-b", Duration.ofSeconds(60))).isFalse();
            }
            storesA.runLockStore().unlock("run-cross", "node-a");
            try (LocalDistributedStores storesC = LocalDistributedStores.create(tempDir)) {
                assertThat(storesC.runLockStore().tryLock("run-cross", "node-b", Duration.ofSeconds(60))).isTrue();
            }
        }
    }

    /**
     * 断言全部业务表已建立且库表结构版本已写入
     * @param dbFile
     * @return
     */
    private void assertSchemaInitialized(Path dbFile) throws SQLException {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile)) {
            Set<String> tables = new HashSet<>();
            try (Statement statement = connection.createStatement();
                 ResultSet resultSet = statement.executeQuery(
                         "SELECT name FROM sqlite_master WHERE type = 'table'")) {
                while (resultSet.next()) {
                    tables.add(resultSet.getString("name"));
                }
            }
            assertThat(tables).contains("harness_run", "harness_checkpoint", "harness_approval",
                    "harness_session_summary", "harness_session_episode", "harness_long_term_memory",
                    "harness_tool_execution", "harness_run_lock");
            assertThat(LocalStoreSchema.currentUserVersion(connection))
                    .isEqualTo(LocalStoreSchema.USER_VERSION);
        }
    }

    /**
     * 等待租约过期直至查询不到持有者
     * @param lockStore
     * @param runId
     * @return
     */
    private void awaitOwnerExpired(RunLockStore lockStore, String runId) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000L;
        while (lockStore.owner(runId).isPresent()) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError("等待租约过期超时: " + runId);
            }
            Thread.sleep(20L);
        }
    }

    /**
     * 构造带单一文本块的测试消息
     * @param role
     * @param text
     * @return
     */
    private AgentMessage message(AgentMessageRole role, String text) {
        return AgentMessage.builder()
                .role(role)
                .content(List.of(AgentTextBlock.builder().text(text).build()))
                .build();
    }
}
