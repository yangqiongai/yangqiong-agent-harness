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

import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Milvus向量索引提供方测试
 * <p>
 * 不依赖真实milvus服务，仅校验存储标识、配置解析与错误分支。
 * </p>
 * @author yangqiong
 */
class MilvusVectorIndexProviderTest {

    @Test
    void storeReturnsMilvusIdentifier() {
        assertThat(new MilvusVectorIndexProvider().store()).isEqualTo("milvus");
    }

    @Test
    void createRejectsMissingUri() {
        assertThatThrownBy(() -> new MilvusVectorIndexProvider().create(Map.of("metric", "COSINE")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createRejectsBlankUri() {
        assertThatThrownBy(() -> new MilvusVectorIndexProvider().create(Map.of("uri", "  ")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createRejectsUnknownMetric() {
        assertThatThrownBy(() -> new MilvusVectorIndexProvider()
                .create(Map.of("uri", "http://localhost:19530", "metric", "NOT_A_METRIC")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
