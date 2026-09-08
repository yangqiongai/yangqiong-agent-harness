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
package com.yangqiongai.agent.harness.store.vector.qdrant;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

import com.google.common.util.concurrent.ListenableFuture;
import com.yangqiongai.agent.harness.durable.serialization.HarnessObjectMapper;
import com.yangqiongai.agent.harness.store.vector.MetadataFilter;
import com.yangqiongai.agent.harness.store.vector.VectorIndex;
import com.yangqiongai.agent.harness.store.vector.VectorMetric;
import com.yangqiongai.agent.harness.store.vector.VectorRecord;
import com.yangqiongai.agent.harness.store.vector.VectorSearchHit;

import io.qdrant.client.ConditionFactory;
import io.qdrant.client.PointIdFactory;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import io.qdrant.client.ValueFactory;
import io.qdrant.client.VectorsFactory;
import io.qdrant.client.WithPayloadSelectorFactory;
import io.qdrant.client.grpc.Collections;
import io.qdrant.client.grpc.Common;
import io.qdrant.client.grpc.JsonWithInt;
import io.qdrant.client.grpc.Points;

/**
 * qdrant向量索引
 * <p>
 * SPI collection 一一映射为 qdrant collection，集合在首次写入时以记录向量维度懒创建，
 * 相似度度量在索引构建时固定。qdrant点id仅接受UUID或无符号整数，以原始id的UTF-8字节
 * 经 nameUUIDFromBytes 派生点id实现同id覆盖幂等，原始id存payload的refId字段供检索还原。
 * metadata落JSON字符串统一由HarnessObjectMapper序列化，元数据过滤一律内存兜底求值，
 * 与pgvector实现保持同一检索口径。
 * </p>
 * @author yangqiong
 */
public class QdrantVectorIndex implements VectorIndex {

    /**
     * 过滤检索的候选放大系数，与Lucene/pgvector实现保持同一召回口径
     */
    private static final int CANDIDATE_MAGNIFIER = 20;

    /**
     * 过滤检索的候选基数下限
     */
    private static final int CANDIDATE_FLOOR = 64;

    /**
     * 原始记录id在payload中的字段名
     */
    private static final String REF_ID_FIELD = "refId";

    /**
     * 内容文本在payload中的字段名
     */
    private static final String CONTENT_FIELD = "content";

    /**
     * 元数据JSON在payload中的字段名
     */
    private static final String METADATA_FIELD = "metadata";

    /**
     * scroll列举id的单页大小
     */
    private static final int SCROLL_PAGE_SIZE = 256;

    /**
     * qdrant服务缺省端口
     */
    private static final int DEFAULT_PORT = 6334;

    /**
     * qdrant客户端
     */
    private final QdrantClient client;

    /**
     * 相似度度量，建集合时固定
     */
    private final VectorMetric metric;

    /**
     * 已就绪集合标记，避免重复建集合请求
     */
    private final Set<String> readyCollections = ConcurrentHashMap.newKeySet();

    /**
     * 集合就绪检查锁
     */
    private final Object readyLock = new Object();

    /**
     * 以服务地址构建qdrant向量索引
     * @param uri 服务地址，形如 http://host:6334 或 host:6334，缺省端口6334，https走TLS
     * @param apiKey 鉴权密钥，可空
     * @param metric 相似度度量
     */
    public QdrantVectorIndex(String uri, String apiKey, VectorMetric metric) {
        Objects.requireNonNull(uri, "服务地址不能为空");
        Objects.requireNonNull(metric, "相似度度量不能为空");
        Endpoint endpoint = parseEndpoint(uri);
        QdrantGrpcClient.Builder builder =
                QdrantGrpcClient.newBuilder(endpoint.host(), endpoint.port(), endpoint.tls());
        if (apiKey != null && !apiKey.isBlank()) {
            builder.withApiKey(apiKey);
        }
        this.client = new QdrantClient(builder.build());
        this.metric = metric;
    }

    @Override
    public void upsert(String collection, VectorRecord record) {
        requireCollection(collection);
        Objects.requireNonNull(record, "向量记录不能为空");
        ensureCollectionReady(collection, record.vector().length);
        await(client.upsertAsync(collection, List.of(pointStruct(record))));
    }

    @Override
    public void upsertBatch(String collection, List<VectorRecord> records) {
        requireCollection(collection);
        Objects.requireNonNull(records, "向量记录列表不能为空");
        if (records.isEmpty()) {
            return;
        }
        ensureCollectionReady(collection, records.get(0).vector().length);
        List<Points.PointStruct> points = new ArrayList<>(records.size());
        for (VectorRecord record : records) {
            points.add(pointStruct(record));
        }
        // 批量记录合并为一次upsert提交
        await(client.upsertAsync(collection, points));
    }

