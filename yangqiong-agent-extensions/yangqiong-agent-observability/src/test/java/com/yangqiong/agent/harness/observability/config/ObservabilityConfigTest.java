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
package com.yangqiong.agent.harness.observability.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.yangqiong.agent.harness.model.ModelPricing;
import com.yangqiong.agent.harness.model.ModelPricingRegistry;

/**
 * 可观测性配置测试
 * @author yangqiong
 */
class ObservabilityConfigTest {

    /**
     * 验证默认值：Trace关闭、指标关闭、批量64
     */
    @Test
    void defaultsShouldDisableTraceAndMetrics() {
        ObservabilityConfig config = ObservabilityConfig.builder().build();

        assertThat(config.getOtlpEndpoint()).isNull();
        assertThat(config.getServiceName()).isEqualTo("yangqiong-agent-harness");
        assertThat(config.getTraceBatchSize()).isEqualTo(64);
        assertThat(config.getTraceFlushIntervalMs()).isEqualTo(5000L);
        assertThat(config.getTraceExportTimeoutMs()).isEqualTo(5000);
        assertThat(config.getTraceMaxBufferedSpans()).isEqualTo(2048);
        assertThat(config.isMetricsEnabled()).isFalse();
        assertThat(config.getCommonTags()).isEmpty();
        assertThat(config.getPricingRegistry()).isNull();
    }

    /**
     * 验证构建器参数覆盖与公共标签
     */
    @Test
    void builderShouldOverrideDefaults() {
        ModelPricingRegistry pricing = new ModelPricingRegistry();
        pricing.register(new ModelPricing("gpt-x", 0.01, 0.02));

        ObservabilityConfig config = ObservabilityConfig.builder()
                .otlpEndpoint("http://collector:4318")
                .serviceName("demo-agent")
                .traceBatchSize(8)
                .traceFlushIntervalMs(0)
                .metricsEnabled(true)
                .commonTag("env", "prod")
                .commonTag("region", null)
                .pricingRegistry(pricing)
                .build();

        assertThat(config.getOtlpEndpoint()).isEqualTo("http://collector:4318");
        assertThat(config.getServiceName()).isEqualTo("demo-agent");
        assertThat(config.getTraceBatchSize()).isEqualTo(8);
        assertThat(config.getTraceFlushIntervalMs()).isZero();
        assertThat(config.isMetricsEnabled()).isTrue();
        assertThat(config.getCommonTags()).containsOnlyKeys("env").containsEntry("env", "prod");
        assertThat(config.getPricingRegistry()).isSameAs(pricing);
    }

    /**
     * 验证配置不可变：返回的公共标签Map不可修改
     */
    @Test
    void commonTagsShouldBeUnmodifiable() {
        ObservabilityConfig config = ObservabilityConfig.builder()
                .commonTag("env", "prod")
                .build();

        assertThat(config.getCommonTags()).isUnmodifiable();
    }
}
