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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.yangqiong.agent.harness.core.message.MessageFactory;
import com.yangqiong.agent.harness.model.ModelCaller;
import com.yangqiong.agent.harness.model.ModelResponseParser;
import com.yangqiong.agent.harness.tool.ToolExecutor;
import com.yangqiong.agent.harness.core.HarnessAgentRuntimeBuilder;
import com.yangqiong.agent.harness.core.message.AgentMessage;
import com.yangqiong.agent.harness.core.message.AgentMessageRole;
import com.yangqiong.agent.harness.core.message.AgentTextBlock;
import com.yangqiong.agent.harness.core.message.AgentToolResultBlock;
import com.yangqiong.agent.harness.core.message.AgentToolUseBlock;
import com.yangqiong.agent.harness.core.model.AgentChatResponse;
import org.junit.jupiter.api.Test;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * ReAct执行引擎测试
 * @author yangqiong
 */
class ReActEngineTest {

    @Test
    void shouldReturnTextResponseInSingleIteration() {
        ModelCaller modelCaller = mock(ModelCaller.class);
        AgentTextBlock textBlock = AgentTextBlock.builder().text("hello").build();
        AgentChatResponse response = new AgentChatResponse(List.of(textBlock), null);
        when(modelCaller.stream(any(), any())).thenReturn(Flux.just(response));

        ToolExecutor toolExecutor = mock(ToolExecutor.class);
        MiddlewareChain chain = new MiddlewareChain(List.of());
        ModelResponseParser parser = new ModelResponseParser();

        ReActEngine engine = new ReActEngine(modelCaller, toolExecutor, chain, parser, null);
        EngineContext ctx = new EngineContext(null, null, AgentRuntimeContext.empty(), null, 10, null);

        AgentMessage input = MessageFactory.createUserMessage("hi");
        StepVerifier.create(engine.call(List.of(input), ctx))
                .assertNext(msg -> {
                    assertThat(msg.getRole()).isEqualTo(AgentMessageRole.ASSISTANT);
                    assertThat(((AgentTextBlock) msg.getContent().get(0)).getText()).isEqualTo("hello");
                })
                .verifyComplete();
    }

    @Test
    void shouldExecuteToolAndContinue() {
        ModelCaller modelCaller = mock(ModelCaller.class);
        ToolExecutor toolExecutor = mock(ToolExecutor.class);

        AgentToolUseBlock toolUse = new AgentToolUseBlock("search", "id1", Map.of("q", "test"));
        AgentChatResponse firstResponse = new AgentChatResponse(List.of(toolUse), null);

        AgentTextBlock textBlock = AgentTextBlock.builder().text("final answer").build();
        AgentChatResponse secondResponse = new AgentChatResponse(List.of(textBlock), null);

        when(modelCaller.stream(any(), any()))
                .thenReturn(Flux.just(firstResponse))
                .thenReturn(Flux.just(secondResponse));

        AgentToolResultBlock toolResult = AgentToolResultBlock.of(List.of(
                AgentTextBlock.builder().text("search result").build()));
        AgentMessage toolMsg = MessageFactory.createToolMessage(toolResult);
        when(toolExecutor.executeTools(any(), any(), any())).thenReturn(Mono.just(List.of(toolMsg)));

        MiddlewareChain chain = new MiddlewareChain(List.of());
        ModelResponseParser parser = new ModelResponseParser();

        ReActEngine engine = new ReActEngine(modelCaller, toolExecutor, chain, parser, null);
        EngineContext ctx = new EngineContext(null, null, AgentRuntimeContext.empty(), null, 10, null);

        AgentMessage input = MessageFactory.createUserMessage("search for test");
        StepVerifier.create(engine.call(List.of(input), ctx))
                .assertNext(msg -> {
                    assertThat(((AgentTextBlock) msg.getContent().get(0)).getText()).isEqualTo("final answer");
                })
                .verifyComplete();
    }

