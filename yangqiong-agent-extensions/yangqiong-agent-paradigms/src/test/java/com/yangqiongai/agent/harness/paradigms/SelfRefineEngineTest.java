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
package com.yangqiongai.agent.harness.paradigms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.yangqiongai.agent.harness.core.event.AgentEvent;
import com.yangqiongai.agent.harness.core.event.AgentEventType;
import com.yangqiongai.agent.harness.core.event.AgentResultEvent;
import com.yangqiongai.agent.harness.core.message.AgentMessage;
import com.yangqiongai.agent.harness.core.message.AgentTextBlock;
import com.yangqiongai.agent.harness.core.message.AgentToolResultBlock;
import com.yangqiongai.agent.harness.core.message.AgentToolUseBlock;
import com.yangqiongai.agent.harness.core.message.MessageFactory;
import com.yangqiongai.agent.harness.core.model.AgentChatResponse;
import com.yangqiongai.agent.harness.engine.EngineContext;
import com.yangqiongai.agent.harness.model.ModelCaller;
import com.yangqiongai.agent.harness.model.ModelResponseParser;
import com.yangqiongai.agent.harness.tool.ToolExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Self-Refine自我精炼范式引擎测试
 * @author yangqiong
 */
class SelfRefineEngineTest {

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
        when(context.getAgentName()).thenReturn("self-refine-agent");
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
     * 初稿UNSATISFIED经一次修订后SATISFIED，以修订稿为最终结果
     */
    @Test
    void shouldReviseOnceWhenUnsatisfiedThenStopWhenSatisfied() {
        AgentToolUseBlock toolUse = new AgentToolUseBlock("search", "id-1", Map.of("q", "资料"));
        stubResponses(
                new AgentChatResponse(List.of(toolUse), null),
                textResponse("第一版答案"),
                textResponse("UNSATISFIED: 建议补充数据来源"),
                textResponse("第二版答案"),
                textResponse("SATISFIED"));
        AgentToolResultBlock toolResult = AgentToolResultBlock.of(
                List.of(AgentTextBlock.builder().text("搜索结果").build()));
        when(toolExecutor.executeTools(any(), any(), any()))
                .thenReturn(Mono.just(List.of(MessageFactory.createToolMessage(toolResult))));

        SelfRefineEngine engine = new SelfRefineEngine();
        List<AgentEvent> events = engine.run(
                List.of(MessageFactory.createUserMessage("请回答问题")), context)
                .collectList().block();

        assertThat(events).isNotNull();
        assertThat(events).extracting(AgentEvent::getType)
                .doesNotContain(AgentEventType.ERROR);
        assertThat(events).extracting(AgentEvent::getType)
                .containsSequence(AgentEventType.TOOL_CALL_START, AgentEventType.TOOL_CALL_END);
        AgentEvent lastEvent = events.get(events.size() - 1);
        assertThat(lastEvent.getType()).isEqualTo(AgentEventType.AGENT_END);
        AgentEvent resultEvent = events.get(events.size() - 2);
        assertThat(resultEvent).isInstanceOf(AgentResultEvent.class);
        assertThat(((AgentResultEvent) resultEvent).getResult().getTextContent())
                .isEqualTo("第二版答案");
        // 生成（含工具轮续答）、批评、修订、再批评共5次模型调用
        long modelCallStarts = events.stream()
                .filter(event -> event.getType() == AgentEventType.MODEL_CALL_START)
                .count();
        assertThat(modelCallStarts).isEqualTo(5);

        assertThat(recordedMessages).hasSize(5);
        // 生成调用带工具schema，批评与修订调用不带工具schema
        assertThat(recordedSchemas.get(0)).hasSize(1);
        assertThat(recordedSchemas.get(1)).hasSize(1);
        assertThat(recordedSchemas.get(2)).isEmpty();
        assertThat(recordedSchemas.get(3)).isEmpty();
        assertThat(recordedSchemas.get(4)).isEmpty();
        assertThat(recordedMessages.get(2))
                .anyMatch(msg -> msg.getTextContent().contains("第一版答案"));
        assertThat(recordedMessages.get(3))
                .anyMatch(msg -> msg.getTextContent().contains("建议补充数据来源"));
    }

    /**
     * 批评始终UNSATISFIED时按maxRefinements上限修订后返回最后一版
     */
    @Test
    void shouldReturnLastDraftWhenRefinementsExhausted() {
        stubResponses(
                textResponse("第一版答案"),
                textResponse("UNSATISFIED: 第一版论据不足"),
                textResponse("第二版答案"),
                textResponse("UNSATISFIED: 第二版结构混乱"),
                textResponse("第三版答案"),
                textResponse("UNSATISFIED: 第三版仍需打磨"));

        SelfRefineEngine engine = new SelfRefineEngine();
        List<AgentEvent> events = engine.run(
                List.of(MessageFactory.createUserMessage("请回答问题")), context)
                .collectList().block();

        assertThat(events).isNotNull();
        assertThat(events).extracting(AgentEvent::getType)
                .doesNotContain(AgentEventType.ERROR);
        AgentEvent lastEvent = events.get(events.size() - 1);
        assertThat(lastEvent.getType()).isEqualTo(AgentEventType.AGENT_END);
        AgentEvent resultEvent = events.get(events.size() - 2);
        assertThat(resultEvent).isInstanceOf(AgentResultEvent.class);
        assertThat(((AgentResultEvent) resultEvent).getResult().getTextContent())
                .isEqualTo("第三版答案");
        // 初始生成1次 + 批评3次 + 修订2次（maxRefinements默认2）共6次模型调用
        long modelCallStarts = events.stream()
                .filter(event -> event.getType() == AgentEventType.MODEL_CALL_START)
                .count();
        assertThat(modelCallStarts).isEqualTo(6);

        assertThat(recordedMessages).hasSize(6);
        assertThat(recordedMessages.get(1))
                .anyMatch(msg -> msg.getTextContent().contains("第一版答案"));
        assertThat(recordedMessages.get(2))
                .anyMatch(msg -> msg.getTextContent().contains("第一版论据不足"));
        assertThat(recordedMessages.get(3))
                .anyMatch(msg -> msg.getTextContent().contains("第二版答案"));
        assertThat(recordedMessages.get(4))
                .anyMatch(msg -> msg.getTextContent().contains("第二版结构混乱"));
        assertThat(recordedMessages.get(5))
                .anyMatch(msg -> msg.getTextContent().contains("第三版答案"));
    }

    /**
     * 生命周期契约：事件流以AGENT_START开头，AgentResultEvent之后以AGENT_END收尾且无ERROR
     */
    @Test
    void shouldWrapResultWithLifecycleEvents() {
        stubResponses(
                textResponse("生命周期初稿"),
                textResponse("SATISFIED"));

        SelfRefineEngine engine = new SelfRefineEngine();
        List<AgentEvent> events = engine.run(
                List.of(MessageFactory.createUserMessage("请回答问题")), context)
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