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

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.yangqiongai.agent.harness.core.memory.AgentLongTermMemory;
import com.yangqiongai.agent.harness.durable.ScopeKeys;
import com.yangqiongai.agent.harness.model.embedding.EmbeddingModel;

/**
 * 持久化向量记忆
 * <p>
 * 基于向量索引SPI持久化的语义长期记忆，存储时以嵌入模型将内容转为向量持久化；
 * 检索按 (scopeId, userId) 复合桶隔离后做KNN相似度召回，删除带所有权校验，
 * 进程重启后记忆不丢失。依赖VectorIndex而非具体实现，可无缝切换Lucene、
 * pgvector、Milvus、Qdrant等后端。
 * </p>
 * @author yangqiong
 */
public class PersistentVectorMemory implements AgentLongTermMemory {

    /**
     * 底层向量索引
     */
    private final VectorIndex index;

    /**
     * 嵌入模型
     */
    private final EmbeddingModel embeddingModel;

    /**
     * 以索引与嵌入模型构建持久化向量记忆
     * @param index
     * @param embeddingModel
     */
    PersistentVectorMemory(VectorIndex index, EmbeddingModel embeddingModel) {
        this.index = index;
        this.embeddingModel = embeddingModel;
    }

    @Override
    public void store(String userId, String sessionId, String content, Map<String, Object> metadata) {
        store(null, userId, sessionId, content, metadata);
    }

    @Override
    public void store(String scopeId, String userId, String sessionId,
                      String content, Map<String, Object> metadata) {
        if (content == null || content.isBlank()) {
            return;
        }
        String id = UUID.randomUUID().toString();
        Map<String, Object> enriched = metadata;
        if (sessionId != null) {
            enriched = metadata == null ? new java.util.LinkedHashMap<>()
                    : new java.util.LinkedHashMap<>(metadata);
            enriched.put("sessionId", sessionId);
        }
        float[] vector = embeddingModel.embed(content);
        index.upsert(ScopeKeys.memoryBucket(scopeId, userId),
                new VectorRecord(id, vector, content, enriched));
    }

    @Override
    public List<String> search(String userId, String query, int limit) {
        return search(null, userId, query, limit);
    }

    @Override
    public List<String> search(String scopeId, String userId, String query, int limit) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        float[] queryVector = embeddingModel.embed(query);
        int topK = limit > 0 ? limit : 10;
        List<VectorSearchHit> hits =
                index.search(ScopeKeys.memoryBucket(scopeId, userId), queryVector, topK, null);
        return hits.stream()
                .filter(h -> h.content() != null)
                .map(VectorSearchHit::content)
                .toList();
    }

    @Override
    public void delete(String memoryId) {
        if (memoryId == null) {
            return;
        }
        String collection = index.collectionOf(memoryId);
        if (collection == null) {
            return;
        }
        index.delete(collection, List.of(memoryId));
    }

    @Override
    public void delete(String scopeId, String memoryId) {
        if (memoryId == null) {
            return;
        }
        String collection = index.collectionOf(memoryId);
        if (collection == null) {
            return;
        }
        // 所有权校验：记忆必须属于当前租户作用域才允许删除
        if (!collection.equals(ScopeKeys.memoryBucket(scopeId, extractUserId(collection)))) {
            throw new SecurityException("记忆删除所有权校验失败: memoryId=" + memoryId);
        }
        index.delete(collection, List.of(memoryId));
    }

    /**
     * 列出指定作用域与用户下的全部记忆ID
     * @param scopeId
     * @param userId
     * @return
     */
    public List<String> listMemoryIds(String scopeId, String userId) {
        return index.listIds(ScopeKeys.memoryBucket(scopeId, userId));
    }

    /**
     * 获取当前记忆条目总数
     * @return
     */
    public int size() {
        return index.size();
    }

    /**
     * 从复合桶键中提取userId段
     * @param bucket
     * @return
     */
    private String extractUserId(String bucket) {
        int idx = bucket.indexOf("::");
        return idx >= 0 ? bucket.substring(idx + 2) : bucket;
    }
}
