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
package com.yangqiongai.agent.harness.store.jdbc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangqiongai.agent.harness.core.memory.AgentLongTermMemory;
import com.yangqiongai.agent.harness.durable.serialization.HarnessObjectMapper;
import com.yangqiongai.agent.harness.store.jdbc.entity.LongTermMemoryEntity;
import com.yangqiongai.agent.harness.store.jdbc.mapper.LongTermMemoryMapper;

/**
 * 长期记忆JDBC实现
 * <p>
 * 覆写带scopeId的默认方法实现复合桶隔离：按(scope_id, user_id)隔离记忆条目，
 * 检索时对query分词后按LIKE子串匹配并排序取TopN，删除带所有权校验，
 * scope不匹配抛SecurityException。
 * </p>
 * @author yangqiong
 */
public class JdbcLongTermMemory implements AgentLongTermMemory {

    /**
     * 长期记忆映射器
     */
    private final LongTermMemoryMapper mapper;

    /**
     * JSON映射器
     */
    private static final ObjectMapper MAPPER = HarnessObjectMapper.get();

    /**
     * 构造器
     * @param mapper
     */
    public JdbcLongTermMemory(LongTermMemoryMapper mapper) {
        this.mapper = mapper;
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
        LongTermMemoryEntity entity = new LongTermMemoryEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setScopeId(normalizeScope(scopeId));
        entity.setUserId(userId);
        entity.setSessionId(sessionId);
        entity.setContent(content);
        entity.setMetadata(metadataToJson(metadata));
        entity.setCreatedAt(System.currentTimeMillis());
        mapper.insert(entity);
    }

    @Override
    public List<String> search(String userId, String query, int limit) {
        return search(null, userId, query, limit);
    }

    @Override
    public List<String> search(String scopeId, String userId, String query, int limit) {
        if (query == null || query.isBlank()) {
            return Collections.emptyList();
        }
        List<String> tokens = tokenize(query);
        if (tokens.isEmpty()) {
            return Collections.emptyList();
        }
        List<LongTermMemoryEntity> entities = mapper.searchByTokens(normalizeScope(scopeId), userId, tokens);
        // 按命中词数排序并对空字节段做兜底排序
        List<Scored> scored = new ArrayList<>();
        for (LongTermMemoryEntity entity : entities) {
            int s = score(entity.getContent(), tokens);
            if (s > 0) {
                scored.add(new Scored(entity.getContent(), s));
            }
        }
        scored.sort(Comparator.comparingInt((Scored x) -> x.score).reversed());
        int topK = limit > 0 ? limit : scored.size();
        return scored.stream().limit(topK).map(x -> x.content).toList();
    }

    @Override
    public void delete(String memoryId) {
        if (memoryId == null) {
            return;
        }
        mapper.deleteById(memoryId);
    }

    @Override
    public void delete(String scopeId, String memoryId) {
        if (memoryId == null) {
            return;
        }
        // 所有权校验：记忆必须属于当前租户作用域才允许删除
        LongTermMemoryEntity entity = mapper.selectById(memoryId);
        if (entity != null) {
            if (!java.util.Objects.equals(entity.getScopeId(), normalizeScope(scopeId))) {
                throw new SecurityException("记忆删除所有权校验失败: memoryId=" + memoryId);
            }
            delete(memoryId);
        }
    }

    /**
     * 元数据转JSON
     * @param metadata
     * @return
     */
    private String metadataToJson(Map<String, Object> metadata) {
        if (metadata == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(metadata);
        } catch (Exception e) {
            throw new IllegalStateException("序列化记忆元数据失败", e);
        }
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
     * 计算记忆内容对查询词的命中数
     * @param content
     * @param tokens
     * @return
     */
    private int score(String content, List<String> tokens) {
        String text = content.toLowerCase();
        int hits = 0;
        for (String token : tokens) {
            if (text.contains(token)) {
                hits++;
            }
        }
        return hits;
    }

    /**
     * 空scope归一为""以适配非空列，与复合桶键语义一致
     * @param scopeId
     * @return
     */
    private String normalizeScope(String scopeId) {
        return scopeId != null ? scopeId : "";
    }

    /**
     * 带分值的检索命中
     */
    private static final class Scored {

        /**
         * 命中内容
         */
        final String content;

        /**
         * 命中分值
         */
        final int score;

        Scored(String content, int score) {
            this.content = content;
            this.score = score;
        }
    }
}
