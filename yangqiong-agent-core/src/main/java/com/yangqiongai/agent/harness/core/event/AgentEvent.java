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
package com.yangqiongai.agent.harness.core.event;

import java.util.Objects;

/**
 * Agent事件基类
 * @author yangqiong
 */
public class AgentEvent {

    /**
     * 事件类型
     */
    private final AgentEventType type;

    /**
     * 事件载荷
     */
    private final Object payload;

    /**
     * 父级代理路径，用于子代理事件冒泡时标记来源，可空
     */
    private final String parentAgentPath;

    /**
     * 追踪ID，供可观测性事件关联，可空
     */
    private final String traceId;

    /**
     * Span ID，供可观测性事件关联，可空
     */
    private final String spanId;

    protected AgentEvent(AgentEventType type, Object payload) {
        this(type, payload, null, null, null);
    }

    protected AgentEvent(AgentEventType type, Object payload, String parentAgentPath) {
        this(type, payload, parentAgentPath, null, null);
    }

    protected AgentEvent(AgentEventType type, Object payload, String parentAgentPath,
                         String traceId, String spanId) {
        this.type = type;
        this.payload = payload;
        this.parentAgentPath = parentAgentPath;
        this.traceId = traceId;
        this.spanId = spanId;
    }

    /**
     * 创建完成事件
     * @return
     */
    public static AgentEvent completed() {
        return new AgentEvent(AgentEventType.COMPLETED, null);
    }

    /**
     * 创建通用事件
     * @param type
     * @param payload
     * @return
     */
    public static AgentEvent of(AgentEventType type, Object payload) {
        return new AgentEvent(type, payload);
    }

    /**
     * 创建带父级代理路径的事件，用于子代理事件冒泡
     * @param type
     * @param payload
     * @param parentAgentPath
     * @return
     */
    public static AgentEvent of(AgentEventType type, Object payload, String parentAgentPath) {
        return new AgentEvent(type, payload, parentAgentPath);
    }

    /**
     * 创建带父级代理路径与可观测性ID的事件，用于子代理事件冒泡与span关联
     * @param type
     * @param payload
     * @param parentAgentPath
     * @param traceId
     * @param spanId
     * @return
     */
    public static AgentEvent of(AgentEventType type, Object payload, String parentAgentPath,
                                String traceId, String spanId) {
        return new AgentEvent(type, payload, parentAgentPath, traceId, spanId);
    }

    /**
     * 获取事件类型
     * @return
     */
    public AgentEventType getType() {
        return type;
    }

    /**
     * 获取事件载荷
     * @return
     */
    public Object getPayload() {
        return payload;
    }

    /**
     * 获取父级代理路径
     * @return
     */
    public String getParentAgentPath() {
        return parentAgentPath;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AgentEvent that = (AgentEvent) o;
        return type == that.type && Objects.equals(payload, that.payload)
                && Objects.equals(parentAgentPath, that.parentAgentPath)
                && Objects.equals(traceId, that.traceId)
                && Objects.equals(spanId, that.spanId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, payload, parentAgentPath, traceId, spanId);
    }

    @Override
    public String toString() {
        return "AgentEvent{type=" + type + ", payload=" + payload
                + (parentAgentPath != null ? ", parentAgentPath=" + parentAgentPath : "")
                + (traceId != null ? ", traceId=" + traceId : "")
                + (spanId != null ? ", spanId=" + spanId : "")
                + "}";
    }
}