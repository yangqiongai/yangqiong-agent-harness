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
package com.yangqiongai.agent.harness.store.vector.pgvector;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

import javax.sql.DataSource;

import com.yangqiongai.agent.harness.durable.serialization.HarnessObjectMapper;
import com.yangqiongai.agent.harness.store.vector.MetadataFilter;
import com.yangqiongai.agent.harness.store.vector.VectorIndex;
import com.yangqiongai.agent.harness.store.vector.VectorMetric;
import com.yangqiongai.agent.harness.store.vector.VectorRecord;
import com.yangqiongai.agent.harness.store.vector.VectorSearchHit;

/**
 * pgvector向量索引
 * <p>
 * 纯 JDBC 实现的 VectorIndex：单表多集合（collection 列过滤），vector 列存储稠密向量，
 * metadata 落 JSONB；upsert 走 ON CONFLICT DO UPDATE 幂等，检索按 pgvector 距离操作符
 * 排序后以 MetadataFilter 内存兜底过滤。维度在首次建表时固定，元数据过滤不依赖驱动类，
 * 编译零驱动依赖，运行期由调用方提供 PostgreSQL 驱动；支持经 DataSource 复用外部连接池。
 * 向量写入不参与业务事务，失败以重试补偿。
 * </p>
 * @author yangqiong
 */
public class PgVectorIndex implements VectorIndex {

    /**
     * 合法表名模式，防注入
     */
    private static final Pattern TABLE_NAME = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*$");

    /**
     * 过滤检索的候选放大系数，与Lucene实现保持同一召回口径
     */
    private static final int CANDIDATE_MAGNIFIER = 20;

    /**
     * 过滤检索的候选基数下限
     */
    private static final int CANDIDATE_FLOOR = 64;

    /**
     * 数据源
     */
    private final DataSource dataSource;

    /**
     * jdbc地址，dataSource为null时按连接参数获取连接
     */
    private final String jdbcUrl;

    /**
     * 数据库用户
     */
    private final String user;

    /**
     * 数据库密码
     */
    private final String password;

    /**
     * 向量记录表名
     */
    private final String table;

    /**
     * 相似度度量，决定距离操作符与排序换算
     */
    private final VectorMetric metric;

    /**
     * 已建表标记，单进程内避免重复DDL
     */
    private volatile boolean tableReady;

    /**
     * 以连接参数构建pgvector索引
     * @param jdbcUrl jdbc地址
     * @param user 数据库用户
     * @param password 数据库密码
     * @param table 向量记录表名
     * @param metric 相似度度量
     */
    public PgVectorIndex(String jdbcUrl, String user, String password, String table, VectorMetric metric) {
        this(null, jdbcUrl, user, password, table, metric);
    }

    /**
     * 以外部数据源构建pgvector索引，复用既有连接池
     * @param dataSource 数据源
     * @param table 向量记录表名
     * @param metric 相似度度量
     */
    public PgVectorIndex(DataSource dataSource, String table, VectorMetric metric) {
        this(dataSource, null, null, null, table, metric);
    }

    private PgVectorIndex(DataSource dataSource, String jdbcUrl, String user,
                          String password, String table, VectorMetric metric) {
        Objects.requireNonNull(table, "表名不能为空");
        if (!TABLE_NAME.matcher(table).matches()) {
            throw new IllegalArgumentException("非法向量记录表名: " + table);
        }
        Objects.requireNonNull(metric, "相似度度量不能为空");
        this.dataSource = dataSource;
        this.jdbcUrl = jdbcUrl;
        this.user = user;
        this.password = password;
        this.table = table;
        this.metric = metric;
    }

