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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 模型定价注册表测试
 * @author yangqiong
 */
class ModelPricingRegistryTest {

    @Test
    void shouldReturnRegisteredPricing() {
        ModelPricingRegistry registry = new ModelPricingRegistry();
        ModelPricing pricing = new ModelPricing("gpt-4o", 2.5, 10.0);
        registry.register(pricing);
        ModelPricing result = registry.priceOf("gpt-4o");
        assertThat(result).isEqualTo(pricing);
        assertThat(result.promptUsdPer1k()).isEqualTo(2.5);
        assertThat(result.completionUsdPer1k()).isEqualTo(10.0);
    }

    @Test
    void shouldFallbackToZeroPriceForUnknownModel() {
        ModelPricingRegistry registry = new ModelPricingRegistry();
        ModelPricing result = registry.priceOf("unknown-model");
        assertThat(result.promptUsdPer1k()).isZero();
        assertThat(result.completionUsdPer1k()).isZero();
    }

    @Test
    void shouldFallbackToZeroPriceForNullModel() {
        ModelPricingRegistry registry = new ModelPricingRegistry();
        ModelPricing result = registry.priceOf(null);
        assertThat(result.promptUsdPer1k()).isZero();
        assertThat(result.completionUsdPer1k()).isZero();
    }

    @Test
    void shouldIgnoreNullPricing() {
        ModelPricingRegistry registry = new ModelPricingRegistry();
        registry.register(null);
        assertThat(registry.size()).isZero();
    }

    @Test
    void shouldBeBoundedByMaxSize() {
        ModelPricingRegistry registry = new ModelPricingRegistry(2);
        registry.register(new ModelPricing("model-a", 1.0, 2.0));
        registry.register(new ModelPricing("model-b", 1.0, 2.0));
        registry.register(new ModelPricing("model-c", 1.0, 2.0));
        assertThat(registry.size()).isEqualTo(2);
        assertThat(registry.priceOf("model-c").promptUsdPer1k()).isZero();
    }
}