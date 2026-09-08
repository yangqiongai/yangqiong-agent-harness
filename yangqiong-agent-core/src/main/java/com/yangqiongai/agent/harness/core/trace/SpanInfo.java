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
package com.yangqiongai.agent.harness.core.trace;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Span信息
 * <p>
 * 描述Agent执行过程中一个阶段的时间跨度，供{@link TraceEmitter}导出。
 * 结构对齐OTel Span语义（traceId/spanId/parentSpanId/operation/duration/attributes）。
 * </p>
 * @author yangqiong
 */
public final class SpanInfo {

    /**
     * 追踪ID，同一Agent执行的根Span与所有子Span共享
     */
    private final String traceId;

    /**
     * Span ID
     */
    private final String spanId;

    /**
     * 父Span ID，根Span为null
     */
    private final String parentSpanId;

    /**
     * 操作名称（agent_run/reasoning/acting）
     */
    private final String operation;

    /**
     * 耗时（毫秒）
     */
    private final long durationMs;

    /**
     * 属性标签
     */
    private final Map<String, Object> attributes;

    /**
     * 状态(OK/ERROR)
     */
    private final String status;

    /**
     * 错误信息，无错误为null
     */
    private final String errorMessage;

    /**
     * 开始时间（epoch毫秒，0表示未知）
     */
    private final long startTimeMs;

    /**
     * 全参构造
     * @param traceId
     * @param spanId
     * @param parentSpanId
     * @param operation
     * @param durationMs
     * @param attributes
     */
    public SpanInfo(String traceId, String spanId, String parentSpanId, String operation,
                    long durationMs, Map<String, Object> attributes) {
        this(traceId, spanId, parentSpanId, operation, durationMs, attributes,
                "OK", null, System.currentTimeMillis() - durationMs);
    }

    /**
     * 带状态全参构造
     * @param traceId
     * @param spanId
     * @param parentSpanId
     * @param operation
     * @param durationMs
     * @param attributes
     * @param status
     * @param errorMessage
     * @param startTimeMs
     */
    public SpanInfo(String traceId, String spanId, String parentSpanId, String operation,
                    long durationMs, Map<String, Object> attributes, String status,
                    String errorMessage, long startTimeMs) {
        this.traceId = traceId;
        this.spanId = spanId;
        this.parentSpanId = parentSpanId;
        this.operation = operation;
        this.durationMs = durationMs;
        this.attributes = attributes != null ? new LinkedHashMap<>(attributes) : new LinkedHashMap<>();
        this.status = status != null ? status : "OK";
        this.errorMessage = errorMessage;
        this.startTimeMs = startTimeMs;
    }

    /**
     * 获取状态
     * @return
     */
    public String getStatus() {
        return status;
    }

    /**
     * 获取错误信息
     * @return
     */
    public String getErrorMessage() {
        return errorMessage;
    }

    /**
     * 获取开始时间（epoch毫秒）
     * @return
     */
    public long getStartTimeMs() {
        return startTimeMs;
    }

    /**
     * 获取追踪ID
     * @return
     */
    public String getTraceId() {
        return traceId;
    }

    /**
     * 获取Span ID
     * @return
     */
    public String getSpanId() {
        return spanId;
    }

    /**
     * 获取父Span ID
     * @return
     */
    public String getParentSpanId() {
        return parentSpanId;
    }

    /**
     * 获取操作名称
     * @return
     */
    public String getOperation() {
        return operation;
    }

    /**
     * 获取耗时（毫秒）
     * @return
     */
    public long getDurationMs() {
        return durationMs;
    }

    /**
     * 获取属性标签
     * @return
     */
    public Map<String, Object> getAttributes() {
        return new LinkedHashMap<>(attributes);
    }

    @Override
    public String toString() {
        return "SpanInfo{traceId='" + traceId + "', spanId='" + spanId + "', parentSpanId='"
                + parentSpanId + "', operation='" + operation + "', durationMs=" + durationMs + "}";
    }
}