    @Test
    void shouldStopAtMaxIters() {
        ModelCaller modelCaller = mock(ModelCaller.class);
        ToolExecutor toolExecutor = mock(ToolExecutor.class);

        AgentToolUseBlock toolUse = new AgentToolUseBlock("search", "id1", Map.of());
        AgentChatResponse response = new AgentChatResponse(List.of(toolUse), null);
        when(modelCaller.stream(any(), any())).thenReturn(Flux.just(response));

        AgentToolResultBlock toolResult = AgentToolResultBlock.of(List.of(
                AgentTextBlock.builder().text("result").build()));
        AgentMessage toolMsg = MessageFactory.createToolMessage(toolResult);
        when(toolExecutor.executeTools(any(), any(), any())).thenReturn(Mono.just(List.of(toolMsg)));

        MiddlewareChain chain = new MiddlewareChain(List.of());
        ModelResponseParser parser = new ModelResponseParser();

        ReActEngine engine = new ReActEngine(modelCaller, toolExecutor, chain, parser, null);
        EngineContext ctx = new EngineContext(null, null, AgentRuntimeContext.empty(), null, 1, null);

        AgentMessage input = MessageFactory.createUserMessage("hi");
        StepVerifier.create(engine.call(List.of(input), ctx))
                .assertNext(msg -> assertThat(msg.getContent()).isNotNull())
                .verifyComplete();
    }

    @Test
    void shouldInjectSystemPrompt() {
        ModelCaller modelCaller = mock(ModelCaller.class);
        AgentTextBlock textBlock = AgentTextBlock.builder().text("response").build();
        AgentChatResponse response = new AgentChatResponse(List.of(textBlock), null);

        when(modelCaller.stream(any(), any())).thenAnswer(invocation -> {
            List<AgentMessage> msgs = invocation.getArgument(0);
            assertThat(msgs.get(0).getRole()).isEqualTo(AgentMessageRole.SYSTEM);
            return Flux.just(response);
        });

        ToolExecutor toolExecutor = mock(ToolExecutor.class);
        MiddlewareChain chain = new MiddlewareChain(List.of());
        ModelResponseParser parser = new ModelResponseParser();

        ReActEngine engine = new ReActEngine(modelCaller, toolExecutor, chain, parser, null);
        AgentRuntimeContext runtimeCtx = AgentRuntimeContext.empty();
        EngineContext ctx = new EngineContext(null, "you are an agent", runtimeCtx, null, 10, null);

        AgentMessage input = MessageFactory.createUserMessage("hi");
        StepVerifier.create(engine.call(List.of(input), ctx))
                .assertNext(msg -> assertThat(msg.getContent()).isNotNull())
                .verifyComplete();
    }

    @Test
    void shouldNotInjectSystemPromptWhenNull() {
        ModelCaller modelCaller = mock(ModelCaller.class);
        AgentTextBlock textBlock = AgentTextBlock.builder().text("response").build();
        AgentChatResponse response = new AgentChatResponse(List.of(textBlock), null);

        when(modelCaller.stream(any(), any())).thenAnswer(invocation -> {
            List<AgentMessage> msgs = invocation.getArgument(0);
            AgentMessage first = msgs.get(0);
            assertThat(first.getRole()).isNotEqualTo(AgentMessageRole.SYSTEM);
            return Flux.just(response);
        });

        ToolExecutor toolExecutor = mock(ToolExecutor.class);
        MiddlewareChain chain = new MiddlewareChain(List.of());
        ModelResponseParser parser = new ModelResponseParser();

        ReActEngine engine = new ReActEngine(modelCaller, toolExecutor, chain, parser, null);
        EngineContext ctx = new EngineContext(null, null, AgentRuntimeContext.empty(), null, 10, null);

        AgentMessage input = MessageFactory.createUserMessage("hi");
        StepVerifier.create(engine.call(List.of(input), ctx))
                .assertNext(msg -> assertThat(msg.getContent()).isNotNull())
                .verifyComplete();
    }

    @Test
    void shouldHandleModelErrorInCall() {
        ModelCaller modelCaller = mock(ModelCaller.class);
        when(modelCaller.stream(any(), any())).thenReturn(Flux.error(new RuntimeException("model error")));

        ToolExecutor toolExecutor = mock(ToolExecutor.class);
        MiddlewareChain chain = new MiddlewareChain(List.of());
        ModelResponseParser parser = new ModelResponseParser();

        ReActEngine engine = new ReActEngine(modelCaller, toolExecutor, chain, parser, null);
        EngineContext ctx = new EngineContext(null, null, AgentRuntimeContext.empty(), null, 10, null);

        AgentMessage input = MessageFactory.createUserMessage("hi");
        StepVerifier.create(engine.call(List.of(input), ctx))
                .expectError(RuntimeException.class)
                .verify();
    }

