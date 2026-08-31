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

import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import com.yangqiong.agent.harness.engine.AgentRuntimeContext;
import com.yangqiong.agent.harness.config.AgentToolResultEvictionConfig;
import com.yangqiong.agent.harness.core.event.AgentEvent;
import com.yangqiong.agent.harness.core.message.AgentMessage;
import com.yangqiong.agent.harness.core.message.AgentMessageRole;
import com.yangqiong.agent.harness.core.message.AgentTextBlock;
import com.yangqiong.agent.harness.core.message.AgentToolResultBlock;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

/**
 * 工具结果驱逐中间件测试
 * @author yangqiong
 */
class ToolResultEvictionMiddlewareTest {

    @Test
    void shouldEvictLargeToolResult() {
        AgentToolResultEvictionConfig config = AgentToolResultEvictionConfig.builder()
                .maxResultChars(5)
                .previewChars(3)
                .build();
        ToolResultEvictionMiddleware middleware = new ToolResultEvictionMiddleware(config);
        AgentToolResultBlock bigResult = AgentToolResultBlock.of(List.of(
                AgentTextBlock.builder().text("1234567890").build()));
        AgentMessage toolMsg = AgentMessage.builder()
                .role(AgentMessageRole.TOOL)
                .content(Collections.singletonList(bigResult))
                .build();

        final java.util.concurrent.atomic.AtomicReference<List<AgentMessage>> captured = new java.util.concurrent.atomic.AtomicReference<>();
        Function<List<AgentMessage>, Flux<AgentEvent>> next = msgs -> {
            captured.set(msgs);
            return Flux.empty();
        };
        middleware.onReasoning(AgentRuntimeContext.empty(), List.of(toolMsg), next).blockLast();
        AgentMessage result = captured.get().get(0);
        AgentToolResultBlock block = (AgentToolResultBlock) result.getContent().get(0);
        assertThat(block.getTextContent()).contains("123");
        assertThat(block.getTextContent()).contains("[已截断]");
    }

    @Test
    void shouldKeepSmallToolResult() {
        AgentToolResultEvictionConfig config = AgentToolResultEvictionConfig.builder()
                .maxResultChars(100)
                .previewChars(10)
                .build();
        ToolResultEvictionMiddleware middleware = new ToolResultEvictionMiddleware(config);
        AgentToolResultBlock smallResult = AgentToolResultBlock.of(List.of(
                AgentTextBlock.builder().text("small").build()));
        AgentMessage toolMsg = AgentMessage.builder()
                .role(AgentMessageRole.TOOL)
                .content(Collections.singletonList(smallResult))
                .build();

        final java.util.concurrent.atomic.AtomicReference<List<AgentMessage>> captured = new java.util.concurrent.atomic.AtomicReference<>();
        Function<List<AgentMessage>, Flux<AgentEvent>> next = msgs -> {
            captured.set(msgs);
            return Flux.empty();
        };
        middleware.onReasoning(AgentRuntimeContext.empty(), List.of(toolMsg), next).blockLast();
        AgentToolResultBlock block = (AgentToolResultBlock) captured.get().get(0).getContent().get(0);
        assertThat(block.getTextContent()).isEqualTo("small");
    }

    @Test
    void shouldSkipWhenConfigIsNull() {
        ToolResultEvictionMiddleware middleware = new ToolResultEvictionMiddleware(null);
        AgentMessage msg = AgentMessage.builder()
                .role(AgentMessageRole.USER)
                .content(Collections.singletonList(AgentTextBlock.builder().text("test").build()))
                .build();
        StepVerifier.create(middleware.onReasoning(AgentRuntimeContext.empty(), List.of(msg), msgs -> Flux.just(AgentEvent.completed())))
                .expectNextCount(1)
                .verifyComplete();
    }

    @Test
    void shouldHandleNullInput() {
        AgentToolResultEvictionConfig config = AgentToolResultEvictionConfig.builder()
                .maxResultChars(10)
                .previewChars(5)
                .build();
        ToolResultEvictionMiddleware middleware = new ToolResultEvictionMiddleware(config);
        StepVerifier.create(middleware.onReasoning(AgentRuntimeContext.empty(), null, msgs -> Flux.empty()))
                .verifyComplete();
    }

    @Test
    void shouldHandleErrorMessage() {
        AgentToolResultEvictionConfig config = AgentToolResultEvictionConfig.builder()
                .maxResultChars(3)
                .previewChars(2)
                .build();
        ToolResultEvictionMiddleware middleware = new ToolResultEvictionMiddleware(config);
        AgentToolResultBlock errorResult = AgentToolResultBlock.error("1234567890");
        AgentMessage toolMsg = AgentMessage.builder()
                .role(AgentMessageRole.TOOL)
                .content(Collections.singletonList(errorResult))
                .build();

        final java.util.concurrent.atomic.AtomicReference<List<AgentMessage>> captured = new java.util.concurrent.atomic.AtomicReference<>();
        Function<List<AgentMessage>, Flux<AgentEvent>> next = msgs -> {
            captured.set(msgs);
            return Flux.empty();
        };
        middleware.onReasoning(AgentRuntimeContext.empty(), List.of(toolMsg), next).blockLast();
        AgentToolResultBlock block = (AgentToolResultBlock) captured.get().get(0).getContent().get(0);
        assertThat(block.isError()).isTrue();
    }
}
