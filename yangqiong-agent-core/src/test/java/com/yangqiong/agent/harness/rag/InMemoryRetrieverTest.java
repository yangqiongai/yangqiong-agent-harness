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
package com.yangqiong.agent.harness.rag;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * 内存检索器默认实现测试
 * @author yangqiong
 */
class InMemoryRetrieverTest {

    @Test
    void indexAndRetrieveHits() {
        Map<String, String> docs = new HashMap<>();
        docs.put("d1", "Java是面向对象的编程语言");
        docs.put("d2", "Python适合数据分析");
        InMemoryRetriever retriever = new InMemoryRetriever(docs);

        List<RetrievedChunk> hits = retriever.retrieve("Java 编程", 3, null);

        assertThat(hits).isNotEmpty();
        assertThat(hits.get(0).content()).contains("Java");
        assertThat(hits.get(0).source()).isEqualTo("in_memory");
    }

    @Test
    void rankByKeywordHitsDesc() {
        Map<String, String> docs = new HashMap<>();
        docs.put("d1", "Spring框架用于依赖注入");
        docs.put("d2", "Spring框架 Spring框架 依赖注入 依赖注入");
        InMemoryRetriever retriever = new InMemoryRetriever(docs);

        List<RetrievedChunk> hits = retriever.retrieve("Spring 依赖注入", 2, null);

        assertThat(hits).hasSize(2);
        assertThat(hits.get(0).score()).isGreaterThan(hits.get(1).score());
        assertThat(hits.get(0).content()).contains("Spring框架 Spring框架");
    }

    @Test
    void topKTruncatesResults() {
        Map<String, String> docs = new HashMap<>();
        for (int i = 0; i < 10; i++) {
            docs.put("d" + i, "Java相关内容" + i);
        }
        InMemoryRetriever retriever = new InMemoryRetriever(docs);

        List<RetrievedChunk> hits = retriever.retrieve("Java 内容", 3, null);

        assertThat(hits).hasSize(3);
    }

    @Test
    void resultCharsBounded() {
        Map<String, String> docs = new HashMap<>();
        String big = "J".repeat(InMemoryRetriever.MAX_RESULT_CHARS + 500);
        docs.put("d1", big);
        docs.put("d2", big);
        InMemoryRetriever retriever = new InMemoryRetriever(docs);

        List<RetrievedChunk> hits = retriever.retrieve("J", 2, null);
        int totalChars = hits.stream().mapToInt(h -> h.content().length()).sum();

        assertThat(totalChars).isLessThanOrEqualTo(InMemoryRetriever.MAX_RESULT_CHARS);
    }

    @Test
    void documentCountBounded() {
        Map<String, String> docs = new HashMap<>();
        for (int i = 0; i < InMemoryRetriever.MAX_DOCS + 100; i++) {
            docs.put("d" + i, "Java" + i);
        }
        InMemoryRetriever retriever = new InMemoryRetriever(docs);

        List<RetrievedChunk> hits = retriever.retrieve("Java", InMemoryRetriever.MAX_DOCS, null);

        assertThat(hits).hasSizeLessThanOrEqualTo(InMemoryRetriever.MAX_DOCS);
    }

    @Test
    void filterByIds() {
        Map<String, String> docs = new HashMap<>();
        docs.put("d1", "Java内容");
        docs.put("d2", "Java内容");
        InMemoryRetriever retriever = new InMemoryRetriever(docs);

        List<RetrievedChunk> hits = retriever.retrieve("Java", 10, Map.of("ids", List.of("d1")));

        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).metadata()).containsEntry("id", "d1");
    }

    @Test
    void noMatchReturnsEmpty() {
        InMemoryRetriever retriever = new InMemoryRetriever(Map.of("d1", "Python内容"));

        assertThat(retriever.retrieve("Java", 3, null)).isEmpty();
    }

    @Test
    void addDocumentRespectsCapacity() {
        InMemoryRetriever retriever = new InMemoryRetriever(Map.of());
        for (int i = 0; i < InMemoryRetriever.MAX_DOCS; i++) {
            retriever.addDocument("d" + i, "Java" + i);
        }
        // 已满时新增被拒绝
        assertThat(retriever.addDocument("overflow", "Java")).isFalse();
        // 更新已有文档仍允许
        assertThat(retriever.addDocument("d0", "Java更新")).isTrue();
        // 空参数返回false
        assertThat(retriever.addDocument(null, "Java")).isFalse();
    }
}