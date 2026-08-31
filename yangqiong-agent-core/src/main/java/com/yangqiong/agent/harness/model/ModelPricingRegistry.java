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

import java.util.concurrent.ConcurrentHashMap;

/**
 * 模型定价注册表
 * @author yangqiong
 */
public class ModelPricingRegistry {

    /**
     * 定价表默认容量上限
     */
    private static final int DEFAULT_MAX_SIZE = 128;

    /**
     * 定价表
     */
    private final ConcurrentHashMap<String, ModelPricing> pricingTable;

    /**
     * 定价表容量上限
     */
    private final int maxSize;

    /**
     * 默认构造器，容量上限为128
     */
    public ModelPricingRegistry() {
        this(DEFAULT_MAX_SIZE);
    }

    /**
     * 指定容量上限构造器
     * @param maxSize
     */
    public ModelPricingRegistry(int maxSize) {
        this.maxSize = Math.max(1, maxSize);
        this.pricingTable = new ConcurrentHashMap<>(this.maxSize);
    }

    /**
     * 注册模型定价，超出容量上限时忽略新条目
     * @param pricing
     */
    public void register(ModelPricing pricing) {
        if (pricing == null || pricing.modelCode() == null) {
            return;
        }
        if (pricingTable.size() >= maxSize && !pricingTable.containsKey(pricing.modelCode())) {
            return;
        }
        pricingTable.put(pricing.modelCode(), pricing);
    }

    /**
     * 查询模型定价，未命中时回退默认价（0）
     * @param modelCode
     * @return
     */
    public ModelPricing priceOf(String modelCode) {
        if (modelCode == null) {
            return new ModelPricing(null, 0.0, 0.0);
        }
        return pricingTable.getOrDefault(modelCode, new ModelPricing(modelCode, 0.0, 0.0));
    }

    /**
     * 获取注册表大小
     * @return
     */
    public int size() {
        return pricingTable.size();
    }
}