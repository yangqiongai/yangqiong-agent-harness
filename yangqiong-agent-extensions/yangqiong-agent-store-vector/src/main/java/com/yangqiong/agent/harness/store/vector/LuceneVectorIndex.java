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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.yangqiong.agent.harness.durable.serialization.HarnessObjectMapper;

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
 * </p>
 * @author yangqiong
 */
final class LuceneVectorIndex implements AutoCloseable {

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

    /**
     * 新增或覆盖一条向量记录并提交落盘
     * @param id
     * @param bucket
     * @param content
     * @param metadata
     * @param vector
     */
    synchronized void upsert(String id, String bucket, String content,
                             Map<String, Object> metadata, float[] vector) {
        Document doc = new Document();
        doc.add(new StringField(FIELD_ID, id, Field.Store.YES));
        doc.add(new StringField(FIELD_BUCKET, bucket, Field.Store.YES));
        doc.add(new StoredField(FIELD_CONTENT, content));
        if (metadata != null && !metadata.isEmpty()) {
            doc.add(new StoredField(FIELD_METADATA, toJson(metadata)));
        }
        doc.add(new KnnFloatVectorField(FIELD_VECTOR, vector, VectorSimilarityFunction.COSINE));
        try {
            writer.updateDocument(new Term(FIELD_ID, id), doc);
            writer.commit();
        } catch (IOException e) {
            throw new UncheckedIOException("向量记录写入失败: " + id, e);
        }
    }

    /**
     * 按ID删除向量记录并提交落盘
     * @param id
     */
    synchronized void removeById(String id) {
        try {
            writer.deleteDocuments(new Term(FIELD_ID, id));
            writer.commit();
        } catch (IOException e) {
            throw new UncheckedIOException("向量记录删除失败: " + id, e);
        }
    }

    /**
     * 按ID查询所属复合桶，不存在时返回null
     * @param id
     * @return
     */
    synchronized String bucketOf(String id) {
        List<Hit> hits = searchByTerm(FIELD_ID, id);
        return hits.isEmpty() ? null : hits.get(0).bucket;
    }

    /**
     * 列出指定复合桶下的全部记录ID
     * @param bucket
     * @return
     */
    synchronized List<String> listIds(String bucket) {
        if (bucket == null) {
            return List.of();
        }
        return searchByTerm(FIELD_BUCKET, bucket).stream().map(h -> h.id).toList();
    }

    /**
     * KNN向量检索，返回经复合桶与标量元数据过滤后的命中
     * @param queryVector
     * @param bucket 复合桶过滤，为null时不过滤
     * @param scalarFilters 标量元数据过滤，按key/value精确匹配
     * @param topK
     * @return
     */
    synchronized List<Hit> search(float[] queryVector, String bucket,
                                  Map<String, Object> scalarFilters, int topK) {
        int numCandidates = Math.max(topK * CANDIDATE_MAGNIFIER, CANDIDATE_FLOOR);
        try (IndexReader reader = DirectoryReader.open(writer)) {
            IndexSearcher searcher = new IndexSearcher(reader);
            KnnFloatVectorQuery query = new KnnFloatVectorQuery(FIELD_VECTOR, queryVector, numCandidates);
            TopDocs topDocs = searcher.search(query, numCandidates);
            List<Hit> results = new ArrayList<>();
            for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
                Document doc = searcher.storedFields().document(scoreDoc.doc);
                String hitBucket = doc.get(FIELD_BUCKET);
                if (bucket != null && !bucket.equals(hitBucket)) {
                    continue;
                }
                Map<String, Object> metadata = parseMetadata(doc.get(FIELD_METADATA));
                if (!matchesFilter(metadata, scalarFilters)) {
                    continue;
                }
                results.add(new Hit(doc.get(FIELD_ID), hitBucket,
                        doc.get(FIELD_CONTENT), scoreDoc.score, metadata));
                if (results.size() >= topK) {
                    break;
                }
            }
            return results;
        } catch (IOException e) {
            throw new UncheckedIOException("向量检索失败", e);
        }
    }

    /**
     * 获取当前索引记录总数
     * @return
     */
    synchronized int size() {
        return writer.getDocStats().numDocs;
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
     * 校验标量元数据过滤：全部键值均需精确匹配才放行，空过滤直接放行
     * @param metadata
     * @param filters
     * @return
     */
    private boolean matchesFilter(Map<String, Object> metadata, Map<String, Object> filters) {
        if (filters == null || filters.isEmpty()) {
            return true;
        }
        for (Map.Entry<String, Object> entry : filters.entrySet()) {
            Object value = metadata == null ? null : metadata.get(entry.getKey());
            if (value == null || !value.toString().equals(String.valueOf(entry.getValue()))) {
                return false;
            }
        }
        return true;
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