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

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.yangqiongai.agent.harness.core.message.AgentMessage;
import com.yangqiongai.agent.harness.core.message.AgentMessageRole;
import com.yangqiongai.agent.harness.core.message.AgentTextBlock;
import com.yangqiongai.agent.harness.core.message.AgentToolUseBlock;
import com.yangqiongai.agent.harness.durable.AgentCheckpoint;
import com.yangqiongai.agent.harness.durable.CheckpointStore;
import com.yangqiongai.agent.harness.store.jdbc.mapper.CheckpointMapper;

/**
 * 检查点存储JDBC实现测试
 * @author yangqiong
 */
class JdbcCheckpointStoreTest {

    /**
     * 检查点存储
     */
    private CheckpointStore store;

    @BeforeEach
    void setUp() {
        store = new JdbcCheckpointStore(JdbcTestSupport.newFactory().getMapper(CheckpointMapper.class));
    }

    /**
     * 保存并读取最近检查点
     */
    @Test
    void saveAndLatest() {
        AgentCheckpoint checkpoint = checkpoint("run1", "scope1", "session1", 1, "你好，助手");
        store.save(checkpoint);

        AgentCheckpoint latest = store.latest("scope1", "session1").orElseThrow();
        assertThat(latest.getRunId()).isEqualTo("run1");
        assertThat(latest.getVersion()).isEqualTo(1L);
        assertThat(latest.getMessages().get(0).getTextContent()).isEqualTo("你好，助手");
    }

    /**
     * 版本回退保存抛出异常
     */
    @Test
    void saveVersionConflictThrows() {
        store.save(checkpoint("run1", "scope1", "session1", 1, "第一版"));
        store.save(checkpoint("run1", "scope1", "session1", 2, "第二版"));

        assertThatThrownBy(() -> store.save(checkpoint("run1", "scope1", "session1", 2, "旧版本覆盖")))
                .isInstanceOf(IllegalStateException.class);
    }

    /**
     * 新运行接管会话键：版本需高于会话最新版本（引擎语义为最新版本+1）
     */
    @Test
    void saveNewRunTakesOverSessionKey() {
        store.save(checkpoint("run1", "scope1", "session1", 5, "run1最新"));
        store.save(checkpoint("run2", "scope1", "session1", 6, "run2首版"));

        AgentCheckpoint latest = store.latest("scope1", "session1").orElseThrow();
        assertThat(latest.getRunId()).isEqualTo("run2");
    }

    /**
     * 新运行版本回退到会话最新版本之下被拒绝
     */
    @Test
    void newRunBelowSessionVersionRejected() {
        store.save(checkpoint("run1", "scope1", "session1", 5, "run1最新"));

        assertThatThrownBy(() -> store.save(checkpoint("run2", "scope1", "session1", 4, "run2回退版")))
                .isInstanceOf(IllegalStateException.class);
        assertThat(store.latest("scope1", "session1").orElseThrow().getRunId()).isEqualTo("run1");
    }

    /**
     * 按运行ID列出全部检查点且版本升序
     */
    @Test
    void findByRunIdOrdered() {
        store.save(checkpoint("run1", "scope1", "session1", 1, "第一版"));
        store.save(checkpoint("run1", "scope1", "session1", 2, "第二版"));
        store.save(checkpoint("run1", "scope1", "session1", 3, "第三版"));

        List<AgentCheckpoint> list = store.findByRunId("run1");
        assertThat(list).hasSize(3);
        assertThat(list.get(0).getVersion()).isEqualTo(1L);
        assertThat(list.get(2).getVersion()).isEqualTo(3L);
        assertThat(store.findByRunId(null)).isEmpty();
    }

    /**
     * 清理运行检查点
     */
    @Test
    void clearRunCheckpoints() {
        store.save(checkpoint("run1", "scope1", "session1", 1, "第一版"));
        store.save(checkpoint("run2", "scope1", "session2", 1, "其他运行"));

        store.clear("run1");

        assertThat(store.findByRunId("run1")).isEmpty();
        assertThat(store.latest("scope1", "session1")).isEmpty();
        assertThat(store.latest("scope1", "session2")).isPresent();
    }

    /**
     * 容量边界：检查点携带待执行工具调用与已完成ID往返一致
     */
    @Test
    void checkpointContentRoundTrip() {
        AgentToolUseBlock toolUse = new AgentToolUseBlock("search", "tool-1", Map.of("q", "test"));
        AgentCheckpoint checkpoint = new AgentCheckpoint("run1", "scope1", "session1", 3,
                List.of(message("用户消息"), message("助手回复")),
                List.of(toolUse),
                List.of("done-1", "done-2"),
                System.currentTimeMillis(), 7L);
        store.save(checkpoint);

        AgentCheckpoint found = store.latest("scope1", "session1").orElseThrow();
        assertThat(found.getIteration()).isEqualTo(3);
        assertThat(found.getMessages()).hasSize(2);
        assertThat(found.getPendingToolCalls()).hasSize(1);
        assertThat(found.getPendingToolCalls().get(0).getToolUseId()).isEqualTo("tool-1");
        assertThat(found.getCompletedToolUseIds()).containsExactly("done-1", "done-2");
        assertThat(found.getVersion()).isEqualTo(7L);
    }

    /**
     * 构造检查点
     * @param runId
     * @param scopeId
     * @param sessionId
     * @param version
     * @param text
     * @return
     */
    private AgentCheckpoint checkpoint(String runId, String scopeId, String sessionId, long version, String text) {
        return new AgentCheckpoint(runId, scopeId, sessionId, 1,
                List.of(message(text)), List.of(), List.of(), System.currentTimeMillis(), version);
    }

    /**
     * 构造文本消息
     * @param text
     * @return
     */
    private AgentMessage message(String text) {
        return AgentMessage.builder()
                .role(AgentMessageRole.USER)
                .content(List.of(AgentTextBlock.builder().text(text).build()))
                .build();
    }
}