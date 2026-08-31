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
package com.yangqiong.agent.harness.core.model.registry;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.yangqiong.agent.harness.core.model.AgentModel;
import com.yangqiong.agent.harness.core.model.spi.AgentModelCreationContext;
import com.yangqiong.agent.harness.core.model.spi.AgentModelProvider;

/**
 * 模型注册表
 * <p>
 * 中心化模型管理，解析模型ID并创建/缓存模型实例。
 * 解析顺序：缓存 → 已注册provider
 * </p>
 * @author yangqiong
 */
public class AgentModelRegistry {

    private static final Logger log = LoggerFactory.getLogger(AgentModelRegistry.class);

    /**
     * 已注册provider集合
     */
    private final Map<String, AgentModelProvider> providers = new ConcurrentHashMap<>();

    /**
     * 模型缓存
     */
    private final ConcurrentHashMap<String, AgentModel> cache = new ConcurrentHashMap<>();

    /**
     * 默认provider
     */
    private final String defaultProvider;

    /**
     * 构造注册表，默认使用openai provider
     */
    public AgentModelRegistry() {
        this(null);
    }

    /**
     * 构造注册表
     * @param defaultProvider
     */
    public AgentModelRegistry(String defaultProvider) {
        this.defaultProvider = defaultProvider != null && !defaultProvider.isBlank()
                ? defaultProvider : "openai";
    }

    /**
     * 创建启用SPI自动发现的注册表
     * <p>
     * 通过JDK ServiceLoader发现classpath上所有{@link AgentModelProvider}实现并注册，
     * 便于模型提供者以独立模块按需引入（如yangqiong-agent-model-providers）。
     * </p>
     * @param defaultProvider
     * @return
     */
    public static AgentModelRegistry withAutoDiscovery(String defaultProvider) {
        AgentModelRegistry registry = new AgentModelRegistry(defaultProvider);
        int count = 0;
        for (AgentModelProvider provider : java.util.ServiceLoader.load(AgentModelProvider.class)) {
            registry.registerProvider(provider);
            count++;
        }
        log.info("SPI自动发现模型 provider 完成: count={}", count);
        return registry;
    }

    /**
     * 注册provider
     * @param provider
     */
    public void registerProvider(AgentModelProvider provider) {
        if (provider == null || provider.providerId() == null) {
            return;
        }
        providers.put(provider.providerId(), provider);
        log.info("已注册模型 provider: {}", provider.providerId());
    }

    /**
     * 批量注册provider
     * @param providerList
     */
    public void registerProviders(List<AgentModelProvider> providerList) {
        if (providerList == null) {
            return;
        }
        for (AgentModelProvider provider : providerList) {
            registerProvider(provider);
        }
    }

    /**
     * 解析模型ID创建模型实例（无context，使用空context）
     * @param modelId
     * @return
     */
    public AgentModel resolve(String modelId) {
        return resolve(modelId, AgentModelCreationContext.builder().build());
    }

    /**
     * 解析模型ID创建模型实例
     * <p>
     * 1. 解析provider前缀
     * 2. 缓存检查
     * 3. 查找对应provider
     * 4. 调用provider.create()
     * </p>
     * @param modelId
     * @param context
     * @return
     */
    public AgentModel resolve(String modelId, AgentModelCreationContext context) {
        ModelIdParser.ParsedModelId parsed = ModelIdParser.parse(modelId, defaultProvider);
        String cacheKey = buildCacheKey(parsed, context);
        // 缓存策略判断
        AgentModelCreationContext.CachePolicy policy = context.getCachePolicy();
        if (policy != AgentModelCreationContext.CachePolicy.DISABLED) {
            AgentModel cached = cache.get(cacheKey);
            if (cached != null) {
                log.debug("命中模型缓存: {}", cacheKey);
                return cached;
            }
        }
        // 查找provider
        AgentModelProvider provider = providers.get(parsed.provider());
        if (provider == null) {
            throw new IllegalArgumentException("未注册的模型 provider: " + parsed.provider()
                    + "，已注册: " + providers.keySet());
        }
        if (!provider.supports(parsed.provider(), parsed.modelName())) {
            throw new IllegalArgumentException("provider [" + parsed.provider()
                    + "] 不支持模型: " + parsed.modelName());
        }
        // 创建并缓存
        AgentModel model = provider.create(parsed.provider(), parsed.modelName(), context);
        if (model == null) {
            throw new IllegalStateException("provider [" + parsed.provider()
                    + "] 创建模型返回null: " + parsed.modelName());
        }
        if (policy != AgentModelCreationContext.CachePolicy.DISABLED) {
            cache.put(cacheKey, model);
            log.debug("已缓存模型: {}", cacheKey);
        }
        return model;
    }

    /**
     * 清除缓存
     */
    public void clearCache() {
        cache.clear();
        log.info("已清除模型缓存");
    }

    /**
     * 获取已注册provider列表
     * @return
     */
    public List<String> getRegisteredProviders() {
        return List.copyOf(providers.keySet());
    }

    /**
     * 构建缓存键
     * @param parsed
     * @param context
     * @return
     */
    private String buildCacheKey(ModelIdParser.ParsedModelId parsed, AgentModelCreationContext context) {
        // 优先使用context的cacheId
        if (context.getCacheId() != null && !context.getCacheId().isBlank()) {
            return parsed.provider() + ":" + parsed.modelName() + "@" + context.getCacheId();
        }
        // 否则基于关键字段派生
        StringBuilder sb = new StringBuilder();
        sb.append(parsed.provider()).append(':').append(parsed.modelName());
        sb.append('@').append(Objects.hash(
                context.getApiKey(),
                context.getBaseUrl(),
                context.getEndpointPath(),
                context.getStream(),
                context.getEnableThinking(),
                context.getEnableSearch(),
                context.getTimeoutSeconds(),
                context.getOptions()
        ));
        return sb.toString();
    }
}
