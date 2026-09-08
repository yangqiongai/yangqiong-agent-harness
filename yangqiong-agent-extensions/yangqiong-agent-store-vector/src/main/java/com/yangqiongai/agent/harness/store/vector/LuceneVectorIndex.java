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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.yangqiongai.agent.harness.durable.serialization.HarnessObjectMapper;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.KnnFloatVectorQuery;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

/**
 * Lucene向量索引
 * <p>
 * 以落盘 Lucene 索引承载持久化向量存储：每条记录 = id + scope复合桶 + 内容 +
 * 元数据JSON + 稠密向量（KnnFloatVectorField 余弦相似度），写入即提交落盘。
 * 检索采用 KNN 向量近似检索后按复合桶与标量元数据过滤，兼顾语义召回与隔离性。
 * 实现 {@link VectorIndex} SPI，集合维度即scope复合桶字段；相似度固定为余弦，
 * 集合随写入按需产生。
 * </p>
 * @author yangqiong
 */
final class LuceneVectorIndex implements VectorIndex {

    /**
     * 文档ID字段
     */
    static final String FIELD_ID = "id";

    /**
     * scope复合桶字段
     */
    static final String FIELD_BUCKET = "bucket";

    /**
     * 内容字段
     */
    static final String FIELD_CONTENT = "content";

    /**
     * 元数据JSON字段
     */
    static final String FIELD_METADATA = "metadata";

    /**
     * 向量字段
     */
    static final String FIELD_VECTOR = "vector";

    /**
     * KNN候选放大系数，保证单桶小数据集在被过滤后仍有足够召回
     */
    private static final int CANDIDATE_MAGNIFIER = 20;

    /**
     * KNN候选基数下限
     */
    private static final int CANDIDATE_FLOOR = 64;

    /**
     * 底层Lucene写索引
     */
    private final IndexWriter writer;

    /**
     * 以数据目录构建向量索引，目录不存在时自动创建
     * @param dataDir 落盘数据目录
     */
    LuceneVectorIndex(Path dataDir) {
        try {
            IndexWriterConfig config = new IndexWriterConfig(new StandardAnalyzer())
                    .setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
            Directory directory = FSDirectory.open(dataDir);
            this.writer = new IndexWriter(directory, config);
        } catch (IOException e) {
            throw new UncheckedIOException("向量索引打开失败: " + dataDir, e);
        }
    }

    @Override
    public synchronized void upsert(String collection, VectorRecord record) {
        requireCollection(collection);
        try {
            writer.updateDocument(new Term(FIELD_ID, record.id()), toDocument(collection, record));
            writer.commit();
        } catch (IOException e) {
            throw new UncheckedIOException("向量记录写入失败: " + record.id(), e);
        }
    }

    @Override
    public synchronized void upsertBatch(String collection, List<VectorRecord> records) {
        requireCollection(collection);
        Objects.requireNonNull(records, "向量记录列表不能为空");
        try {
            for (VectorRecord record : records) {
                writer.updateDocument(new Term(FIELD_ID, record.id()), toDocument(collection, record));
            }
            writer.commit();
        } catch (IOException e) {
            throw new UncheckedIOException("向量记录批量写入失败", e);
        }
    }

