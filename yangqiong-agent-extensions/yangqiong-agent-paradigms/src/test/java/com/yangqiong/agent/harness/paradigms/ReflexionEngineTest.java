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
package com.yangqiong.agent.harness.paradigms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import com.yangqiong.agent.harness.core.event.AgentEvent;
import com.yangqiong.agent.harness.core.event.AgentEventType;
import com.yangqiong.agent.harness.core.event.AgentResultEvent;
import com.yangqiong.agent.harness.core.message.AgentMessage;
import com.yangqiong.agent.harness.core.message.AgentMessageRole;
import com.yangqiong.agent.harness.core.message.AgentTextBlock;
import com.yangqiong.agent.harness.core.message.AgentToolResultBlock;
import com.yangqiong.agent.harness.core.message.AgentToolUseBlock;
import com.yangqiong.agent.harness.core.message.MessageFactory;
import com.yangqiong.agent.harness.core.middleware.AgentMiddleware;
import com.yangqiong.agent.harness.core.model.AgentChatResponse;
import com.yangqiong.agent.harness.engine.AgentRuntimeContext;
import com.yangqiong.agent.harness.engine.EngineContext;
import com.yangqiong.agent.harness.engine.MiddlewareChain;
import com.yangqiong.agent.harness.paradigms.support.ParadigmOptions;
import com.yangqiong.agent.harness.model.ModelCaller;
import com.yangqiong.agent.harness.model.ModelResponseParser;
import com.yangqiong.agent.harness.tool.ToolExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reflexion反思范式引擎测试
 * @author yangqiong
 */
class ReflexionEngineTest {

    /**
     * 模型调用桩
     */
    private ModelCaller modelCaller;

    /**
     * 工具执行器桩
     */
    private ToolExecutor toolExecutor;

    /**
     * 引擎上下文桩
     */
    private EngineContext context;

    /**
     * 按调用次序记录的模型输入消息
     */
    private List<List<AgentMessage>> recordedMessages;

    /**
     * 按调用次序记录的工具Schema列表
     */
    private List<List<Map<String, Object>>> recordedSchemas;

    @BeforeEach
    void setUp() {
        modelCaller = mock(ModelCaller.class);
        toolExecutor = mock(ToolExecutor.class);
        context = mock(EngineContext.class);
        recordedMessages = new ArrayList<>();
        recordedSchemas = new ArrayList<>();
        when(context.getModelCaller()).thenReturn(modelCaller);
        when(context.getToolExecutor()).thenReturn(toolExecutor);
        when(context.getResponseParser()).thenReturn(new ModelResponseParser());
        when(context.getAgentName()).thenReturn("reflexion-agent");
        Map<String, Object> schema = Map.of("name", "search");
        when(context.getAllToolSchemas()).thenReturn(List.of(schema));
    }

    private void stubResponses(AgentChatResponse... responses) {
        AtomicInteger index = new AtomicInteger();
        when(modelCaller.stream(any(), any())).thenAnswer(invocation -> {
            List<AgentMessage> messages = invocation.getArgument(0);
            List<Map<String, Object>> schemas = invocation.getArgument(1);
            recordedMessages.add(new ArrayList<>(messages));
            recordedSchemas.add(new ArrayList<>(schemas));
            return Flux.just(responses[index.getAndIncrement()]);
        });
    }

    private AgentChatResponse textResponse(String text) {
        return new AgentChatResponse(List.of(AgentTextBlock.builder().text(text).build()), null);
    }

    /**
     * 统计评估阶段调用次数，以请求末尾携带评估提示词识别
     * @return
     */
    private long countEvaluationCalls() {
        return recordedMessages.stream()
                .filter(messages -> !messages.isEmpty()
                        && messages.get(messages.size() - 1).getTextContent()
                                .startsWith("请评估以下回答"))
                .count();
    }

