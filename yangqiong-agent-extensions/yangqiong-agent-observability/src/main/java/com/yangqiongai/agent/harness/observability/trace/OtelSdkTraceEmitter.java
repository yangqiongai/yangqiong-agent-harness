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
package com.yangqiongai.agent.harness.observability.trace;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerProvider;

import com.yangqiongai.agent.harness.core.trace.SpanInfo;
import com.yangqiongai.agent.harness.core.trace.TraceEmitter;

/**
 * OpenTelemetry SDK追踪桥接
 * <p>
 * core TraceEmitter SPI 的OTel SDK实现：将SpanInfo转为OTel Span并交给使用方提供的
 * TracerProvider（含Processor/Exporter链路），适用于已全局部署OTel SDK的应用。
 * 与零依赖的OtlpHttpTraceEmitter二选一。子Span通过remote parent关联保持traceId；
 * 根Span因SDK不允许自定义ID，traceId以 harness.trace.id 属性保留供关联查询。
 * 本类依赖OTel API类，仅在classpath存在OTel时加载，其余环境不受影响。
 * </p>
 * @author yangqiong
 */
public final class OtelSdkTraceEmitter implements TraceEmitter {

    /**
     * 根Span保留core traceId的属性键
     */
    public static final String ATTR_CORE_TRACE_ID = "harness.trace.id";

    /**
     * InstrumentationScope名称
     */
    private final Tracer tracer;

    /**
     * 构造OTel SDK桥接
     * @param tracerProvider 使用方的OTel TracerProvider（如SdkTracerProvider）
     * @param instrumentationScopeName InstrumentationScope名称
     */
    public OtelSdkTraceEmitter(TracerProvider tracerProvider, String instrumentationScopeName) {
        this.tracer = tracerProvider.get(instrumentationScopeName);
    }

    /**
     * Span结束回调：转为OTel Span立即end，由SDK Processor链路异步导出
     * @param spanInfo
     */
    @Override
    public void onSpan(SpanInfo spanInfo) {
        if (spanInfo == null) {
            return;
        }
        boolean hasParent = spanInfo.getParentSpanId() != null && !spanInfo.getParentSpanId().isBlank();
        long endMillis = System.currentTimeMillis();
        Span span = (hasParent ? bridgeWithRemoteParent(spanInfo, endMillis)
                : bridgeAsRoot(spanInfo, endMillis)).startSpan();
        span.end(endMillis, TimeUnit.MILLISECONDS);
    }

    /**
     * 构建带remote parent的Span构建器，保持core traceId与父链路
     * @param spanInfo
     * @param endMillis
     * @return
     */
    private io.opentelemetry.api.trace.SpanBuilder bridgeWithRemoteParent(SpanInfo spanInfo, long endMillis) {
        SpanContext parentContext = SpanContext.createFromRemoteParent(
                spanInfo.getTraceId(), spanInfo.getParentSpanId(),
                TraceFlags.getSampled(), TraceState.getDefault());
        io.opentelemetry.context.Context parent = io.opentelemetry.context.Context.root()
                .with(Span.wrap(parentContext));
        long startEpochNanos = (endMillis - spanInfo.getDurationMs()) * 1_000_000L;
        return tracer.spanBuilder(spanInfo.getOperation())
                .setParent(parent)
                .setSpanKind(SpanKind.INTERNAL)
                .setStartTimestamp(startEpochNanos, TimeUnit.NANOSECONDS)
                .setAllAttributes(toOtelAttributes(spanInfo.getAttributes()));
    }

    /**
     * 构建根Span构建器：SDK不允许自定义ID，core traceId以属性保留供关联
     * @param spanInfo
     * @param endMillis
     * @return
     */
    private io.opentelemetry.api.trace.SpanBuilder bridgeAsRoot(SpanInfo spanInfo, long endMillis) {
        long startEpochNanos = (endMillis - spanInfo.getDurationMs()) * 1_000_000L;
        return tracer.spanBuilder(spanInfo.getOperation())
                .setNoParent()
                .setSpanKind(SpanKind.INTERNAL)
                .setStartTimestamp(startEpochNanos, TimeUnit.NANOSECONDS)
                .setAttribute(ATTR_CORE_TRACE_ID, spanInfo.getTraceId())
                .setAllAttributes(toOtelAttributes(spanInfo.getAttributes()));
    }

    /**
     * 属性Map转OTel Attributes
     * @param attributes
     * @return
     */
    private static io.opentelemetry.api.common.Attributes toOtelAttributes(Map<String, Object> attributes) {
        io.opentelemetry.api.common.AttributesBuilder builder = io.opentelemetry.api.common.Attributes.builder();
        if (attributes == null) {
            return builder.build();
        }
        for (Map.Entry<String, Object> entry : attributes.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Integer || value instanceof Long || value instanceof Short
                    || value instanceof Byte) {
                builder.put(entry.getKey(), ((Number) value).longValue());
            } else if (value instanceof Boolean) {
                builder.put(entry.getKey(), (Boolean) value);
            } else if (value instanceof Number) {
                builder.put(entry.getKey(), ((Number) value).doubleValue());
            } else if (value != null) {
                builder.put(entry.getKey(), String.valueOf(value));
            }
        }
        return builder.build();
    }
}
