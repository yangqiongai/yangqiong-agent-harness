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
package com.yangqiong.agent.harness.observability.trace;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.Test;

import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;

import com.yangqiong.agent.harness.core.trace.SpanInfo;

/**
 * OTel SDK追踪桥接测试
 * <p>
 * 使用内存SpanExporter捕获导出结果，验证remote parent链路与根Span属性保留语义。
 * </p>
 * @author yangqiong
 */
class OtelSdkTraceEmitterTest {

    /**
     * traceId样例
     */
    private static final String TRACE_ID = "0123456789abcdef0123456789abcdef";

    /**
     * spanId样例
     */
    private static final String SPAN_ID = "fedcba9876543210";

    /**
     * 父spanId样例
     */
    private static final String PARENT_SPAN_ID = "abcdef0123456789";

    /**
     * 捕获导出的内存导出器
     */
    private static final class CapturingExporter implements SpanExporter {

        /**
         * 已导出的SpanData
         */
        private final List<SpanData> spans = new CopyOnWriteArrayList<>();

        @Override
        public CompletableResultCode export(Collection<SpanData> spans) {
            this.spans.addAll(spans);
            return CompletableResultCode.ofSuccess();
        }

        @Override
        public CompletableResultCode flush() {
            return CompletableResultCode.ofSuccess();
        }

        @Override
        public CompletableResultCode shutdown() {
            return CompletableResultCode.ofSuccess();
        }
    }

    /**
     * 验证子Span通过remote parent保持core traceId与父链路，属性正确映射
     */
    @Test
    void childSpanShouldKeepTraceIdAndParentLink() {
        CapturingExporter exporter = new CapturingExporter();
        SdkTracerProvider provider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .build();
        OtelSdkTraceEmitter emitter = new OtelSdkTraceEmitter(provider, "test-scope");

        SpanInfo spanInfo = new SpanInfo(TRACE_ID, SPAN_ID, PARENT_SPAN_ID, "reasoning", 40L,
                Map.of("iteration", 2, "success", true));
        emitter.onSpan(spanInfo);
        provider.close();

        assertThat(exporter.spans).hasSize(1);
        SpanData spanData = exporter.spans.get(0);
        assertThat(spanData.getTraceId()).isEqualTo(TRACE_ID);
        assertThat(spanData.getParentSpanContext().getSpanId()).isEqualTo(PARENT_SPAN_ID);
        assertThat(spanData.getName()).isEqualTo("reasoning");
        assertThat(spanData.getKind()).isEqualTo(io.opentelemetry.api.trace.SpanKind.INTERNAL);
        assertThat(spanData.getAttributes().get(io.opentelemetry.api.common.AttributeKey.longKey("iteration")))
                .isEqualTo(2L);
        assertThat(spanData.getAttributes().get(io.opentelemetry.api.common.AttributeKey.booleanKey("success")))
                .isTrue();
        long durationMs = (spanData.getEndEpochNanos() - spanData.getStartEpochNanos()) / 1_000_000L;
        assertThat(durationMs).isEqualTo(40L);
    }

    /**
     * 验证根Span以属性保留core traceId供关联
     */
    @Test
    void rootSpanShouldKeepCoreTraceIdAsAttribute() {
        CapturingExporter exporter = new CapturingExporter();
        SdkTracerProvider provider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .build();
        OtelSdkTraceEmitter emitter = new OtelSdkTraceEmitter(provider, "test-scope");

        SpanInfo spanInfo = new SpanInfo(TRACE_ID, SPAN_ID, null, "agent_run", 100L, Map.of());
        emitter.onSpan(spanInfo);
        provider.close();

        assertThat(exporter.spans).hasSize(1);
        SpanData spanData = exporter.spans.get(0);
        assertThat(spanData.getParentSpanContext()).isEqualTo(SpanContext.getInvalid());
        assertThat(spanData.getAttributes().get(
                io.opentelemetry.api.common.AttributeKey.stringKey(OtelSdkTraceEmitter.ATTR_CORE_TRACE_ID)))
                .isEqualTo(TRACE_ID);
        long durationMs = (spanData.getEndEpochNanos() - spanData.getStartEpochNanos()) / 1_000_000L;
        assertThat(durationMs).isEqualTo(100L);
    }

    /**
     * 验证null入参静默忽略
     */
    @Test
    void nullSpanShouldBeIgnored() {
        SdkTracerProvider provider = SdkTracerProvider.builder().build();
        OtelSdkTraceEmitter emitter = new OtelSdkTraceEmitter(provider, "test-scope");

        emitter.onSpan(null);
        provider.close();
        assertThat(true).isTrue();
    }
}
