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
package com.yangqiongai.agent.harness.memory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import com.yangqiongai.agent.harness.core.memory.AgentLongTermMemory;
import com.yangqiongai.agent.harness.durable.ScopeKeys;
import com.yangqiongai.agent.harness.model.embedding.EmbeddingCache;
import com.yangqiongai.agent.harness.model.embedding.EmbeddingModel;

/**
 * 向量长期记忆
 * <p>
 * 基于嵌入模型的语义长期记忆，存储时将内容嵌入为向量；
 * 检索采用混合打分（余弦相似度×0.6 + 关键字命中×0.4），兼顾语义与字面匹配。
 * 按 (scopeId, userId) 复合桶隔离，删除带所有权校验；
 * 容量超限按最近访问时间淘汰，可选配置遗忘策略清理过期条目。
 * </p>
 * @author yangqiong
 */
public class VectorLongTermMemory implements AgentLongTermMemory {

    /**
     * 最大记忆条目数，超限淘汰最近未访问条目，防止内存无界增长
     */
    public static final int MAX_ENTRIES = 10000;

    /**
     * 余弦相似度打分权重
     */
    public static final double COSINE_WEIGHT = 0.6d;

    /**
     * 关键字命中打分权重
     */
    public static final double KEYWORD_WEIGHT = 0.4d;

    /**
     * 嵌入模型
     */
    private final EmbeddingModel embeddingModel;

    /**
     * 嵌入去重缓存
     */
    private final EmbeddingCache embeddingCache;

    /**
     * 遗忘策略，为null时不启用遗忘清理
     */
    private final ForgettingPolicy forgettingPolicy;

    /**
     * 按复合桶隔离的记忆条目
     */
    private final Map<String, List<Entry>> storeByBucket = new ConcurrentHashMap<>();

    /**
     * 按记忆ID索引，用于快速删除与所有权校验
     */
    private final Map<String, Entry> indexById = new ConcurrentHashMap<>();

    public VectorLongTermMemory(EmbeddingModel embeddingModel) {
        this(embeddingModel, null);
    }

    /**
     * 按指定嵌入模型与遗忘策略构建向量长期记忆
     * @param embeddingModel
     * @param forgettingPolicy
     */
    public VectorLongTermMemory(EmbeddingModel embeddingModel, ForgettingPolicy forgettingPolicy) {
        if (embeddingModel == null) {
            throw new IllegalArgumentException("嵌入模型不能为空");
        }
        this.embeddingModel = embeddingModel;
        this.embeddingCache = new EmbeddingCache();
        this.forgettingPolicy = forgettingPolicy;
    }

    @Override
    public void store(String userId, String sessionId, String content, Map<String, Object> metadata) {
        store(null, userId, sessionId, content, metadata);
    }

    @Override
    public void store(String scopeId, String userId, String sessionId,
                      String content, Map<String, Object> metadata) {
        if (content == null || content.isBlank()) {
            return;
        }
        String bucket = ScopeKeys.memoryBucket(scopeId, userId);
        String id = UUID.randomUUID().toString();
        float[] vector = embeddingCache.getOrCompute(content, embeddingModel);
        Entry entry = new Entry(id, bucket, content, metadata, vector);
        indexById.put(id, entry);
        storeByBucket.computeIfAbsent(bucket, k -> new CopyOnWriteArrayList<>()).add(entry);
        evictOverflow(entry);
        applyForgetting();
    }

    /**
     * 容量超限时淘汰最近未访问条目并保持双索引一致
     * @param protectedEntry 本次保存的条目，禁止作为淘汰对象
     */
    private void evictOverflow(Entry protectedEntry) {
        if (indexById.size() <= MAX_ENTRIES) {
            return;
        }
        indexById.values().stream()
                .filter(e -> e != protectedEntry)
                .min(Comparator.comparingLong(e -> e.lastAccessedAt))
                .ifPresent(this::removeEntry);
    }

    /**
     * 按遗忘策略清理过期条目，策略为null时跳过
     */
    private void applyForgetting() {
        if (forgettingPolicy == null) {
            return;
        }
        List<Entry> victims = new ArrayList<>();
        for (Entry entry : indexById.values()) {
            ForgettingPolicy.EntryInfo info = new ForgettingPolicy.EntryInfo(
                    entry.id, entry.content, entry.createdAt, entry.lastAccessedAt, entry.accessCount);
            if (forgettingPolicy.shouldForget(info)) {
                victims.add(entry);
            }
        }
        victims.forEach(this::removeEntry);
    }

    /**
     * 从双索引中移除记忆条目
     * @param entry
     */
    private void removeEntry(Entry entry) {
        indexById.remove(entry.id, entry);
        List<Entry> entries = storeByBucket.get(entry.bucket);
        if (entries != null) {
            entries.remove(entry);
            if (entries.isEmpty()) {
                storeByBucket.remove(entry.bucket, entries);
            }
        }
    }