    @Override
    public synchronized List<VectorSearchHit> search(String collection, float[] queryVector,
                                                     int topK, MetadataFilter filter) {
        Objects.requireNonNull(queryVector, "查询向量不能为空");
        int effectiveTopK = topK > 0 ? topK : 10;
        int numCandidates = Math.max(effectiveTopK * CANDIDATE_MAGNIFIER, CANDIDATE_FLOOR);
        try (IndexReader reader = DirectoryReader.open(writer)) {
            IndexSearcher searcher = new IndexSearcher(reader);
            // 集合条件下推为KNN过滤，避免多集合数据倾斜时候选被其他集合挤占导致召回退化
            KnnFloatVectorQuery query = collection == null
                    ? new KnnFloatVectorQuery(FIELD_VECTOR, queryVector, numCandidates)
                    : new KnnFloatVectorQuery(FIELD_VECTOR, queryVector, numCandidates,
                            new TermQuery(new Term(FIELD_BUCKET, collection)));
            TopDocs topDocs = searcher.search(query, numCandidates);
            List<VectorSearchHit> results = new ArrayList<>();
            for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
                Document doc = searcher.storedFields().document(scoreDoc.doc);
                String hitBucket = doc.get(FIELD_BUCKET);
                if (collection != null && !collection.equals(hitBucket)) {
                    continue;
                }
                Map<String, Object> metadata = parseMetadata(doc.get(FIELD_METADATA));
                if (filter != null && !filter.isEmpty() && !filter.matches(metadata)) {
                    continue;
                }
                results.add(new VectorSearchHit(doc.get(FIELD_ID), scoreDoc.score,
                        doc.get(FIELD_CONTENT), metadata));
                if (results.size() >= effectiveTopK) {
                    break;
                }
            }
            return results;
        } catch (IOException e) {
            throw new UncheckedIOException("向量检索失败", e);
        }
    }

    @Override
    public synchronized void delete(String collection, List<String> ids) {
        requireCollection(collection);
        Objects.requireNonNull(ids, "记录id列表不能为空");
        if (ids.isEmpty()) {
            return;
        }
        try {
            for (String id : ids) {
                writer.deleteDocuments(idInCollectionQuery(collection, id));
            }
            writer.commit();
        } catch (IOException e) {
            throw new UncheckedIOException("向量记录删除失败", e);
        }
    }

    @Override
    public synchronized void dropCollection(String collection) {
        requireCollection(collection);
        try {
            writer.deleteDocuments(new Term(FIELD_BUCKET, collection));
            writer.commit();
        } catch (IOException e) {
            throw new UncheckedIOException("向量集合删除失败: " + collection, e);
        }
    }

    @Override
    public synchronized void ensureCollection(String collection, int dimension, VectorMetric metric) {
        requireCollection(collection);
        Objects.requireNonNull(metric, "相似度度量不能为空");
        if (dimension <= 0) {
            throw new IllegalArgumentException("向量维度必须为正数: " + dimension);
        }
        if (metric != VectorMetric.COSINE) {
            throw new UnsupportedOperationException("Lucene向量索引仅支持COSINE相似度: " + metric);
        }
    }

    @Override
    public synchronized int size() {
        return writer.getDocStats().numDocs;
    }

    @Override
    public synchronized String collectionOf(String id) {
        List<Hit> hits = searchByTerm(FIELD_ID, id);
        return hits.isEmpty() ? null : hits.get(0).bucket;
    }

    @Override
    public synchronized List<String> listIds(String collection) {
        requireCollection(collection);
        return searchByTerm(FIELD_BUCKET, collection).stream().map(h -> h.id).toList();
    }

    /**
     * 关闭底层写索引并释放目录资源
     */
    @Override
    public synchronized void close() {
        try {
            writer.close();
        } catch (IOException e) {
            throw new UncheckedIOException("向量索引关闭失败", e);
        }
    }

    /**
     * 以VectorRecord构建Lucene文档
     * @param collection 集合名
     * @param record 向量记录
     * @return
     */
    private Document toDocument(String collection, VectorRecord record) {
        Document doc = new Document();
        doc.add(new StringField(FIELD_ID, record.id(), Field.Store.YES));
        doc.add(new StringField(FIELD_BUCKET, collection, Field.Store.YES));
        doc.add(new StoredField(FIELD_CONTENT, record.content()));
        if (!record.metadata().isEmpty()) {
            doc.add(new StoredField(FIELD_METADATA, toJson(record.metadata())));
        }
        doc.add(new KnnFloatVectorField(FIELD_VECTOR, record.vector(), VectorSimilarityFunction.COSINE));
        return doc;
    }

    /**
     * 构建集合内按id定位的删除条件
     * @param collection 集合名
     * @param id 记录id
     * @return
     */
    private BooleanQuery idInCollectionQuery(String collection, String id) {
        return new BooleanQuery.Builder()
                .add(new TermQuery(new Term(FIELD_ID, id)), BooleanClause.Occur.FILTER)
                .add(new TermQuery(new Term(FIELD_BUCKET, collection)), BooleanClause.Occur.FILTER)
                .build();
    }

    /**
     * 按索引项精确查找并还原命中记录
     * @param field
     * @param value
     * @return
     */
    private List<Hit> searchByTerm(String field, String value) {
        try (IndexReader reader = DirectoryReader.open(writer)) {
            IndexSearcher searcher = new IndexSearcher(reader);
            TopDocs topDocs = searcher.search(new TermQuery(new Term(field, value)), Integer.MAX_VALUE);
            List<Hit> hits = new ArrayList<>();
            for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
                Document doc = searcher.storedFields().document(scoreDoc.doc);
                hits.add(new Hit(doc.get(FIELD_ID), doc.get(FIELD_BUCKET),
                        doc.get(FIELD_CONTENT), scoreDoc.score,
                        parseMetadata(doc.get(FIELD_METADATA))));
            }
            return hits;
        } catch (IOException e) {
            throw new UncheckedIOException("向量索引项查询失败: " + field + "=" + value, e);
        }
    }

    /**
     * 元数据序列化为JSON字符串
     * @param metadata
     * @return
     */
    private String toJson(Map<String, Object> metadata) {
        try {
            return HarnessObjectMapper.get().writeValueAsString(metadata);
        } catch (IOException e) {
            throw new UncheckedIOException("元数据序列化失败", e);
        }
    }

    /**
     * 解析元数据JSON字符串，为null或空时返回空映射
     * @param json
     * @return
     */
    private Map<String, Object> parseMetadata(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = HarnessObjectMapper.get().readValue(json, Map.class);
            return parsed == null ? Map.of() : parsed;
        } catch (IOException e) {
            throw new UncheckedIOException("元数据解析失败", e);
        }
    }

    /**
     * 校验集合名非空
     * @param collection 集合名
     */
    private void requireCollection(String collection) {
        if (collection == null || collection.isBlank()) {
            throw new IllegalArgumentException("集合名不能为空");
        }
    }

    /**
     * 检索命中
     * @author yangqiong
     * @param id 记录ID
     * @param bucket 所属复合桶
     * @param content 内容文本
     * @param score 相似度分值
     * @param metadata 元数据
     */
    record Hit(String id, String bucket, String content, float score, Map<String, Object> metadata) {
    }
}
