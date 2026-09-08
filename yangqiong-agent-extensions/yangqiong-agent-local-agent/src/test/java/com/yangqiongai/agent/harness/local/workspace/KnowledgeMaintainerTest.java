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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 知识库与记忆自维护测试
 * @author yangqiong
 */
class KnowledgeMaintainerTest {

    @TempDir
    Path tempDir;

    @Test
    void reindexRefreshesKnowledgeCount() throws Exception {
        Path knowledgeDir = tempDir.resolve("knowledge");
        Files.createDirectories(knowledgeDir);
        Files.writeString(knowledgeDir.resolve("a.md"), "第一条知识", StandardCharsets.UTF_8);

        KnowledgeMaintainer maintainer = new KnowledgeMaintainer(knowledgeDir, tempDir.resolve("memory"));
        assertThat(maintainer.reindex()).isEqualTo(1);

        Files.writeString(knowledgeDir.resolve("b.txt"), "第二条知识", StandardCharsets.UTF_8);
        assertThat(maintainer.reindex()).isEqualTo(2);
    }

    @Test
    void pruneArchivesExpiredAndDeduplicatesMemories() throws Exception {
        Path memoryDir = tempDir.resolve("memory");
        Files.createDirectories(memoryDir);
        long now = System.currentTimeMillis();
        writeMemory(memoryDir, "old.jsonl", "相同的记忆内容", now - 100_000L);
        writeMemory(memoryDir, "dup-old.jsonl", "相同的记忆内容", now - 2_000L);
        writeMemory(memoryDir, "dup-new.jsonl", "相同的记忆内容", now - 1_000L);
        writeMemory(memoryDir, "fresh.jsonl", "新内容", now);

        KnowledgeMaintainer maintainer = new KnowledgeMaintainer(tempDir.resolve("knowledge"), memoryDir);
        KnowledgeMaintainer.MemoryPrune prune = maintainer.pruneMemories(50_000L);

        assertThat(prune.archivedCount()).isPositive();
        assertThat(prune.dedupedCount()).isPositive();
        // 三条同内容记忆：过期一条归档、另两条去重保一新归档，最终同内容只留一条，再加上fresh共两条
        Path archive = memoryDir.resolve(".archive");
        assertThat(Files.list(archive).count()).isGreaterThanOrEqualTo(2);
        assertThat(Files.list(memoryDir)
                .filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().endsWith(".jsonl"))
                .count()).isEqualTo(2);
    }

    private void writeMemory(Path memoryDir, String name, String content, long ts) throws Exception {
        String json = "{\"id\":\"%s\",\"content\":\"%s\",\"ts\":%d}\n"
                .formatted(name.replace(".jsonl", ""), content, ts);
        Files.writeString(memoryDir.resolve(name), json, StandardCharsets.UTF_8);
    }
}