    @Test
    void shouldReflectAndPassOnSecondAttempt() {
        stubResponses(
                textResponse("首次答案"),
                textResponse("FAIL: 首次回答遗漏关键约束"),
                textResponse("下次必须覆盖关键约束"),
                textResponse("修正后的答案"),
                textResponse("PASS"));

        ReflexionEngine engine = new ReflexionEngine();
        List<AgentEvent> events = engine.run(
                List.of(MessageFactory.createUserMessage("请解决问题")), context)
                .collectList().block();

        assertThat(events).isNotNull();
        assertThat(events).extracting(AgentEvent::getType)
                .doesNotContain(AgentEventType.ERROR);
        AgentEvent lastEvent = events.get(events.size() - 1);
        assertThat(lastEvent.getType()).isEqualTo(AgentEventType.AGENT_END);
        AgentEvent resultEvent = events.get(events.size() - 2);
        assertThat(resultEvent).isInstanceOf(AgentResultEvent.class);
        assertThat(((AgentResultEvent) resultEvent).getResult().getTextContent())
                .isEqualTo("修正后的答案");
        long modelCallStarts = events.stream()
                .filter(event -> event.getType() == AgentEventType.MODEL_CALL_START)
                .count();
        assertThat(modelCallStarts).isEqualTo(5);

        assertThat(recordedMessages).hasSize(5);
        // 反思轮模型输入必须携带第一轮反思文本
        assertThat(recordedMessages.get(3))
                .anyMatch(msg -> msg.getTextContent().contains("下次必须覆盖关键约束"));
        assertThat(recordedMessages.get(3).get(0).getTextContent()).isEqualTo("请解决问题");
        assertThat(recordedMessages.get(1))
                .anyMatch(msg -> msg.getTextContent().contains("首次答案"));
        assertThat(recordedMessages.get(2))
                .anyMatch(msg -> msg.getTextContent().contains("首次回答遗漏关键约束"));

        assertThat(recordedSchemas.get(0)).hasSize(1);
        assertThat(recordedSchemas.get(1)).isEmpty();
        assertThat(recordedSchemas.get(2)).isEmpty();
        assertThat(recordedSchemas.get(3)).hasSize(1);
        assertThat(recordedSchemas.get(4)).isEmpty();

        // 反思文本仅注入模型输入，不写入上下文内部状态
        verify(context, never()).addMessage(any());
    }

    @Test
    void shouldReturnLastAnswerWhenReflectionsExhausted() {
        stubResponses(
                textResponse("第1轮答案"),
                textResponse("FAIL: 回答不够完整"),
                textResponse("教训一"),
                textResponse("第2轮答案"),
                textResponse("FAIL: 回答仍不够完整"));

        ReflexionEngine engine = new ReflexionEngine(
                new ParadigmOptions().maxReflections(1));
        List<AgentEvent> events = engine.run(
                List.of(MessageFactory.createUserMessage("请解决问题")), context)
                .collectList().block();

        assertThat(events).isNotNull();
        assertThat(events).extracting(AgentEvent::getType)
                .doesNotContain(AgentEventType.ERROR);
        AgentEvent lastEvent = events.get(events.size() - 1);
        assertThat(lastEvent.getType()).isEqualTo(AgentEventType.AGENT_END);
        AgentEvent resultEvent = events.get(events.size() - 2);
        assertThat(resultEvent).isInstanceOf(AgentResultEvent.class);
        assertThat(((AgentResultEvent) resultEvent).getResult().getTextContent())
                .isEqualTo("第2轮答案");

        assertThat(recordedMessages).hasSize(5);
        // 总评估次数等于反思上限加一
        assertThat(countEvaluationCalls()).isEqualTo(2);
        // 重试轮模型输入携带第一轮反思文本
        assertThat(recordedMessages.get(3))
                .anyMatch(msg -> msg.getTextContent().contains("教训一"));

        verify(context, never()).addMessage(any());
    }

    @Test
    void shouldPassOnFirstAttemptWithoutReflection() {
        stubResponses(
                textResponse("一次性正确答案"),
                textResponse("PASS"));

        ReflexionEngine engine = new ReflexionEngine();
        List<AgentEvent> events = engine.run(
                List.of(MessageFactory.createUserMessage("请解决问题")), context)
                .collectList().block();

        assertThat(events).isNotNull();
        AgentEvent lastEvent = events.get(events.size() - 1);
        assertThat(lastEvent.getType()).isEqualTo(AgentEventType.AGENT_END);
        AgentEvent resultEvent = events.get(events.size() - 2);
        assertThat(resultEvent).isInstanceOf(AgentResultEvent.class);
        assertThat(((AgentResultEvent) resultEvent).getResult().getTextContent())
                .isEqualTo("一次性正确答案");

        assertThat(recordedMessages).hasSize(2);
        verify(context, never()).addMessage(any());
    }

