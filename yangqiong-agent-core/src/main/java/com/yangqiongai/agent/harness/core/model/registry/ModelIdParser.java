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
package com.yangqiongai.agent.harness.core.model.registry;

import java.util.Set;

/**
 * 模型ID解析器
 * <p>
 * 解析"provider:modelName"格式的模型ID，兼容无前缀格式（使用默认provider）。
 * </p>
 * @author yangqiong
 */
public final class ModelIdParser {

    /**
     * 已知provider集合
     */
    private static final Set<String> KNOWN_PROVIDERS = Set.of("openai", "dashscope", "ollama", "anthropic");

    /**
     * 默认provider
     */
    private static final String DEFAULT_PROVIDER = "openai";

    private ModelIdParser() {
    }

    /**
     * 解析模型ID
     * @param modelId
     * @param defaultProvider
     * @return
     */
    public static ParsedModelId parse(String modelId, String defaultProvider) {
        if (modelId == null || modelId.isBlank()) {
            throw new IllegalArgumentException("模型 ID 不能为空");
        }
        String effectiveDefault = defaultProvider != null && !defaultProvider.isBlank()
                ? defaultProvider : DEFAULT_PROVIDER;
        int colonIndex = modelId.indexOf(':');
        if (colonIndex <= 0) {
            // 无冒号或前缀为空：使用默认provider
            String modelName = colonIndex == 0 ? modelId.substring(1) : modelId;
            if (modelName.isBlank()) {
                throw new IllegalArgumentException("模型名称不能为空");
            }
            return new ParsedModelId(effectiveDefault, modelName);
        }
        String providerPrefix = modelId.substring(0, colonIndex);
        if (!KNOWN_PROVIDERS.contains(providerPrefix)) {
            // 前缀非已知provider：整个modelId作为modelName，使用默认provider
            return new ParsedModelId(effectiveDefault, modelId);
        }
        String modelName = modelId.substring(colonIndex + 1);
        if (modelName.isBlank()) {
            throw new IllegalArgumentException("模型名称不能为空");
        }
        return new ParsedModelId(providerPrefix, modelName);
    }

    /**
     * 解析结果
     */
    public record ParsedModelId(String provider, String modelName) {
    }
}
