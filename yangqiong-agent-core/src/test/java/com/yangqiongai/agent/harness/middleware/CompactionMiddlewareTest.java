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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import com.yangqiongai.agent.harness.engine.AgentRuntimeContext;
import com.yangqiongai.agent.harness.config.AgentCompactionConfig;
import com.yangqiongai.agent.harness.core.event.AgentEvent;
import com.yangqiongai.agent.harness.core.message.AgentMessage;
import com.yangqiongai.agent.harness.core.message.AgentMessageRole;
import com.yangqiongai.agent.harness.core.message.AgentTextBlock;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

/**
 * 上下文压缩中间件测试
 * @author yangqiong
 */
class CompactionMiddlewareTest {

    private AgentMessage buildMessage(AgentMessageRole role, String text) {
        return AgentMessage.builder()
                .role(role)
                .content(Collections.singletonList(AgentTextBlock.builder().text(text).build()))
                .build();
    }

    @Test
    void shouldNotCompactWhenSizeBelowTrigger() {
        AgentCompactionConfig config = AgentCompactionConfig.builder()
                .triggerMessages(5)
                .keepMessages(2)
                .build();
        CompactionMiddleware middleware = new CompactionMiddleware(config);
        List<AgentMessage> input = List.of(buildMessage(AgentMessageRole.USER, "hi"));

        StepVerifier.create(middleware.onReasoning(AgentRuntimeContext.empty(), input, msgs -> Flux.empty()))
                .verifyComplete();
    }

    @Test
    void shouldCompactWhenSizeExceedsTrigger() {
        AgentCompactionConfig config = AgentCompactionConfig.builder()
                .triggerMessages(3)
                .keepMessages(2)
                .build();
        CompactionMiddleware middleware = new CompactionMiddleware(config);
        List<AgentMessage> input = new ArrayList<>();
        input.add(buildMessage(AgentMessageRole.USER, "m1"));
        input.add(buildMessage(AgentMessageRole.USER, "m2"));
        input.add(buildMessage(AgentMessageRole.USER, "m3"));
        input.add(buildMessage(AgentMessageRole.USER, "m4"));

        final java.util.concurrent.atomic.AtomicReference<List<AgentMessage>> captured = new java.util.concurrent.atomic.AtomicReference<>();
        Function<List<AgentMessage>, Flux<AgentEvent>> next = msgs -> {
            captured.set(msgs);
            return Flux.empty();
        };
        middleware.onReasoning(AgentRuntimeContext.empty(), input, next).blockLast();
        assertThat(captured.get()).hasSize(2);
    }

    @Test
    void shouldKeepSystemMessageWhenCompacting() {
        AgentCompactionConfig config = AgentCompactionConfig.builder()
                .triggerMessages(2)
                .keepMessages(2)
                .build();
        CompactionMiddleware middleware = new CompactionMiddleware(config);
        List<AgentMessage> input = new ArrayList<>();
        input.add(buildMessage(AgentMessageRole.SYSTEM, "system"));
        input.add(buildMessage(AgentMessageRole.USER, "u1"));
        input.add(buildMessage(AgentMessageRole.USER, "u2"));
        input.add(buildMessage(AgentMessageRole.USER, "u3"));

        final java.util.concurrent.atomic.AtomicReference<List<AgentMessage>> captured = new java.util.concurrent.atomic.AtomicReference<>();
        Function<List<AgentMessage>, Flux<AgentEvent>> next = msgs -> {
            captured.set(msgs);
            return Flux.empty();
        };
        middleware.onReasoning(AgentRuntimeContext.empty(), input, next).blockLast();
        assertThat(captured.get()).hasSize(2);
        assertThat(captured.get().get(0).getRole()).isEqualTo(AgentMessageRole.SYSTEM);
    }

    @Test
    void shouldUseDefaultConfigWhenNullProvided() {
        CompactionMiddleware middleware = new CompactionMiddleware(null);
        List<AgentMessage> input = List.of(buildMessage(AgentMessageRole.USER, "hi"));
        StepVerifier.create(middleware.onReasoning(AgentRuntimeContext.empty(), input, msgs -> Flux.just(AgentEvent.completed())))
                .expectNextCount(1)
                .verifyComplete();
    }

    @Test
    void shouldHandleNullInput() {
        CompactionMiddleware middleware = new CompactionMiddleware(null);
        StepVerifier.create(middleware.onReasoning(AgentRuntimeContext.empty(), null, msgs -> Flux.empty()))
                .verifyComplete();
    }
}
