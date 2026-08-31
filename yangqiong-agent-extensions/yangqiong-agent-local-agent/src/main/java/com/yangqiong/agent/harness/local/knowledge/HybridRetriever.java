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
package com.yangqiong.agent.harness.local.knowledge;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.yangqiong.agent.harness.rag.InMemoryRetriever;
import com.yangqiong.agent.harness.rag.RetrievedChunk;
import com.yangqiong.agent.harness.rag.Retriever;

/**
 * 混合检索器
 * <p>
 * 组合关键词检索（InMemoryRetriever）与全文检索（LuceneFullTextRetriever），
 * 按 RRF（Reciprocal Rank Fusion）以文档id合并两路结果，弥补关键词精确匹配
 * 与全文相关性打分的各自盲区，统一以 hybrid 来源返回。
 * </p>
 * @author yangqiong
 */
public class HybridRetriever implements Retriever {

    /**
     * RRF 平滑常数
     */
    private static final int RRF_K = 60;

    /**
     * 默认来源
     */
    private static final String DEFAULT_SOURCE = "hybrid";

    /**
     * 关键词检索器
     */
    private final Retriever keywordRetriever;

    /**
     * 全文检索器
     */
    private final Retriever fullTextRetriever;

    /**
     * 以关键词与全文检索器构建混合检索器
     * @param keywordRetriever
     * @param fullTextRetriever
     */
    public HybridRetriever(Retriever keywordRetriever, Retriever fullTextRetriever) {
        this.keywordRetriever = keywordRetriever;
        this.fullTextRetriever = fullTextRetriever;
    }

    /**
     * 执行混合检索
     * @param query
     * @param topK
     * @param filters
     * @return
     */
    @Override
    public List<RetrievedChunk> retrieve(String query, int topK, Map<String, Object> filters) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        int fetchK = topK > 0 ? topK : 10;
        List<RetrievedChunk> keyword = keywordRetriever.retrieve(query, fetchK, filters);
        List<RetrievedChunk> fullText = fullTextRetriever.retrieve(query, fetchK, filters);
        return merge(keyword, fullText, fetchK);
    }

    /**
     * RRF 融合：同id文档并集去重，按排名倒数加权排序
     * @param keyword
     * @param fullText
     * @param topK
     * @return
     */
    private List<RetrievedChunk> merge(List<RetrievedChunk> keyword, List<RetrievedChunk> fullText, int topK) {
        Map<String, RetrievedChunk> byId = new HashMap<>();
        Map<String, Double> rrf = new HashMap<>();
        fuse(keyword, rrf, byId);
        fuse(fullText, rrf, byId);
        List<Map.Entry<String, Double>> ranked = new ArrayList<>(rrf.entrySet());
        ranked.sort(Map.Entry.<String, Double>comparingByValue().reversed());

        List<RetrievedChunk> result = new ArrayList<>();
        int remaining = InMemoryRetriever.MAX_RESULT_CHARS;
        for (Map.Entry<String, Double> entry : ranked) {
            if (result.size() >= topK || remaining <= 0) {
                break;
            }
            RetrievedChunk chunk = byId.get(entry.getKey());
            String content = chunk.content();
            if (content.length() > remaining) {
                content = content.substring(0, remaining);
            }
            remaining -= content.length();
            result.add(new RetrievedChunk(content, entry.getValue(), DEFAULT_SOURCE, chunk.metadata()));
        }
        return result;
    }

    /**
     * 累计一路结果的排名倒数分，并登记首个出现的文档块
     * @param hits
     * @param rrf
     * @param byId
     */
    private void fuse(List<RetrievedChunk> hits, Map<String, Double> rrf, Map<String, RetrievedChunk> byId) {
        for (int i = 0; i < hits.size(); i++) {
            Object id = hits.get(i).metadata().get("id");
            if (id == null) {
                continue;
            }
            String key = id.toString();
            rrf.merge(key, 1.0 / (RRF_K + i + 1), Double::sum);
            byId.putIfAbsent(key, hits.get(i));
        }
    }
}
