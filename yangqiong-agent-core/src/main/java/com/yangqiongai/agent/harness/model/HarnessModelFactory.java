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
package com.yangqiongai.agent.harness.model;

import com.yangqiongai.agent.harness.core.model.AgentGenerateOptions;
import com.yangqiongai.agent.harness.core.model.AgentModel;
import com.yangqiongai.agent.harness.core.model.AgentModelFactory;
import com.yangqiongai.agent.harness.core.model.registry.AgentModelRegistry;
import com.yangqiongai.agent.harness.core.model.spi.AgentModelCreationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Harness模型工厂
 * <p>
 * 基于{@link AgentModelRegistry}解析模型ID创建模型实例，支持多provider协议适配。
 * 通过{@link HarnessModelProperties}提供全局默认配置，modelCode支持"provider:modelName"前缀格式。
 * </p>
 * @author yangqiong
 */
public class HarnessModelFactory implements AgentModelFactory {

    private static final Logger log = LoggerFactory.getLogger(HarnessModelFactory.class);

    /**
     * 模型配置属性
     */
    private final HarnessModelProperties properties;

    /**
     * 模型注册表
     */
    private final AgentModelRegistry registry;

    public HarnessModelFactory(HarnessModelProperties properties, AgentModelRegistry registry) {
        this.properties = properties;
        this.registry = registry;
    }

    /**
     * 按模型编码获取模型
     * <p>
     * modelCode支持"provider:modelName"前缀格式（如"openai:gpt-4o-mini"），
     * 无前缀则使用默认provider，整个modelCode作为modelName使用。
     * </p>
     * @param modelCode
     * @param options
     * @return
     */
    @Override
    public AgentModel getModel(String modelCode, AgentGenerateOptions options) {
        String effectiveModelCode = (modelCode != null && !modelCode.isBlank())
                ? modelCode : properties.getModelName();
        log.info("解析模型: modelCode={}, defaultProvider={}", effectiveModelCode, properties.getDefaultProvider());
        AgentModelCreationContext context = buildContextFromProperties();
        return registry.resolve(effectiveModelCode, context);
    }

    /**
     * 按完整配置获取模型
     * <p>
     * 使用指定的provider/apiKey/modelCode构建context，委托给registry解析。
     * </p>
     * @param provider
     * @param apiKey
     * @param modelCode
     * @param options
     * @return
     */
    @Override
    public AgentModel getModelByConfig(String provider, String apiKey, String modelCode,
                                         AgentGenerateOptions options) {
        log.info("按配置创建模型: provider={}, modelCode={}", provider, modelCode);
        AgentModelCreationContext context = AgentModelCreationContext.builder()
                .apiKey(apiKey != null ? apiKey : properties.resolveApiKey(provider))
                .baseUrl(properties.resolveBaseUrl(provider))
                .timeoutSeconds(properties.getTimeoutSeconds())
                .build();
        // 带前缀解析，确保provider正确路由
        String effectiveModelCode = (provider != null && !provider.isBlank() && !modelCode.startsWith(provider + ":"))
                ? provider + ":" + modelCode : modelCode;
        return registry.resolve(effectiveModelCode, context);
    }

    /**
     * 从properties构建默认context
     * @return
     */
    private AgentModelCreationContext buildContextFromProperties() {
        return AgentModelCreationContext.builder()
                .apiKey(properties.getApiKey())
                .baseUrl(properties.getBaseUrl())
                .timeoutSeconds(properties.getTimeoutSeconds())
                .build();
    }

    /**
     * 清除模型缓存
     */
    public void clearCache() {
        registry.clearCache();
    }
}
