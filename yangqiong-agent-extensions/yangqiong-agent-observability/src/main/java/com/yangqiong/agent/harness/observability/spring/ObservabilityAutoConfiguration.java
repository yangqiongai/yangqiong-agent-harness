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
package com.yangqiong.agent.harness.observability.spring;

import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import com.yangqiong.agent.harness.durable.ApprovalStore;
import com.yangqiong.agent.harness.observability.config.ObservabilityConfig;
import com.yangqiong.agent.harness.observability.metrics.ApprovalPendingMonitor;
import com.yangqiong.agent.harness.observability.metrics.MetricsEventListener;
import com.yangqiong.agent.harness.observability.trace.OtlpBatchBuffer;
import com.yangqiong.agent.harness.observability.trace.OtlpHttpTraceEmitter;

/**
 * 可观测性自动装配
 * <p>
 * 按 yangqiong.agent.observability.enabled=true 激活，产出三组Bean：
 * OTLP Trace导出器、指标监听器、跨进程审批等待监控。产出后需使用方将其装配到
 * HarnessRuntimeBuilder（builder.traceEmitter(...)/addEventListener(...)）。
 * MeterRegistry 优先复用容器已有实例（如Prometheus），缺失时内部创建SimpleMeterRegistry。
 * </p>
 * @author yangqiong
 */
@AutoConfiguration
@EnableConfigurationProperties(ObservabilityProperties.class)
@ConditionalOnProperty(prefix = "yangqiong.agent.observability", name = "enabled", havingValue = "true")
public class ObservabilityAutoConfiguration {

    /**
     * OTLP Trace导出器Bean，随容器销毁触发最后一批导出；otlp-endpoint未配置时不创建
     * @param properties
     * @return
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "yangqiong.agent.observability", name = "otlp-endpoint")
    public OtlpHttpTraceEmitter otlpHttpTraceEmitter(ObservabilityProperties properties) {
        ObservabilityConfig config = toConfig(properties);
        OtlpBatchBuffer buffer = new OtlpBatchBuffer(config.getOtlpEndpoint(), config.getServiceName(),
                config.getTraceBatchSize(), config.getTraceFlushIntervalMs(), config.getTraceExportTimeoutMs(),
                config.getTraceMaxBufferedSpans());
        return new OtlpHttpTraceEmitter(buffer);
    }

    /**
     * 指标与审批监控装配：Micrometer存在时生效
     * @author yangqiong
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(MeterRegistry.class)
    static class MetricsConfiguration {

        /**
         * 指标监听器Bean：复用容器MeterRegistry或内部创建，需装配到EventBus才生效
         * @param properties
         * @param registryProvider
         * @return
         */
        @Bean
        @ConditionalOnMissingBean
        @ConditionalOnProperty(prefix = "yangqiong.agent.observability.metrics", name = "enabled",
                havingValue = "true")
        public MetricsEventListener metricsEventListener(ObservabilityProperties properties,
                                                        ObjectProvider<MeterRegistry> registryProvider) {
            MeterRegistry registry = registryProvider.getIfAvailable(SimpleMeterRegistry::new);
            applyCommonTags(registry, properties.getMetrics().getCommonTags());
            return new MetricsEventListener(registry, null);
        }

        /**
         * 跨进程审批等待监控Bean：容器存在ApprovalStore时生效，随容器销毁停表
         * @param properties
         * @param approvalStoreProvider
         * @param registryProvider
         * @return
         */
        @Bean(destroyMethod = "close")
        @ConditionalOnMissingBean
        @ConditionalOnProperty(prefix = "yangqiong.agent.observability.approval-wait", name = "enabled",
                havingValue = "true")
        public ApprovalPendingMonitor approvalPendingMonitor(ObservabilityProperties properties,
                                                             ObjectProvider<ApprovalStore> approvalStoreProvider,
                                                             ObjectProvider<MeterRegistry> registryProvider) {
            ApprovalStore approvalStore = approvalStoreProvider.getIfAvailable();
            if (approvalStore == null) {
                throw new IllegalStateException(
                        "yangqiong.agent.observability.approval-wait.enabled=true 需要容器中存在 ApprovalStore Bean");
            }
            MeterRegistry registry = registryProvider.getIfAvailable(SimpleMeterRegistry::new);
            return new ApprovalPendingMonitor(approvalStore, registry,
                    properties.getApprovalWait().getScopes(),
                    properties.getApprovalWait().getRefreshIntervalMs());
        }

        /**
         * 追加公共标签到注册表
         * @param registry
         * @param commonTags
         */
        private void applyCommonTags(MeterRegistry registry, Map<String, String> commonTags) {
            if (commonTags == null || commonTags.isEmpty()) {
                return;
            }
            io.micrometer.core.instrument.Tags tags = io.micrometer.core.instrument.Tags.empty();
            for (Map.Entry<String, String> entry : commonTags.entrySet()) {
                tags = tags.and(entry.getKey(), entry.getValue());
            }
            registry.config().commonTags(tags);
        }
    }

    /**
     * Spring属性转核心配置对象（复用非Spring装配路径的参数语义）
     * @param properties
     * @return
     */
    private static ObservabilityConfig toConfig(ObservabilityProperties properties) {
        return ObservabilityConfig.builder()
                .otlpEndpoint(properties.getOtlpEndpoint())
                .serviceName(properties.getServiceName())
                .traceBatchSize(properties.getTraceBatchSize())
                .traceFlushIntervalMs(properties.getTraceFlushIntervalMs())
                .traceExportTimeoutMs(properties.getTraceExportTimeoutMs())
                .traceMaxBufferedSpans(properties.getTraceMaxBufferedSpans())
                .build();
    }
}
