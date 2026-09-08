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

import com.yangqiongai.agent.harness.store.vector.VectorIndex;
import com.yangqiongai.agent.harness.store.vector.VectorIndexContractTest;
import com.yangqiongai.agent.harness.store.vector.VectorMetric;

/**
 * qdrant向量索引契约测试
 * <p>
 * 依赖真实 qdrant 服务，通过环境变量注入服务地址，未配置时整类按假设跳过，
 * 日常 mvn test 自动跳过。
 * 环境变量：HARNESS_QDRANT_URI / HARNESS_QDRANT_API_KEY（可空）。
 * </p>
 * @author yangqiong
 */
class QdrantVectorIndexContractTest extends VectorIndexContractTest {

    @Override
    protected VectorIndex createIndex() {
        String uri = System.getenv("HARNESS_QDRANT_URI");
        org.junit.jupiter.api.Assumptions.assumeTrue(uri != null && !uri.isBlank(),
                "未配置HARNESS_QDRANT_URI，跳过qdrant契约测试");
        return new QdrantVectorIndex(uri, System.getenv("HARNESS_QDRANT_API_KEY"), VectorMetric.COSINE);
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
