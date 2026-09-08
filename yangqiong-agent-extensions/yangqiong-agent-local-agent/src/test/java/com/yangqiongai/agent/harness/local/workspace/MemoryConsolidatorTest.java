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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.yangqiongai.agent.harness.local.memory.FileLongTermMemory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 复盘记忆沉淀测试
 * @author yangqiong
 */
class MemoryConsolidatorTest {

    @TempDir
    Path tempDir;

    private MemoryConsolidator newConsolidator(LocalWorkspace workspace) {
        SessionTranscriptStore transcript = new SessionTranscriptStore(workspace);
        FileLongTermMemory memory = new FileLongTermMemory(workspace.memoryDir());
        return new MemoryConsolidator(transcript, memory,
                workspace.memoryDir().resolve(".consolidated.jsonl"), "scope-1", "user-1");
    }

    @Test
    void consolidateSessionStoresMemoryAndIsIdempotent() {
        LocalWorkspace workspace = new LocalWorkspace(tempDir.resolve("workspace")).init();
        SessionTranscriptStore transcript = new SessionTranscriptStore(workspace);
        transcript.append("s1", record("user", "帮我配置定时任务"));
        transcript.append("s1", record("assistant", "已为你创建每小时的定时任务"));

        MemoryConsolidator consolidator = newConsolidator(workspace);

        assertThat(consolidator.consolidateSession("s1")).isTrue();

        FileLongTermMemory memory = new FileLongTermMemory(workspace.memoryDir());
        assertThat(memory.search("scope-1", "user-1", "定时任务", 5)).isNotEmpty();
        assertThat(memory.search("scope-1", "user-1", "定时任务", 5).get(0)).contains("定时任务");
    }

    @Test
    void consolidateSessionSkippedForNonTextRecordsOnly() {
        LocalWorkspace workspace = new LocalWorkspace(tempDir.resolve("workspace")).init();
        SessionTranscriptStore transcript = new SessionTranscriptStore(workspace);
        Map<String, Object> toolRecord = new LinkedHashMap<>();
        toolRecord.put("role", "assistant");
        toolRecord.put("type", "tool");
        toolRecord.put("content", "内部工具举动");
        transcript.append("s2", toolRecord);

        MemoryConsolidator consolidator = newConsolidator(workspace);

        assertThat(consolidator.consolidateSession("s2")).isFalse();
    }

    @Test
    void consolidateAllTracksProcessedMarker() throws Exception {
        LocalWorkspace workspace = new LocalWorkspace(tempDir.resolve("workspace")).init();
        SessionTranscriptStore transcript = new SessionTranscriptStore(workspace);
        transcript.append("a", record("user", "汇报今天的进展"));
        transcript.append("a", record("assistant", "今日完成知识库索引重建"));

        MemoryConsolidator consolidator = newConsolidator(workspace);

        assertThat(consolidator.consolidateAll()).isEqualTo(1);
        assertThat(consolidator.consolidateAll()).isZero();

        List<String> lines = Files.readAllLines(
                workspace.memoryDir().resolve(".consolidated.jsonl"), java.nio.charset.StandardCharsets.UTF_8);
        assertThat(lines).contains("a");
    }

    private Map<String, Object> record(String role, String content) {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("role", role);
        record.put("type", "text");
        record.put("content", content);
        record.put("ts", System.currentTimeMillis());
        return record;
    }
}