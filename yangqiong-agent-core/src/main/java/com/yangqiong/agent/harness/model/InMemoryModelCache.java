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
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.yangqiong.agent.harness.core.message.AgentMessage;
import com.yangqiong.agent.harness.core.model.AgentChatResponse;
import com.yangqiong.agent.harness.core.model.AgentGenerateOptions;

/**
 * 内存模型缓存
 * <p>
 * 基于LRU策略的内存缓存，按消息内容+工具Schema+生成选项的哈希作为缓存键。
 * </p>
 * @author yangqiong
 */
public class InMemoryModelCache implements ModelCache {

    /**
     * LRU缓存存储
     */
    private final LinkedHashMap<String, List<AgentChatResponse>> cache;

    /**
     * 最大缓存条目数
     */
    private final int maxSize;

    public InMemoryModelCache() {
        this(256);
    }

    public InMemoryModelCache(int maxSize) {
        this.maxSize = maxSize;
        this.cache = new LinkedHashMap<>(maxSize, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, List<AgentChatResponse>> eldest) {
                return size() > InMemoryModelCache.this.maxSize;
            }
        };
    }

    @Override
    public synchronized List<AgentChatResponse> get(List<AgentMessage> messages,
                                                       List<Map<String, Object>> toolSchemas,
                                                       AgentGenerateOptions options) {
        String key = buildKey(messages, toolSchemas, options);
        List<AgentChatResponse> cached = cache.get(key);
        return cached != null ? new ArrayList<>(cached) : null;
    }

    @Override
    public synchronized void put(List<AgentMessage> messages, List<Map<String, Object>> toolSchemas,
                                   AgentGenerateOptions options, List<AgentChatResponse> responses) {
        String key = buildKey(messages, toolSchemas, options);
        cache.put(key, new ArrayList<>(responses));
    }

    @Override
    public synchronized void clear() {
        cache.clear();
    }

    @Override
    public synchronized int size() {
        return cache.size();
    }

    /**
     * 构建缓存键，基于消息内容、工具Schema与生成选项的SHA-256摘要
     * <p>
     * 使用SHA-256而非32位哈希，避免不同输入碰撞导致错误命中缓存。
     * </p>
     * @param messages
     * @param toolSchemas
     * @param options
     * @return
     */
    private String buildKey(List<AgentMessage> messages, List<Map<String, Object>> toolSchemas,
                              AgentGenerateOptions options) {
        StringBuilder sb = new StringBuilder();
        if (messages != null) {
            for (AgentMessage msg : messages) {
                sb.append(msg != null ? msg.getTextContent() : "").append("|");
            }
        }
        sb.append("##");
        if (toolSchemas != null) {
            for (Map<String, Object> schema : toolSchemas) {
                sb.append(schema != null ? schema.toString() : "").append("|");
            }
        }
        sb.append("##");
        if (options != null) {
            sb.append(options.toString());
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16))
                        .append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            // JDK必然提供SHA-256，理论上不可达；兜底退化为字符串本身保证正确性
            return sb.toString();
        }
    }
}
