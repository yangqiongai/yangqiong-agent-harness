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

import io.micrometer.core.instrument.MeterRegistry;

import com.yangqiongai.agent.harness.observability.trace.OtlpHttpTraceEmitter;

/**
 * 可观测性装配句柄
 * <p>
 * 持有装配产物：close 时触发 Trace 缓冲的最后一批导出；meterRegistry()
 * 仅在 Micrometer 存在时可用，用于外部暴露（如Prometheus HTTP端点）。
 * </p>
 * @author yangqiong
 */
public final class ObservabilityHandle implements AutoCloseable {

    /**
     * Trace导出器，Trace关闭时为null
     */
    private final OtlpHttpTraceEmitter emitter;

    /**
     * 指标注册表，指标降级时为null（Object类型避免类加载依赖Micrometer）
     */
    private final Object meterRegistry;

    /**
     * 构造句柄
     * @param emitter
     * @param meterRegistry
     */
    ObservabilityHandle(OtlpHttpTraceEmitter emitter, Object meterRegistry) {
        this.emitter = emitter;
        this.meterRegistry = meterRegistry;
    }

    /**
     * 获取内部创建或外部传入的指标注册表，需Micrometer存在时调用
     * @return
     */
    public MeterRegistry meterRegistry() {
        return (MeterRegistry) meterRegistry;
    }

    /**
     * 关闭：触发Trace最后一批导出，幂等
     */
    @Override
    public void close() {
        if (emitter != null) {
            emitter.close();
        }
    }
}
