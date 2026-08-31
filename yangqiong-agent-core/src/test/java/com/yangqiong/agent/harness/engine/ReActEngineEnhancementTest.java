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
import java.util.concurrent.CopyOnWriteArrayList;
import java.time.Duration;

import com.yangqiong.agent.harness.core.message.MessageFactory;
import com.yangqiong.agent.harness.model.ModelCaller;
import com.yangqiong.agent.harness.model.ModelResponseParser;
import com.yangqiong.agent.harness.tool.ToolExecutor;
import com.yangqiong.agent.harness.core.HarnessAgentRuntimeBuilder;
import com.yangqiong.agent.harness.core.event.AgentEvent;
import com.yangqiong.agent.harness.core.event.AgentEventType;
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
 * ReAct引擎增强测试（连续工具失败限制与Token估算精确化）
 * @author yangqiong
 */
class ReActEngineEnhancementTest {

    @Test
    void shouldAbortOnConsecutiveToolFailures() {
        ModelCaller modelCaller = mock(ModelCaller.class);
        ToolExecutor toolExecutor = mock(ToolExecutor.class);

        AgentToolUseBlock toolUse = new AgentToolUseBlock("search", "id1", Map.of());
        AgentChatResponse toolCallResponse = new AgentChatResponse(List.of(toolUse), null);
        when(modelCaller.stream(any(), any())).thenReturn(Flux.just(toolCallResponse));

        AgentToolResultBlock errorResult = AgentToolResultBlock.error("id1", "工具执行失败");
        AgentMessage errorMsg = MessageFactory.createToolMessage(errorResult);
        when(toolExecutor.executeTools(any(), any(), any())).thenReturn(Mono.just(List.of(errorMsg)));

        MiddlewareChain chain = new MiddlewareChain(List.of());
        ModelResponseParser parser = new ModelResponseParser();
        ReActEngine engine = new ReActEngine(modelCaller, toolExecutor, chain, parser, null);

        // maxConsecutiveToolFailures=2：每轮1个失败，第3轮累计3>2时中止
        EngineContext ctx = new EngineContext(null, null, AgentRuntimeContext.empty(), null, 10, null,
                null, null, 1, null, 0, 0, null, null, 0, 2);

        AgentMessage input = MessageFactory.createUserMessage("hi");
        StepVerifier.create(engine.call(List.of(input), ctx))
                .expectErrorMatches(e -> e instanceof RuntimeException
                        && e.getMessage().contains("连续工具失败超过阈值"))
                .verify();
    }

    @Test
    void shouldResetConsecutiveFailuresOnSuccess() {
        ModelCaller modelCaller = mock(ModelCaller.class);
        ToolExecutor toolExecutor = mock(ToolExecutor.class);

        AgentToolUseBlock toolUse = new AgentToolUseBlock("search", "id1", Map.of());
        AgentChatResponse toolCallResponse = new AgentChatResponse(List.of(toolUse), null);
        AgentTextBlock textBlock = AgentTextBlock.builder().text("final answer").build();
        AgentChatResponse finalResponse = new AgentChatResponse(List.of(textBlock), null);
        // 三次工具调用后给出最终答案
        when(modelCaller.stream(any(), any()))
                .thenReturn(Flux.just(toolCallResponse))
                .thenReturn(Flux.just(toolCallResponse))
                .thenReturn(Flux.just(toolCallResponse))
                .thenReturn(Flux.just(finalResponse));

        AgentToolResultBlock errorResult = AgentToolResultBlock.error("id1", "失败");
        AgentToolResultBlock successResult = AgentToolResultBlock.of(List.of(
                AgentTextBlock.builder().text("ok").build()));
        // 失败、成功、失败交替，永不达到连续阈值
        when(toolExecutor.executeTools(any(), any(), any()))
                .thenReturn(Mono.just(List.of(MessageFactory.createToolMessage(errorResult))))
                .thenReturn(Mono.just(List.of(MessageFactory.createToolMessage(successResult))))
                .thenReturn(Mono.just(List.of(MessageFactory.createToolMessage(errorResult))));

        MiddlewareChain chain = new MiddlewareChain(List.of());
        ModelResponseParser parser = new ModelResponseParser();
        ReActEngine engine = new ReActEngine(modelCaller, toolExecutor, chain, parser, null);

        // maxConsecutiveToolFailures=2：中间有成功重置，不会中止
        EngineContext ctx = new EngineContext(null, null, AgentRuntimeContext.empty(), null, 10, null,
                null, null, 1, null, 0, 0, null, null, 0, 2);

        AgentMessage input = MessageFactory.createUserMessage("hi");
        StepVerifier.create(engine.call(List.of(input), ctx))
                .assertNext(msg -> assertThat(((AgentTextBlock) msg.getContent().get(0)).getText()).isEqualTo("final answer"))
                .verifyComplete();
    }

