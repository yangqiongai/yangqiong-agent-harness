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
package com.yangqiong.agent.harness.spi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.ServiceLoader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JDK SPI扩展发现器
 * <p>
 * 通过ServiceLoader扫描classpath下各SPI提供者（{@link MiddlewareProvider}、
 * {@link AgentToolProvider}、{@link DefaultModelProvider}、{@link StoreProvider}、
 * {@link GuardrailDetectorProvider}），聚合为{@link DiscoveredExtensions}。
 * 纯 Java SE 场景下，扩展模块只需提供jar + 写META-INF/services即可被识别。
 * </p>
 * @author yangqiong
 */
public final class HarnessDiscovery {

    private static final Logger log = LoggerFactory.getLogger(HarnessDiscovery.class);

    private HarnessDiscovery() {
    }

    /**
     * 使用默认类加载器发现全部扩展
     * @return
     */
    public static DiscoveredExtensions discover() {
        return discover(HarnessDiscovery.class.getClassLoader());
    }

    /**
     * 使用指定类加载器发现全部扩展
     * @param classLoader
     * @return
     */
    public static DiscoveredExtensions discover(ClassLoader classLoader) {
        List<MiddlewareProvider> middlewareProviders = load(MiddlewareProvider.class, classLoader);
        List<AgentToolProvider> toolProviders = load(AgentToolProvider.class, classLoader);
        List<DefaultModelProvider> modelProviders = load(DefaultModelProvider.class, classLoader);
        List<StoreProvider> storeProviders = load(StoreProvider.class, classLoader);
        List<GuardrailDetectorProvider> detectorProviders = load(GuardrailDetectorProvider.class, classLoader);

        List<com.yangqiong.agent.harness.core.middleware.AgentMiddleware> middlewares =
                new ArrayList<>();
        List<com.yangqiong.agent.harness.core.tool.AgentTool> tools = new ArrayList<>();
        List<com.yangqiong.agent.harness.core.model.AgentModel> models = new ArrayList<>();
        List<com.yangqiong.agent.harness.durable.DistributedStores> stores = new ArrayList<>();
        List<com.yangqiong.agent.harness.guardrail.InjectionDetector> detectors = new ArrayList<>();

        middlewareProviders.stream().sorted(Comparator.comparingInt(MiddlewareProvider::order))
                .forEach(p -> collect(middlewares, p.provideMiddlewares()));
        toolProviders.stream().sorted(Comparator.comparingInt(AgentToolProvider::order))
                .forEach(p -> collect(tools, p.provideTools()));
        modelProviders.stream().sorted(Comparator.comparingInt(DefaultModelProvider::order))
                .forEach(p -> collect(models, p.provideModels()));
        storeProviders.stream().sorted(Comparator.comparingInt(StoreProvider::order))
                .forEach(p -> {
                    com.yangqiong.agent.harness.durable.DistributedStores s = p.provideStores();
                    if (s != null) {
                        stores.add(s);
                    }
                });
        detectorProviders.stream().sorted(Comparator.comparingInt(GuardrailDetectorProvider::order))
                .forEach(p -> collect(detectors, p.provideDetectors()));

        log.debug("SPI扩展发现完成: middleware={}, tool={}, model={}, store={}, detector={}",
                middlewares.size(), tools.size(), models.size(), stores.size(), detectors.size());
        return new DiscoveredExtensions(middlewares, tools, models, stores, detectors);
    }

    /**
     * 通过ServiceLoader加载指定SPI类型的所有实现
     * @param type
     * @param classLoader
     * @return
     */
    private static <T> List<T> load(Class<T> type, ClassLoader classLoader) {
        try {
            List<T> result = new ArrayList<>();
            ServiceLoader.load(type, classLoader).forEach(result::add);
            return result;
        } catch (Throwable t) {
            log.warn("SPI扩展加载失败: {}", type.getSimpleName(), t);
            return Collections.emptyList();
        }
    }

    /**
     * 收集提供者产出的扩展项
     * @param target
     * @param provided
     */
    private static <T> void collect(List<T> target, List<? extends T> provided) {
        if (provided != null) {
            for (T item : provided) {
                if (item != null) {
                    target.add(item);
                }
            }
        }
    }
}