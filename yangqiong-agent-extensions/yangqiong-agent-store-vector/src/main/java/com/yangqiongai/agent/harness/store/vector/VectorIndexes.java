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

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.stream.Collectors;

/**
 * 向量索引装配工厂
 * <p>
 * 经 ServiceLoader 发现全部 {@link VectorIndexProvider}，按 harness.vector.store
 * 标识匹配后以配置实例化；未匹配时列出全部可用存储类型，便于定位缺失的适配器依赖。
 * </p>
 * @author yangqiong
 */
public final class VectorIndexes {

    private VectorIndexes() {
    }

    /**
     * 按存储类型标识与配置创建向量索引
     * @param store 存储类型标识（lucene/pgvector/milvus/qdrant，忽略大小写）
     * @param config 归一化配置键值（不含 harness.vector 前缀），可为null
     * @return
     */
    public static VectorIndex create(String store, Map<String, String> config) {
        String normalized = store == null ? "" : store.trim().toLowerCase(Locale.ROOT);
        Map<String, String> safeConfig = config == null ? Map.of() : config;
        for (VectorIndexProvider provider : ServiceLoader.load(VectorIndexProvider.class)) {
            if (provider.store().equalsIgnoreCase(normalized)) {
                return provider.create(safeConfig);
            }
        }
        String available = ServiceLoader.load(VectorIndexProvider.class).stream()
                .map(ServiceLoader.Provider::get)
                .map(VectorIndexProvider::store)
                .sorted()
                .collect(Collectors.joining(", "));
        throw new IllegalArgumentException(
                "未找到向量存储类型: " + normalized + "，可用类型: " + available);
    }

    /**
     * 列出当前类路径下全部已注册的存储类型标识
     * @return
     */
    public static List<String> availableStores() {
        return ServiceLoader.load(VectorIndexProvider.class).stream()
                .map(ServiceLoader.Provider::get)
                .map(VectorIndexProvider::store)
                .sorted()
                .toList();
    }
}
