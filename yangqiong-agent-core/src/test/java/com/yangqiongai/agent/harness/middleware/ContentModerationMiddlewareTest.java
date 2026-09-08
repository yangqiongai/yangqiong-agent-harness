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

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import com.yangqiongai.agent.harness.core.event.AgentEvent;
import com.yangqiongai.agent.harness.core.event.AgentEventType;
import com.yangqiongai.agent.harness.core.message.AgentMessage;
import com.yangqiongai.agent.harness.core.message.AgentMessageRole;
import com.yangqiongai.agent.harness.core.message.AgentTextBlock;
import com.yangqiongai.agent.harness.engine.AgentRuntimeContext;
import com.yangqiongai.agent.harness.guardrail.ContentModerationPolicy;
import com.yangqiongai.agent.harness.guardrail.ModerationVerdict;
import com.yangqiongai.agent.harness.core.message.MessageFactory;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

/**
 * 内容审查中间件测试
 * @author yangqiong
 */
class ContentModerationMiddlewareTest {

    @Test
    void shouldBlockWhenVerdictIsBlock() {
        ContentModerationPolicy policy = message -> ModerationVerdict.block("涉政内容");
        ContentModerationMiddleware middleware = new ContentModerationMiddleware(policy);
        AtomicBoolean nextCalled = new AtomicBoolean(false);
        Function<List<AgentMessage>, Flux<AgentEvent>> next = input -> {
            nextCalled.set(true);
            return Flux.empty();
        };
        AgentMessage userMsg = MessageFactory.createUserMessage("敏感内容测试");

        List<AgentEvent> events = middleware.onReasoning(AgentRuntimeContext.empty(), List.of(userMsg), next)
                .collectList().block();

        assertThat(events).isNotEmpty();
        assertThat(events.get(0).getType()).isEqualTo(AgentEventType.ERROR);
        assertThat(events.get(0).getPayload()).isInstanceOf(ContentModerationMiddleware.ContentModerationException.class);
        assertThat(nextCalled.get()).isFalse();
    }

    @Test
    void shouldMaskWhenVerdictIsMask() {
        ContentModerationPolicy policy = message -> ModerationVerdict.mask("敏感词");
        ContentModerationMiddleware middleware = new ContentModerationMiddleware(policy);
        AtomicBoolean nextCalled = new AtomicBoolean(false);
        Function<List<AgentMessage>, Flux<AgentEvent>> next = input -> {
            nextCalled.set(true);
            AgentMessage masked = input.get(0);
            assertThat(masked.getTextContent()).startsWith("[内容已脱敏");
            return Flux.empty();
        };
        AgentMessage userMsg = MessageFactory.createUserMessage("包含敏感词的内容");

        middleware.onReasoning(AgentRuntimeContext.empty(), List.of(userMsg), next).blockLast();

        assertThat(nextCalled.get()).isTrue();
    }

    @Test
    void shouldPassThroughWhenVerdictIsPass() {
        ContentModerationPolicy policy = message -> ModerationVerdict.pass();
        ContentModerationMiddleware middleware = new ContentModerationMiddleware(policy);
        AtomicBoolean nextCalled = new AtomicBoolean(false);
        Function<List<AgentMessage>, Flux<AgentEvent>> next = input -> {
            nextCalled.set(true);
            return Flux.empty();
        };
        AgentMessage userMsg = MessageFactory.createUserMessage("正常内容");

        middleware.onReasoning(AgentRuntimeContext.empty(), List.of(userMsg), next).blockLast();

        assertThat(nextCalled.get()).isTrue();
    }

    @Test
    void shouldPassThroughWithDefaultPolicy() {
        ContentModerationMiddleware middleware = new ContentModerationMiddleware(null);
        AtomicBoolean nextCalled = new AtomicBoolean(false);
        Function<List<AgentMessage>, Flux<AgentEvent>> next = input -> {
            nextCalled.set(true);
            return Flux.empty();
        };
        AgentMessage userMsg = MessageFactory.createUserMessage("任意内容");

        middleware.onReasoning(AgentRuntimeContext.empty(), List.of(userMsg), next).blockLast();

        assertThat(nextCalled.get()).isTrue();
    }

    @Test
    void shouldOnlyCheckUserMessages() {
        ContentModerationPolicy policy = message -> ModerationVerdict.block("违规");
        ContentModerationMiddleware middleware = new ContentModerationMiddleware(policy);
        AtomicBoolean nextCalled = new AtomicBoolean(false);
        Function<List<AgentMessage>, Flux<AgentEvent>> next = input -> {
            nextCalled.set(true);
            return Flux.empty();
        };
        // assistant消息即使命中审查策略也不被拦截（仅审查USER输入）
        AgentMessage assistantMsg = AgentMessage.builder()
                .role(AgentMessageRole.ASSISTANT)
                .content(List.of(AgentTextBlock.builder().text("违规内容").build()))
                .build();

        middleware.onReasoning(AgentRuntimeContext.empty(), List.of(assistantMsg), next).blockLast();

        assertThat(nextCalled.get()).isTrue();
    }
}