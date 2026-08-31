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
package com.yangqiong.agent.harness.model.embedding;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 文本嵌入去重缓存
 * <p>
 * 可选文本到向量的去重缓存，避免相同文本重复嵌入计算。
 * 基于LinkedHashMap实现有界LRU语义，容量超限时淘汰最近最少使用项。
 * </p>
 * @author yangqiong
 */
public class EmbeddingCache {

    /**
     * 最大缓存条目数
     */
    public static final int MAX_ENTRIES = 10000;

    /**
     * LRU缓存存储
     */
    private final LinkedHashMap<String, float[]> cache;

    /**
     * 最大缓存条目数
     */
    private final int maxEntries;

    public EmbeddingCache() {
        this(MAX_ENTRIES);
    }

    /**
     * 按指定容量构建嵌入缓存
     * @param maxEntries
     */
    public EmbeddingCache(int maxEntries) {
        this.maxEntries = maxEntries;
        this.cache = new LinkedHashMap<>(maxEntries, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, float[]> eldest) {
                return size() > EmbeddingCache.this.maxEntries;
            }
        };
    }

    /**
     * 获取文本对应的向量，未命中时通过模型计算并写入缓存
     * @param text
     * @param model
     * @return
     */
    public synchronized float[] getOrCompute(String text, EmbeddingModel model) {
        float[] cached = cache.get(text);
        if (cached != null) {
            return cached;
        }
        float[] computed = model.embed(text);
        cache.put(text, computed);
        return computed;
    }

    /**
     * 判断缓存中是否已存在指定文本的向量
     * @param text
     * @return
     */
    public synchronized boolean contains(String text) {
        return cache.containsKey(text);
    }

    /**
     * 清空缓存
     */
    public synchronized void clear() {
        cache.clear();
    }

    /**
     * 获取当前缓存条目数
     * @return
     */
    public synchronized int size() {
        return cache.size();
    }
}
