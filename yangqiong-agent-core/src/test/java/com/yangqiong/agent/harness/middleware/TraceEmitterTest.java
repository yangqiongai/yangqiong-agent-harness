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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import com.yangqiong.agent.harness.core.trace.SpanInfo;
import com.yangqiong.agent.harness.engine.AgentRuntimeContext;
import com.yangqiong.agent.harness.core.message.AgentMessage;
import com.yangqiong.agent.harness.core.message.MessageFactory;
import com.yangqiong.agent.harness.core.message.AgentToolUseBlock;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

/**
 * 追踪中间件测试
 * @author yangqiong
 */
class TraceEmitterTest {

    @Test
    void shouldEmitRootAndNestedChildSpan() {
        List<SpanInfo> spans = new ArrayList<>();
        TraceMiddleware middleware = new TraceMiddleware(spans::add);
        AgentRuntimeContext context = AgentRuntimeContext.empty();
        AgentMessage msg = MessageFactory.createUserMessage("hi");

        middleware.onAgent(context, List.of(msg), input ->
                        middleware.onReasoning(context, input, inner -> Flux.empty()))
                .blockLast();

        assertThat(spans).hasSize(2);
        SpanInfo root = spans.stream().filter(s -> "agent_run".equals(s.getOperation())).findFirst().orElseThrow();
        SpanInfo reasoning = spans.stream().filter(s -> "reasoning".equals(s.getOperation())).findFirst().orElseThrow();
        // 同一traceId贯穿
        assertThat(reasoning.getTraceId()).isEqualTo(root.getTraceId());
        // 子span的父spanId指向根span
        assertThat(reasoning.getParentSpanId()).isEqualTo(root.getSpanId());
        // 根span无父span
        assertThat(root.getParentSpanId()).isNull();
    }

    @Test
    void shouldEmitActingSpanWithToolCount() {
        List<SpanInfo> spans = new ArrayList<>();
        TraceMiddleware middleware = new TraceMiddleware(spans::add);
        AgentRuntimeContext context = AgentRuntimeContext.empty();
        var toolCalls = List.of(
                new AgentToolUseBlock("search", "id1", java.util.Map.of()));

        middleware.onActing(context, toolCalls, calls -> Flux.empty()).blockLast();

        assertThat(spans).hasSize(1);
        SpanInfo acting = spans.get(0);
        assertThat(acting.getOperation()).isEqualTo("acting");
        assertThat(acting.getAttributes()).containsEntry("toolCount", 1);
    }

    @Test
    void shouldDegradeSilentlyWithoutEmitter() {
        TraceMiddleware middleware = new TraceMiddleware(null);
        AgentRuntimeContext context = AgentRuntimeContext.empty();
        AtomicBoolean nextCalled = new AtomicBoolean(false);

        middleware.onReasoning(context, List.of(MessageFactory.createUserMessage("hi")), input -> {
            nextCalled.set(true);
            return Flux.empty();
        }).blockLast();

        assertThat(nextCalled).isTrue();
    }
}