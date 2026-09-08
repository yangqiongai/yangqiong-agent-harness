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
package com.yangqiongai.agent.harness.memory;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.yangqiongai.agent.harness.core.message.AgentMessage;
import com.yangqiongai.agent.harness.core.message.AgentMessageRole;
import com.yangqiongai.agent.harness.core.message.AgentTextBlock;

/**
 * 检查点管理器测试
 * @author yangqiong
 */
class CheckpointManagerTest {

    @Test
    void shouldSaveAndRestoreMessages() {
        CheckpointManager manager = new CheckpointManager();
        List<AgentMessage> messages = List.of(
                createMessage("hello"),
                createMessage("world"));
        manager.save("session-1", messages, 2);
        List<AgentMessage> restored = manager.restore("session-1");
        assertThat(restored).hasSize(2);
        assertThat(restored.get(0).getTextContent()).isEqualTo("hello");
    }

    @Test
    void shouldRestoreIteration() {
        CheckpointManager manager = new CheckpointManager();
        manager.save("session-1", List.of(), 5);
        assertThat(manager.restoreIteration("session-1")).isEqualTo(5);
    }

    @Test
    void shouldReturnEmptyWhenNoCheckpoint() {
        CheckpointManager manager = new CheckpointManager();
        assertThat(manager.restore("unknown")).isEmpty();
        assertThat(manager.restoreIteration("unknown")).isZero();
    }

    @Test
    void shouldDetectCheckpointExists() {
        CheckpointManager manager = new CheckpointManager();
        assertThat(manager.hasCheckpoint("session-1")).isFalse();
        manager.save("session-1", List.of(), 1);
        assertThat(manager.hasCheckpoint("session-1")).isTrue();
    }

    @Test
    void shouldClearCheckpoint() {
        CheckpointManager manager = new CheckpointManager();
        manager.save("session-1", List.of(), 1);
        manager.clear("session-1");
        assertThat(manager.hasCheckpoint("session-1")).isFalse();
    }

    @Test
    void shouldReturnNullWhenSessionIdNull() {
        CheckpointManager manager = new CheckpointManager();
        manager.save(null, List.of(), 1);
        assertThat(manager.hasCheckpoint(null)).isFalse();
    }

    @Test
    void shouldRestoreSnapshotCopy() {
        CheckpointManager manager = new CheckpointManager();
        List<AgentMessage> messages = new java.util.ArrayList<>();
        messages.add(createMessage("original"));
        manager.save("session-1", messages, 1);
        // 修改原始列表不应影响检查点
        messages.add(createMessage("added"));
        List<AgentMessage> restored = manager.restore("session-1");
        assertThat(restored).hasSize(1);
    }

    private AgentMessage createMessage(String text) {
        return AgentMessage.builder()
                .role(AgentMessageRole.USER)
                .content(List.of(AgentTextBlock.builder().text(text).build()))
                .build();
    }
}
