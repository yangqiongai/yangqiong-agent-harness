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
package com.yangqiongai.agent.harness.store.vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 向量索引契约测试基类
 * <p>
 * 定义全部VectorIndex实现必须遵守的行为契约：upsert幂等、批量写入、三谓词过滤、
 * topK与排序、集合隔离、跨集合检索、删除、删集合、ensureCollection语义、维度校验
 * 与并发写读。各后端（Lucene/pgvector/Milvus/Qdrant）测试类继承本基类并仅提供索引
 * 实例即可跑同一套用例；受限后端通过能力开关关闭对应断言。
 * </p>
 * @author yangqiong
 */
public abstract class VectorIndexContractTest {

    /**
     * 并发写读测试的写入线程数
     */
    private static final int CONCURRENT_THREADS = 8;

    /**
     * 每线程写入条数
     */
    private static final int PER_THREAD_RECORDS = 25;

    /**
     * 被测向量索引实例
     */
    protected VectorIndex index;

    /**
     * 创建被测向量索引实例，每个测试方法独立实例
     * @return
     * @throws Exception
     */
    protected abstract VectorIndex createIndex() throws Exception;

    /**
     * 是否支持按id定位集合（collectionOf）
     * @return
     */
    protected boolean supportsCollectionDiscovery() {
        return true;
    }

    /**
     * 是否支持列举集合内id（listIds）
     * @return
     */
    protected boolean supportsIdListing() {
        return true;
    }

    /**
     * 是否强制集合内向量维度一致
     * @return
     */
    protected boolean enforcesUniformDimension() {
        return true;
    }

    @BeforeEach
    void setUpIndex() throws Exception {
        index = createIndex();
    }

    @AfterEach
    void tearDownIndex() {
        if (index != null) {
            index.close();
        }
    }

