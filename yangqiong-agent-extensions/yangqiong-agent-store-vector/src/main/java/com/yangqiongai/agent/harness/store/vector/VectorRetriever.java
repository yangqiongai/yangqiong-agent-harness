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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.yangqiongai.agent.harness.model.embedding.EmbeddingModel;
import com.yangqiongai.agent.harness.rag.RetrievedChunk;
import com.yangqiongai.agent.harness.rag.Retriever;

/**
 * 向量检索器
 * <p>
 * 以嵌入模型将文档块转为向量持久化，查询时向量化后在本模块统一索引上做KNN语义召回，
 * 将RAG从"关键词+全文"升级到语义级；支持按标量元数据过滤，来源标识经metadata.source回传。
 * 依赖VectorIndex而非具体实现，可无缝切换Lucene、pgvector、Milvus、Qdrant等后端。
 * </p>
 * @author yangqiong
 */
public class VectorRetriever implements Retriever {

    /**
     * 来源元数据键
     */
    static final String META_SOURCE = "source";

    /**
     * 检索文档块默认集合
     */
    static final String COLLECTION_SHARED = "shared";

    /**
     * 底层向量索引
     */
    private final VectorIndex index;

    /**
     * 嵌入模型
     */
    private final EmbeddingModel embeddingModel;

    /**
     * 以索引与嵌入模型构建向量检索器（文档块写入复合桶 shared）
     * @param index
     * @param embeddingModel
     */
    VectorRetriever(VectorIndex index, EmbeddingModel embeddingModel) {
        this.index = index;
        this.embeddingModel = embeddingModel;
    }

    /**
     * 写入一个向量检索文档块，来源取metadata.source，缺省用随机ID
     * @param content
     * @param metadata
     * @return 文档ID
     */
    public String addContent(String content, Map<String, Object> metadata) {
        Object sourceValue = metadata == null ? null : metadata.get(META_SOURCE);
        String source = sourceValue == null ? null : String.valueOf(sourceValue);
        String id = (source != null && !source.isBlank()) ? source : UUID.randomUUID().toString();
        addDocument(id, content, metadata);
        return id;
    }

    /**
     * 按ID写入或覆盖一个向量检索文档块
     * @param id
     * @param content
     * @param metadata
     */
    public void addDocument(String id, String content, Map<String, Object> metadata) {
        Map<String, Object> enriched = metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata);
        enriched.put(META_SOURCE, id);
        float[] vector = embeddingModel.embed(content);
        index.upsert(COLLECTION_SHARED, new VectorRecord(id, vector, content, enriched));
    }

    /**
     * 按文档ID删除向量检索文档块
     * @param id
     */
    public void remove(String id) {
        index.delete(COLLECTION_SHARED, List.of(id));
    }

    /**
     * 获取当前索引中的文档块总数
     * @return
     */
    public int size() {
        return index.size();
    }

    @Override
    public List<RetrievedChunk> retrieve(String query, int topK, Map<String, Object> filters) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        float[] queryVector = embeddingModel.embed(query);
        int limit = topK > 0 ? topK : 10;
        List<VectorSearchHit> hits =
                index.search(null, queryVector, limit, MetadataFilter.equalities(filters));
        List<RetrievedChunk> chunks = new ArrayList<>();
        for (VectorSearchHit hit : hits) {
            Map<String, Object> metadata = new LinkedHashMap<>(hit.metadata());
            String source = hit.id();
            Object sourceMeta = metadata.get(META_SOURCE);
            if (sourceMeta != null) {
                source = String.valueOf(sourceMeta);
            }
            chunks.add(RetrievedChunk.of(hit.content(), hit.score(), source, metadata));
        }
        return chunks;
    }
}
