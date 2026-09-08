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
package com.yangqiongai.agent.harness.observability.wiring;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import com.yangqiongai.agent.harness.HarnessRuntimeBuilder;
import com.yangqiongai.agent.harness.observability.config.ObservabilityConfig;
import com.yangqiongai.agent.harness.observability.metrics.MetricsEventListener;
import com.yangqiongai.agent.harness.observability.trace.OtlpBatchBuffer;
import com.yangqiongai.agent.harness.observability.trace.OtlpHttpTraceEmitter;

/**
 * 可观测性装配入口
 * <p>
 * 一行装配：Trace侧挂入 builder.traceEmitter(...) 激活 TraceMiddleware；
 * 指标侧注册 MetricsEventListener 到 builder 的事件总线。Micrometer 缺失时
 * 指标侧静默降级（捕获链接错误仅告警），不影响 Trace 导出与 Agent 主流程。
 * </p>
 * @author yangqiong
 */
public final class ObservabilityInstaller {

    private static final Logger log = LoggerFactory.getLogger(ObservabilityInstaller.class);

    /**
     * 私有构造，工具类禁止实例化
     */
    private ObservabilityInstaller() {
    }

    /**
     * 装配到运行时构建器，内部创建SimpleMeterRegistry（适合本地与测试）
     * @param builder
     * @param config
     * @return
     */
    public static ObservabilityHandle install(HarnessRuntimeBuilder builder, ObservabilityConfig config) {
        return install(builder, config, null);
    }

    /**
     * 装配到运行时构建器，指标写入外部指定的MeterRegistry（如Prometheus注册表）
     * @param builder
     * @param config
     * @param meterRegistry 外部指标注册表，null时内部创建SimpleMeterRegistry
     * @return
     */
    public static ObservabilityHandle install(HarnessRuntimeBuilder builder, ObservabilityConfig config,
                                               MeterRegistry meterRegistry) {
        OtlpHttpTraceEmitter emitter = installTrace(builder, config);
        Object registry = installMetrics(builder, config, meterRegistry);
        return new ObservabilityHandle(emitter, registry);
    }

    /**
     * 装配Trace导出：端点为空时跳过
     * @param builder
     * @param config
     * @return
     */
    private static OtlpHttpTraceEmitter installTrace(HarnessRuntimeBuilder builder, ObservabilityConfig config) {
        if (config.getOtlpEndpoint() == null || config.getOtlpEndpoint().isBlank()) {
            return null;
        }
        OtlpBatchBuffer buffer = new OtlpBatchBuffer(config.getOtlpEndpoint(), config.getServiceName(),
                config.getTraceBatchSize(), config.getTraceFlushIntervalMs(), config.getTraceExportTimeoutMs(),
                config.getTraceMaxBufferedSpans());
        OtlpHttpTraceEmitter emitter = new OtlpHttpTraceEmitter(buffer);
        builder.traceEmitter(emitter);
        return emitter;
    }

    /**
     * 装配指标采集：Micrometer缺失或开关关闭时降级返回null
     * @param builder
     * @param config
     * @param externalRegistry
     * @return
     */
    private static Object installMetrics(HarnessRuntimeBuilder builder, ObservabilityConfig config,
                                         MeterRegistry externalRegistry) {
        if (!config.isMetricsEnabled()) {
            return null;
        }
        try {
            MeterRegistry registry = externalRegistry != null ? externalRegistry : new SimpleMeterRegistry();
            applyCommonTags(registry, config.getCommonTags());
            MetricsEventListener listener = new MetricsEventListener(registry, config.getPricingRegistry());
            builder.addEventListener(listener);
            return registry;
        } catch (LinkageError e) {
            log.warn("[Observability] Micrometer缺失，指标采集降级关闭（仅保留Trace导出）: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 追加公共标签到注册表
     * @param registry
     * @param commonTags
     */
    private static void applyCommonTags(MeterRegistry registry, Map<String, String> commonTags) {
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
