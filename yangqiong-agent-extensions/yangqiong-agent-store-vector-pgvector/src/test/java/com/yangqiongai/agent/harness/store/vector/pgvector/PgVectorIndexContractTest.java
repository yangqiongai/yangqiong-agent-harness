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

import java.util.Map;

import com.yangqiongai.agent.harness.store.vector.VectorIndex;
import com.yangqiongai.agent.harness.store.vector.VectorIndexContractTest;
import com.yangqiongai.agent.harness.store.vector.VectorMetric;

/**
 * pgvector向量索引契约测试
 * <p>
 * 依赖真实 pgvector 服务（H2不支持vector类型），通过环境变量注入连接信息，
 * 未配置时整类按假设跳过，日常 mvn test 自动跳过。
 * 环境变量：HARNESS_PGVECTOR_JDBC_URL / HARNESS_PGVECTOR_USER / HARNESS_PGVECTOR_PASSWORD。
 * </p>
 * @author yangqiong
 */
class PgVectorIndexContractTest extends VectorIndexContractTest {

    /**
     * 每个测试方法独立的表名，避免用例间数据串扰
     */
    private static final String TABLE = "harness_vector_contract_test";

    @Override
    protected VectorIndex createIndex() {
        String jdbcUrl = System.getenv("HARNESS_PGVECTOR_JDBC_URL");
        org.junit.jupiter.api.Assumptions.assumeTrue(jdbcUrl != null && !jdbcUrl.isBlank(),
                "未配置HARNESS_PGVECTOR_JDBC_URL，跳过pgvector契约测试");
        return new PgVectorIndex(jdbcUrl, System.getenv("HARNESS_PGVECTOR_USER"),
                System.getenv("HARNESS_PGVECTOR_PASSWORD"), TABLE, VectorMetric.COSINE);
    }

    @Override
    protected boolean supportsCollectionDiscovery() {
        return true;
    }

    @Override
    protected boolean supportsIdListing() {
        return true;
    }

    @Override
    protected boolean enforcesUniformDimension() {
        return true;
    }
}
