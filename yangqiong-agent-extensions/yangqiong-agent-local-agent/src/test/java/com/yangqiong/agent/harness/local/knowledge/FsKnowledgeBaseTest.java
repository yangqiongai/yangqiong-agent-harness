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
package com.yangqiong.agent.harness.local.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.yangqiong.agent.harness.rag.RetrievedChunk;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 文件系统知识库测试
 * @author yangqiong
 */
class FsKnowledgeBaseTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldReturnEmptyWhenDirMissingOrEmpty() {
        FsKnowledgeBase missing = FsKnowledgeBase.open(tempDir.resolve("nope"));
        assertThat(missing.isEmpty()).isTrue();
        assertThat(missing.retriever().retrieve("关键字", 3, null)).isEmpty();

        FsKnowledgeBase empty = FsKnowledgeBase.open(tempDir.resolve("empty"));
        assertThat(empty.isEmpty()).isTrue();
    }

    @Test
    void shouldIndexDocsAndRetrieve() throws Exception {
        Path kbDir = tempDir.resolve("knowledge");
        Files.createDirectories(kbDir.resolve("sub"));
        Files.writeString(kbDir.resolve("readme.md"), "本地智能体使用指南：配置Ollama端点");
        Files.writeString(kbDir.resolve("sub/faq.txt"), "常见问题：如何在本地启动服务");
        Files.writeString(kbDir.resolve("ignore.bin"), "非文档内容");

        FsKnowledgeBase kb = FsKnowledgeBase.open(kbDir);
        assertThat(kb.isEmpty()).isFalse();

        List<RetrievedChunk> hits = kb.retriever().retrieve("Ollama", 3, null);
        assertThat(hits).isNotEmpty();
        assertThat(hits.get(0).content()).contains("Ollama");
    }

    @Test
    void shouldSaveDocThenReopenAndRetrieve() {
        Path kbDir = tempDir.resolve("knowledge");
        FsKnowledgeBase kb = FsKnowledgeBase.saveDoc(kbDir, "manual/install.md", "安装本地模型服务指南\n");
        assertThat(kb.isEmpty()).isFalse();

        List<RetrievedChunk> hits = kb.retriever().retrieve("安装", 3, null);
        assertThat(hits).isNotEmpty();
        assertThat(hits.get(0).source()).isEqualTo("hybrid");
    }

    @Test
    void shouldIndexBinaryDocsAndHybridRetrieve() throws Exception {
        Path kbDir = tempDir.resolve("knowledge");
        Files.createDirectories(kbDir);
        Files.write(kbDir.resolve("budget.docx"), DocFixtures.minimalDocx("重点：项目预算方案"));
        Files.write(kbDir.resolve("report.pdf"), DocFixtures.minimalPdf());

        FsKnowledgeBase kb = FsKnowledgeBase.open(kbDir);
        assertThat(kb.isEmpty()).isFalse();

        List<RetrievedChunk> hits = kb.retriever().retrieve("预算", 3, null);
        assertThat(hits).isNotEmpty();
        assertThat(hits.get(0).source()).isEqualTo("hybrid");
        assertThat(hits.get(0).content()).contains("预算");
        assertThat(hits.get(0).metadata().get("id")).isEqualTo("budget.docx");

        List<RetrievedChunk> pdfHits = kb.retriever().retrieve("budget", 3, null);
        assertThat(pdfHits).isNotEmpty();
        assertThat(pdfHits.get(0).metadata().get("id")).isEqualTo("report.pdf");
    }

    @Test
    void shouldRejectPathOutsideDir() {
        Path kbDir = tempDir.resolve("knowledge");
        assertThatThrownBy(() -> FsKnowledgeBase.saveDoc(kbDir, "../../../evil.md", "x"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldReindexHotAndSaveDocInPlace() throws Exception {
        Path kbDir = tempDir.resolve("knowledge");
        Files.createDirectories(kbDir);
        Files.writeString(kbDir.resolve("a.md"), "初始文档内容");
        FsKnowledgeBase kb = FsKnowledgeBase.open(kbDir);
        assertThat(kb.retriever().retrieve("初始", 3, null)).isNotEmpty();

        Files.writeString(kbDir.resolve("b.md"), "新增文档内容");
        kb.reindex();
        assertThat(kb.retriever().retrieve("新增", 3, null)).isNotEmpty();

        kb.saveDoc("c.md", "实例保存内容");
        assertThat(kb.retriever().retrieve("实例保存", 3, null)).isNotEmpty();
    }
}