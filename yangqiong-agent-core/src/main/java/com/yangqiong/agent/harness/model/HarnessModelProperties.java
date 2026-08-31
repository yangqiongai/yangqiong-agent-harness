/*
 * Copyright 2026 yangqiongtech.com
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
package com.yangqiong.agent.harness.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Harness模型配置
 * <p>
 * 通过application.yml配置大模型连接参数，使harness模块自满足，
 * </p>
 * @author yangqiong
 */
public class HarnessModelProperties {

    /**
     * 是否启用harness内置模型连接
     */
    private boolean enabled = true;

    /**
     * 默认提供商（无前缀modelCode时使用），支持openai/dashscope/ollama/anthropic
     */
    private String defaultProvider = "openai";

    /**
     * 默认API密钥（兼容旧配置，可作为providers未配置时的全局默认）
     */
    private String apiKey;

    /**
     * 默认API基础地址（兼容旧配置，可作为providers未配置时的全局默认）
     */
    private String baseUrl = "https://api.openai.com/v1";

    /**
     * 默认模型名称
     */
    private String modelName = "gpt-4o-mini";

    /**
     * 默认温度
     */
    private Double temperature = 0.7;

    /**
     * 默认最大Token数
     */
    private Integer maxTokens = 4096;

    /**
     * 请求超时（秒）
     */
    private int timeoutSeconds = 60;

    /**
     * 各provider独立配置（支持多provider并存）
     */
    private Map<String, ProviderConfig> providers = new LinkedHashMap<>();

    /**
     * DashScope兼容模式基础地址（旧配置兼容）
     */
    private static final String DASHSCOPE_COMPATIBLE_BASE = "https://dashscope.aliyuncs.com/compatible-mode/v1";

    /**
     * Ollama默认基础地址（旧配置兼容）
     */
    private static final String OLLAMA_DEFAULT_BASE = "http://localhost:11434/v1";

    /**
     * DashScope原生协议默认基础地址
     */
    private static final String DASHSCOPE_NATIVE_BASE = "https://dashscope.aliyuncs.com";

    /**
     * Anthropic默认基础地址
     */
    private static final String ANTHROPIC_DEFAULT_BASE = "https://api.anthropic.com";

    /**
     * Gemini OpenAI兼容模式默认基础地址
     */
    private static final String GEMINI_DEFAULT_BASE = "https://generativelanguage.googleapis.com/v1beta/openai";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 获取默认提供商
     * <p>
     * 优先返回defaultProvider，为空时返回"openai"
     * </p>
     * @return
     */
    public String getDefaultProvider() {
        return defaultProvider != null && !defaultProvider.isBlank()
                ? defaultProvider : "openai";
    }

    public void setDefaultProvider(String defaultProvider) {
        this.defaultProvider = defaultProvider;
    }

    /**
     * 旧版本兼容：获取provider字段（等价于defaultProvider）
     * @return
     */
    public String getProvider() {
        return getDefaultProvider();
    }

    /**
     * 旧版本兼容：设置provider字段
     * @param provider
     */
    public void setProvider(String provider) {
        this.defaultProvider = provider;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    /**
     * 按模型编码同时设置提供商与模型名称
     * <p>
     * modelCode支持"provider:model"格式（如openai:gpt-4o-mini），
     * 冒号前为提供商，冒号后为模型名称；无冒号时仅设置模型名称，提供商保持不变。
     * </p>
     * @param modelCode
     */
    public void setModelCode(String modelCode) {
        if (modelCode == null || modelCode.isBlank()) {
            return;
        }
        int idx = modelCode.indexOf(':');
        if (idx > 0) {
            this.defaultProvider = modelCode.substring(0, idx);
            this.modelName = modelCode.substring(idx + 1);
        } else {
            this.modelName = modelCode;
        }
    }

    public Double getTemperature() {
        return temperature;
    }

    public void setTemperature(Double temperature) {
        this.temperature = temperature;
    }

    public Integer getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(Integer maxTokens) {
        this.maxTokens = maxTokens;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public Map<String, ProviderConfig> getProviders() {
        return providers;
    }

    public void setProviders(Map<String, ProviderConfig> providers) {
        this.providers = providers != null ? providers : new LinkedHashMap<>();
    }

    /**
     * 获取指定provider的配置
     * @param providerName
     * @return
     */
    public ProviderConfig getProviderConfig(String providerName) {
        if (providerName == null) {
            return null;
        }
        return providers.get(providerName);
    }

    /**
     * 根据提供商获取实际基础地址
     * <p>
     * 优先级：providers[provider].baseUrl > 顶层baseUrl > provider默认地址
     * </p>
     * @param provider
     * @return
     */
    public String resolveBaseUrl(String provider) {
        // 优先使用providers配置中的baseUrl
        ProviderConfig config = getProviderConfig(provider);
        if (config != null && config.getBaseUrl() != null && !config.getBaseUrl().isBlank()) {
            return config.getBaseUrl();
        }
        // 兼容顶层baseUrl配置
        if (baseUrl != null && !baseUrl.isBlank()) {
            return baseUrl;
        }
        // provider默认地址
        if (provider == null) {
            return "https://api.openai.com/v1";
        }
        return switch (provider.toLowerCase()) {
            case "dashscope" -> DASHSCOPE_COMPATIBLE_BASE;
            case "ollama" -> OLLAMA_DEFAULT_BASE;
            case "anthropic" -> ANTHROPIC_DEFAULT_BASE;
            case "gemini" -> GEMINI_DEFAULT_BASE;
            default -> "https://api.openai.com/v1";
        };
    }

    /**
     * 根据提供商获取API密钥
     * <p>
     * 优先级：providers[provider].apiKey > 顶层apiKey
     * </p>
     * @param provider
     * @return
     */
    public String resolveApiKey(String provider) {
        ProviderConfig config = getProviderConfig(provider);
        if (config != null && config.getApiKey() != null && !config.getApiKey().isBlank()) {
            return config.getApiKey();
        }
        return apiKey;
    }

    /**
     * 判断指定provider是否启用
     * @param providerName
     * @return
     */
    public boolean isProviderEnabled(String providerName) {
        ProviderConfig config = getProviderConfig(providerName);
        if (config != null) {
            return config.isEnabled();
        }
        return true;
    }

    /**
     * Provider独立配置
     * @author yangqiong
     */
    public static class ProviderConfig {

        /**
         * 是否启用此provider
         */
        private boolean enabled = true;

        /**
         * API密钥
         */
        private String apiKey;

        /**
         * API基础地址
         */
        private String baseUrl;

        /**
         * provider特有选项
         */
        private Map<String, Object> options = new LinkedHashMap<>();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public Map<String, Object> getOptions() {
            return options;
        }

        public void setOptions(Map<String, Object> options) {
            this.options = options != null ? options : new LinkedHashMap<>();
        }
    }
}
