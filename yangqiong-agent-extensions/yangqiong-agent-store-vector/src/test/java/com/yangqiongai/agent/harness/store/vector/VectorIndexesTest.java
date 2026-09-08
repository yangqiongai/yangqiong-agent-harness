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

import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 向量索引装配工厂测试
 * @author yangqiong
 */
class VectorIndexesTest {

    /**
     * 测试数据目录
     */
    @TempDir
    Path dataDir;

    @Test
    void createLuceneIndexByServiceLoader() {
        try (VectorIndex index = VectorIndexes.create("Lucene",
                Map.of("dataDir", dataDir.resolve("spi").toString()))) {
            assertThat(index).isInstanceOf(LuceneVectorIndex.class);
            index.upsert("c1", new VectorRecord("s-1", new float[] {1, 0, 0, 0}, "SPI装配", Map.of()));
            assertThat(index.search("c1", new float[] {1, 0, 0, 0}, 5, null)).hasSize(1);
        }
    }

    @Test
    void availableStoresContainsBuiltInLucene() {
        assertThat(VectorIndexes.availableStores()).contains("lucene");
    }

    @Test
    void createUnknownStoreFailsWithAvailableTypes() {
        assertThatThrownBy(() -> VectorIndexes.create("no-such-store", Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("lucene");
    }

    @Test
    void createLuceneWithoutDataDirFails() {
        assertThatThrownBy(() -> VectorIndexes.create("lucene", Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dataDir");
    }
}
