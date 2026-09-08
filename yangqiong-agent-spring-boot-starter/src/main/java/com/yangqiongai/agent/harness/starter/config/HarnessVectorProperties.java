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
package com.yangqiongai.agent.harness.starter.config;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 向量存储配置
 * <p>
 * 前缀 ai.harness.vector，store 对应向量存储类型标识（lucene/pgvector/milvus/qdrant），
 * config 为归一化配置键值（不含 ai.harness.vector 前缀，如 dataDir/uri/token/api-key/jdbcUrl），
 * 键值经{@link com.yangqiongai.agent.harness.store.vector.VectorIndexes}分发给对应适配器。
 * </p>
 * @author yangqiong
 */
@ConfigurationProperties(prefix = "ai.harness.vector")
public class HarnessVectorProperties {

    /**
     * 是否启用向量存储自动装配（类路径需引入store-vector或任一适配器模块）
     */
    private boolean enabled = false;

    /**
     * 存储类型标识
     */
    private String store = "lucene";

    /**
     * 适配器配置键值
     */
    private Map<String, String> config = new LinkedHashMap<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getStore() {
        return store;
    }

    public void setStore(String store) {
        this.store = store;
    }

    public Map<String, String> getConfig() {
        return config;
    }

    public void setConfig(Map<String, String> config) {
        this.config = config;
    }
}