    @Test
    void shouldStreamEvents() {
        ModelCaller modelCaller = mock(ModelCaller.class);
        AgentTextBlock textBlock = AgentTextBlock.builder().text("hello").build();
        AgentChatResponse response = new AgentChatResponse(List.of(textBlock), null);
        when(modelCaller.stream(any(), any())).thenReturn(Flux.just(response));

        ToolExecutor toolExecutor = mock(ToolExecutor.class);
        MiddlewareChain chain = new MiddlewareChain(List.of());
        ModelResponseParser parser = new ModelResponseParser();

        ReActEngine engine = new ReActEngine(modelCaller, toolExecutor, chain, parser, null);
        EngineContext ctx = new EngineContext(null, null, AgentRuntimeContext.empty(), null, 10, null);

        AgentMessage input = MessageFactory.createUserMessage("hi");
        StepVerifier.create(engine.stream(List.of(input), ctx))
                .expectNextCount(1)
                .thenCancel()
                .verify();
    }

    @Test
    void shouldHandleMultipleToolCalls() {
        ModelCaller modelCaller = mock(ModelCaller.class);
        ToolExecutor toolExecutor = mock(ToolExecutor.class);

        AgentToolUseBlock toolUse1 = new AgentToolUseBlock("tool1", "id1", Map.of());
        AgentToolUseBlock toolUse2 = new AgentToolUseBlock("tool2", "id2", Map.of());
        AgentChatResponse firstResponse = new AgentChatResponse(List.of(toolUse1, toolUse2), null);

        AgentTextBlock textBlock = AgentTextBlock.builder().text("done").build();
        AgentChatResponse secondResponse = new AgentChatResponse(List.of(textBlock), null);

        when(modelCaller.stream(any(), any()))
                .thenReturn(Flux.just(firstResponse))
                .thenReturn(Flux.just(secondResponse));

        AgentToolResultBlock result1 = AgentToolResultBlock.of(List.of(AgentTextBlock.builder().text("r1").build()));
        AgentToolResultBlock result2 = AgentToolResultBlock.of(List.of(AgentTextBlock.builder().text("r2").build()));
        when(toolExecutor.executeTools(any(), any(), any()))
                .thenReturn(Mono.just(List.of(
                        MessageFactory.createToolMessage(result1),
                        MessageFactory.createToolMessage(result2)
                )));

        MiddlewareChain chain = new MiddlewareChain(List.of());
        ModelResponseParser parser = new ModelResponseParser();

        ReActEngine engine = new ReActEngine(modelCaller, toolExecutor, chain, parser, null);
        EngineContext ctx = new EngineContext(null, null, AgentRuntimeContext.empty(), null, 10, null);

        AgentMessage input = MessageFactory.createUserMessage("run tools");
        StepVerifier.create(engine.call(List.of(input), ctx))
                .assertNext(msg -> assertThat(((AgentTextBlock) msg.getContent().get(0)).getText()).isEqualTo("done"))
                .verifyComplete();
    }

    @Test
    void shouldHandleToolErrorAndContinue() {
        ModelCaller modelCaller = mock(ModelCaller.class);
        ToolExecutor toolExecutor = mock(ToolExecutor.class);

        AgentToolUseBlock toolUse = new AgentToolUseBlock("failing_tool", "id1", Map.of());
        AgentChatResponse firstResponse = new AgentChatResponse(List.of(toolUse), null);

        AgentTextBlock textBlock = AgentTextBlock.builder().text("after error").build();
        AgentChatResponse secondResponse = new AgentChatResponse(List.of(textBlock), null);

        when(modelCaller.stream(any(), any()))
                .thenReturn(Flux.just(firstResponse))
                .thenReturn(Flux.just(secondResponse));

        AgentToolResultBlock errorResult = AgentToolResultBlock.error("tool failed");
        when(toolExecutor.executeTools(any(), any(), any()))
                .thenReturn(Mono.just(List.of(MessageFactory.createToolMessage(errorResult))));

        MiddlewareChain chain = new MiddlewareChain(List.of());
        ModelResponseParser parser = new ModelResponseParser();

        ReActEngine engine = new ReActEngine(modelCaller, toolExecutor, chain, parser, null);
        EngineContext ctx = new EngineContext(null, null, AgentRuntimeContext.empty(), null, 10, null);

        AgentMessage input = MessageFactory.createUserMessage("hi");
        StepVerifier.create(engine.call(List.of(input), ctx))
                .assertNext(msg -> assertThat(((AgentTextBlock) msg.getContent().get(0)).getText()).isEqualTo("after error"))
                .verifyComplete();
    }

