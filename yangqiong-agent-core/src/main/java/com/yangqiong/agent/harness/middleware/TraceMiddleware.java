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

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import com.yangqiong.agent.harness.engine.AgentRuntimeContext;
import com.yangqiong.agent.harness.core.event.AgentEvent;
import com.yangqiong.agent.harness.core.event.AgentEventType;
import com.yangqiong.agent.harness.core.message.AgentMessage;
import com.yangqiong.agent.harness.core.message.AgentToolUseBlock;
import com.yangqiong.agent.harness.core.middleware.AgentMiddleware;
import com.yangqiong.agent.harness.core.trace.SpanInfo;
import com.yangqiong.agent.harness.core.trace.TraceEmitter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

/**
 * 追踪中间件
 * <p>
 * 通过洋葱模型包裹Agent执行：入口{@code agent_run}创建根Span，推理阶段{@code reasoning}、
 * 执行阶段{@code acting}创建子Span，Span结束时通过{@link TraceEmitter}导出{@link SpanInfo}。
 * 父子关系通过上下文中的Span栈维护，traceId在整个Agent运行内共享。
 * 未注入TraceEmitter时静默降级，不采集、不抛异常。
 * </p>
 * @author yangqiong
 */
public class TraceMiddleware implements AgentMiddleware {

    private static final Logger log = LoggerFactory.getLogger(TraceMiddleware.class);

    /**
     * 上下文属性键：追踪ID
     */
    private static final String ATTR_TRACE_ID = "harness.traceId";

    /**
     * 上下文属性键：Span栈（Deque&lt;String&gt;），用于推导父子关系
     */
    private static final String ATTR_SPAN_STACK = "harness.spanStack";

    /**
     * 追踪导出器，null时静默降级
     */
    private final TraceEmitter emitter;

    /**
     * 构造
     * @param emitter 追踪导出器，null时静默降级
     */
    public TraceMiddleware(TraceEmitter emitter) {
        this.emitter = emitter;
    }

    /**
     * Agent整体调用拦截：初始化traceId与Span栈，包裹整个ReAct循环
     * @param context
     * @param input
     * @param next
     * @return
     */
    @Override
    public Flux<AgentEvent> onAgent(AgentRuntimeContext context, List<AgentMessage> input,
                                     Function<List<AgentMessage>, Flux<AgentEvent>> next) {
        if (emitter == null) {
            return next.apply(input);
        }
        if (context != null) {
            context.put(ATTR_TRACE_ID, newTraceId());
            context.put(ATTR_SPAN_STACK, new ArrayDeque<String>());
        }
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("messageCount", input.size());
        return withSpan(context, "agent_run", attributes, event -> {
            if (event.getType() == AgentEventType.AGENT_RESULT) {
                attributes.put("status", "completed");
            } else if (event.getType() == AgentEventType.ERROR) {
                attributes.put("status", "error");
                Object payload = event.getPayload();
                if (payload instanceof Throwable t && t.getMessage() != null) {
                    attributes.put("error", t.getMessage());
                }
            }
        }, () -> next.apply(input));
    }

    /**
     * 推理阶段拦截：创建reasoning子Span
     * @param context
     * @param input
     * @param next
     * @return
     */
    @Override
    public Flux<AgentEvent> onReasoning(AgentRuntimeContext context, List<AgentMessage> input,
                                         Function<List<AgentMessage>, Flux<AgentEvent>> next) {
        if (emitter == null) {
            return next.apply(input);
        }
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("messageCount", input.size());
        return withSpan(context, "reasoning", attributes, null, () -> next.apply(input));
    }

    /**
     * 执行阶段拦截：创建acting子Span
     * @param context
     * @param toolCalls
     * @param next
     * @return
     */
    @Override
    public Flux<AgentEvent> onActing(AgentRuntimeContext context, List<AgentToolUseBlock> toolCalls,
                                      Function<List<AgentToolUseBlock>, Flux<AgentEvent>> next) {
        if (emitter == null) {
            return next.apply(toolCalls);
        }
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("toolCount", toolCalls.size());
        return withSpan(context, "acting", attributes, null, () -> next.apply(toolCalls));
    }

