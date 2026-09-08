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
package com.yangqiongai.agent.harness.store.vector.milvus;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.yangqiongai.agent.harness.durable.serialization.HarnessObjectMapper;
import com.yangqiongai.agent.harness.store.vector.MetadataFilter;
import com.yangqiongai.agent.harness.store.vector.VectorIndex;
import com.yangqiongai.agent.harness.store.vector.VectorMetric;
import com.yangqiongai.agent.harness.store.vector.VectorRecord;
import com.yangqiongai.agent.harness.store.vector.VectorSearchHit;

import io.milvus.client.MilvusServiceClient;
import io.milvus.common.clientenum.ConsistencyLevelEnum;
import io.milvus.grpc.DataType;
import io.milvus.grpc.GetCollectionStatisticsResponse;
import io.milvus.grpc.KeyValuePair;
import io.milvus.grpc.QueryResults;
import io.milvus.grpc.SearchResults;
import io.milvus.grpc.ShowCollectionsResponse;
import io.milvus.param.ConnectParam;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import io.milvus.param.R;
import io.milvus.param.collection.CreateCollectionParam;
import io.milvus.param.collection.DropCollectionParam;
import io.milvus.param.collection.FieldType;
import io.milvus.param.collection.GetCollectionStatisticsParam;
import io.milvus.param.collection.HasCollectionParam;
import io.milvus.param.collection.LoadCollectionParam;
import io.milvus.param.collection.ShowCollectionsParam;
import io.milvus.param.dml.DeleteParam;
import io.milvus.param.dml.QueryParam;
import io.milvus.param.dml.SearchParam;
import io.milvus.param.dml.UpsertParam;
import io.milvus.param.index.CreateIndexParam;
import io.milvus.response.QueryResultsWrapper;
import io.milvus.response.SearchResultsWrapper;

/**
 * Milvus向量索引
 * <p>
 * SPI集合一一映射为Milvus collection，schema固定为id/content/metadata/vector四字段，
 * 集合在首次写入时以首条记录向量维度懒创建并按构造度量建AUTOINDEX索引后加载；
 * upsert走SDK原生upsert通道按主键幂等，检索按构造度量请求后以MetadataFilter内存
 * 兜底过滤，L2距离换算为越大越相似的统一分值；相似度度量在构建时固定，跨集合
 * 检索经showCollections逐集合合并，size经flush后统计行数求和。
 * </p>
 * @author yangqiong
 */
public class MilvusVectorIndex implements VectorIndex {

    /**
     * 主键字段名
     */
    private static final String FIELD_ID = "id";

    /**
     * 内容字段名
     */
    private static final String FIELD_CONTENT = "content";

    /**
     * 元数据字段名
     */
    private static final String FIELD_METADATA = "metadata";

    /**
     * 向量字段名
     */
    private static final String FIELD_VECTOR = "vector";

    /**
     * VarChar字段最大长度上限
     */
    private static final int MAX_TEXT_LENGTH = 65535;

    /**
     * 过滤检索的候选放大系数，与Lucene/pgvector实现保持同一召回口径
     */
    private static final int CANDIDATE_MAGNIFIER = 20;

    /**
     * 过滤检索的候选基数下限
     */
    private static final int CANDIDATE_FLOOR = 64;

    /**
     * 集合统计中记录行数的键名
     */
    private static final String STATS_ROW_COUNT = "row_count";

    /**
     * 列举id的分页大小
     */
    private static final long LIST_PAGE_SIZE = 1000;

    /**
     * 列举id的分页总数上限，受Milvus查询分页上限约束
     */
    private static final long LIST_TOTAL_LIMIT = 16384;

    /**
     * milvus服务客户端
     */
    private final MilvusServiceClient client;

    /**
     * 相似度度量，决定索引与检索口径
     */
    private final VectorMetric metric;

    /**
     * gson实例，用于构建upsert行记录
     */
    private final Gson gson = new Gson();

