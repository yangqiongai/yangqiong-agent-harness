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
package com.yangqiongai.agent.harness.local.knowledge;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.yangqiongai.agent.harness.rag.InMemoryRetriever;
import com.yangqiongai.agent.harness.rag.RetrievedChunk;
import com.yangqiongai.agent.harness.rag.Retriever;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.cjk.CJKAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;

/**
 * 全文索引检索器
 * <p>
 * 基于 Lucene 内存索引提供全文检索：CJKAnalyzer 分词（中文按相邻二元组、英文按词）、
 * 默认 BM25 相关性打分；检索时以查询分词结果构建 SHOULD 布尔查询，命中数越高排序越靠前。
 * </p>
 * @author yangqiong
 */
public class LuceneFullTextRetriever implements Retriever {

    /**
     * 默认来源
     */
    private static final String DEFAULT_SOURCE = "full_text";

    /**
     * 文档id字段
     */
    private static final String ID_FIELD = "id";

    /**
     * 内容字段
     */
    private static final String CONTENT_FIELD = "content";

    /**
     * 文档索引，重建时整体替换
     */
    private Map<String, String> documents;

    /**
     * 分词器
     */
    private final Analyzer analyzer;

    /**
     * 内存索引目录
     */
    private final Directory directory;

    /**
     * 索引读取器，重建时关闭重开
     */
    private DirectoryReader reader;

    /**
     * 以文档索引构建全文检索器
     * @param documents
     */
    public LuceneFullTextRetriever(Map<String, String> documents) {
        this.documents = documents == null ? Map.of() : documents;
        this.analyzer = new CJKAnalyzer();
        this.directory = new ByteBuffersDirectory();
        index();
        try {
            this.reader = DirectoryReader.open(directory);
        } catch (IOException e) {
            throw new UncheckedIOException("全文索引打开失败", e);
        }
    }

    /**
     * 构建全文索引
     */
    private void index() {
        try (IndexWriter writer = new IndexWriter(directory, new IndexWriterConfig(analyzer))) {
            // 重建时先清空旧索引，避免残留文档干扰检索
            writer.deleteAll();
            for (Map.Entry<String, String> entry : documents.entrySet()) {
                if (entry.getValue() == null || entry.getValue().isBlank()) {
                    continue;
                }
                Document doc = new Document();
                doc.add(new StringField(ID_FIELD, entry.getKey(), Field.Store.YES));
                doc.add(new TextField(CONTENT_FIELD, entry.getValue(), Field.Store.NO));
                writer.addDocument(doc);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("全文索引构建失败", e);
        }
    }

    /**
     * 以新文档集合重建全文索引，线程安全（本实例串行重建）
     * @param docs
     */
    public synchronized void rebuild(Map<String, String> docs) {
        this.documents = docs == null ? Map.of() : docs;
        try {
            reader.close();
        } catch (IOException e) {
            throw new UncheckedIOException("全文索引关闭失败", e);
        }
        index();
        try {
            this.reader = DirectoryReader.open(directory);
        } catch (IOException e) {
            throw new UncheckedIOException("全文索引打开失败", e);
        }
    }

    /**
     * 执行全文检索
     * @param query
     * @param topK
     * @param filters
     * @return
     */
    @Override
    public List<RetrievedChunk> retrieve(String query, int topK, Map<String, Object> filters) {
        if (query == null || query.isBlank() || documents.isEmpty()) {
            return List.of();
        }
        List<String> terms = analyze(query);
        if (terms.isEmpty()) {
            return List.of();
        }
        String source = resolveSource(filters);
        int limit = topK > 0 ? topK : documents.size();

        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        for (String term : terms) {
            builder.add(new TermQuery(new Term(CONTENT_FIELD, term)), BooleanClause.Occur.SHOULD);
        }
        try {
            IndexSearcher searcher = new IndexSearcher(reader);
            TopDocs topDocs = searcher.search(builder.build(), Math.max(limit, 1));
            List<RetrievedChunk> result = new ArrayList<>();
            int remaining = InMemoryRetriever.MAX_RESULT_CHARS;
            for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
                if (result.size() >= limit || remaining <= 0) {
                    break;
                }
                String id = searcher.storedFields().document(scoreDoc.doc).get(ID_FIELD);
                if (id == null || !matchFilters(id, filters)) {
                    continue;
                }
                String content = documents.getOrDefault(id, "");
                if (content.length() > remaining) {
                    content = content.substring(0, remaining);
                }
                remaining -= content.length();
                result.add(buildChunk(id, content, scoreDoc.score, source));
            }
            return result;
        } catch (IOException e) {
            throw new UncheckedIOException("全文检索失败", e);
        }
    }

    /**
     * 构造结果块
     * @param id
     * @param content
     * @param score
     * @param source
     * @return
     */
    private RetrievedChunk buildChunk(String id, String content, double score, String source) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("id", id);
        return new RetrievedChunk(content, score, source, metadata);
    }

    /**
     * 解析来源，filters中的source优先
     * @param filters
     * @return
     */
    private String resolveSource(Map<String, Object> filters) {
        if (filters != null && filters.get("source") != null) {
            return filters.get("source").toString();
        }
        return DEFAULT_SOURCE;
    }

    /**
     * 按过滤器匹配文档id
     * @param id
     * @param filters
     * @return
     */
    private boolean matchFilters(String id, Map<String, Object> filters) {
        if (filters == null || filters.isEmpty()) {
            return true;
        }
        if (filters.get("ids") instanceof List<?> ids) {
            return ids.contains(id);
        }
        return true;
    }

    /**
     * 以同款分词器切分查询文本
     * @param text
     * @return
     */
    private List<String> analyze(String text) {
        List<String> terms = new ArrayList<>();
        try (TokenStream stream = analyzer.tokenStream(CONTENT_FIELD, text)) {
            CharTermAttribute attr = stream.addAttribute(CharTermAttribute.class);
            stream.reset();
            while (stream.incrementToken()) {
                terms.add(attr.toString());
            }
            stream.end();
        } catch (IOException e) {
            throw new UncheckedIOException("查询分词失败", e);
        }
        return terms;
    }
}
