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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.yangqiong.agent.harness.core.event.AgentEvent;
import com.yangqiong.agent.harness.core.event.AgentEventType;
import com.yangqiong.agent.harness.core.event.AgentResultEvent;
import com.yangqiong.agent.harness.core.message.AgentMessage;
import com.yangqiong.agent.harness.core.message.AgentMessageRole;
import com.yangqiong.agent.harness.core.message.AgentTextBlock;
import com.yangqiong.agent.harness.core.message.AgentToolResultBlock;
import com.yangqiong.agent.harness.core.message.AgentToolUseBlock;
import com.yangqiong.agent.harness.core.message.MessageFactory;
import com.yangqiong.agent.harness.core.model.AgentChatResponse;
import com.yangqiong.agent.harness.engine.EngineContext;
import com.yangqiong.agent.harness.paradigms.support.ParadigmOptions;
import com.yangqiong.agent.harness.model.ModelCaller;
import com.yangqiong.agent.harness.model.ModelResponseParser;
import com.yangqiong.agent.harness.tool.ToolExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Self-Ask自问自答范式引擎测试
 * @author yangqiong
 */
class SelfAskEngineTest {

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
        when(context.getAgentName()).thenReturn("self-ask-agent");
        Map<String, Object> schema = Map.of("name", "search");
        when(context.getAllToolSchemas()).thenReturn(List.of(schema));
    }

    /**
     * 按调用次序给模型调用桩编排响应
     * @param responses
     */
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

    /**
     * 构造纯文本响应
     * @param text
     * @return
     */
    private AgentChatResponse textResponse(String text) {
        return new AgentChatResponse(List.of(AgentTextBlock.builder().text(text).build()), null);
    }

    /**
     * 拆解、逐个作答（含工具轮续答）、汇总的正常路径
     */
    @Test
    void shouldDecomposeAnswerSubQuestionsAndAggregate() {
        AgentToolUseBlock toolUse = new AgentToolUseBlock("search", "id-1", Map.of("q", "子问题二"));
        stubResponses(
                textResponse("```json\n[\"子问题一\", \"子问题二\"]\n```"),
                textResponse("子问题一的答案"),
                new AgentChatResponse(List.of(toolUse), null),
                textResponse("子问题二的答案"),
                textResponse("最终汇总答案"));
        AgentToolResultBlock toolResult = AgentToolResultBlock.of(
                List.of(AgentTextBlock.builder().text("搜索结果").build()));
        when(toolExecutor.executeTools(any(), any(), any()))
                .thenReturn(Mono.just(List.of(MessageFactory.createToolMessage(toolResult))));

        SelfAskEngine engine = new SelfAskEngine();
        List<AgentEvent> events = engine.run(
                List.of(MessageFactory.createUserMessage("原始问题文本")), context)
                .collectList().block();

        assertThat(events).isNotNull();
        assertThat(events).extracting(AgentEvent::getType)
                .doesNotContain(AgentEventType.ERROR);
        assertThat(events).extracting(AgentEvent::getType)
                .containsSequence(AgentEventType.TOOL_CALL_START, AgentEventType.TOOL_CALL_END);
        // 生命周期：首事件为AGENT_START
        assertThat(events.get(0).getType()).isEqualTo(AgentEventType.AGENT_START);
        AgentEvent lastEvent = events.get(events.size() - 1);
        assertThat(lastEvent.getType()).isEqualTo(AgentEventType.AGENT_END);
        AgentEvent resultEvent = events.get(events.size() - 2);
        assertThat(resultEvent).isInstanceOf(AgentResultEvent.class);
        assertThat(((AgentResultEvent) resultEvent).getResult().getTextContent())
                .isEqualTo("最终汇总答案");
        long modelCallStarts = events.stream()
                .filter(event -> event.getType() == AgentEventType.MODEL_CALL_START)
                .count();
        assertThat(modelCallStarts).isEqualTo(5);

        assertThat(recordedMessages).hasSize(5);
        // 拆解与汇总调用不带工具schema，子问题作答调用带工具schema
        assertThat(recordedSchemas.get(0)).isEmpty();
        assertThat(recordedSchemas.get(1)).hasSize(1);
        assertThat(recordedSchemas.get(2)).hasSize(1);
        assertThat(recordedSchemas.get(3)).hasSize(1);
        assertThat(recordedSchemas.get(4)).isEmpty();
        assertThat(recordedMessages.get(0).get(0).getTextContent()).isEqualTo("原始问题文本");
        assertThat(recordedMessages.get(0).get(1).getTextContent()).contains("JSON");
        assertThat(recordedMessages.get(1))
                .anyMatch(msg -> msg.getTextContent().equals("子问题一"));
        assertThat(recordedMessages.get(3))
                .anyMatch(msg -> msg.getRole() == AgentMessageRole.TOOL);
        assertThat(recordedMessages.get(4))
                .anyMatch(msg -> msg.getTextContent().contains("子问题一的答案")
                        && msg.getTextContent().contains("子问题二的答案"));
    }

    /**
     * 子问题解析失败时降级为单轮直接回答
     */
    @Test
    void shouldDegradeToDirectAnswerWhenSubQuestionsParseFailed() {
        stubResponses(
                textResponse("抱歉，我无法将该问题拆解为子问题。"),
                textResponse("直接回答的最终答案"));

        SelfAskEngine engine = new SelfAskEngine();
        List<AgentEvent> events = engine.run(
                List.of(MessageFactory.createUserMessage("原始问题文本")), context)
                .collectList().block();

        assertThat(events).isNotNull();
        assertThat(events).extracting(AgentEvent::getType)
                .doesNotContain(AgentEventType.ERROR);
        assertThat(events).extracting(AgentEvent::getType)
                .doesNotContain(AgentEventType.TOOL_CALL_START);
        AgentEvent lastEvent = events.get(events.size() - 1);
        assertThat(lastEvent.getType()).isEqualTo(AgentEventType.AGENT_END);
        AgentEvent resultEvent = events.get(events.size() - 2);
        assertThat(resultEvent).isInstanceOf(AgentResultEvent.class);
        assertThat(((AgentResultEvent) resultEvent).getResult().getTextContent())
                .isEqualTo("直接回答的最终答案");

        assertThat(recordedMessages).hasSize(2);
        assertThat(recordedMessages.get(1).get(0).getTextContent()).isEqualTo("原始问题文本");
        assertThat(recordedSchemas.get(0)).isEmpty();
        assertThat(recordedSchemas.get(1)).hasSize(1);
    }

    /**
     * 子问题数量超过maxSteps时仅保留前maxSteps个
     */
    @Test
    void shouldCapSubQuestionsByMaxSteps() {
        stubResponses(
                textResponse("[\"子问题一\", \"子问题二\", \"子问题三\"]"),
                textResponse("子问题一的答案"),
                textResponse("最终汇总答案"));

        SelfAskEngine engine = new SelfAskEngine(new ParadigmOptions().maxSteps(1));
        List<AgentEvent> events = engine.run(
                List.of(MessageFactory.createUserMessage("原始问题文本")), context)
                .collectList().block();

        assertThat(events).isNotNull();
        assertThat(events).extracting(AgentEvent::getType)
                .doesNotContain(AgentEventType.ERROR);
        AgentEvent lastEvent = events.get(events.size() - 1);
        assertThat(lastEvent.getType()).isEqualTo(AgentEventType.AGENT_END);
        AgentEvent resultEvent = events.get(events.size() - 2);
        assertThat(resultEvent).isInstanceOf(AgentResultEvent.class);
        assertThat(((AgentResultEvent) resultEvent).getResult().getTextContent())
                .isEqualTo("最终汇总答案");
        assertThat(recordedMessages).hasSize(3);
        assertThat(recordedMessages.get(1))
                .anyMatch(msg -> msg.getTextContent().equals("子问题一"));
        assertThat(recordedMessages.get(2))
                .anyMatch(msg -> msg.getTextContent().contains("子问题一")
                        && !msg.getTextContent().contains("子问题二"));
    }

    /**
     * 生命周期契约：事件流以AGENT_START开头，AgentResultEvent之后以AGENT_END收尾且无ERROR
     */
    @Test
    void shouldWrapResultWithLifecycleEvents() {
        stubResponses(
                textResponse("无法拆解为子问题"),
                textResponse("生命周期最终答案"));

        SelfAskEngine engine = new SelfAskEngine();
        List<AgentEvent> events = engine.run(
                List.of(MessageFactory.createUserMessage("原始问题文本")), context)
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
}