    @Override
    public void upsert(String collection, VectorRecord record) {
        requireCollection(collection);
        ensureTable(record.vector().length);
        String vectorText = toVectorText(record.vector());
        String sql = "INSERT INTO " + table
                + " (collection, id, content, metadata, vector) VALUES (?, ?, ?, ?::jsonb, ?::vector) "
                + "ON CONFLICT (collection, id) DO UPDATE SET content = EXCLUDED.content, "
                + "metadata = EXCLUDED.metadata, vector = EXCLUDED.vector";
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, collection);
            statement.setString(2, record.id());
            statement.setString(3, record.content());
            statement.setString(4, record.metadata().isEmpty() ? null : toJson(record.metadata()));
            statement.setString(5, vectorText);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("向量记录写入失败: " + record.id(), e);
        }
    }

    @Override
    public void upsertBatch(String collection, List<VectorRecord> records) {
        requireCollection(collection);
        Objects.requireNonNull(records, "向量记录列表不能为空");
        if (records.isEmpty()) {
            return;
        }
        ensureTable(records.get(0).vector().length);
        String sql = "INSERT INTO " + table
                + " (collection, id, content, metadata, vector) VALUES (?, ?, ?, ?::jsonb, ?::vector) "
                + "ON CONFLICT (collection, id) DO UPDATE SET content = EXCLUDED.content, "
                + "metadata = EXCLUDED.metadata, vector = EXCLUDED.vector";
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (VectorRecord record : records) {
                statement.setString(1, collection);
                statement.setString(2, record.id());
                statement.setString(3, record.content());
                statement.setString(4, record.metadata().isEmpty() ? null : toJson(record.metadata()));
                statement.setString(5, toVectorText(record.vector()));
                statement.addBatch();
            }
            statement.executeBatch();
        } catch (SQLException e) {
            throw new IllegalStateException("向量记录批量写入失败", e);
        }
    }

    @Override
    public List<VectorSearchHit> search(String collection, float[] queryVector,
                                        int topK, MetadataFilter filter) {
        Objects.requireNonNull(queryVector, "查询向量不能为空");
        int effectiveTopK = topK > 0 ? topK : 10;
        if (!tableReady && !tableExists()) {
            return List.of();
        }
        int candidates = Math.max(effectiveTopK * CANDIDATE_MAGNIFIER, CANDIDATE_FLOOR);
        String vectorText = toVectorText(queryVector);
        // 集合为null时跨全部集合检索，过滤谓词统一内存兜底求值，保证各后端口径一致
        String where = collection == null ? "" : " WHERE collection = ?";
        String sql = "SELECT id, content, metadata::text, " + scoreExpression()
                + " AS score FROM " + table + where + " ORDER BY " + distanceExpression()
                + " LIMIT " + candidates;
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            if (collection != null) {
                statement.setString(index++, collection);
            }
            statement.setString(index, vectorText);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<VectorSearchHit> results = new ArrayList<>();
                while (resultSet.next() && results.size() < effectiveTopK) {
                    Map<String, Object> metadata = parseMetadata(resultSet.getString(3));
                    if (filter != null && !filter.isEmpty() && !filter.matches(metadata)) {
                        continue;
                    }
                    results.add(new VectorSearchHit(resultSet.getString(1),
                            resultSet.getFloat(4), resultSet.getString(2), metadata));
                }
                return results;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("向量检索失败", e);
        }
    }

    @Override
    public void delete(String collection, List<String> ids) {
        requireCollection(collection);
        Objects.requireNonNull(ids, "记录id列表不能为空");
        if (ids.isEmpty()) {
            return;
        }
        String sql = "DELETE FROM " + table + " WHERE collection = ? AND id = ?";
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (String id : ids) {
                statement.setString(1, collection);
                statement.setString(2, id);
                statement.addBatch();
            }
            statement.executeBatch();
        } catch (SQLException e) {
            throw new IllegalStateException("向量记录删除失败", e);
        }
    }

    @Override
    public void dropCollection(String collection) {
        requireCollection(collection);
        String sql = "DELETE FROM " + table + " WHERE collection = ?";
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, collection);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("向量集合删除失败: " + collection, e);
        }
    }

    @Override
    public void ensureCollection(String collection, int dimension, VectorMetric expected) {
        requireCollection(collection);
        Objects.requireNonNull(expected, "相似度度量不能为空");
        if (dimension <= 0) {
            throw new IllegalArgumentException("向量维度必须为正数: " + dimension);
        }
        if (expected != metric) {
            throw new UnsupportedOperationException(
                    "pgvector索引构建时已固定相似度度量: " + metric + "，不支持按集合切换为 " + expected);
        }
        ensureTable(dimension);
    }

    @Override
    public int size() {
        if (!tableReady && !tableExists()) {
            return 0;
        }
        String sql = "SELECT COUNT(*) FROM " + table;
        try (Connection connection = connection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            BigDecimal count = resultSet.getBigDecimal(1);
            return count == null ? 0 : count.intValue();
        } catch (SQLException e) {
            throw new IllegalStateException("向量记录计数失败", e);
        }
    }

    @Override
    public String collectionOf(String id) {
        Objects.requireNonNull(id, "记录id不能为空");
        if (!tableReady && !tableExists()) {
            return null;
        }
        String sql = "SELECT collection FROM " + table + " WHERE id = ? LIMIT 1";
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getString(1) : null;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("向量记录定位失败: " + id, e);
        }
    }

    @Override
    public List<String> listIds(String collection) {
        requireCollection(collection);
        if (!tableReady && !tableExists()) {
            return List.of();
        }
        String sql = "SELECT id FROM " + table + " WHERE collection = ?";
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, collection);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<String> ids = new ArrayList<>();
                while (resultSet.next()) {
                    ids.add(resultSet.getString(1));
                }
                return ids;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("向量记录列举失败: " + collection, e);
        }
    }

    /**
     * 关闭无底层长连接资源，兼容连接池场景不主动归还外部数据源
     */
    @Override
    public void close() {
    }

    /**
     * 确保记录表存在，维度在首次建表时固定
     * @param dimension 向量维度
     */
    private synchronized void ensureTable(int dimension) {
        if (tableReady) {
            return;
        }
        String createTable = "CREATE TABLE IF NOT EXISTS " + table + " ("
                + "collection text NOT NULL, "
                + "id text NOT NULL, "
                + "content text, "
                + "metadata jsonb, "
                + "vector vector(" + dimension + "), "
                + "PRIMARY KEY (collection, id))";
        String createIndex = "CREATE INDEX IF NOT EXISTS " + table + "_hnsw ON " + table
                + " USING hnsw (vector " + operatorClass() + ")";
        try (Connection connection = connection();
             Statement statement = connection.createStatement()) {
            statement.execute(createTable);
            try {
                statement.execute(createIndex);
            } catch (SQLException ignored) {
                // 老版本pgvector可能缺少hnsw索引类型，退化为顺序扫描
            }
            tableReady = true;
        } catch (SQLException e) {
            throw new IllegalStateException("向量记录表创建失败: " + table, e);
        }
    }

    /**
     * 判断记录表是否已存在，存在时置位就绪标记
     * @return
     */
    private boolean tableExists() {
        String sql = "SELECT 1 FROM " + table + " LIMIT 1";
        try (Connection connection = connection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            tableReady = true;
            return resultSet.next();
        } catch (SQLException e) {
            return false;
        }
    }

    /**
     * 获取jdbc连接，优先使用外部数据源
     * @return
     * @throws SQLException
     */
    private Connection connection() throws SQLException {
        if (dataSource != null) {
            return dataSource.getConnection();
        }
        return java.sql.DriverManager.getConnection(jdbcUrl, user, password);
    }

    /**
     * 构建距离排序表达式
     * @return
     */
    private String distanceExpression() {
        return switch (metric) {
            case COSINE -> "vector <=> ?";
            case DOT -> "vector <#> ?";
            case L2 -> "vector <-> ?";
        };
    }

    /**
     * 构建相似度分值表达式（越大越相似）
     * @return
     */
    private String scoreExpression() {
        return switch (metric) {
            case COSINE -> "1 - (vector <=> ?)";
            case DOT -> "-(vector <#> ?)";
            case L2 -> "1.0 / (1 + (vector <-> ?))";
        };
    }

    /**
     * 构建hnsw索引操作符类
     * @return
     */
    private String operatorClass() {
        return switch (metric) {
            case COSINE -> "vector_cosine_ops";
            case DOT -> "vector_ip_ops";
            case L2 -> "vector_l2_ops";
        };
    }

    /**
     * 向量序列化为pgvector文本字面量
     * @param vector
     * @return
     */
    private String toVectorText(float[] vector) {
        StringBuilder text = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                text.append(',');
            }
            text.append(vector[i]);
        }
        return text.append(']').toString();
    }

    /**
     * 元数据序列化为JSON字符串
     * @param metadata
     * @return
     */
    private String toJson(Map<String, Object> metadata) {
        try {
            return HarnessObjectMapper.get().writeValueAsString(metadata);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException("元数据序列化失败", e);
        }
    }

    /**
     * 解析元数据JSON字符串，为null或空时返回空映射
     * @param json
     * @return
     */
    private Map<String, Object> parseMetadata(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = HarnessObjectMapper.get().readValue(json, Map.class);
            return parsed == null ? Map.of() : parsed;
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException("元数据解析失败", e);
        }
    }

    /**
     * 校验集合名非空
     * @param collection
     */
    private void requireCollection(String collection) {
        if (collection == null || collection.isBlank()) {
            throw new IllegalArgumentException("集合名不能为空");
        }
    }
}
