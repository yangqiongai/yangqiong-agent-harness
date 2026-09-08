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

import java.util.Map;

/**
 * 向量索引提供方
 * <p>
 * 各向量库适配器经 ServiceLoader 注册本接口，由 {@link VectorIndexes} 按存储类型
 * 标识匹配并按配置实例化，与 core 既有 HarnessDiscovery 模式一致。
 * </p>
 * @author yangqiong
 */
public interface VectorIndexProvider {

    /**
     * 存储类型标识，与配置 harness.vector.store 对应（如 lucene/pgvector/milvus/qdrant）
     * @return
     */
    String store();

    /**
     * 按配置创建向量索引实例
     * @param config 归一化配置键值（不含 harness.vector 前缀），常见键：
     *               uri/token/api-key/jdbcUrl/user/password/table/dataDir/dimension/metric/collection
     * @return
     */
    VectorIndex create(Map<String, String> config);
}
