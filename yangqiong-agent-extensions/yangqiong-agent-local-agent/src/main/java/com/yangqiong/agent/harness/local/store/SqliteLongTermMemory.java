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
package com.yangqiong.agent.harness.local.store;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangqiong.agent.harness.core.memory.AgentLongTermMemory;
import com.yangqiong.agent.harness.durable.serialization.HarnessObjectMapper;

/**
 * SQLite长期记忆
 * <p>
 * 按 (scope_id, user_id) 复合桶隔离记忆条目，检索采用包含匹配简化实现：
 * 对query分词后按内容命中词数排序取TopN；删除带所有权校验，
 * scope不匹配抛出SecurityException。
 * </p>
 * @author yangqiong
 */
public class SqliteLongTermMemory implements AgentLongTermMemory {

    /**
     * JSON映射器
     */
    private static final ObjectMapper MAPPER = HarnessObjectMapper.get();

    /**
     * 插入记忆条目
     */
    private static final String SQL_INSERT = """
            INSERT INTO harness_long_term_memory (id, scope_id, user_id, session_id, content, metadata, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)""";

    /**
     * 按复合桶列出全部记忆
     */
    private static final String SQL_FIND_BY_BUCKET = """
            SELECT content, created_at FROM harness_long_term_memory
            WHERE scope_id = ? AND user_id = ?""";

    /**
     * 按记忆ID查询所属租户
     */
    private static final String SQL_FIND_SCOPE_BY_ID = """
            SELECT scope_id FROM harness_long_term_memory
            WHERE id = ?""";

    /**
     * 按记忆ID删除
     */
    private static final String SQL_DELETE_BY_ID = "DELETE FROM harness_long_term_memory WHERE id = ?";

    /**
     * 共享数据库连接
     */
    private final Connection connection;

    /**
     * 构造器
     * @param connection
     */
    public SqliteLongTermMemory(Connection connection) {
        this.connection = Objects.requireNonNull(connection, "connection不能为空");
    }

    /**
     * 存储记忆
     * @param userId
     * @param sessionId
     * @param content
     * @param metadata
     * @return
     */
    @Override
    public void store(String userId, String sessionId, String content, Map<String, Object> metadata) {
        store(null, userId, sessionId, content, metadata);
    }

    /**
     * 按租户作用域存储记忆
     * @param scopeId
     * @param userId
     * @param sessionId
     * @param content
     * @param metadata
     */
    @Override
    public void store(String scopeId, String userId, String sessionId,
                      String content, Map<String, Object> metadata) {
        if (content == null || content.isBlank()) {
            return;
        }
        synchronized (connection) {
            SqliteSupport.update(connection, SQL_INSERT, statement -> {
                statement.setString(1, UUID.randomUUID().toString());
                statement.setString(2, normalize(scopeId));
                statement.setString(3, normalize(userId));
                statement.setString(4, sessionId);
                statement.setString(5, content);
                statement.setString(6, metadataToJson(metadata));
                statement.setLong(7, System.currentTimeMillis());
            });
        }
    }

    /**
     * 检索记忆
     * @param userId
     * @param query
     * @param limit
     * @return
     */
    @Override
    public List<String> search(String userId, String query, int limit) {
        return search(null, userId, query, limit);
    }

    /**
     * 按租户作用域检索记忆
     * @param scopeId
     * @param userId
     * @param query
     * @param limit
     * @return
     */
    @Override
    public List<String> search(String scopeId, String userId, String query, int limit) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        List<String> tokens = tokenize(query);
        if (tokens.isEmpty()) {
            return List.of();
        }
        synchronized (connection) {
            List<Scored> scored = SqliteSupport.query(connection, SQL_FIND_BY_BUCKET, statement -> {
                statement.setString(1, normalize(scopeId));
                statement.setString(2, normalize(userId));
            }, resultSet -> new Scored(
                    resultSet.getString("content"),
                    score(resultSet.getString("content"), tokens),
                    resultSet.getLong("created_at")));
            // 包含匹配简化检索：按命中词数降序，同分按创建时间倒序兜底
            return scored.stream()
                    .filter(item -> item.score > 0)
                    .sorted(Comparator.comparingInt((Scored item) -> item.score).reversed()
                            .thenComparing(Comparator.comparingLong((Scored item) -> item.createdAt).reversed()))
                    .limit(limit > 0 ? limit : Long.MAX_VALUE)
                    .map(item -> item.content)
                    .toList();
        }
    }

    /**
     * 删除记忆
     * @param memoryId
     * @return
     */
    @Override
    public void delete(String memoryId) {
        if (memoryId == null) {
            return;
        }
        synchronized (connection) {
            SqliteSupport.update(connection, SQL_DELETE_BY_ID,
                    statement -> statement.setString(1, memoryId));
        }
    }

    /**
     * 按租户作用域删除记忆
     * @param scopeId
     * @param memoryId
     */
    @Override
    public void delete(String scopeId, String memoryId) {
        if (memoryId == null) {
            return;
        }
        synchronized (connection) {
            String ownerScope = SqliteSupport.queryFirst(connection, SQL_FIND_SCOPE_BY_ID,
                    statement -> statement.setString(1, memoryId),
                    resultSet -> resultSet.getString("scope_id"));
            if (ownerScope == null) {
                return;
            }
            // 所有权校验：记忆必须属于当前租户作用域才允许删除
            if (!ownerScope.equals(normalize(scopeId))) {
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
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < query.length(); i++) {
            char c = query.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                builder.append(Character.toLowerCase(c));
            } else if (builder.length() > 0) {
                tokens.add(builder.toString());
                builder.setLength(0);
            }
        }
        if (builder.length() > 0) {
            tokens.add(builder.toString());
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
     * 空值归一为空串以适配非空列，与复合桶键语义一致
     * @param value
     * @return
     */
    private String normalize(String value) {
        return value != null ? value : "";
    }

    /**
     * 带分值的检索命中
     * @author yangqiong
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

        /**
         * 创建时间戳
         */
        final long createdAt;

        Scored(String content, int score, long createdAt) {
            this.content = content;
            this.score = score;
            this.createdAt = createdAt;
        }
    }
}
