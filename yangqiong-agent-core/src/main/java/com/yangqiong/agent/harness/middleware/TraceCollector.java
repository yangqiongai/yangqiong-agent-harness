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
package com.yangqiong.agent.harness.middleware;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 追踪收集器
 * <p>
 * 按会话隔离管理Span树，支持创建嵌套Span、记录属性、导出OTLP格式Trace。
 * </p>
 * @author yangqiong
 */
public class TraceCollector {

    /**
     * 最大会话追踪数，超限时淘汰最旧条目以防内存泄漏
     */
    private static final int MAX_SESSIONS = 500;

    /**
     * 按sessionId隔离的根Span
     */
    private final Map<String, TraceSpan> rootSpans = new ConcurrentHashMap<>();

    /**
     * 按sessionId隔离的当前活跃Span
     */
    private final Map<String, TraceSpan> currentSpans = new ConcurrentHashMap<>();

    /**
     * 开始一个根Span
     * @param sessionId
     * @param name
     * @param type
     * @return
     */
    public TraceSpan startRoot(String sessionId, String name, String type) {
        // 会话数超限时清理一个最旧条目
        if (rootSpans.size() >= MAX_SESSIONS) {
            Iterator<String> it = rootSpans.keySet().iterator();
            if (it.hasNext()) {
                String oldest = it.next();
                rootSpans.remove(oldest);
                currentSpans.remove(oldest);
            }
        }
        TraceSpan span = new TraceSpan(name, type, null);
        rootSpans.put(sessionId, span);
        currentSpans.put(sessionId, span);
        return span;
    }

    /**
     * 在当前Span下创建子Span
     * @param sessionId
     * @param name
     * @param type
     * @return
     */
    public TraceSpan startChild(String sessionId, String name, String type) {
        TraceSpan current = currentSpans.get(sessionId);
        if (current == null) {
            return startRoot(sessionId, name, type);
        }
        TraceSpan child = current.child(name, type);
        currentSpans.put(sessionId, child);
        return child;
    }

    /**
     * 结束当前Span，将活跃指针回退到父Span
     * @param sessionId
     */
    public void endCurrent(String sessionId) {
        TraceSpan current = currentSpans.get(sessionId);
        if (current != null) {
            current.end();
            if (current.getParent() != null) {
                currentSpans.put(sessionId, current.getParent());
            }
        }
    }

    /**
     * 为当前Span添加属性
     * @param sessionId
     * @param key
     * @param value
     */
    public void putAttribute(String sessionId, String key, Object value) {
        TraceSpan current = currentSpans.get(sessionId);
        if (current != null) {
            current.putAttribute(key, value);
        }
    }

    /**
     * 获取会话的根Span
     * @param sessionId
     * @return
     */
    public TraceSpan getRootSpan(String sessionId) {
        return rootSpans.get(sessionId);
    }

    /**
     * 导出会话的完整Trace为OTLP JSON
     * @param sessionId
     * @return
     */
    public String exportOtlp(String sessionId) {
        TraceSpan root = rootSpans.get(sessionId);
        return root != null ? root.exportOtlpJson() : "{}";
    }

    /**
     * 清理会话的追踪数据
     * @param sessionId
     */
    public void clear(String sessionId) {
        rootSpans.remove(sessionId);
        currentSpans.remove(sessionId);
    }

    /**
     * 获取所有会话的根Span列表
     * @return
     */
    public List<TraceSpan> getAllRootSpans() {
        return new ArrayList<>(rootSpans.values());
    }
}
