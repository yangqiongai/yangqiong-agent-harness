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
package com.yangqiong.agent.harness.ratelimit;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 模型速率限制注册表
 * <p>
 * 按 modelCode → RateLimiter 命名表，供多模型路由场景按模型编码查询限流器。
 * 有界容量（默认256），容量超限时不再登记新条目，防止无界增长。
 * </p>
 * @author yangqiong
 */
public class ModelRateLimitRegistry {

    /**
     * 默认最大条目数
     */
    public static final int MAX_ENTRIES = 256;

    /**
     * 最大条目数
     */
    private final int maxEntries;

    /**
     * modelCode → RateLimiter
     */
    private final ConcurrentHashMap<String, RateLimiter> limiters = new ConcurrentHashMap<>();

    public ModelRateLimitRegistry() {
        this(MAX_ENTRIES);
    }

    /**
     * 按指定容量构建注册表
     * @param maxEntries
     */
    public ModelRateLimitRegistry(int maxEntries) {
        this.maxEntries = maxEntries > 0 ? maxEntries : MAX_ENTRIES;
    }

    /**
     * 登记限流器，容量超限时忽略新条目
     * @param modelCode
     * @param limiter
     * @return 是否登记成功
     */
    public boolean register(String modelCode, RateLimiter limiter) {
        if (modelCode == null || modelCode.isBlank() || limiter == null) {
            return false;
        }
        if (limiters.size() >= maxEntries && !limiters.containsKey(modelCode)) {
            return false;
        }
        limiters.put(modelCode, limiter);
        return true;
    }

    /**
     * 按模型编码查询限流器，未登记返回null
     * @param modelCode
     * @return
     */
    public RateLimiter limiterFor(String modelCode) {
        return modelCode != null ? limiters.get(modelCode) : null;
    }

    /**
     * 获取已登记的条目数
     * @return
     */
    public int size() {
        return limiters.size();
    }
}