    @Override
    public List<String> search(String userId, String query, int limit) {
        return search(null, userId, query, limit);
    }

    @Override
    public List<String> search(String scopeId, String userId, String query, int limit) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        List<Entry> entries = storeByBucket.get(ScopeKeys.memoryBucket(scopeId, userId));
        if (entries == null || entries.isEmpty()) {
            return List.of();
        }
        float[] queryVector = embeddingCache.getOrCompute(query, embeddingModel);
        List<String> tokens = tokenize(query);
        int topK = limit > 0 ? limit : entries.size();
        return entries.stream()
                .map(e -> {
                    touch(e);
                    return new Scored(e, score(e, queryVector, tokens));
                })
                .filter(s -> s.score > 0)
                .sorted(Comparator.comparingDouble((Scored s) -> s.score).reversed())
                .limit(topK)
                .map(s -> s.entry.content)
                .toList();
    }

    /**
     * 更新条目的最近访问时间与访问次数
     * @param entry
     */
    private void touch(Entry entry) {
        entry.lastAccessedAt = System.currentTimeMillis();
        entry.accessCount++;
    }

    /**
     * 混合打分：余弦相似度×0.6 + 关键字命中占比×0.4
     * @param entry
     * @param queryVector
     * @param tokens
     * @return
     */
    private double score(Entry entry, float[] queryVector, List<String> tokens) {
        double cosine = EmbeddingModel.cosine(queryVector, entry.vector);
        double keyword = keywordScore(entry.content, tokens);
        return COSINE_WEIGHT * cosine + KEYWORD_WEIGHT * keyword;
    }

    /**
     * 计算关键字命中占比
     * @param content
     * @param tokens
     * @return
     */
    private double keywordScore(String content, List<String> tokens) {
        if (tokens.isEmpty()) {
            return 0.0d;
        }
        String text = content.toLowerCase();
        int hits = 0;
        for (String token : tokens) {
            if (text.contains(token)) {
                hits++;
            }
        }
        return (double) hits / tokens.size();
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

    @Override
    public void delete(String memoryId) {
        if (memoryId == null) {
            return;
        }
        Entry entry = indexById.remove(memoryId);
        if (entry == null) {
            return;
        }
        List<Entry> entries = storeByBucket.get(entry.bucket);
        if (entries != null) {
            entries.removeIf(e -> e.id.equals(memoryId));
        }
    }

    @Override
    public void delete(String scopeId, String memoryId) {
        if (memoryId == null) {
            return;
        }
        Entry entry = indexById.get(memoryId);
        if (entry == null) {
            return;
        }
        // 所有权校验：记忆必须属于当前租户作用域才允许删除
        if (!entry.bucket.equals(ScopeKeys.memoryBucket(scopeId, extractUserId(entry.bucket)))) {
            throw new SecurityException("记忆删除所有权校验失败: memoryId=" + memoryId);
        }
        delete(memoryId);
    }

    /**
     * 从复合桶键中提取userId段
     * @param bucket
     * @return
     */
    private String extractUserId(String bucket) {
        int idx = bucket.indexOf("::");
        return idx >= 0 ? bucket.substring(idx + 2) : bucket;
    }

    /**
     * 列出指定作用域与用户下的全部记忆ID
     * @param scopeId
     * @param userId
     * @return
     */
    public List<String> listMemoryIds(String scopeId, String userId) {
        List<Entry> entries = storeByBucket.get(ScopeKeys.memoryBucket(scopeId, userId));
        if (entries == null || entries.isEmpty()) {
            return List.of();
        }
        return entries.stream().map(e -> e.id).toList();
    }

    /**
     * 获取当前记忆条目总数
     * @return
     */
    public int size() {
        return indexById.size();
    }

    /**
     * 记忆条目
     */
    private static final class Entry {

        /**
         * 记忆ID
         */
        final String id;

        /**
         * 所属复合桶键
         */
        final String bucket;

        /**
         * 记忆内容
         */
        final String content;

        /**
         * 元数据
         */
        final Map<String, Object> metadata;

        /**
         * 内容嵌入向量
         */
        final float[] vector;

        /**
         * 创建时间戳
         */
        final long createdAt;

        /**
         * 最近访问时间戳，用于容量超限时淘汰最近未访问条目
         */
        volatile long lastAccessedAt;

        /**
         * 访问次数
         */
        volatile int accessCount;

        Entry(String id, String bucket, String content, Map<String, Object> metadata, float[] vector) {
            this.id = id;
            this.bucket = bucket;
            this.content = content;
            this.metadata = metadata;
            this.vector = vector;
            this.createdAt = System.currentTimeMillis();
            this.lastAccessedAt = createdAt;
        }
    }

    /**
     * 带分值的检索命中
     */
    private static final class Scored {

        /**
         * 命中的记忆条目
         */
        final Entry entry;

        /**
         * 混合打分分值
         */
        final double score;

        Scored(Entry entry, double score) {
            this.entry = entry;
            this.score = score;
        }
    }
}