    @Test
    void call_委托stream聚合返回最终消息() {
        ModelCaller modelCaller = mock(ModelCaller.class);
        AgentTextBlock textBlock = AgentTextBlock.builder().text("aggregated result").build();
        AgentChatResponse response = new AgentChatResponse(List.of(textBlock), null);
        when(modelCaller.stream(any(), any())).thenReturn(Flux.just(response));

        ToolExecutor toolExecutor = mock(ToolExecutor.class);
        MiddlewareChain chain = new MiddlewareChain(List.of());
        ModelResponseParser parser = new ModelResponseParser();

        ReActEngine engine = new ReActEngine(modelCaller, toolExecutor, chain, parser, null);
        EngineContext ctx = new EngineContext(null, null, AgentRuntimeContext.empty(), null, 10, null);

        AgentMessage input = MessageFactory.createUserMessage("hi");
        StepVerifier.create(engine.call(List.of(input), ctx))
                .assertNext(msg -> {
                    assertThat(msg.getRole()).isEqualTo(AgentMessageRole.ASSISTANT);
                    assertThat(((AgentTextBlock) msg.getContent().get(0)).getText()).isEqualTo("aggregated result");
                })
                .verifyComplete();
    }

    @Test
    void call_错误传播() {
        ModelCaller modelCaller = mock(ModelCaller.class);
        when(modelCaller.stream(any(), any())).thenReturn(Flux.error(new RuntimeException("stream error")));

        ToolExecutor toolExecutor = mock(ToolExecutor.class);
        MiddlewareChain chain = new MiddlewareChain(List.of());
        ModelResponseParser parser = new ModelResponseParser();

        ReActEngine engine = new ReActEngine(modelCaller, toolExecutor, chain, parser, null);
        EngineContext ctx = new EngineContext(null, null, AgentRuntimeContext.empty(), null, 10, null);

        AgentMessage input = MessageFactory.createUserMessage("hi");
        StepVerifier.create(engine.call(List.of(input), ctx))
                .expectError(RuntimeException.class)
                .verify();
    }

    @Test
    void shouldTruncateHistoryWithTailStrategyWhenMaxContextTokensExceeded() {
        ModelCaller modelCaller = mock(ModelCaller.class);
        List<AgentMessage> capturedMessages = new java.util.concurrent.CopyOnWriteArrayList<>();
        AgentTextBlock textBlock = AgentTextBlock.builder().text("ok").build();
        AgentChatResponse response = new AgentChatResponse(List.of(textBlock), null);
        when(modelCaller.stream(any(), any())).thenAnswer(invocation -> {
            List<AgentMessage> msgs = invocation.getArgument(0);
            capturedMessages.addAll(msgs);
            return Flux.just(response);
        });

        ToolExecutor toolExecutor = mock(ToolExecutor.class);
        MiddlewareChain chain = new MiddlewareChain(List.of());
        ModelResponseParser parser = new ModelResponseParser();

        ReActEngine engine = new ReActEngine(modelCaller, toolExecutor, chain, parser, null);
        // maxContextTokens=30，每条消息约27 token，仅能保留system+1条最新消息
        EngineContext ctx = new EngineContext(null, "sys", AgentRuntimeContext.empty(), null,
                10, null, null, null, 1, null, 0,
                30, HarnessAgentRuntimeBuilder.TruncationStrategy.TAIL);

        // 构造5条长消息，总Token远超30
        List<AgentMessage> inputs = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            inputs.add(MessageFactory.createUserMessage("msg" + i + repeatChar('x', 100)));
        }
        StepVerifier.create(engine.call(inputs, ctx))
                .assertNext(msg -> assertThat(msg.getContent()).isNotNull())
                .verifyComplete();

