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
package com.yangqiongai.agent.harness.store.vector;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import com.yangqiongai.agent.harness.durable.ScopeKeys;
import com.yangqiongai.agent.harness.model.embedding.EmbeddingModel;
import com.yangqiongai.agent.harness.model.embedding.HashingEmbeddingModel;
import com.yangqiongai.agent.harness.rag.RetrievedChunk;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 落盘向量存储测试
 * @author yangqiong
 */
class PersistentVectorStoreTest {

    /**
     * 测试数据目录
     */
    @TempDir
    Path dataDir;

    /**
     * 嵌入模型
     */
    private EmbeddingModel embeddingModel;

    @BeforeEach
    void setUp() {
        embeddingModel = new HashingEmbeddingModel();
    }

    @Test
    void storeAndSearchWhenStoredThenRetrieved() {
        try (PersistentVectorStore store = PersistentVectorStore.open(dataDir, embeddingModel)) {
            store.memory().store("scope-a", "user-1", "s1", "今天修复了审批流程的并发锁问题", Map.of());
            store.memory().store("scope-a", "user-1", "s2", "明天评审向量存储接入方案", Map.of());

            List<String> memories = store.memory().search("scope-a", "user-1", "向量存储方案", 10);

            assertThat(memories).contains("明天评审向量存储接入方案");
        }
    }

    @Test
    void searchIsolatesByScopeUserBucket() {
        try (PersistentVectorStore store = PersistentVectorStore.open(dataDir, embeddingModel)) {
            store.memory().store("scope-a", "user-1", "s1", "只有A租户用户1可见的记忆", Map.of());

            List<String> other = store.memory().search("scope-b", "user-2", "记忆", 10);
            List<String> own = store.memory().search("scope-a", "user-1", "记忆", 10);

            assertThat(other).isEmpty();
            assertThat(own).contains("只有A租户用户1可见的记忆");
        }
    }

    @Test
    void deleteByMemoryIdRemovesEntry() {
        try (PersistentVectorStore store = PersistentVectorStore.open(dataDir, embeddingModel)) {
            store.memory().store("scope-a", "user-1", "s1", "待删除的记忆", Map.of());
            String id = store.vectorMemory().listMemoryIds("scope-a", "user-1").get(0);

            store.memory().delete(id);

            assertThat(store.vectorMemory().listMemoryIds("scope-a", "user-1")).isEmpty();
            assertThat(store.size()).isZero();
        }
    }

    @Test
    void deleteWithWrongScopeThrowsSecurityException() {
        try (PersistentVectorStore store = PersistentVectorStore.open(dataDir, embeddingModel)) {
            store.memory().store("scope-a", "user-1", "s1", "受租户保护的记忆", Map.of());
            String id = store.vectorMemory().listMemoryIds("scope-a", "user-1").get(0);

            assertThatThrownBy(() -> store.memory().delete("scope-b", id))
                    .isInstanceOf(SecurityException.class);
        }
    }

    @Test
    void memoryPersistsAcrossReopen() {
        try (PersistentVectorStore store = PersistentVectorStore.open(dataDir, embeddingModel)) {
            store.memory().store("scope-a", "user-1", "s1", "重启后依然存在的记忆", Map.of());
        }

        try (PersistentVectorStore reopened = PersistentVectorStore.open(dataDir, embeddingModel)) {
            List<String> memories = reopened.memory().search("scope-a", "user-1", "记忆", 10);
            assertThat(memories).contains("重启后依然存在的记忆");
        }
    }

    @Test
    void retrieverRanksSemanticMatchFirst() {
        try (PersistentVectorStore store = PersistentVectorStore.open(dataDir, embeddingModel)) {
            store.vectorRetriever().addDocument("doc-cat", "the quick brown fox jumps over lazy dog", Map.of());
            store.vectorRetriever().addDocument("doc-fruit", "apples oranges bananas fruit market", Map.of());

            List<RetrievedChunk> chunks = store.retriever().retrieve("quick brown fox", 2, null);

            assertThat(chunks).isNotEmpty();
            assertThat(chunks.get(0).source()).isEqualTo("doc-cat");
            assertThat(chunks.get(0).score()).isGreaterThan(chunks.get(1).score());
        }
    }

    @Test
    void retrieverFiltersByScalarMetadata() {
        try (PersistentVectorStore store = PersistentVectorStore.open(dataDir, embeddingModel)) {
            store.vectorRetriever().addContent("如何接入向量检索", Map.of("category", "guide", "lang", "java"));
            store.vectorRetriever().addContent("记忆隔离说明", Map.of("category", "reference"));

            List<RetrievedChunk> chunks =
                    store.retriever().retrieve("接入向量检索", 10, Map.of("category", "guide"));

            assertThat(chunks).hasSize(1);
            assertThat(chunks.get(0).metadata().get("lang")).isEqualTo("java");
        }
    }

    @Test
    void storeAttachesSessionIdToRecordMetadata() {
        try (LuceneVectorIndex index = new LuceneVectorIndex(dataDir.resolve("session-meta"));
             PersistentVectorStore store = PersistentVectorStore.open(index, embeddingModel)) {
            store.memory().store("scope-a", "user-1", "s1", "带会话元数据的记忆", Map.of("kind", "note"));

            List<VectorSearchHit> hits = index.search(
                    ScopeKeys.memoryBucket("scope-a", "user-1"),
                    embeddingModel.embed("记忆"), 5, null);

            assertThat(hits).hasSize(1);
            assertThat(hits.get(0).metadata())
                    .containsEntry("sessionId", "s1")
                    .containsEntry("kind", "note");
        }
    }

    @Test
    void openWithExternalIndexBehavesSameAsPathFactory() {
        try (LuceneVectorIndex index = new LuceneVectorIndex(dataDir.resolve("external"));
             PersistentVectorStore store = PersistentVectorStore.open(index, embeddingModel)) {
            store.memory().store("scope-a", "user-1", "s1", "外部索引承载的记忆", Map.of());
            store.vectorRetriever().addContent("外部索引承载的文档", Map.of());

            assertThat(store.memory().search("scope-a", "user-1", "记忆", 10))
                    .contains("外部索引承载的记忆");
            assertThat(store.retriever().retrieve("文档", 5, null)).isNotEmpty();
            assertThat(store.vectorMemory().listMemoryIds("scope-a", "user-1")).hasSize(1);
            assertThat(store.size()).isEqualTo(2);
        }
    }
}