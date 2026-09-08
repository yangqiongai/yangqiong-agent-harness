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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 会话JSONL落盘存储测试
 * @author yangqiong
 */
class SessionTranscriptStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void appendThenReadAllKeepsOrderAndContent() {
        LocalWorkspace workspace = new LocalWorkspace(tempDir.resolve("workspace")).init();
        SessionTranscriptStore store = new SessionTranscriptStore(workspace);

        store.append("s1", record("user", "你好"));
        store.append("s1", record("assistant", "你好，有什么可以帮你？"));
        store.append("s1", record("user", "继续"));

        List<Map<String, Object>> records = store.readAll("s1");
        assertThat(records).hasSize(3);
        assertThat(records.get(0)).containsEntry("role", "user").containsEntry("content", "你好");
        assertThat(records.get(1)).containsEntry("role", "assistant").containsEntry("content", "你好，有什么可以帮你？");
        assertThat(records.get(2)).containsEntry("role", "user").containsEntry("content", "继续");
    }

    @Test
    void appendSupportsNestedStructureRoundTrip() {
        LocalWorkspace workspace = new LocalWorkspace(tempDir.resolve("workspace")).init();
        SessionTranscriptStore store = new SessionTranscriptStore(workspace);

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("text", "多行\n内容");
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("role", "user");
        record.put("count", 42);
        record.put("meta", meta);
        store.append("s2", record);

        List<Map<String, Object>> records = store.readAll("s2");
        assertThat(records).hasSize(1);
        assertThat(records.get(0)).containsEntry("role", "user").containsEntry("count", 42);
        Object actualMeta = records.get(0).get("meta");
        assertThat(actualMeta).isInstanceOf(Map.class);
        assertThat(((Map<?, ?>) actualMeta).get("text")).isEqualTo("多行\n内容");
    }

    @Test
    void appendWritesSingleLineJsonPerRecord() throws Exception {
        LocalWorkspace workspace = new LocalWorkspace(tempDir.resolve("workspace")).init();
        SessionTranscriptStore store = new SessionTranscriptStore(workspace);

        store.append("s3", record("user", "包含\n换行的内容"));
        store.append("s3", record("assistant", "回复"));

        List<String> lines = Files.readAllLines(
                workspace.sessionsDir().resolve("s3.jsonl"), StandardCharsets.UTF_8);
        assertThat(lines).hasSize(2);
    }

    @Test
    void readAllReturnsEmptyWhenSessionFileAbsent() {
        LocalWorkspace workspace = new LocalWorkspace(tempDir.resolve("workspace")).init();
        SessionTranscriptStore store = new SessionTranscriptStore(workspace);

        assertThat(store.readAll("absent-session")).isEmpty();
    }

    @Test
    void appendCreatesSessionFileUnderSessionsDir() {
        LocalWorkspace workspace = new LocalWorkspace(tempDir.resolve("workspace")).init();
        SessionTranscriptStore store = new SessionTranscriptStore(workspace);

        store.append("s4", record("user", "hello"));

        assertThat(workspace.sessionsDir().resolve("s4.jsonl")).exists();
    }

    private Map<String, Object> record(String role, String content) {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("role", role);
        record.put("content", content);
        record.put("timestamp", System.currentTimeMillis());
        return record;
    }
}