    /**
     * 通用Span包裹：入栈建Span，出栈导出SpanInfo
     * @param context
     * @param operation
     * @param attributes
     * @param onEvent
     * @param body
     * @return
     */
    @SuppressWarnings("unchecked")
    private Flux<AgentEvent> withSpan(AgentRuntimeContext context, String operation,
                                       Map<String, Object> attributes, Consumer<AgentEvent> onEvent,
                                       Supplier<Flux<AgentEvent>> body) {
        String existingTraceId = context != null ? (String) context.get(ATTR_TRACE_ID) : null;
        final String traceId = existingTraceId != null ? existingTraceId : newTraceId();
        if (context != null && existingTraceId == null) {
            context.put(ATTR_TRACE_ID, traceId);
        }
        String spanId = newTraceId();
        String parentSpanId = pushSpan(context, spanId);
        long startNanos = System.nanoTime();
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        Flux<AgentEvent> flux = body.get();
        if (onEvent != null) {
            flux = flux.doOnNext(onEvent);
        }
        flux = flux.doOnError(errorRef::set);
        return flux.doFinally(signalType -> {
            popSpan(context, spanId);
            long endMillis = System.currentTimeMillis();
            long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
            Throwable error = errorRef.get();
            String status = error != null ? "ERROR" : "OK";
            String errorMessage = error != null && error.getMessage() != null
                    ? truncate(error.getMessage(), 1024) : null;
            enrichAttributes(context, attributes);
            try {
                emitter.onSpan(new SpanInfo(traceId, spanId, parentSpanId, operation, durationMs, attributes,
                        status, errorMessage, endMillis - durationMs));
            } catch (Exception e) {
                log.warn("[TraceMiddleware] 导出Span异常: {}", e.getMessage());
            }
        });
    }

    /**
     * 冗余上下文字段注入Span属性（taskId/agentCode来自attributes，sessionId/userId/scopeId来自上下文）
     * @param context
     * @param attributes
     */
    private void enrichAttributes(AgentRuntimeContext context, Map<String, Object> attributes) {
        if (context == null) {
            return;
        }
        attributes.putIfAbsent("sessionId", context.getSessionId());
        attributes.putIfAbsent("userId", context.getUserId());
        attributes.putIfAbsent("scopeId", context.getScopeId());
        Object taskId = context.get("taskId");
        if (taskId != null) {
            attributes.putIfAbsent("taskId", taskId);
        }
        Object agentCode = context.get("agentCode");
        if (agentCode != null) {
            attributes.putIfAbsent("agentCode", agentCode);
        }
    }

    /**
     * 截断文本
     * @param text
     * @param maxLength
     * @return
     */
    private static String truncate(String text, int maxLength) {
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }

    /**
     * 入栈当前Span，返回父Span ID
     * @param context
     * @param spanId
     * @return
     */
    @SuppressWarnings("unchecked")
    private String pushSpan(AgentRuntimeContext context, String spanId) {
        if (context == null) {
            return null;
        }
        Object stackObj = context.get(ATTR_SPAN_STACK);
        Deque<String> stack;
        if (stackObj instanceof Deque<?> existing) {
            stack = (Deque<String>) existing;
        } else {
            stack = new ArrayDeque<>();
            context.put(ATTR_SPAN_STACK, stack);
        }
        String parent = stack.peek();
        stack.push(spanId);
        return parent;
    }

    /**
     * 出栈当前Span，仅当栈顶与当前Span一致时弹出
     * @param context
     * @param spanId
     */
    @SuppressWarnings("unchecked")
    private void popSpan(AgentRuntimeContext context, String spanId) {
        if (context == null) {
            return;
        }
        Object stackObj = context.get(ATTR_SPAN_STACK);
        if (stackObj instanceof Deque<?> stack && spanId.equals(stack.peek())) {
            ((Deque<String>) stack).pop();
        }
    }

    /**
     * 生成新的Trace/Span ID（32位十六进制）
     * @return
     */
    private static String newTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}