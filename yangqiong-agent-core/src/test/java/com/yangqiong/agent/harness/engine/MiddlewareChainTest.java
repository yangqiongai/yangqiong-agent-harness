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
package com.yangqiong.agent.harness.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.yangqiong.agent.harness.core.event.AgentEvent;
import com.yangqiong.agent.harness.core.message.AgentMessage;
import com.yangqiong.agent.harness.core.message.AgentMessageRole;
import com.yangqiong.agent.harness.core.message.AgentToolResultBlock;
import com.yangqiong.agent.harness.core.message.AgentToolUseBlock;
import com.yangqiong.agent.harness.core.middleware.AgentMiddleware;
import org.junit.jupiter.api.Test;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * 中间件链测试
 * @author yangqiong
 */
class MiddlewareChainTest {

    @Test
    void shouldApplyOnSystemPromptInOrder() {
        AgentMiddleware mw1 = new AgentMiddleware() {
            @Override
            public String onSystemPrompt(String prompt, AgentRuntimeContext ctx) {
                return prompt + "-mw1";
            }
        };
        AgentMiddleware mw2 = new AgentMiddleware() {
            @Override
            public String onSystemPrompt(String prompt, AgentRuntimeContext ctx) {
                return prompt + "-mw2";
            }
        };
        MiddlewareChain chain = new MiddlewareChain(List.of(mw1, mw2));
        String result = chain.applyOnSystemPrompt("base", AgentRuntimeContext.empty());
        assertThat(result).isEqualTo("base-mw1-mw2");
    }

    @Test
    void shouldApplyOnMessageInOrder() {
        AgentMiddleware mw1 = new AgentMiddleware() {
            @Override
            public AgentMessage onMessage(AgentMessage message, AgentRuntimeContext ctx) {
                return AgentMessage.builder()
                        .role(message.getRole())
                        .content(message.getContent())
                        .name("mw1-" + (message.getName() == null ? "" : message.getName()))
                        .build();
            }
        };
        MiddlewareChain chain = new MiddlewareChain(List.of(mw1));
        AgentMessage msg = AgentMessage.builder().role(AgentMessageRole.USER).build();
        AgentMessage result = chain.applyOnMessage(msg, AgentRuntimeContext.empty());
        assertThat(result.getName()).isEqualTo("mw1-");
    }

    @Test
    void shouldApplyOnToolCallInOrder() {
        AgentMiddleware mw1 = new AgentMiddleware() {
            @Override
            public Map<String, Object> onToolCall(String toolName, Map<String, Object> input, AgentRuntimeContext ctx) {
                Map<String, Object> modified = new java.util.HashMap<>(input);
                modified.put("modified", true);
                return modified;
            }
        };
        MiddlewareChain chain = new MiddlewareChain(List.of(mw1));
        Map<String, Object> result = chain.applyOnToolCall("tool", Map.of("key", "val"), AgentRuntimeContext.empty());
        assertThat(result).containsEntry("key", "val").containsEntry("modified", true);
    }

    @Test
    void shouldApplyOnToolResultInOrder() {
        AgentMiddleware mw1 = new AgentMiddleware() {
            @Override
            public AgentToolResultBlock onToolResult(String toolName, AgentToolResultBlock result, AgentRuntimeContext ctx) {
                return AgentToolResultBlock.error("modified");
            }
        };
        MiddlewareChain chain = new MiddlewareChain(List.of(mw1));
        AgentToolResultBlock result = chain.applyOnToolResult("tool", AgentToolResultBlock.of(List.of()), AgentRuntimeContext.empty());
        assertThat(result.isError()).isTrue();
    }

    @Test
    void shouldApplyOnErrorToAll() {
        AtomicBoolean called = new AtomicBoolean(false);
        AgentMiddleware mw1 = new AgentMiddleware() {
            @Override
            public void onError(Throwable t, AgentRuntimeContext ctx, String phase) {
                called.set(true);
            }
        };
        MiddlewareChain chain = new MiddlewareChain(List.of(mw1));
        chain.applyOnError(new RuntimeException("err"), AgentRuntimeContext.empty(), "reasoning");
        assertThat(called.get()).isTrue();
    }

    @Test
    void shouldWrapOnAgentInOnionOrder() {
        AtomicInteger order = new AtomicInteger(0);
        StringBuilder sb = new StringBuilder();

        AgentMiddleware outer = new AgentMiddleware() {
            @Override
            public Flux<AgentEvent> onAgent(AgentRuntimeContext ctx, List<AgentMessage> input,
                                             java.util.function.Function<List<AgentMessage>, Flux<AgentEvent>> next) {
                sb.append("outer-before-");
                return next.apply(input).doOnNext(e -> sb.append("outer-after-"));
            }
        };
        AgentMiddleware inner = new AgentMiddleware() {
            @Override
            public Flux<AgentEvent> onAgent(AgentRuntimeContext ctx, List<AgentMessage> input,
                                             java.util.function.Function<List<AgentMessage>, Flux<AgentEvent>> next) {
                sb.append("inner-before-");
                return next.apply(input).doOnNext(e -> sb.append("inner-after-"));
            }
        };
        MiddlewareChain chain = new MiddlewareChain(List.of(outer, inner));
        StepVerifier.create(chain.applyOnAgent(AgentRuntimeContext.empty(), List.of(), msgs -> {
            sb.append("core-");
            return Flux.just(AgentEvent.completed());
        })).expectNextCount(1).verifyComplete();

        assertThat(sb.toString()).contains("outer-before-");
        assertThat(sb.toString()).contains("inner-before-");
        assertThat(sb.toString()).contains("core-");
    }