    @Override
    public List<VectorSearchHit> search(String collection, float[] queryVector,
                                        int topK, MetadataFilter filter) {
        Objects.requireNonNull(queryVector, "查询向量不能为空");
        int effectiveTopK = topK > 0 ? topK : 10;
        List<String> targets = collection == null ? listCollectionNames() : resolveSingleTarget(collection);
        if (targets.isEmpty()) {
            return List.of();
        }
        int candidates = Math.max(effectiveTopK * CANDIDATE_MAGNIFIER, CANDIDATE_FLOOR);
        List<Float> vector = toFloatList(queryVector);
        List<Points.ScoredPoint> merged = new ArrayList<>();
        for (String name : targets) {
            Points.SearchPoints request = Points.SearchPoints.newBuilder()
                    .setCollectionName(name)
                    .addAllVector(vector)
                    .setLimit(candidates)
                    .setWithPayload(WithPayloadSelectorFactory.enable(true))
                    .build();
            merged.addAll(await(client.searchAsync(request)));
        }
        merged.sort(Comparator.comparingDouble(Points.ScoredPoint::getScore).reversed());
        List<VectorSearchHit> hits = new ArrayList<>(Math.min(effectiveTopK, merged.size()));
        for (Points.ScoredPoint point : merged) {
            if (hits.size() >= effectiveTopK) {
                break;
            }
            // 元数据过滤统一内存兜底求值，保证各后端口径一致
            Map<String, Object> metadata = parseMetadata(payloadText(point.getPayloadMap(), METADATA_FIELD));
            if (filter != null && !filter.isEmpty() && !filter.matches(metadata)) {
                continue;
            }
            hits.add(new VectorSearchHit(payloadText(point.getPayloadMap(), REF_ID_FIELD),
                    toSimilarity(point.getScore()),
                    payloadText(point.getPayloadMap(), CONTENT_FIELD), metadata));
        }
        return hits;
    }

    @Override
    public void delete(String collection, List<String> ids) {
        requireCollection(collection);
        Objects.requireNonNull(ids, "记录id列表不能为空");
        if (ids.isEmpty()) {
            return;
        }
        if (!await(client.collectionExistsAsync(collection))) {
            return;
        }
        List<Common.PointId> pointIds = new ArrayList<>(ids.size());
        for (String id : ids) {
            pointIds.add(PointIdFactory.id(derivePointId(id)));
        }
        await(client.deleteAsync(collection, pointIds));
    }