    @Test
    void shouldExecuteToolsWithinBaseLoop() {
        AgentToolUseBlock toolUse = new AgentToolUseBlock("search", "id1", Map.of("q", "test"));
        stubResponses(
                new AgentChatResponse(List.of(toolUse), null),
                textResponse("工具增强后的答案"),
                textResponse("PASS"));
        AgentToolResultBlock toolResult = AgentToolResultBlock.of(
                List.of(AgentTextBlock.builder().text("搜索结果").build()));
        when(toolExecutor.executeTools(any(), any(), any()))
                .thenReturn(Mono.just(List.of(MessageFactory.createToolMessage(toolResult))));

        ReflexionEngine engine = new ReflexionEngine();
        List<AgentEvent> events = engine.run(
                List.of(MessageFactory.createUserMessage("搜索并回答")), context)
                .collectList().block();

        assertThat(events).isNotNull();
        assertThat(events).extracting(AgentEvent::getType)
                .containsSequence(AgentEventType.TOOL_CALL_START, AgentEventType.TOOL_CALL_END);
        AgentEvent lastEvent = events.get(events.size() - 1);
        assertThat(lastEvent.getType()).isEqualTo(AgentEventType.AGENT_END);
        AgentEvent resultEvent = events.get(events.size() - 2);
        assertThat(resultEvent).isInstanceOf(AgentResultEvent.class);
        assertThat(((AgentResultEvent) resultEvent).getResult().getTextContent())
                .isEqualTo("工具增强后的答案");

        assertThat(recordedMessages).hasSize(3);
        assertThat(recordedMessages.get(1))
                .anyMatch(msg -> msg.getRole() == AgentMessageRole.TOOL);
    }

    /**
     * 生命周期契约：事件流以AGENT_START开头，AgentResultEvent之后以AGENT_END收尾且无ERROR
     */
    @Test
    void shouldWrapResultWithLifecycleEvents() {
        stubResponses(
                textResponse("生命周期答案"),
                textResponse("PASS"));

        ReflexionEngine engine = new ReflexionEngine();
        List<AgentEvent> events = engine.run(
                List.of(MessageFactory.createUserMessage("请解决问题")), context)
                .collectList().block();

        assertThat(events).isNotNull();
        List<AgentEventType> types = events.stream()
                .map(AgentEvent::getType)
                .toList();
        assertThat(types.get(0)).isEqualTo(AgentEventType.AGENT_START);
        assertThat(types.get(types.size() - 1)).isEqualTo(AgentEventType.AGENT_END);
        assertThat(events.get(events.size() - 2)).isInstanceOf(AgentResultEvent.class);
        assertThat(types).doesNotContain(AgentEventType.ERROR);
    }

    /**
     * 工具中间件接入：基础执行循环的工具执行经onActing中间件洋葱包裹，且工具事件对保持完整
     */
    @Test
    void shouldInvokeActingMiddlewareOnToolExecution() {
        AtomicInteger actingInvocations = new AtomicInteger();
        AgentMiddleware middleware = new AgentMiddleware() {
            @Override
            public Flux<AgentEvent> onActing(AgentRuntimeContext ctx, List<AgentToolUseBlock> toolCalls,
                                             Function<List<AgentToolUseBlock>, Flux<AgentEvent>> next) {
                actingInvocations.incrementAndGet();
                return next.apply(toolCalls);
            }
        };
        when(context.getMiddlewareChain()).thenReturn(new MiddlewareChain(List.of(middleware)));
        AgentToolUseBlock toolUse = new AgentToolUseBlock("search", "id1", Map.of("q", "test"));
        stubResponses(
                new AgentChatResponse(List.of(toolUse), null),
                textResponse("中间件增强后的答案"),
                textResponse("PASS"));
        AgentToolResultBlock toolResult = AgentToolResultBlock.of(
                List.of(AgentTextBlock.builder().text("搜索结果").build()));
        when(toolExecutor.executeTools(any(), any(), any()))
                .thenReturn(Mono.just(List.of(MessageFactory.createToolMessage(toolResult))));

        ReflexionEngine engine = new ReflexionEngine();
        List<AgentEvent> events = engine.run(
                List.of(MessageFactory.createUserMessage("搜索并回答")), context)
                .collectList().block();

        assertThat(events).isNotNull();
        // 基础执行循环的一次工具执行经过一次onActing中间件
        assertThat(actingInvocations.get()).isEqualTo(1);
        // 工具事件对仍完整且最终答案不受影响
        assertThat(events).extracting(AgentEvent::getType)
                .containsSequence(AgentEventType.TOOL_CALL_START, AgentEventType.TOOL_CALL_END);
        AgentEvent resultEvent = events.get(events.size() - 2);
        assertThat(resultEvent).isInstanceOf(AgentResultEvent.class);
        assertThat(((AgentResultEvent) resultEvent).getResult().getTextContent())
                .isEqualTo("中间件增强后的答案");
    }

