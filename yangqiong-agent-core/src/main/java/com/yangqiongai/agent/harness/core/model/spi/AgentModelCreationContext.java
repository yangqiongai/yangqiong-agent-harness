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
package com.yangqiongai.agent.harness.core.model.spi;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 模型创建上下文
 * @author yangqiong
 */
public final class AgentModelCreationContext {

    /**
     * API密钥
     */
    private final String apiKey;

    /**
     * API基础地址
     */
    private final String baseUrl;

    /**
     * 端点路径
     */
    private final String endpointPath;

    /**
     * 是否流式
     */
    private final Boolean stream;

    /**
     * 是否启用思考模式
     */
    private final Boolean enableThinking;

    /**
     * 是否启用搜索增强
     */
    private final Boolean enableSearch;

    /**
     * 请求超时秒数
     */
    private final Integer timeoutSeconds;

    /**
     * 扩展选项（provider特有）
     */
    private final Map<String, Object> options;

    /**
     * 组件容器（注入额外依赖）
     */
    private final Map<Class<?>, Object> components;

    /**
     * 缓存标识
     */
    private final String cacheId;

    /**
     * 缓存策略
     */
    private final CachePolicy cachePolicy;

    private AgentModelCreationContext(Builder b) {
        this.apiKey = b.apiKey;
        this.baseUrl = b.baseUrl;
        this.endpointPath = b.endpointPath;
        this.stream = b.stream;
        this.enableThinking = b.enableThinking;
        this.enableSearch = b.enableSearch;
        this.timeoutSeconds = b.timeoutSeconds;
        this.options = b.options != null ? Collections.unmodifiableMap(new LinkedHashMap<>(b.options)) : Collections.emptyMap();
        this.components = b.components != null ? Collections.unmodifiableMap(new LinkedHashMap<>(b.components)) : Collections.emptyMap();
        this.cacheId = b.cacheId;
        this.cachePolicy = b.cachePolicy != null ? b.cachePolicy : CachePolicy.DEFAULT;
    }

    /**
     * 获取API密钥
     * @return
     */
    public String getApiKey() {
        return apiKey;
    }

    /**
     * 获取基础地址
     * @return
     */
    public String getBaseUrl() {
        return baseUrl;
    }

    /**
     * 获取端点路径
     * @return
     */
    public String getEndpointPath() {
        return endpointPath;
    }

    /**
     * 是否流式
     * @return
     */
    public Boolean getStream() {
        return stream;
    }

    /**
     * 是否启用思考模式
     * @return
     */
    public Boolean getEnableThinking() {
        return enableThinking;
    }

    /**
     * 是否启用搜索增强
     * @return
     */
    public Boolean getEnableSearch() {
        return enableSearch;
    }

    /**
     * 获取超时秒数
     * @return
     */
    public Integer getTimeoutSeconds() {
        return timeoutSeconds;
    }

    /**
     * 获取扩展选项
     * @return
     */
    public Map<String, Object> getOptions() {
        return options;
    }

    /**
     * 获取扩展选项值
     * @param key
     * @param clazz
     * @param <T>
     * @return
     */
    public <T> T getOption(String key, Class<T> clazz) {
        Object value = options.get(key);
        return clazz.isInstance(value) ? clazz.cast(value) : null;
    }

    /**
     * 获取组件
     * @param clazz
     * @param <T>
     * @return
     */
    @SuppressWarnings("unchecked")
    public <T> T getComponent(Class<T> clazz) {
        return (T) components.get(clazz);
    }

    /**
     * 获取缓存标识
     * @return
     */
    public String getCacheId() {
        return cacheId;
    }

    /**
     * 获取缓存策略
     * @return
     */
    public CachePolicy getCachePolicy() {
        return cachePolicy;
    }

