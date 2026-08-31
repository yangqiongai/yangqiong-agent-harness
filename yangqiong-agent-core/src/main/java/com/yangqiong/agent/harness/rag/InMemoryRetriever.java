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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存检索器默认实现
 * <p>
 * 注入文档索引（id->content），按查询关键字命中数排序（风格对齐InMemoryLongTermMemory），
 * 文档数与结果字符均有界（MAX_DOCS=1000, MAX_RESULT_CHARS=10000）。
 * </p>
 * @author yangqiong
 */
public class InMemoryRetriever implements Retriever {

    /**
     * 最大文档数
     */
    public static final int MAX_DOCS = 1000;

    /**
     * 最大结果字符数
     */
    public static final int MAX_RESULT_CHARS = 10000;

    /**
     * 默认来源
     */
    private static final String DEFAULT_SOURCE = "in_memory";

    /**
     * 文档索引
     */
    private final Map<String, String> documents = new ConcurrentHashMap<>();

    public InMemoryRetriever(Map<String, String> documents) {
        if (documents != null) {
            documents.forEach((id, content) -> {
                if (this.documents.size() < MAX_DOCS && content != null) {
                    this.documents.put(id, content);
                }
            });
        }
    }

    /**
     * 新增文档，超上限时忽略
     * @param id
     * @param content
     * @return
     */
    public boolean addDocument(String id, String content) {
        if (id == null || content == null) {
            return false;
        }
        if (documents.size() >= MAX_DOCS && !documents.containsKey(id)) {
            return false;
        }
        documents.put(id, content);
        return true;
    }

    /**
     * 执行关键字检索
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
        List<String> tokens = tokenize(query);
        if (tokens.isEmpty()) {
            return List.of();
        }
        String source = resolveSource(filters);
        int limit = topK > 0 ? topK : documents.size();

        List<Scored> scored = new ArrayList<>();
        for (Map.Entry<String, String> entry : documents.entrySet()) {
            if (!matchFilters(entry.getKey(), filters)) {
                continue;
            }
            int hits = score(entry.getValue(), tokens);
            if (hits > 0) {
                scored.add(new Scored(entry.getKey(), entry.getValue(), hits));
            }
        }
        scored.sort(Comparator.comparingInt((Scored s) -> s.score).reversed());

        List<RetrievedChunk> result = new ArrayList<>();
        int remaining = MAX_RESULT_CHARS;
        for (Scored s : scored) {
            if (result.size() >= limit || remaining <= 0) {
                break;
            }
            String content = s.content;
            if (content.length() > remaining) {
                content = content.substring(0, remaining);
            }
            remaining -= content.length();
            result.add(buildChunk(s.id, content, s.score, source));
        }
        return result;
    }

    /**
     * 构造结果块
     * @param id
     * @param content
     * @param score
     * @param source
     * @return
     */
    private RetrievedChunk buildChunk(String id, String content, int score, String source) {
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
     * 简单分词：按非字母数字字符切分并小写
     * @param query
     * @return
     */
    private List<String> tokenize(String query) {
        List<String> tokens = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < query.length(); i++) {
            char c = query.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                sb.append(Character.toLowerCase(c));
            } else if (sb.length() > 0) {
                tokens.add(sb.toString());
                sb.setLength(0);
            }
        }
        if (sb.length() > 0) {
            tokens.add(sb.toString());
        }
        return tokens;
    }

    /**
     * 计算文档对查询词的命中数（按出现次数累计）
     * @param content
     * @param tokens
     * @return
     */
    private int score(String content, List<String> tokens) {
        String text = content.toLowerCase();
        int hits = 0;
        for (String token : tokens) {
            int idx = 0;
            while ((idx = text.indexOf(token, idx)) >= 0) {
                hits++;
                idx += token.length();
            }
        }
        return hits;
    }

    /**
     * 带分值的检索命中
     */
    private static final class Scored {

        /**
         * 文档id
         */
        final String id;

        /**
         * 文档内容
         */
        final String content;

        /**
         * 命中分值
         */
        final int score;

        Scored(String id, String content, int score) {
            this.id = id;
            this.content = content;
            this.score = score;
        }
    }
}