    /**
     * 本进程已就绪集合及固定维度，避免重复DDL与维度校验
     */
    private final Map<String, Integer> readyDimensions = new ConcurrentHashMap<>();

    /**
     * 本进程已确认加载的集合，读路径避免重复load
     */
    private final Set<String> loadedCollections = ConcurrentHashMap.newKeySet();

    /**
     * 构建milvus向量索引
     * @param uri milvus服务地址（如http://localhost:19530）
     * @param token 认证令牌，可空
     * @param metric 相似度度量
     */
    public MilvusVectorIndex(String uri, String token, VectorMetric metric) {
        if (uri == null || uri.isBlank()) {
            throw new IllegalArgumentException("milvus连接地址不能为空");
        }
        Objects.requireNonNull(metric, "相似度度量不能为空");
        this.metric = metric;
        try {
            ConnectParam.Builder builder = ConnectParam.newBuilder().withUri(uri);
            if (token != null && !token.isBlank()) {
                builder.withToken(token);
            }
            this.client = new MilvusServiceClient(builder.build());
        } catch (Exception e) {
            throw new IllegalStateException("milvus连接初始化失败: " + uri, e);
        }
    }

    @Override
    public void upsert(String collection, VectorRecord record) {
        Objects.requireNonNull(record, "向量记录不能为空");
        upsertBatch(collection, List.of(record));
    }

    @Override
    public void upsertBatch(String collection, List<VectorRecord> records) {
        requireCollection(collection);
        Objects.requireNonNull(records, "向量记录列表不能为空");
        if (records.isEmpty()) {
            return;
        }
        ensureCollectionReady(collection, records.get(0).vector().length);
        List<JsonObject> rows = new ArrayList<>(records.size());
        for (VectorRecord record : records) {
            rows.add(toRow(record));
        }
        R<?> response = client.upsert(UpsertParam.newBuilder()
                .withCollectionName(collection)
                .withRows(rows)
                .build());
        requireOk(response, "向量记录写入失败: " + collection);
    }

    @Override
    public List<VectorSearchHit> search(String collection, float[] queryVector,
                                        int topK, MetadataFilter filter) {
        Objects.requireNonNull(queryVector, "查询向量不能为空");
        int effectiveTopK = topK > 0 ? topK : 10;
        List<String> targets = collection == null ? listCollections() : List.of(collection);
        List<VectorSearchHit> hits = new ArrayList<>();
        for (String target : targets) {
            if (!ensureLoaded(target)) {
                continue;
            }
            hits.addAll(searchOne(target, queryVector, effectiveTopK, filter));
        }
        // 跨集合合并后统一按相似度降序截断
        hits.sort((a, b) -> Float.compare(b.score(), a.score()));
        return hits.size() > effectiveTopK ? List.copyOf(hits.subList(0, effectiveTopK)) : hits;
    }