    /**
     * 连续失败熔断：工具结果全为错误块且达到连续失败阈值时，基础循环第二次工具调用后
     * 发射ERROR中止且不再有第二次TOOL_CALL_END
     */
    @Test
    void shouldAbortBaseLoopWhenConsecutiveToolFailuresExceeded() {
        when(context.getMaxConsecutiveToolFailures()).thenReturn(1);
        AtomicInteger failures = new AtomicInteger();
        when(context.addConsecutiveFailures(anyInt()))
                .thenAnswer(invocation -> failures.addAndGet(invocation.getArgument(0, Integer.class)));
        when(context.getConsecutiveFailures()).thenAnswer(invocation -> failures.get());
        AgentToolUseBlock toolUse = new AgentToolUseBlock("search", "id1", Map.of("q", "test"));
        stubResponses(
                new AgentChatResponse(List.of(toolUse), null),
                new AgentChatResponse(List.of(toolUse), null),
                textResponse("FAIL: 需要改进"));
        when(toolExecutor.executeTools(any(), any(), any())).thenAnswer(invocation -> {
            List<AgentToolUseBlock> calls = invocation.getArgument(0);
            List<AgentMessage> results = new ArrayList<>();
            for (AgentToolUseBlock call : calls) {
                results.add(MessageFactory.createToolMessage(
                        AgentToolResultBlock.error(call.getToolUseId(), "boom")));
            }
            return Mono.just(results);
        });

        // 反思上限为0，熔断中止基础循环后评估阶段直接以当前答案收口
        ReflexionEngine engine = new ReflexionEngine(new ParadigmOptions().maxReflections(0));
        List<AgentEvent> events = engine.run(
                List.of(MessageFactory.createUserMessage("连续失败场景")), context)
                .collectList().block();

        assertThat(events).isNotNull();
        List<AgentEventType> types = events.stream()
                .map(AgentEvent::getType)
                .toList();
        // 基础循环两次工具调用，仅第一次发射TOOL_CALL_END
        assertThat(types.stream().filter(type -> type == AgentEventType.TOOL_CALL_START).count()).isEqualTo(2);
        assertThat(types.stream().filter(type -> type == AgentEventType.TOOL_CALL_END).count()).isEqualTo(1);
        // 第二次工具调用未发射TOOL_CALL_END，直接以连续失败ERROR收口
        int secondStart = -1;
        int seen = 0;
        for (int i = 0; i < types.size(); i++) {
            if (types.get(i) == AgentEventType.TOOL_CALL_START) {
                seen++;
                if (seen == 2) {
                    secondStart = i;
                    break;
                }
            }
        }
        assertThat(secondStart).isGreaterThan(0);
        assertThat(types.get(secondStart + 1)).isEqualTo(AgentEventType.ERROR);
        assertThat(((RuntimeException) events.get(secondStart + 1).getPayload()).getMessage())
                .contains("连续工具失败超过阈值");
        // 熔断ERROR之后不再有第二次TOOL_CALL_END
        assertThat(types.subList(secondStart + 1, types.size()))
                .doesNotContain(AgentEventType.TOOL_CALL_END);
    }
}