    @Test
    void shouldEstimateChineseHigherThanAscii() {
        ModelCaller modelCaller = mock(ModelCaller.class);
        CopyOnWriteArrayList<AgentMessage> capturedMessages = new CopyOnWriteArrayList<>();
        AgentTextBlock textBlock = AgentTextBlock.builder().text("ok").build();
        AgentChatResponse response = new AgentChatResponse(List.of(textBlock), null);
        when(modelCaller.stream(any(), any())).thenAnswer(invocation -> {
            capturedMessages.addAll(invocation.getArgument(0));
            return Flux.just(response);
        });

        ToolExecutor toolExecutor = mock(ToolExecutor.class);
        MiddlewareChain chain = new MiddlewareChain(List.of());
        ModelResponseParser parser = new ModelResponseParser();
        ReActEngine engine = new ReActEngine(modelCaller, toolExecutor, chain, parser, null);

        // maxContextTokens=40：两条40字符中文消息，新公式估算每条约34 token，总68>40触发截断
        // 旧公式(chars/4)估算总24 token不会截断，保留2条；新公式截断到1条
        EngineContext ctx = new EngineContext(null, null, AgentRuntimeContext.empty(), null, 10, null,
                null, null, 1, null, 0, 40, null, null, 0, 0);

        String chinese = repeatChinese(40);
        List<AgentMessage> inputs = new ArrayList<>();
        inputs.add(MessageFactory.createUserMessage(chinese));
        inputs.add(MessageFactory.createUserMessage(chinese));

        StepVerifier.create(engine.call(inputs, ctx))
                .assertNext(msg -> assertThat(msg.getContent()).isNotNull())
                .verifyComplete();

        long userMsgCount = capturedMessages.stream()
                .filter(m -> m.getRole() == AgentMessageRole.USER)
                .count();
        assertThat(userMsgCount).isEqualTo(1);
    }

    /**
     * 硬墙钟总超时：到点无论模型是否持续输出都应截断并发ERROR
     */
    @Test
    void shouldEnforceWallClockTotalTimeout() {
        ModelCaller modelCaller = mock(ModelCaller.class);
        ToolExecutor toolExecutor = mock(ToolExecutor.class);
        AgentTextBlock textBlock = AgentTextBlock.builder().text("ok").build();
        AgentChatResponse response = new AgentChatResponse(List.of(textBlock), null);
        // 模型每200ms持续输出一个分片，共7片约1.4s，全程无停顿
        when(modelCaller.stream(any(), any())).thenReturn(
                Flux.interval(Duration.ofMillis(200)).take(7).map(i -> response));

        MiddlewareChain chain = new MiddlewareChain(List.of());
        ModelResponseParser parser = new ModelResponseParser();
        ReActEngine engine = new ReActEngine(modelCaller, toolExecutor, chain, parser, null);

        // 硬墙钟模式：总超时800ms，到点即断
        EngineContext ctx = new EngineContext(null, null, AgentRuntimeContext.empty(), null, 10, null,
                Duration.ofMillis(800), null, 1, null, 0, 0, null, null, 0, 0);
        ctx.setTimeoutMode(HarnessAgentRuntimeBuilder.TimeoutMode.WALL_CLOCK);

        AgentMessage input = MessageFactory.createUserMessage("hi");
        long startNanos = System.nanoTime();
        List<AgentEvent> events = engine.stream(List.of(input), ctx)
                .collectList().block(Duration.ofSeconds(5));
        long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000;

        assertThat(events).isNotNull();
        assertThat(elapsedMillis).as("硬墙钟超时应到点截断，不应等模型流自然结束")
                .isLessThan(1300);
        assertThat(events).as("硬墙钟超时应发射ERROR事件")
                .anyMatch(e -> e.getType() == AgentEventType.ERROR);
        assertThat(events).as("硬墙钟超时截断后不应有最终结果")
                .noneMatch(e -> e.getType() == AgentEventType.AGENT_RESULT);
    }

    /**
     * 空闲超时：持续输出时不应掐断，等待模型流自然完成
     */
    @Test
    void shouldKeepStreamingUnderIdleTotalTimeout() {
        ModelCaller modelCaller = mock(ModelCaller.class);
        ToolExecutor toolExecutor = mock(ToolExecutor.class);
        AgentTextBlock textBlock = AgentTextBlock.builder().text("ok").build();
        AgentChatResponse response = new AgentChatResponse(List.of(textBlock), null);
        // 模型每200ms持续输出一个分片，共7片约1.4s
        when(modelCaller.stream(any(), any())).thenReturn(
                Flux.interval(Duration.ofMillis(200)).take(7).map(i -> response));

        MiddlewareChain chain = new MiddlewareChain(List.of());
        ModelResponseParser parser = new ModelResponseParser();
        ReActEngine engine = new ReActEngine(modelCaller, toolExecutor, chain, parser, null);

        // 空闲模式：总超时800ms，但分片间隔200ms持续刷新，不触发超时
        EngineContext ctx = new EngineContext(null, null, AgentRuntimeContext.empty(), null, 10, null,
                Duration.ofMillis(800), null, 1, null, 0, 0, null, null, 0, 0);
        ctx.setTimeoutMode(HarnessAgentRuntimeBuilder.TimeoutMode.IDLE);

        AgentMessage input = MessageFactory.createUserMessage("hi");
        long startNanos = System.nanoTime();
        List<AgentEvent> events = engine.stream(List.of(input), ctx)
                .collectList().block(Duration.ofSeconds(5));
        long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000;

        assertThat(events).isNotNull();
        assertThat(elapsedMillis).as("空闲超时下持续输出应跑完模型流，总时长超过超时时长")
                .isGreaterThanOrEqualTo(1200);
        assertThat(events).as("空闲超时下持续输出不应发射ERROR")
                .noneMatch(e -> e.getType() == AgentEventType.ERROR);
        assertThat(events).as("空闲超时下应正常产出最终结果")
                .anyMatch(e -> e.getType() == AgentEventType.AGENT_RESULT);
    }

    private static String repeatChinese(int count) {
        StringBuilder sb = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            sb.append('测');
        }
        return sb.toString();
    }
}