    @Override
    public void delete(String collection, List<String> ids) {
        requireCollection(collection);
        Objects.requireNonNull(ids, "记录id列表不能为空");
        if (ids.isEmpty() || !ensureLoaded(collection)) {
            return;
        }
        StringBuilder expr = new StringBuilder("id in [");
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) {
                expr.append(',');
            }
            expr.append('"').append(escapeExpr(ids.get(i))).append('"');
        }
        expr.append(']');
        R<?> response = client.delete(DeleteParam.newBuilder()
                .withCollectionName(collection)
                .withExpr(expr.toString())
                .build());
        requireOk(response, "向量记录删除失败: " + collection);
    }

    @Override
    public void dropCollection(String collection) {
        requireCollection(collection);
        R<Boolean> exists = client.hasCollection(HasCollectionParam.newBuilder()
                .withCollectionName(collection)
                .build());
        requireOk(exists, "集合存在性检查失败: " + collection);
        if (!Boolean.TRUE.equals(exists.getData())) {
            return;
        }
        R<?> response = client.dropCollection(DropCollectionParam.newBuilder()
                .withCollectionName(collection)
                .build());
        requireOk(response, "向量集合删除失败: " + collection);
        readyDimensions.remove(collection);
        loadedCollections.remove(collection);
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
                    "milvus索引构建时已固定相似度度量: " + metric + "，不支持按集合切换为 " + expected);
        }
        ensureCollectionReady(collection, dimension);
    }

    @Override
    public int size() {
        int total = 0;
        for (String collection : listCollections()) {
            // withFlush(true)内部先同步flush再统计，保证row_count反映已写入数据
            R<GetCollectionStatisticsResponse> response = client.getCollectionStatistics(
                    GetCollectionStatisticsParam.newBuilder()
                            .withCollectionName(collection)
                            .withFlush(Boolean.TRUE)
                            .build());
            requireOk(response, "向量记录计数失败: " + collection);
            for (KeyValuePair stat : response.getData().getStatsList()) {
                if (STATS_ROW_COUNT.equals(stat.getKey())) {
                    total += Integer.parseInt(stat.getValue());
                }
            }
        }
        return total;
    }

    @Override
    public String collectionOf(String id) {
        Objects.requireNonNull(id, "记录id不能为空");
        for (String collection : listCollections()) {
            if (!ensureLoaded(collection)) {
                continue;
            }
            R<QueryResults> response = client.query(QueryParam.newBuilder()
                    .withCollectionName(collection)
                    .withExpr("id == \"" + escapeExpr(id) + "\"")
                    .withOutFields(List.of(FIELD_ID))
                    .withLimit(1L)
                    .withConsistencyLevel(ConsistencyLevelEnum.STRONG)
                    .build());
            requireOk(response, "向量记录定位失败: " + id);
            if (!new QueryResultsWrapper(response.getData()).getRowRecords().isEmpty()) {
                return collection;
            }
        }
        return null;
    }

    @Override
    public List<String> listIds(String collection) {
        requireCollection(collection);
        if (!ensureLoaded(collection)) {
            return List.of();
        }
        List<String> ids = new ArrayList<>();
        long offset = 0;
        while (offset < LIST_TOTAL_LIMIT) {
            R<QueryResults> response = client.query(QueryParam.newBuilder()
                    .withCollectionName(collection)
                    .withExpr("id != \"\"")
                    .withOutFields(List.of(FIELD_ID))
                    .withOffset(offset)
                    .withLimit(LIST_PAGE_SIZE)
                    .withConsistencyLevel(ConsistencyLevelEnum.STRONG)
                    .build());
            requireOk(response, "向量记录列举失败: " + collection);
            List<QueryResultsWrapper.RowRecord> rows =
                    new QueryResultsWrapper(response.getData()).getRowRecords();
            for (QueryResultsWrapper.RowRecord row : rows) {
                Object value = row.get(FIELD_ID);
                ids.add(value == null ? null : String.valueOf(value));
            }
            if (rows.size() < LIST_PAGE_SIZE) {
                break;
            }
            offset += LIST_PAGE_SIZE;
        }
        return ids;
    }

    @Override
    public void close() {
        client.close();
    }

    /**
     * 确保集合存在且已加载：本进程已知则直接返回，否则按需创建或加载
     * @param collection 集合名
     * @param dimension 向量维度
     */
    private synchronized void ensureCollectionReady(String collection, int dimension) {
        Integer known = readyDimensions.get(collection);
        if (known != null) {
            if (known != dimension) {
                throw new IllegalStateException("向量维度与集合已固定维度不一致: 集合=" + collection
                        + ", 已固定维度=" + known + ", 请求维度=" + dimension);
            }
            return;
        }
        R<Boolean> exists = client.hasCollection(HasCollectionParam.newBuilder()
                .withCollectionName(collection)
                .build());
        requireOk(exists, "集合存在性检查失败: " + collection);
        if (Boolean.TRUE.equals(exists.getData())) {
            loadCollection(collection);
        } else {
            createCollection(collection, dimension);
        }
        readyDimensions.put(collection, dimension);
    }

    /**
     * 创建集合并按构造度量建立向量索引后加载
     * @param collection 集合名
     * @param dimension 向量维度
     */
    private void createCollection(String collection, int dimension) {
        CreateCollectionParam create = CreateCollectionParam.newBuilder()
                .withCollectionName(collection)
                .addFieldType(FieldType.newBuilder()
                        .withName(FIELD_ID)
                        .withDataType(DataType.VarChar)
                        .withPrimaryKey(true)
                        .withAutoID(false)
                        .withMaxLength(MAX_TEXT_LENGTH)
                        .build())
                .addFieldType(FieldType.newBuilder()
                        .withName(FIELD_CONTENT)
                        .withDataType(DataType.VarChar)
                        .withMaxLength(MAX_TEXT_LENGTH)
                        .build())
                .addFieldType(FieldType.newBuilder()
                        .withName(FIELD_METADATA)
                        .withDataType(DataType.JSON)
                        .build())
                .addFieldType(FieldType.newBuilder()
                        .withName(FIELD_VECTOR)
                        .withDataType(DataType.FloatVector)
                        .withDimension(dimension)
                        .build())
                .withConsistencyLevel(ConsistencyLevelEnum.STRONG)
                .build();
        requireOk(client.createCollection(create), "向量集合创建失败: " + collection);
        requireOk(client.createIndex(CreateIndexParam.newBuilder()
                .withCollectionName(collection)
                .withFieldName(FIELD_VECTOR)
                .withIndexType(IndexType.AUTOINDEX)
                .withMetricType(milvusMetric())
                .withSyncMode(Boolean.TRUE)
                .build()), "向量索引创建失败: " + collection);
        loadCollection(collection);
    }

    /**
     * 同步加载集合到查询节点，重复加载幂等
     * @param collection 集合名
     */
    private void loadCollection(String collection) {
        requireOk(client.loadCollection(LoadCollectionParam.newBuilder()
                .withCollectionName(collection)
                .withSyncLoad(Boolean.TRUE)
                .build()), "向量集合加载失败: " + collection);
    }

    /**
     * 确保集合已加载并返回是否存在，读路径与写路径共用监视器避免集合创建过程中检索报错
     * @param collection 集合名
     * @return
     */
    private boolean ensureLoaded(String collection) {
        if (readyDimensions.containsKey(collection) || loadedCollections.contains(collection)) {
            return true;
        }
        synchronized (this) {
            if (readyDimensions.containsKey(collection) || loadedCollections.contains(collection)) {
                return true;
            }
            R<Boolean> exists = client.hasCollection(HasCollectionParam.newBuilder()
                    .withCollectionName(collection)
                    .build());
            requireOk(exists, "集合存在性检查失败: " + collection);
            if (!Boolean.TRUE.equals(exists.getData())) {
                return false;
            }
            loadCollection(collection);
            loadedCollections.add(collection);
            return true;
        }
    }

    /**
     * 检索单个集合，过滤谓词统一内存兜底求值，保证各后端口径一致
     * @param collection 集合名
     * @param queryVector 查询向量
     * @param effectiveTopK 返回条数
     * @param filter 元数据过滤
     * @return
     */
    private List<VectorSearchHit> searchOne(String collection, float[] queryVector,
                                            int effectiveTopK, MetadataFilter filter) {
        int candidates = Math.max(effectiveTopK * CANDIDATE_MAGNIFIER, CANDIDATE_FLOOR);
        SearchParam param = SearchParam.newBuilder()
                .withCollectionName(collection)
                .withMetricType(milvusMetric())
                .withVectorFieldName(FIELD_VECTOR)
                .withVectors(List.of(toFloatList(queryVector)))
                .withTopK(candidates)
                .withOutFields(List.of(FIELD_CONTENT, FIELD_METADATA))
                .withConsistencyLevel(ConsistencyLevelEnum.STRONG)
                .build();
        R<SearchResults> response = client.search(param);
        requireOk(response, "向量检索失败: " + collection);
        SearchResultsWrapper wrapper = new SearchResultsWrapper(response.getData().getResults());
        List<SearchResultsWrapper.IDScore> scores = wrapper.getIDScore(0);
        List<VectorSearchHit> hits = new ArrayList<>(scores.size());
        for (SearchResultsWrapper.IDScore idScore : scores) {
            Map<String, Object> metadata = parseMetadata(idScore.getFieldValues().get(FIELD_METADATA));
            if (filter != null && !filter.isEmpty() && !filter.matches(metadata)) {
                continue;
            }
            Object content = idScore.getFieldValues().get(FIELD_CONTENT);
            hits.add(new VectorSearchHit(idScore.getStrID(), toSimilarityScore(idScore.getScore()),
                    content == null ? null : String.valueOf(content), metadata));
        }
        return hits;
    }

    /**
     * 获取服务端全部集合名
     * @return
     */
    private List<String> listCollections() {
        R<ShowCollectionsResponse> response = client.showCollections(
                ShowCollectionsParam.newBuilder().build());
        requireOk(response, "集合列表获取失败");
        return List.copyOf(response.getData().getCollectionNamesList());
    }

    /**
     * 向量记录转换为upsert行记录，content为null时归一为空串
     * @param record 向量记录
     * @return
     */
    private JsonObject toRow(VectorRecord record) {
        JsonObject row = new JsonObject();
        row.addProperty(FIELD_ID, record.id());
        row.addProperty(FIELD_CONTENT, record.content() == null ? "" : record.content());
        row.add(FIELD_METADATA, gson.toJsonTree(record.metadata()));
        JsonArray vector = new JsonArray();
        for (float value : record.vector()) {
            vector.add(value);
        }
        row.add(FIELD_VECTOR, vector);
        return row;
    }

    /**
     * 映射为milvus度量类型，DOT对应内积IP
     * @return
     */
    private MetricType milvusMetric() {
        return switch (metric) {
            case COSINE -> MetricType.COSINE;
            case DOT -> MetricType.IP;
            case L2 -> MetricType.L2;
        };
    }

    /**
     * 换算为越大越相似的统一分值口径，L2距离按1/(1+d)转换
     * @param score milvus原始分值
     * @return
     */
    private float toSimilarityScore(float score) {
        return metric == VectorMetric.L2 ? 1.0f / (1.0f + score) : score;
    }

    /**
     * float数组转换为检索参数的浮点列表
     * @param vector 向量
     * @return
     */
    private List<Float> toFloatList(float[] vector) {
        List<Float> values = new ArrayList<>(vector.length);
        for (float value : vector) {
            values.add(value);
        }
        return values;
    }

    /**
     * 解析检索结果中的元数据，milvus返回gson JsonObject，为null时归一为空映射
     * @param raw 元数据原始值
     * @return
     */
    private Map<String, Object> parseMetadata(Object raw) {
        if (raw == null) {
            return Map.of();
        }
        if (raw instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> existing = (Map<String, Object>) raw;
            return existing;
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = HarnessObjectMapper.get().readValue(String.valueOf(raw), Map.class);
            return parsed == null ? Map.of() : parsed;
        } catch (IOException e) {
            throw new UncheckedIOException("元数据解析失败", e);
        }
    }

    /**
     * 转义表达式内的字符串字面量，防止id中的引号破坏表达式
     * @param value 原始值
     * @return
     */
    private String escapeExpr(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /**
     * 校验milvus响应状态，失败时统一包装为IllegalStateException
     * @param response 响应
     * @param message 错误消息前缀
     */
    private void requireOk(R<?> response, String message) {
        if (response.getStatus() != R.Status.Success.getCode()) {
            throw new IllegalStateException(message + ": " + response.getMessage());
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
