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
package com.yangqiong.agent.harness.store.vector;

import java.nio.file.Path;
import java.util.Objects;

import com.yangqiong.agent.harness.core.memory.AgentLongTermMemory;
import com.yangqiong.agent.harness.model.embedding.EmbeddingModel;
import com.yangqiong.agent.harness.rag.Retriever;

/**
 * 落盘向量存储
 * <p>
 * 通用持久的向量后端，不绑定本地模式：基于同一Lucene向量索引同时提供
 * 长期向量记忆（PersistentVectorMemory）与语义向量检索（VectorRetriever），
 * 嵌入模型由调用方注入（可复用以OllamaEmbeddingModel为代表的任意EmbeddingModel实现）。
 * </p>
 * @author yangqiong
 */
public final class PersistentVectorStore implements AutoCloseable {

    /**
     * 底层Lucene向量索引
     */
    private final LuceneVectorIndex index;

    /**
     * 持久化向量记忆
     */
    private final PersistentVectorMemory memory;

    /**
     * 向量检索器
     */
    private final VectorRetriever retriever;

    private PersistentVectorStore(LuceneVectorIndex index, EmbeddingModel embeddingModel) {
        Objects.requireNonNull(index, "索引不能为空");
        Objects.requireNonNull(embeddingModel, "嵌入模型不能为空");
        this.index = index;
        this.memory = new PersistentVectorMemory(index, embeddingModel);
        this.retriever = new VectorRetriever(index, embeddingModel);
    }

    /**
     * 打开落盘向量存储，数据目录不存在时自动创建
     * @param dataDir
     * @param embeddingModel
     * @return
     */
    public static PersistentVectorStore open(Path dataDir, EmbeddingModel embeddingModel) {
        Objects.requireNonNull(dataDir, "数据目录不能为空");
        return new PersistentVectorStore(new LuceneVectorIndex(dataDir), embeddingModel);
    }

    /**
     * 获取持久化向量记忆
     * @return
     */
    public AgentLongTermMemory memory() {
        return memory;
    }

    /**
     * 获取持久化向量记忆的专有视图（含逐桶ID列举等辅助能力）
     * @return
     */
    public PersistentVectorMemory vectorMemory() {
        return memory;
    }

    /**
     * 获取向量检索器
     * @return
     */
    public Retriever retriever() {
        return retriever;
    }

    /**
     * 获取向量检索器的专有视图（含文档块写入/删除能力）
     * @return
     */
    public VectorRetriever vectorRetriever() {
        return retriever;
    }

    /**
     * 获取当前索引记录总数
     * @return
     */
    public int size() {
        return index.size();
    }

    /**
     * 关闭并释放底层索引资源
     */
    @Override
    public void close() {
        index.close();
    }
}