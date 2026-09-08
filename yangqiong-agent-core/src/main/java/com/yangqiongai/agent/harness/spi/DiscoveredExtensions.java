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
package com.yangqiongai.agent.harness.spi;

import java.util.Collections;
import java.util.List;

import com.yangqiongai.agent.harness.core.middleware.AgentMiddleware;
import com.yangqiongai.agent.harness.core.model.AgentModel;
import com.yangqiongai.agent.harness.core.tool.AgentTool;
import com.yangqiongai.agent.harness.durable.DistributedStores;
import com.yangqiongai.agent.harness.guardrail.InjectionDetector;

/**
 * 自动发现的扩展集合
 * <p>
 * 由{@link HarnessDiscovery}聚合各SPI提供者产出的扩展项，供运行时装配消费。
 * </p>
 * @author yangqiong
 */
public final class DiscoveredExtensions {

    /**
     * 发现的中间件
     */
    private final List<AgentMiddleware> middlewares;

    /**
     * 发现的工具
     */
    private final List<AgentTool> tools;

    /**
     * 发现的模型
     */
    private final List<AgentModel> models;

    /**
     * 发现的聚合存储
     */
    private final List<DistributedStores> stores;

    /**
     * 发现的注入检测器
     */
    private final List<InjectionDetector> detectors;

    /**
     * 构造发现的扩展集合
     * @param middlewares
     * @param tools
     * @param models
     * @param stores
     * @param detectors
     */
    public DiscoveredExtensions(List<AgentMiddleware> middlewares, List<AgentTool> tools,
                                List<AgentModel> models, List<DistributedStores> stores,
                                List<InjectionDetector> detectors) {
        this.middlewares = middlewares != null ? middlewares : Collections.emptyList();
        this.tools = tools != null ? tools : Collections.emptyList();
        this.models = models != null ? models : Collections.emptyList();
        this.stores = stores != null ? stores : Collections.emptyList();
        this.detectors = detectors != null ? detectors : Collections.emptyList();
    }

    /**
     * 获取发现的中间件
     * @return
     */
    public List<AgentMiddleware> middlewares() {
        return middlewares;
    }

    /**
     * 获取发现的工具
     * @return
     */
    public List<AgentTool> tools() {
        return tools;
    }

    /**
     * 获取发现的模型
     * @return
     */
    public List<AgentModel> models() {
        return models;
    }

    /**
     * 获取发现的聚合存储
     * @return
     */
    public List<DistributedStores> stores() {
        return stores;
    }

    /**
     * 获取发现的注入检测器
     * @return
     */
    public List<InjectionDetector> detectors() {
        return detectors;
    }
}