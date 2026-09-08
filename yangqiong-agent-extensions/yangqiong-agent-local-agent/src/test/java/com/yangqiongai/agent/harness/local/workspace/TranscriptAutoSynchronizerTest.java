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
package com.yangqiongai.agent.harness.local.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import com.yangqiongai.agent.harness.core.event.AgentEvent;
import com.yangqiongai.agent.harness.core.event.AgentEventType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 会话转录自动同步器测试
 * @author yangqiong
 */
class TranscriptAutoSynchronizerTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldAutoPersistAssistantDeltaOnRoundEnd() {
        LocalWorkspace ws = new LocalWorkspace(tempDir.resolve("ws")).init();
        SessionTranscriptStore store = new SessionTranscriptStore(ws);
        TranscriptAutoSynchronizer sync = new TranscriptAutoSynchronizer(store, "s1");

        sync.onEvent(AgentEvent.of(AgentEventType.AGENT_START, null, "root", "t0", "sp0"));
        sync.onEvent(AgentEvent.of(AgentEventType.TEXT_BLOCK_DELTA, "你好"));
        sync.onEvent(AgentEvent.of(AgentEventType.TEXT_BLOCK_DELTA, "，世界"));
        sync.onEvent(AgentEvent.of(AgentEventType.AGENT_RESULT, "ok"));

        List<Map<String, Object>> records = store.readAll("s1");
        assertThat(records).hasSize(1);
        assertThat(records.get(0)).containsEntry("role", "assistant");
        assertThat(records.get(0)).containsEntry("content", "你好，世界");
        assertThat(records.get(0)).containsEntry("attemptId", "sp0");
    }

    @Test
    void shouldRecordUserInputExplicitly() {
        LocalWorkspace ws = new LocalWorkspace(tempDir.resolve("ws")).init();
        SessionTranscriptStore store = new SessionTranscriptStore(ws);
        TranscriptAutoSynchronizer sync = new TranscriptAutoSynchronizer(store, "s2");

        sync.recordUserInput("请总结今天的成果");

        List<Map<String, Object>> records = store.readAll("s2");
        assertThat(records).hasSize(1);
        assertThat(records.get(0)).containsEntry("role", "user");
        assertThat(records.get(0).get("content").toString()).contains("总结");
    }

    @Test
    void shouldIgnoreEmptyAndUnrelatedEvents() {
        LocalWorkspace ws = new LocalWorkspace(tempDir.resolve("ws")).init();
        SessionTranscriptStore store = new SessionTranscriptStore(ws);
        TranscriptAutoSynchronizer sync = new TranscriptAutoSynchronizer(store, "s3");

        sync.recordUserInput("   ");
        sync.onEvent(AgentEvent.of(AgentEventType.MODEL_CALL_START, null));
        sync.onEvent(AgentEvent.of(AgentEventType.TEXT_BLOCK_DELTA, "未接回合也允许聚合"));
        sync.onEvent(AgentEvent.of(AgentEventType.COMPLETED, null));

        List<Map<String, Object>> records = store.readAll("s3");
        assertThat(records).hasSize(1);
        assertThat(records.get(0)).containsEntry("content", "未接回合也允许聚合");
    }
}