    @Test
    void shouldWrapOnReasoningInOnionOrder() {
        AtomicBoolean called = new AtomicBoolean(false);
        AgentMiddleware mw = new AgentMiddleware() {
            @Override
            public Flux<AgentEvent> onReasoning(AgentRuntimeContext ctx, List<AgentMessage> input,
                                                 java.util.function.Function<List<AgentMessage>, Flux<AgentEvent>> next) {
                called.set(true);
                return next.apply(input);
            }
        };
        MiddlewareChain chain = new MiddlewareChain(List.of(mw));
        StepVerifier.create(chain.applyOnReasoning(AgentRuntimeContext.empty(), List.of(), msgs -> Flux.just(AgentEvent.completed())))
                .expectNextCount(1)
                .verifyComplete();
        assertThat(called.get()).isTrue();
    }

    @Test
    void shouldWrapOnActingInOnionOrder() {
        AtomicBoolean called = new AtomicBoolean(false);
        AgentMiddleware mw = new AgentMiddleware() {
            @Override
            public Flux<AgentEvent> onActing(AgentRuntimeContext ctx, List<AgentToolUseBlock> toolCalls,
                                              java.util.function.Function<List<AgentToolUseBlock>, Flux<AgentEvent>> next) {
                called.set(true);
                return next.apply(toolCalls);
            }
        };
        MiddlewareChain chain = new MiddlewareChain(List.of(mw));
        StepVerifier.create(chain.applyOnActing(AgentRuntimeContext.empty(), List.of(), calls -> Flux.just(AgentEvent.completed())))
                .expectNextCount(1)
                .verifyComplete();
        assertThat(called.get()).isTrue();
    }

    @Test
    void shouldWrapOnModelCallInOnionOrder() {
        AtomicBoolean called = new AtomicBoolean(false);
        AgentMiddleware mw = new AgentMiddleware() {
            @Override
            public Mono<AgentMessage> onModelCall(AgentRuntimeContext ctx, List<AgentMessage> input,
                                                   java.util.function.Function<List<AgentMessage>, Mono<AgentMessage>> next) {
                called.set(true);
                return next.apply(input);
            }
        };
        MiddlewareChain chain = new MiddlewareChain(List.of(mw));
        AgentMessage msg = AgentMessage.builder().role(AgentMessageRole.ASSISTANT).build();
        StepVerifier.create(chain.applyOnModelCall(AgentRuntimeContext.empty(), List.of(), msgs -> Mono.just(msg)))
                .expectNext(msg)
                .verifyComplete();
        assertThat(called.get()).isTrue();
    }

    @Test
    void shouldPassThroughWhenEmptyMiddlewareList() {
        MiddlewareChain chain = new MiddlewareChain(List.of());
        assertThat(chain.applyOnSystemPrompt("test", AgentRuntimeContext.empty())).isEqualTo("test");

        AgentMessage msg = AgentMessage.builder().role(AgentMessageRole.USER).build();
        assertThat(chain.applyOnMessage(msg, AgentRuntimeContext.empty())).isEqualTo(msg);

        StepVerifier.create(chain.applyOnAgent(AgentRuntimeContext.empty(), List.of(), msgs -> Flux.just(AgentEvent.completed())))
                .expectNextCount(1)
                .verifyComplete();
    }

    @Test
    void shouldPassThroughWhenNullMiddlewareList() {
        MiddlewareChain chain = new MiddlewareChain(null);
        assertThat(chain.applyOnSystemPrompt("test", AgentRuntimeContext.empty())).isEqualTo("test");
    }

    @Test
    void shouldShortCircuitOnReasoning() {
        AgentMiddleware shortCircuit = new AgentMiddleware() {
            @Override
            public Flux<AgentEvent> onReasoning(AgentRuntimeContext ctx, List<AgentMessage> input,
                                                 java.util.function.Function<List<AgentMessage>, Flux<AgentEvent>> next) {
                return Flux.just(AgentEvent.of(AgentEvent.completed().getType(), "short"));
            }
        };
        MiddlewareChain chain = new MiddlewareChain(List.of(shortCircuit));
        AtomicBoolean coreCalled = new AtomicBoolean(false);
        StepVerifier.create(chain.applyOnReasoning(AgentRuntimeContext.empty(), List.of(), msgs -> {
            coreCalled.set(true);
            return Flux.just(AgentEvent.completed());
        })).expectNextCount(1).verifyComplete();
        assertThat(coreCalled.get()).isFalse();
    }
}
