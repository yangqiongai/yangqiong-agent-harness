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
package com.yangqiong.agent.harness.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.yangqiong.agent.harness.core.message.AgentChatUsage;
import com.yangqiong.agent.harness.core.message.AgentContentBlock;
import com.yangqiong.agent.harness.core.message.AgentMessage;
import com.yangqiong.agent.harness.core.message.AgentMessageRole;
import com.yangqiong.agent.harness.core.model.AgentChatResponse;
import com.yangqiong.agent.harness.core.model.AgentGenerateOptions;
import com.yangqiong.agent.harness.core.model.AgentModel;
import com.yangqiong.agent.harness.model.embedding.EmbeddingModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

/**
 * 语义缓存模型调用器
 * <p>
 * 装饰{@link AgentModel}，将查询嵌入为向量后与缓存命中比对，余弦相似度达到阈值
 * 且上下文指纹一致时直接返回缓存响应，未命中时调用真实模型并将结果写入有界缓存。
 * 上下文指纹覆盖完整对话历史、工具集与生成选项，确保多轮循环与不同会话间
 * 相同/相近的查询不会误命中彼此的缓存。
 * 可与{@link RetryableModelCaller}、{@link FallbackModel}自由组合
 * （外层重试→语义缓存→内层降级），不互斥。
 * </p>
 * @author yangqiong
 */
public class SemanticCachingModelCaller implements AgentModel {

    private static final Logger log = LoggerFactory.getLogger(SemanticCachingModelCaller.class);

    /**
     * 最大缓存条目数，超限淘汰最近最少使用条目
     */
    public static final int MAX_ENTRIES = 1000;

    /**
     * 默认命中相似度阈值
     */
    public static final double DEFAULT_THRESHOLD = 0.92d;

    /**
     * 被装饰的实际模型
     */
    private final AgentModel delegate;

    /**
     * 嵌入模型
     */
    private final EmbeddingModel embeddingModel;

    /**
     * 命中相似度阈值
     */
    private final double threshold;

    /**
     * 按查询文本存储的LRU语义缓存
     */
    private final LinkedHashMap<String, CachedEntry> cache;

    public SemanticCachingModelCaller(AgentModel delegate, EmbeddingModel embeddingModel) {
        this(delegate, embeddingModel, DEFAULT_THRESHOLD);
    }

    /**
     * 按指定嵌入模型与命中阈值构建语义缓存模型调用器
     * @param delegate
     * @param embeddingModel
     * @param threshold
     */
    public SemanticCachingModelCaller(AgentModel delegate, EmbeddingModel embeddingModel, double threshold) {
        this(delegate, embeddingModel, threshold, MAX_ENTRIES);
    }

