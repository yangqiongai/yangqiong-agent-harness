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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.yangqiong.agent.harness.model.ModelPricingRegistry;

/**
 * 可观测性扩展配置
 * <p>
 * Trace 导出与指标采集的统一开关与参数。otlpEndpoint 为空时关闭 Trace 导出；
 * metricsEnabled 开启但运行时缺失 Micrometer 时自动降级为仅 Trace。
 * </p>
 * @author yangqiong
 */
public final class ObservabilityConfig {

    /**
     * OTLP Trace 导出端点（如 http://collector:4318），null 时关闭 Trace 导出
     */
    private final String otlpEndpoint;

    /**
     * 上报服务名，写入 OTLP Resource 属性 service.name
     */
    private final String serviceName;

    /**
     * 单次批量导出的最大 Span 数
     */
    private final int traceBatchSize;

    /**
     * 定时刷新间隔（毫秒），0 表示不启动定时任务，仅手动或关闭时刷新
     */
    private final long traceFlushIntervalMs;

    /**
     * 导出 HTTP 请求的连接与读取超时（毫秒）
     */
    private final int traceExportTimeoutMs;

    /**
     * 缓冲区最大 Span 数，超限时丢弃最旧 Span 防止内存膨胀
     */
    private final int traceMaxBufferedSpans;

    /**
     * 是否启用指标采集，需运行时存在 Micrometer，缺失时自动降级
     */
    private final boolean metricsEnabled;

    /**
     * 指标公共标签（如 env=prod），附加到全部指标用于全局维度区分
     */
    private final Map<String, String> commonTags;

    /**
     * 模型定价注册表，注册后启用成本指标，null 时跳过成本统计
     */
    private final ModelPricingRegistry pricingRegistry;

    private ObservabilityConfig(Builder builder) {
        this.otlpEndpoint = builder.otlpEndpoint;
        this.serviceName = builder.serviceName;
        this.traceBatchSize = builder.traceBatchSize;
        this.traceFlushIntervalMs = builder.traceFlushIntervalMs;
        this.traceExportTimeoutMs = builder.traceExportTimeoutMs;
        this.traceMaxBufferedSpans = builder.traceMaxBufferedSpans;
        this.metricsEnabled = builder.metricsEnabled;
        this.commonTags = Collections.unmodifiableMap(new LinkedHashMap<>(builder.commonTags));
        this.pricingRegistry = builder.pricingRegistry;
    }

    /**
     * 创建配置构建器
     * @return
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 获取 OTLP 端点
     * @return
     */
    public String getOtlpEndpoint() {
        return otlpEndpoint;
    }

    /**
     * 获取服务名
     * @return
     */
    public String getServiceName() {
        return serviceName;
    }

    /**
     * 获取批量大小
     * @return
     */
    public int getTraceBatchSize() {
        return traceBatchSize;
    }

    /**
     * 获取刷新间隔
     * @return
     */
    public long getTraceFlushIntervalMs() {
        return traceFlushIntervalMs;
    }

    /**
     * 获取导出超时
     * @return
     */
    public int getTraceExportTimeoutMs() {
        return traceExportTimeoutMs;
    }

    /**
     * 获取缓冲上限
     * @return
     */
    public int getTraceMaxBufferedSpans() {
        return traceMaxBufferedSpans;
    }

    /**
     * 获取指标开关
     * @return
     */
    public boolean isMetricsEnabled() {
        return metricsEnabled;
    }

    /**
     * 获取公共标签
     * @return
     */
    public Map<String, String> getCommonTags() {
        return commonTags;
    }

    /**
     * 获取定价注册表
     * @return
     */
    public ModelPricingRegistry getPricingRegistry() {
        return pricingRegistry;
    }

    /**
     * 可观测性配置构建器
     * @author yangqiong
     */
    public static final class Builder {

        /**
         * OTLP 端点，默认关闭
         */
        private String otlpEndpoint;

        /**
         * 服务名，默认 yangqiong-agent-harness
         */
        private String serviceName = "yangqiong-agent-harness";

        /**
         * 批量大小，默认 64
         */
        private int traceBatchSize = 64;

        /**
         * 刷新间隔，默认 5000 毫秒
         */
        private long traceFlushIntervalMs = 5000L;

        /**
         * 导出超时，默认 5000 毫秒
         */
        private int traceExportTimeoutMs = 5000;

        /**
         * 缓冲上限，默认 2048
         */
        private int traceMaxBufferedSpans = 2048;

        /**
         * 指标开关，默认关闭
         */
        private boolean metricsEnabled;

        /**
         * 公共标签
         */
        private final Map<String, String> commonTags = new LinkedHashMap<>();

        /**
         * 定价注册表
         */
        private ModelPricingRegistry pricingRegistry;

        /**
         * 私有构造，仅通过 ObservabilityConfig.builder() 创建
         */
        private Builder() {
        }

        /**
         * 设置 OTLP 端点，传 null 关闭 Trace 导出
         * @param otlpEndpoint
         * @return
         */
        public Builder otlpEndpoint(String otlpEndpoint) {
            this.otlpEndpoint = otlpEndpoint;
            return this;
        }

        /**
         * 设置服务名
         * @param serviceName
         * @return
         */
        public Builder serviceName(String serviceName) {
            this.serviceName = serviceName;
            return this;
        }

        /**
         * 设置批量大小
         * @param traceBatchSize
         * @return
         */
        public Builder traceBatchSize(int traceBatchSize) {
            this.traceBatchSize = traceBatchSize;
            return this;
        }

        /**
         * 设置刷新间隔毫秒，0 表示仅手动刷新
         * @param traceFlushIntervalMs
         * @return
         */
        public Builder traceFlushIntervalMs(long traceFlushIntervalMs) {
            this.traceFlushIntervalMs = traceFlushIntervalMs;
            return this;
        }

        /**
         * 设置导出超时毫秒
         * @param traceExportTimeoutMs
         * @return
         */
        public Builder traceExportTimeoutMs(int traceExportTimeoutMs) {
            this.traceExportTimeoutMs = traceExportTimeoutMs;
            return this;
        }

        /**
         * 设置缓冲上限
         * @param traceMaxBufferedSpans
         * @return
         */
        public Builder traceMaxBufferedSpans(int traceMaxBufferedSpans) {
            this.traceMaxBufferedSpans = traceMaxBufferedSpans;
            return this;
        }

        /**
         * 开启或关闭指标采集
         * @param metricsEnabled
         * @return
         */
        public Builder metricsEnabled(boolean metricsEnabled) {
            this.metricsEnabled = metricsEnabled;
            return this;
        }

        /**
         * 追加一个公共标签
         * @param key
         * @param value
         * @return
         */
        public Builder commonTag(String key, String value) {
            if (key != null && value != null) {
                this.commonTags.put(key, value);
            }
            return this;
        }

        /**
         * 设置定价注册表以启用成本指标
         * @param pricingRegistry
         * @return
         */
        public Builder pricingRegistry(ModelPricingRegistry pricingRegistry) {
            this.pricingRegistry = pricingRegistry;
            return this;
        }

        /**
         * 构建不可变配置
         * @return
         */
        public ObservabilityConfig build() {
            return new ObservabilityConfig(this);
        }
    }
}
