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

/**
 * 向量索引统一接口
 * <p>
 * 屏蔽Lucene本地索引与Milvus/Qdrant/pgvector等独立向量服务的差异：集合维度由
 * 各实现自行承载（Lucene为scope复合桶字段，独立服务为collection），上层
 * PersistentVectorMemory与VectorRetriever仅依赖本接口，切换后端零改动。
 * </p>
 * @author yangqiong
 */
public interface VectorIndex extends AutoCloseable {

    /**
     * 写入或覆盖一条向量记录（以id幂等）并持久化
     * @param collection 集合名，不能为空
     * @param record 向量记录
     */
    void upsert(String collection, VectorRecord record);

    /**
     * 批量写入向量记录（id幂等），实现应尽量合并为一次提交
     * @param collection 集合名，不能为空
     * @param records 向量记录列表
     */
    void upsertBatch(String collection, List<VectorRecord> records);

    /**
     * 相似度检索，返回按相似度降序的命中
     * @param collection 集合名，为null时跨全部集合检索
     * @param vector 查询向量
     * @param topK 返回条数，非正数时按10处理
     * @param filter 元数据过滤，为null或空时不过滤
     * @return
     */
    List<VectorSearchHit> search(String collection, float[] vector, int topK, MetadataFilter filter);

    /**
     * 按id删除集合内的向量记录，集合外的同名id不受影响
     * @param collection 集合名，不能为空
     * @param ids 记录id列表
     */
    void delete(String collection, List<String> ids);

    /**
     * 删除集合及其全部记录，集合不存在时不报错
     * @param collection 集合名，不能为空
     */
    void dropCollection(String collection);

    /**
     * 确保集合存在（相似度度量与维度在创建时固定）；按需产生集合的实现（如Lucene）可空实现，
     * 不支持指定度量或维度的实现应抛UnsupportedOperationException
     * @param collection 集合名，不能为空
     * @param dimension 向量维度
     * @param metric 相似度度量
     */
    default void ensureCollection(String collection, int dimension, VectorMetric metric) {
    }

    /**
     * 获取当前索引内跨集合的记录总数
     * @return
     */
    int size();

    /**
     * 定位记录所属集合，可选能力：不支持定位的实现抛UnsupportedOperationException
     * @param id 记录id
     * @return 所属集合名，记录不存在时返回null
     */
    default String collectionOf(String id) {
        throw new UnsupportedOperationException("当前向量索引不支持按id定位集合: " + getClass().getName());
    }

    /**
     * 列出集合内的全部记录id，可选能力：不支持列举的实现抛UnsupportedOperationException
     * @param collection 集合名，不能为空
     * @return
     */
    default List<String> listIds(String collection) {
        throw new UnsupportedOperationException("当前向量索引不支持列举集合内id: " + getClass().getName());
    }

    /**
     * 关闭并释放底层索引资源
     */
    @Override
    void close();
}