    @Test
    void upsertIsIdempotentById() {
        index.upsert("c1", record("id-1", vec(1, 0, 0, 0), "第一版内容", Map.of("k", "v")));
        index.upsert("c1", record("id-1", vec(0, 1, 0, 0), "第二版内容", Map.of("k", "v2")));

        assertThat(index.size()).isEqualTo(1);
        List<VectorSearchHit> hits = index.search("c1", vec(0, 1, 0, 0), 10, null);
        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).id()).isEqualTo("id-1");
        assertThat(hits.get(0).content()).isEqualTo("第二版内容");
        assertThat(hits.get(0).metadata()).containsEntry("k", "v2");
    }

    @Test
    void upsertBatchWritesAllRecords() {
        List<VectorRecord> records = List.of(
                record("b-1", vec(1, 0, 0, 0), "批量一", Map.of()),
                record("b-2", vec(0, 1, 0, 0), "批量二", Map.of()),
                record("b-3", vec(0, 0, 1, 0), "批量三", Map.of()));

        index.upsertBatch("c1", records);

        assertThat(index.size()).isEqualTo(3);
        assertThat(index.search("c1", vec(0, 1, 0, 0), 10, null)).hasSize(3);
    }

    @Test
    void searchIsolatesByCollection() {
        index.upsert("c1", record("x-1", vec(1, 0, 0, 0), "集合一", Map.of()));
        index.upsert("c2", record("x-2", vec(1, 0, 0, 0), "集合二", Map.of()));

        List<VectorSearchHit> hits = index.search("c1", vec(1, 0, 0, 0), 10, null);

        assertThat(hits).singleElement().satisfies(hit -> {
            assertThat(hit.id()).isEqualTo("x-1");
            assertThat(hit.content()).isEqualTo("集合一");
        });
    }

    @Test
    void searchAcrossAllCollectionsWhenCollectionNull() {
        index.upsert("c1", record("x-1", vec(1, 0, 0, 0), "集合一", Map.of()));
        index.upsert("c2", record("x-2", vec(1, 0, 0, 0), "集合二", Map.of()));

        List<VectorSearchHit> hits = index.search(null, vec(1, 0, 0, 0), 10, null);

        assertThat(hits).extracting(VectorSearchHit::id).containsExactlyInAnyOrder("x-1", "x-2");
    }

    @Test
    void searchFiltersByEqualityInAndRange() {
        index.upsert("c1", record("f-1", vec(1, 0, 0, 0), "计费系统故障", Map.of("env", "prod", "level", 1)));
        index.upsert("c1", record("f-2", vec(0.9f, 0.1f, 0, 0), "网关超时告警", Map.of("env", "dev", "level", 2)));
        index.upsert("c1", record("f-3", vec(0.8f, 0.2f, 0, 0), "数据库慢查询", Map.of("env", "prod", "level", 3)));

        List<VectorSearchHit> eqHits = index.search("c1", vec(1, 0, 0, 0), 10, MetadataFilter.eq("env", "prod"));
        assertThat(eqHits).extracting(VectorSearchHit::id).containsExactlyInAnyOrder("f-1", "f-3");

        List<VectorSearchHit> inHits =
                index.search("c1", vec(1, 0, 0, 0), 10, MetadataFilter.in("level", List.of(1, 2)));
        assertThat(inHits).extracting(VectorSearchHit::id).containsExactlyInAnyOrder("f-1", "f-2");

        List<VectorSearchHit> rangeHits = index.search("c1", vec(1, 0, 0, 0), 10, MetadataFilter.gte("level", 2));
        assertThat(rangeHits).extracting(VectorSearchHit::id).containsExactlyInAnyOrder("f-2", "f-3");

        List<VectorSearchHit> ltHits = index.search("c1", vec(1, 0, 0, 0), 10, MetadataFilter.lt("level", 2));
        assertThat(ltHits).extracting(VectorSearchHit::id).containsExactly("f-1");
    }

    @Test
    void searchRespectsTopKAndScoreOrder() {
        index.upsert("c1", record("t-1", vec(1, 0, 0, 0), "完全匹配", Map.of()));
        index.upsert("c1", record("t-2", vec(0, 1, 0, 0), "正交一", Map.of()));
        index.upsert("c1", record("t-3", vec(0, 0, 1, 0), "正交二", Map.of()));

        List<VectorSearchHit> hits = index.search("c1", vec(1, 0, 0, 0), 2, null);

        assertThat(hits).hasSize(2);
        assertThat(hits.get(0).id()).isEqualTo("t-1");
        assertThat(hits.get(0).score()).isGreaterThanOrEqualTo(hits.get(1).score());
    }

    @Test
    void deleteRemovesOnlyGivenIdsInCollection() {
        index.upsert("c1", record("d-1", vec(1, 0, 0, 0), "待删除", Map.of()));
        index.upsert("c1", record("d-2", vec(0, 1, 0, 0), "保留一", Map.of()));
        index.upsert("c2", record("d-1", vec(0, 0, 1, 0), "同名异集合", Map.of()));

        index.delete("c1", List.of("d-1"));

        // KNN返回topK近邻且不做相似度阈值截断，删除语义表现为已删除id不再出现
        assertThat(index.search("c1", vec(1, 0, 0, 0), 10, null))
                .extracting(VectorSearchHit::id)
                .doesNotContain("d-1");
        assertThat(index.search("c1", vec(0, 1, 0, 0), 10, null))
                .extracting(VectorSearchHit::id).containsExactly("d-2");
        assertThat(index.search("c2", vec(0, 0, 1, 0), 10, null))
                .extracting(VectorSearchHit::id).containsExactly("d-1");
        assertThat(index.size()).isEqualTo(2);
    }

    @Test
    void dropCollectionRemovesAllRecordsInCollection() {
        index.upsert("c1", record("r-1", vec(1, 0, 0, 0), "集合一记录一", Map.of()));
        index.upsert("c1", record("r-2", vec(0, 1, 0, 0), "集合一记录二", Map.of()));
        index.upsert("c2", record("r-3", vec(0, 0, 1, 0), "集合二记录", Map.of()));

        index.dropCollection("c1");

        assertThat(index.search("c1", vec(1, 0, 0, 0), 10, null)).isEmpty();
        assertThat(index.search(null, vec(0, 0, 1, 0), 10, null))
                .extracting(VectorSearchHit::id).containsExactly("r-3");
        assertThat(index.size()).isEqualTo(1);
    }

    @Test
    void ensureCollectionIsIdempotentAndSearchable() {
        index.ensureCollection("fresh", 4, VectorMetric.COSINE);
        assertThat(index.search("fresh", vec(1, 0, 0, 0), 10, null)).isEmpty();

        index.ensureCollection("fresh", 4, VectorMetric.COSINE);
        index.upsert("fresh", record("e-1", vec(1, 0, 0, 0), "预建后写入", Map.of()));

        assertThat(index.search("fresh", vec(1, 0, 0, 0), 10, null))
                .extracting(VectorSearchHit::id).containsExactly("e-1");
    }

    @Test
    void collectionOfReturnsOwningCollection() {
        Assumptions.assumeTrue(supportsCollectionDiscovery());

        index.upsert("c1", record("o-1", vec(1, 0, 0, 0), "归属集合一", Map.of()));

        assertThat(index.collectionOf("o-1")).isEqualTo("c1");
        assertThat(index.collectionOf("missing-id")).isNull();
    }

    @Test
    void listIdsReturnsIdsOfCollection() {
        Assumptions.assumeTrue(supportsIdListing());

        index.upsert("c1", record("l-1", vec(1, 0, 0, 0), "列举一", Map.of()));
        index.upsert("c1", record("l-2", vec(0, 1, 0, 0), "列举二", Map.of()));
        index.upsert("c2", record("l-3", vec(0, 0, 1, 0), "其他集合", Map.of()));

        assertThat(index.listIds("c1")).containsExactlyInAnyOrder("l-1", "l-2");
        assertThat(index.listIds("no-such-collection")).isEmpty();
    }

    @Test
    void upsertRejectsDimensionMismatch() {
        Assumptions.assumeTrue(enforcesUniformDimension());

        index.upsert("c1", record("dim-3", new float[] {1, 0, 0}, "三维向量", Map.of()));

        assertThatThrownBy(() -> index.upsert("c1", record("dim-4", vec(1, 0, 0, 0), "四维向量", Map.of())))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void concurrentUpsertsAndSearches() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(CONCURRENT_THREADS);
        CountDownLatch start = new CountDownLatch(1);
        List<Callable<Void>> tasks = new ArrayList<>();
        for (int t = 0; t < CONCURRENT_THREADS; t++) {
            final int worker = t;
            tasks.add(() -> {
                start.await();
                for (int i = 0; i < PER_THREAD_RECORDS; i++) {
                    index.upsert("concurrent",
                            record("p-" + worker + "-" + i, new float[] {worker + 1, i, 0, 0}, "并发写入", Map.of()));
                    if (i % 5 == 0) {
                        index.search("concurrent", new float[] {0, 0, 0, 1}, 5, null);
                    }
                }
                return null;
            });
        }
        try {
            // 先放行再提交，invokeAll会阻塞等待全部任务完成，顺序颠倒会死锁
            start.countDown();
            List<java.util.concurrent.Future<Void>> futures = pool.invokeAll(tasks);
            for (java.util.concurrent.Future<Void> future : futures) {
                future.get(60, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(index.size()).isEqualTo(CONCURRENT_THREADS * PER_THREAD_RECORDS);
        assertThat(index.search("concurrent", new float[] {0, 0, 0, 1}, CONCURRENT_THREADS * PER_THREAD_RECORDS, null))
                .hasSize(CONCURRENT_THREADS * PER_THREAD_RECORDS);
    }

    /**
     * 构建四维测试向量
     * @param a
     * @param b
     * @param c
     * @param d
     * @return
     */
    private static float[] vec(float a, float b, float c, float d) {
        return new float[] {a, b, c, d};
    }

    /**
     * 构建测试向量记录
     * @param id
     * @param vector
     * @param content
     * @param metadata
     * @return
     */
    private static VectorRecord record(String id, float[] vector, String content, Map<String, Object> metadata) {
        return new VectorRecord(id, vector, content, metadata);
    }
}