        // TAIL策略：保留system + 最新1条用户消息
        assertThat(capturedMessages).isNotEmpty();
        long userMsgCount = capturedMessages.stream()
                .filter(m -> m.getRole() == AgentMessageRole.USER)
                .count();
        assertThat(userMsgCount).isLessThan(5);
        assertThat(capturedMessages.get(0).getRole()).isEqualTo(AgentMessageRole.SYSTEM);
    }

    @Test
    void shouldTruncateHistoryWithHeadStrategyWhenMaxContextTokensExceeded() {
        ModelCaller modelCaller = mock(ModelCaller.class);
        List<AgentMessage> capturedMessages = new java.util.concurrent.CopyOnWriteArrayList<>();
        AgentTextBlock textBlock = AgentTextBlock.builder().text("ok").build();
        AgentChatResponse response = new AgentChatResponse(List.of(textBlock), null);
        when(modelCaller.stream(any(), any())).thenAnswer(invocation -> {
            List<AgentMessage> msgs = invocation.getArgument(0);
            capturedMessages.addAll(msgs);
            return Flux.just(response);
        });

        ToolExecutor toolExecutor = mock(ToolExecutor.class);
        MiddlewareChain chain = new MiddlewareChain(List.of());
        ModelResponseParser parser = new ModelResponseParser();

        ReActEngine engine = new ReActEngine(modelCaller, toolExecutor, chain, parser, null);
        EngineContext ctx = new EngineContext(null, "sys", AgentRuntimeContext.empty(), null,
                10, null, null, null, 1, null, 0,
                30, HarnessAgentRuntimeBuilder.TruncationStrategy.HEAD);

        List<AgentMessage> inputs = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            inputs.add(MessageFactory.createUserMessage("msg" + i + repeatChar('x', 100)));
        }
        StepVerifier.create(engine.call(inputs, ctx))
                .assertNext(msg -> assertThat(msg.getContent()).isNotNull())
                .verifyComplete();

        // HEAD策略：保留最早的消息，删除尾部最新
        assertThat(capturedMessages).isNotEmpty();
        long userMsgCount = capturedMessages.stream()
                .filter(m -> m.getRole() == AgentMessageRole.USER)
                .count();
        assertThat(userMsgCount).isLessThan(5);
    }

    @Test
    void shouldNotTruncateWhenMaxContextTokensIsZero() {
        ModelCaller modelCaller = mock(ModelCaller.class);
        List<AgentMessage> capturedMessages = new java.util.concurrent.CopyOnWriteArrayList<>();
        AgentTextBlock textBlock = AgentTextBlock.builder().text("ok").build();
        AgentChatResponse response = new AgentChatResponse(List.of(textBlock), null);
        when(modelCaller.stream(any(), any())).thenAnswer(invocation -> {
            List<AgentMessage> msgs = invocation.getArgument(0);
            capturedMessages.addAll(msgs);
            return Flux.just(response);
        });

        ToolExecutor toolExecutor = mock(ToolExecutor.class);
        MiddlewareChain chain = new MiddlewareChain(List.of());
        ModelResponseParser parser = new ModelResponseParser();

        ReActEngine engine = new ReActEngine(modelCaller, toolExecutor, chain, parser, null);
        // maxContextTokens=0表示不限制
        EngineContext ctx = new EngineContext(null, null, AgentRuntimeContext.empty(), null,
                10, null, null, null, 1, null, 0,
                0, null);

        List<AgentMessage> inputs = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            inputs.add(MessageFactory.createUserMessage("msg" + i + repeatChar('x', 100)));
        }
        StepVerifier.create(engine.call(inputs, ctx))
                .assertNext(msg -> assertThat(msg.getContent()).isNotNull())
                .verifyComplete();

        // 不截断，所有5条消息保留
        long userMsgCount = capturedMessages.stream()
                .filter(m -> m.getRole() == AgentMessageRole.USER)
                .count();
        assertThat(userMsgCount).isEqualTo(5);
    }

    /**
     * 重复字符构造长文本
     */
    private static String repeatChar(char c, int count) {
        char[] arr = new char[count];
        java.util.Arrays.fill(arr, c);
        return new String(arr);
    }
}
