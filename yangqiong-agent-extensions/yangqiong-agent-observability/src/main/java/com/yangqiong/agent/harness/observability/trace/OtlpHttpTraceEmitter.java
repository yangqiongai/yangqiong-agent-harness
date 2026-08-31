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

import com.yangqiong.agent.harness.core.trace.SpanInfo;
import com.yangqiong.agent.harness.core.trace.TraceEmitter;

/**
 * OTLP/HTTP追踪导出器
 * <p>
 * core TraceEmitter SPI 的扩展实现：Span 结束回调时入缓冲队列，
 * 由 OtlpBatchBuffer 批量编码为 OTLP JSON 并 POST 到 Collector。
 * 通过 HarnessRuntimeBuilder.traceEmitter(...) 注入后 TraceMiddleware 自动生效。
 * </p>
 * @author yangqiong
 */
public final class OtlpHttpTraceEmitter implements TraceEmitter, AutoCloseable {

    /**
     * Span批量缓冲导出器
     */
    private final OtlpBatchBuffer buffer;

    /**
     * 构造导出器
     * @param buffer 批量缓冲导出器
     */
    public OtlpHttpTraceEmitter(OtlpBatchBuffer buffer) {
        this.buffer = buffer;
    }

    /**
     * Span结束时入队缓冲，导出失败由缓冲器内部消化
     * @param spanInfo
     */
    @Override
    public void onSpan(SpanInfo spanInfo) {
        buffer.offer(spanInfo);
    }

    /**
     * 立即触发一次导出
     */
    public void flush() {
        buffer.flush();
    }

    /**
     * 关闭并做最后一次导出
     */
    @Override
    public void close() {
        buffer.close();
    }
}