    /**
     * 派生新Builder
     * @return
     */
    public Builder toBuilder() {
        Builder builder = new Builder();
        builder.apiKey = this.apiKey;
        builder.baseUrl = this.baseUrl;
        builder.endpointPath = this.endpointPath;
        builder.stream = this.stream;
        builder.enableThinking = this.enableThinking;
        builder.enableSearch = this.enableSearch;
        builder.timeoutSeconds = this.timeoutSeconds;
        if (this.options != null && !this.options.isEmpty()) {
            builder.options = new LinkedHashMap<>(this.options);
        }
        if (this.components != null && !this.components.isEmpty()) {
            builder.components = new LinkedHashMap<>(this.components);
        }
        builder.cacheId = this.cacheId;
        builder.cachePolicy = this.cachePolicy;
        return builder;
    }

    /**
     * 创建空Builder
     * @return
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 缓存策略
     */
    public enum CachePolicy {
        /**
         * 默认策略（由Registry决定）
         */
        DEFAULT,

        /**
         * 启用缓存
         */
        ENABLED,

        /**
         * 禁用缓存
         */
        DISABLED
    }

    /**
     * 上下文构建器
     * @author yangqiong
     */
    public static class Builder {

        private String apiKey;
        private String baseUrl;
        private String endpointPath;
        private Boolean stream;
        private Boolean enableThinking;
        private Boolean enableSearch;
        private Integer timeoutSeconds;
        private Map<String, Object> options;
        private Map<Class<?>, Object> components;
        private String cacheId;
        private CachePolicy cachePolicy;

        /**
         * 设置API密钥
         * @param apiKey
         * @return
         */
        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        /**
         * 设置基础地址
         * @param baseUrl
         * @return
         */
        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        /**
         * 设置端点路径
         * @param endpointPath
         * @return
         */
        public Builder endpointPath(String endpointPath) {
            this.endpointPath = endpointPath;
            return this;
        }

        /**
         * 设置是否流式
         * @param stream
         * @return
         */
        public Builder stream(Boolean stream) {
            this.stream = stream;
            return this;
        }

        /**
         * 设置是否启用思考模式
         * @param enableThinking
         * @return
         */
        public Builder enableThinking(Boolean enableThinking) {
            this.enableThinking = enableThinking;
            return this;
        }

        /**
         * 设置是否启用搜索增强
         * @param enableSearch
         * @return
         */
        public Builder enableSearch(Boolean enableSearch) {
            this.enableSearch = enableSearch;
            return this;
        }

        /**
         * 设置超时秒数
         * @param timeoutSeconds
         * @return
         */
        public Builder timeoutSeconds(Integer timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
            return this;
        }

        /**
         * 设置扩展选项
         * @param options
         * @return
         */
        public Builder options(Map<String, Object> options) {
            this.options = options;
            return this;
        }

        /**
         * 添加扩展选项
         * @param key
         * @param value
         * @return
         */
        public Builder addOption(String key, Object value) {
            if (this.options == null) {
                this.options = new LinkedHashMap<>();
            }
            this.options.put(key, value);
            return this;
        }

        /**
         * 设置组件容器
         * @param components
         * @return
         */
        public Builder components(Map<Class<?>, Object> components) {
            this.components = components;
            return this;
        }

        /**
         * 添加组件
         * @param clazz
         * @param instance
         * @param <T>
         * @return
         */
        public <T> Builder addComponent(Class<T> clazz, T instance) {
            if (this.components == null) {
                this.components = new LinkedHashMap<>();
            }
            this.components.put(clazz, instance);
            return this;
        }

        /**
         * 设置缓存标识
         * @param cacheId
         * @return
         */
        public Builder cacheId(String cacheId) {
            this.cacheId = cacheId;
            return this;
        }

        /**
         * 设置缓存策略
         * @param cachePolicy
         * @return
         */
        public Builder cachePolicy(CachePolicy cachePolicy) {
            this.cachePolicy = cachePolicy;
            return this;
        }

        /**
         * 构建上下文
         * @return
         */
        public AgentModelCreationContext build() {
            return new AgentModelCreationContext(this);
        }
    }
}
