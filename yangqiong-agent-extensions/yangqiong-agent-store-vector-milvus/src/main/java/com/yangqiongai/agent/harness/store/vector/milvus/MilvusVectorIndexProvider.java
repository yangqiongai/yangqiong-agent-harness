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

import java.util.Locale;
import java.util.Map;

import com.yangqiongai.agent.harness.store.vector.VectorIndex;
import com.yangqiongai.agent.harness.store.vector.VectorIndexProvider;
import com.yangqiongai.agent.harness.store.vector.VectorMetric;

/**
 * Milvus向量索引提供方
 * <p>
 * 配置键：uri/token/metric，uri必填，token可空，metric缺省cosine。
 * </p>
 * @author yangqiong
 */
public class MilvusVectorIndexProvider implements VectorIndexProvider {

    @Override
    public String store() {
        return "milvus";
    }

    @Override
    public VectorIndex create(Map<String, String> config) {
        String uri = config.get("uri");
        if (uri == null || uri.isBlank()) {
            throw new IllegalArgumentException("milvus向量索引缺少配置: uri");
        }
        return new MilvusVectorIndex(uri, config.get("token"), metric(config));
    }

    /**
     * 解析相似度度量配置
     * @param config
     * @return
     */
    private VectorMetric metric(Map<String, String> config) {
        String metric = config.get("metric");
        if (metric == null || metric.isBlank()) {
            return VectorMetric.COSINE;
        }
        return VectorMetric.valueOf(metric.trim().toUpperCase(Locale.ROOT));
    }
}
