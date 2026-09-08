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
package com.yangqiongai.agent.harness.middleware;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 追踪Span
 * <p>
 * 表示Agent执行过程中一个阶段的时间跨度，支持父子嵌套形成Span树。
 * 可通过{@link #exportOtlpJson()}导出为OTLP兼容的JSON结构。
 * </p>
 * @author yangqiong
 */
public class TraceSpan {

    /**
     * Span名称
     */
    private final String name;

    /**
     * Span类型（如agent/reasoning/acting/tool_call/model_call）
     */
    private final String type;

    /**
     * 父Span
     */
    private final TraceSpan parent;

    /**
     * 子Span列表
     */
    private final List<TraceSpan> children = Collections.synchronizedList(new ArrayList<>());

    /**
     * 开始时间（纳秒）
     */
    private final long startNanos;

    /**
     * 结束时间（纳秒），0表示未结束
     */
    private volatile long endNanos;

    /**
     * 属性标签
     */
    private final Map<String, Object> attributes = new ConcurrentHashMap<>();

    TraceSpan(String name, String type, TraceSpan parent) {
        this.name = name;
        this.type = type;
        this.parent = parent;
        this.startNanos = System.nanoTime();
    }

    /**
     * 结束Span记录
     */
    void end() {
        this.endNanos = System.nanoTime();
    }

    /**
     * 创建子Span
     * @param childName
     * @param childType
     * @return
     */
    TraceSpan child(String childName, String childType) {
        TraceSpan child = new TraceSpan(childName, childType, this);
        children.add(child);
        return child;
    }

    /**
     * 设置属性
     * @param key
     * @param value
     */
    void putAttribute(String key, Object value) {
        if (key != null && value != null) {
            attributes.put(key, value);
        }
    }

    /**
     * 获取耗时（毫秒）
     * @return
     */
    public long getElapsedMs() {
        long end = endNanos > 0 ? endNanos : System.nanoTime();
        return (end - startNanos) / 1_000_000;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public TraceSpan getParent() {
        return parent;
    }

    public List<TraceSpan> getChildren() {
        return new ArrayList<>(children);
    }

    public Map<String, Object> getAttributes() {
        return new ConcurrentHashMap<>(attributes);
    }

    /**
     * 导出为OTLP兼容的JSON结构
     * @return
     */
    public String exportOtlpJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"name\":\"").append(escape(name)).append("\",");
        sb.append("\"type\":\"").append(escape(type)).append("\",");
        sb.append("\"elapsedMs\":").append(getElapsedMs()).append(",");
        sb.append("\"attributes\":").append(attributesToJson(attributes)).append(",");
        sb.append("\"children\":[");
        List<TraceSpan> snapshot = new ArrayList<>(children);
        for (int i = 0; i < snapshot.size(); i++) {
            sb.append(snapshot.get(i).exportOtlpJson());
            if (i < snapshot.size() - 1) {
                sb.append(",");
            }
        }
        sb.append("]}");
        return sb.toString();
    }

    /**
     * 属性Map转JSON字符串
     * @param attrs
     * @return
     */
    private String attributesToJson(Map<String, Object> attrs) {
        if (attrs.isEmpty()) {
            return "{}";
        }
        StringBuilder sb = new StringBuilder("{");
        int i = 0;
        for (Map.Entry<String, Object> entry : attrs.entrySet()) {
            if (i++ > 0) {
                sb.append(",");
            }
            sb.append("\"").append(escape(entry.getKey())).append("\":");
            Object val = entry.getValue();
            if (val instanceof Number || val instanceof Boolean) {
                sb.append(val);
            } else {
                sb.append("\"").append(escape(String.valueOf(val))).append("\"");
            }
        }
        sb.append("}");
        return sb.toString();
    }

    /**
     * JSON字符串转义
     * @param text
     * @return
     */
    private String escape(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
}
