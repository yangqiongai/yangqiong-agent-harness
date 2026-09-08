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

import java.util.List;
import java.util.function.Function;

import com.yangqiongai.agent.harness.core.message.AgentToolUseBlock;
import com.yangqiongai.agent.harness.engine.AgentRuntimeContext;
import com.yangqiongai.agent.harness.core.event.AgentEvent;
import com.yangqiongai.agent.harness.core.event.AgentEventType;
import com.yangqiongai.agent.harness.core.message.AgentMessage;
import com.yangqiongai.agent.harness.core.middleware.AgentReactiveMiddleware;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

/**
 * Agent执行追踪中间件
 * <p>
 * 构建Span树形结构记录Agent生命周期各阶段（agent→reasoning→acting→tool_call）的执行轨迹，
 * 支持OTLP格式导出，便于接入Jaeger/SkyWalking等分布式追踪系统。
 * </p>
 * @author yangqiong
 */
public class AgentTracingMiddleware implements AgentReactiveMiddleware {

    private static final Logger log = LoggerFactory.getLogger(AgentTracingMiddleware.class);

    /**
     * 追踪开关
     */
    private final boolean enabled;

    /**
     * 追踪收集器（按sessionId隔离Span树）
     */
    private final TraceCollector collector;

    public AgentTracingMiddleware(boolean enabled) {
        this.enabled = enabled;
        this.collector = new TraceCollector();
    }

    public AgentTracingMiddleware() {
        this(true);
    }

    /**
     * 获取追踪收集器，用于导出Trace
     * @return
     */
    public TraceCollector getCollector() {
        return collector;
    }

    /**
     * Agent整体调用拦截，创建根Span并记录执行过程
     * @param context
     * @param input
     * @param next
     * @return
     */
    @Override
    public Flux<AgentEvent> onAgent(AgentRuntimeContext context, List<AgentMessage> input,
                                      Function<List<AgentMessage>, Flux<AgentEvent>> next) {
        if (!enabled) {
            return next.apply(input);
        }
        String sessionId = context != null ? context.getSessionId() : "unknown";
        TraceSpan root = collector.startRoot(sessionId, "agent:" + sessionId, "agent");
        root.putAttribute("inputMessages", input != null ? input.size() : 0);
        log.info("[Trace] Agent开始: session={}, inputMessages={}", sessionId, input != null ? input.size() : 0);
        return next.apply(input)
                .doOnNext(event -> recordEvent(sessionId, event))
                .doFinally(signal -> {
                    root.putAttribute("signal", signal.name());
                    collector.endCurrent(sessionId);
                    long elapsedMs = root.getElapsedMs();
                    log.info("[Trace] Agent结束: session={}, elapsedMs={}, trace={}",
                            sessionId, elapsedMs, collector.exportOtlp(sessionId));
                    collector.clear(sessionId);
                });
    }

    /**
     * 推理阶段拦截，创建reasoning子Span
     * @param context
     * @param input
     * @param next
     * @return
     */
    @Override
    public Flux<AgentEvent> onReasoning(AgentRuntimeContext context, List<AgentMessage> input,
                                          Function<List<AgentMessage>, Flux<AgentEvent>> next) {
        if (!enabled) {
            return next.apply(input);
        }
        String sessionId = context != null ? context.getSessionId() : "unknown";
        TraceSpan span = collector.startChild(sessionId, "reasoning", "reasoning");
        span.putAttribute("messageCount", input != null ? input.size() : 0);
        return next.apply(input)
                .doFinally(signal -> {
                    span.putAttribute("signal", signal.name());
                    collector.endCurrent(sessionId);
                });
    }

    /**
     * 执行阶段拦截，创建acting子Span并记录工具调用
     * @param context
     * @param toolCalls
     * @param next
     * @return
     */
    @Override
    public Flux<AgentEvent> onActing(AgentRuntimeContext context,
                                       List<AgentToolUseBlock> toolCalls,
                                       Function<List<AgentToolUseBlock>, Flux<AgentEvent>> next) {
        if (!enabled) {
            return next.apply(toolCalls);
        }
        String sessionId = context != null ? context.getSessionId() : "unknown";
        TraceSpan span = collector.startChild(sessionId, "acting", "acting");
        span.putAttribute("toolCallCount", toolCalls != null ? toolCalls.size() : 0);
        if (toolCalls != null) {
            for (AgentToolUseBlock call : toolCalls) {
                span.putAttribute("tool:" + call.getToolName(), call.getToolUseId());
            }
        }
        return next.apply(toolCalls)
                .doFinally(signal -> {
                    span.putAttribute("signal", signal.name());
                    collector.endCurrent(sessionId);
                });
    }

    /**
     * 记录事件到当前Span
     * @param sessionId
     * @param event
     */
    private void recordEvent(String sessionId, AgentEvent event) {
        TraceSpan current = collector.getRootSpan(sessionId);
        if (current != null && event.getType() != AgentEventType.TEXT_BLOCK_DELTA
                && event.getType() != AgentEventType.THINKING_BLOCK_DELTA) {
            current.putAttribute("event:" + event.getType().name(), System.currentTimeMillis());
        }
    }
}