    /**
     * 按指定嵌入模型、命中阈值与缓存容量构建语义缓存模型调用器
     * @param delegate
     * @param embeddingModel
     * @param threshold
     * @param maxEntries
     */
    public SemanticCachingModelCaller(AgentModel delegate, EmbeddingModel embeddingModel,
                                      double threshold, int maxEntries) {
        if (delegate == null) {
            throw new IllegalArgumentException("被装饰的模型不能为空");
        }
        if (embeddingModel == null) {
            throw new IllegalArgumentException("嵌入模型不能为空");
        }
        if (maxEntries <= 0) {
            throw new IllegalArgumentException("缓存容量必须为正数: " + maxEntries);
        }
        this.delegate = delegate;
        this.embeddingModel = embeddingModel;
        this.threshold = threshold;
        this.cache = new LinkedHashMap<>(maxEntries, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, CachedEntry> eldest) {
                return size() > maxEntries;
            }
        };
    }

    @Override
    public AgentChatResponse generate(List<AgentMessage> messages, List<Map<String, Object>> tools,
                                      AgentGenerateOptions options) {
        int queryIndex = lastUserMessageIndex(messages);
        String query = extractQuery(messages, queryIndex);
        if (query == null || query.isBlank()) {
            return delegate.generate(messages, tools, options);
        }
        String fingerprint = contextFingerprint(messages, queryIndex, tools, options);
        float[] queryVector = embeddingModel.embed(query);
        CachedEntry hit;
        synchronized (cache) {
            hit = findBestHit(queryVector, fingerprint);
        }
        if (hit != null) {
            log.debug("[SemanticCache] 命中语义缓存: query={}", query);
            return mergeChunks(hit.chunks);
        }
        AgentChatResponse response = delegate.generate(messages, tools, options);
        synchronized (cache) {
            cache.put(query, new CachedEntry(queryVector, fingerprint, List.of(response)));
        }
        return response;
    }

    @Override
    public Flux<AgentChatResponse> stream(List<AgentMessage> messages, List<Map<String, Object>> tools,
                                          AgentGenerateOptions options) {
        int queryIndex = lastUserMessageIndex(messages);
        String query = extractQuery(messages, queryIndex);
        if (query == null || query.isBlank()) {
            return delegate.stream(messages, tools, options);
        }
        String fingerprint = contextFingerprint(messages, queryIndex, tools, options);
        float[] queryVector = embeddingModel.embed(query);
        CachedEntry hit;
        synchronized (cache) {
            hit = findBestHit(queryVector, fingerprint);
        }
        if (hit != null) {
            log.debug("[SemanticCache] 命中语义缓存流式调用: query={}", query);
            return Flux.fromIterable(hit.chunks);
        }
        List<AgentChatResponse> accumulated = Collections.synchronizedList(new ArrayList<>());
        return delegate.stream(messages, tools, options)
                .doOnNext(accumulated::add)
                .doOnComplete(() -> {
                    if (!accumulated.isEmpty()) {
                        synchronized (cache) {
                            cache.put(query, new CachedEntry(queryVector, fingerprint, accumulated));
                        }
                    }
                });
    }

    /**
     * 从缓存中查找与查询向量相似度达到阈值且上下文指纹一致的缓存项
     * @param queryVector
     * @param fingerprint
     * @return
     */
    private CachedEntry findBestHit(float[] queryVector, String fingerprint) {
        CachedEntry best = null;
        double bestScore = threshold;
        for (CachedEntry entry : cache.values()) {
            if (!entry.fingerprint.equals(fingerprint)) {
                continue;
            }
            double similarity = EmbeddingModel.cosine(queryVector, entry.vector);
            if (similarity >= bestScore) {
                best = entry;
                bestScore = similarity;
            }
        }
        return best;
    }

    /**
     * 计算上下文指纹：覆盖除查询消息外的完整对话历史、工具集与生成选项
     * <p>
     * 查询消息本身不参与指纹（由语义相似度判定），其余历史全部参与指纹，
     * 确保多轮工具循环（轮次间中间消息增长）与跨会话场景不会误命中彼此的缓存。
     * </p>
     * @param messages
     * @param queryIndex
     * @param tools
     * @param options
     * @return
     */
    private String contextFingerprint(List<AgentMessage> messages, int queryIndex,
                                      List<Map<String, Object>> tools, AgentGenerateOptions options) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < messages.size(); i++) {
            if (i == queryIndex) {
                continue;
            }
            AgentMessage message = messages.get(i);
            if (message == null) {
                continue;
            }
            sb.append(message.getRole()).append(':').append(message.getTextContent()).append('|');
        }
        sb.append("#tools:").append(tools == null ? "[]" : tools.toString()).append('|');
        sb.append("#options:").append(options == null ? "" : options.toString());
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (Exception e) {
            // 摘要算法不可用时退化为原文指纹，仍保证区分度
            return sb.toString();
        }
    }

    /**
     * 定位最后一条用户消息下标，无用户消息时返回-1
     * @param messages
     * @return
     */
    private int lastUserMessageIndex(List<AgentMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return -1;
        }
        for (int i = messages.size() - 1; i >= 0; i--) {
            AgentMessage message = messages.get(i);
            if (message != null && message.getRole() == AgentMessageRole.USER) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 提取查询文本：有用户消息时取该消息文本，否则拼接全部消息文本
     * @param messages
     * @param queryIndex
     * @return
     */
    private String extractQuery(List<AgentMessage> messages, int queryIndex) {
        if (messages == null || messages.isEmpty()) {
            return null;
        }
        if (queryIndex >= 0) {
            String text = messages.get(queryIndex).getTextContent();
            return text == null || text.isBlank() ? null : text;
        }
        StringBuilder sb = new StringBuilder();
        for (AgentMessage message : messages) {
            if (message != null) {
                sb.append(message.getTextContent());
            }
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    /**
     * 合并多个响应分片为一个完整响应
     * @param chunks
     * @return
     */
    private AgentChatResponse mergeChunks(List<AgentChatResponse> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return new AgentChatResponse(List.of(), null);
        }
        if (chunks.size() == 1) {
            return chunks.get(0);
        }
        List<AgentContentBlock> blocks = new ArrayList<>();
        AgentChatUsage usage = null;
        for (AgentChatResponse chunk : chunks) {
            blocks.addAll(chunk.getContent());
            usage = AgentChatUsage.merge(usage, chunk.getChatUsage());
        }
        return new AgentChatResponse(blocks, usage);
    }

    /**
     * 获取当前缓存条目数
     * @return
     */
    public int size() {
        synchronized (cache) {
            return cache.size();
        }
    }

    /**
     * 清空缓存
     */
    public void clear() {
        synchronized (cache) {
            cache.clear();
        }
    }

    /**
     * 缓存条目
     */
    private static final class CachedEntry {

        /**
         * 查询文本嵌入向量
         */
        final float[] vector;

        /**
         * 上下文指纹（对话历史+工具集+生成选项）
         */
        final String fingerprint;

        /**
         * 缓存的响应分片列表
         */
        final List<AgentChatResponse> chunks;

        CachedEntry(float[] vector, String fingerprint, List<AgentChatResponse> chunks) {
            this.vector = vector;
            this.fingerprint = fingerprint;
            this.chunks = chunks;
        }
    }
}