    @Override
    public void dropCollection(String collection) {
        requireCollection(collection);
        readyCollections.remove(collection);
        if (!await(client.collectionExistsAsync(collection))) {
            return;
        }
        await(client.deleteCollectionAsync(collection));
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
                    "qdrant索引构建时已固定相似度度量: " + metric + "，不支持按集合切换为 " + expected);
        }
        ensureCollectionReady(collection, dimension);
    }

    @Override
    public int size() {
        long total = 0;
        for (String name : listCollectionNames()) {
            total += await(client.countAsync(name, null, true));
        }
        return Math.toIntExact(total);
    }

    @Override
    public String collectionOf(String id) {
        Objects.requireNonNull(id, "记录id不能为空");
        for (String name : listCollectionNames()) {
            Common.Filter filter = Common.Filter.newBuilder()
                    .addMust(ConditionFactory.matchKeyword(REF_ID_FIELD, id))
                    .build();
            Points.ScrollPoints request = Points.ScrollPoints.newBuilder()
                    .setCollectionName(name)
                    .setFilter(filter)
                    .setLimit(1)
                    .setWithPayload(WithPayloadSelectorFactory.enable(true))
                    .build();
            if (!await(client.scrollAsync(request)).getResultList().isEmpty()) {
                return name;
            }
        }
        return null;
    }

    @Override
    public List<String> listIds(String collection) {
        requireCollection(collection);
        if (!await(client.collectionExistsAsync(collection))) {
            return List.of();
        }
        List<String> ids = new ArrayList<>();
        Common.PointId offset = null;
        do {
            Points.ScrollPoints.Builder builder = Points.ScrollPoints.newBuilder()
                    .setCollectionName(collection)
                    .setLimit(SCROLL_PAGE_SIZE)
                    .setWithPayload(WithPayloadSelectorFactory.enable(true));
            if (offset != null) {
                builder.setOffset(offset);
            }
            Points.ScrollResponse response = await(client.scrollAsync(builder.build()));
            for (Points.RetrievedPoint point : response.getResultList()) {
                String refId = payloadText(point.getPayloadMap(), REF_ID_FIELD);
                if (refId != null) {
                    ids.add(refId);
                }
            }
            offset = response.hasNextPageOffset() ? response.getNextPageOffset() : null;
        } while (offset != null);
        return ids;
    }

    /**
     * 关闭qdrant客户端并释放底层连接
     */
    @Override
    public void close() {
        client.close();
    }

    /**
     * 解析服务地址为主机端口与TLS标记
     * @param uri 服务地址
     * @return
     */
    private static Endpoint parseEndpoint(String uri) {
        String rest = uri.trim();
        boolean tls = false;
        String lower = rest.toLowerCase(Locale.ROOT);
        if (lower.startsWith("https://")) {
            tls = true;
            rest = rest.substring("https://".length());
        } else if (lower.startsWith("http://")) {
            rest = rest.substring("http://".length());
        }
        int slash = rest.indexOf('/');
        if (slash >= 0) {
            rest = rest.substring(0, slash);
        }
        String host = rest;
        int port = DEFAULT_PORT;
        int colon = rest.lastIndexOf(':');
        if (colon >= 0) {
            host = rest.substring(0, colon);
            try {
                port = Integer.parseInt(rest.substring(colon + 1));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("qdrant服务地址端口非法: " + uri, e);
            }
        }
        if (host.isBlank()) {
            throw new IllegalArgumentException("qdrant服务地址非法: " + uri);
        }
        return new Endpoint(host, port, tls);
    }

    /**
     * 确保集合就绪，首次访问时以给定维度懒创建
     * @param collection 集合名
     * @param dimension 向量维度
     */
    private void ensureCollectionReady(String collection, int dimension) {
        if (readyCollections.contains(collection)) {
            return;
        }
        synchronized (readyLock) {
            if (readyCollections.contains(collection)) {
                return;
            }
            if (!await(client.collectionExistsAsync(collection))) {
                Collections.VectorParams params = Collections.VectorParams.newBuilder()
                        .setSize(dimension)
                        .setDistance(distance())
                        .build();
                await(client.createCollectionAsync(collection, params));
            }
            readyCollections.add(collection);
        }
    }

    /**
     * 解析单集合检索目标，集合不存在时返回空列表
     * @param collection 集合名
     * @return
     */
    private List<String> resolveSingleTarget(String collection) {
        return await(client.collectionExistsAsync(collection)) ? List.of(collection) : List.of();
    }

    /**
     * 构建qdrant点结构，原始id存payload的refId字段
     * @param record 向量记录
     * @return
     */
    private Points.PointStruct pointStruct(VectorRecord record) {
        Map<String, JsonWithInt.Value> payload = new HashMap<>();
        payload.put(REF_ID_FIELD, ValueFactory.value(record.id()));
        if (record.content() != null) {
            payload.put(CONTENT_FIELD, ValueFactory.value(record.content()));
        }
        if (!record.metadata().isEmpty()) {
            payload.put(METADATA_FIELD, ValueFactory.value(toJson(record.metadata())));
        }
        return Points.PointStruct.newBuilder()
                .setId(PointIdFactory.id(derivePointId(record.id())))
                .setVectors(VectorsFactory.vectors(record.vector()))
                .putAllPayload(payload)
                .build();
    }

    /**
     * 以原始id的UTF-8字节派生qdrant点id，同名id派生结果一致天然幂等
     * @param id 原始记录id
     * @return
     */
    private static UUID derivePointId(String id) {
        return UUID.nameUUIDFromBytes(id.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 映射相似度度量为qdrant距离枚举
     * @return
     */
    private Collections.Distance distance() {
        return switch (metric) {
            case COSINE -> Collections.Distance.Cosine;
            case DOT -> Collections.Distance.Dot;
            case L2 -> Collections.Distance.Euclid;
        };
    }

    /**
     * 换算相似度分值，L2距离反转为越大越相似
     * @param score qdrant原始分值
     * @return
     */
    private float toSimilarity(float score) {
        return metric == VectorMetric.L2 ? 1.0f / (1.0f + score) : score;
    }

    /**
     * 列举服务端全部集合名
     * @return
     */
    private List<String> listCollectionNames() {
        return List.copyOf(await(client.listCollectionsAsync()));
    }

    /**
     * 向量转换为浮点列表
     * @param vector
     * @return
     */
    private static List<Float> toFloatList(float[] vector) {
        List<Float> values = new ArrayList<>(vector.length);
        for (float value : vector) {
            values.add(value);
        }
        return values;
    }

    /**
     * 读取payload字符串字段
     * @param payload
     * @param field
     * @return
     */
    private static String payloadText(Map<String, JsonWithInt.Value> payload, String field) {
        JsonWithInt.Value value = payload.get(field);
        return value == null ? null : value.getStringValue();
    }

    /**
     * 阻塞获取ListenableFuture结果并解包底层异常
     * @param future
     * @return
     */
    private <T> T await(ListenableFuture<T> future) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("向量服务调用被中断", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("向量服务调用失败: " + e.getCause().getMessage(), e.getCause());
        }
    }

    /**
     * 元数据序列化为JSON字符串
     * @param metadata
     * @return
     */
    private String toJson(Map<String, Object> metadata) {
        try {
            return HarnessObjectMapper.get().writeValueAsString(metadata);
        } catch (IOException e) {
            throw new UncheckedIOException("元数据序列化失败", e);
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
        } catch (IOException e) {
            throw new UncheckedIOException("元数据解析失败", e);
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

    /**
     * 服务地址解析结果
     * @param host 主机
     * @param port 端口
     * @param tls 是否启用TLS
     */
    private record Endpoint(String host, int port, boolean tls) {
    }
}
