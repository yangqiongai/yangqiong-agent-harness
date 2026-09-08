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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.yangqiongai.agent.harness.core.event.AgentEvent;
import com.yangqiongai.agent.harness.core.event.AgentEventType;
import com.yangqiongai.agent.harness.core.event.AgentResultEvent;
import com.yangqiongai.agent.harness.core.message.AgentMessage;
import com.yangqiongai.agent.harness.core.message.AgentTextBlock;
import com.yangqiongai.agent.harness.core.message.AgentToolResultBlock;
import com.yangqiongai.agent.harness.core.message.AgentToolUseBlock;
import com.yangqiongai.agent.harness.core.message.MessageFactory;
import com.yangqiongai.agent.harness.core.model.AgentChatResponse;
import com.yangqiongai.agent.harness.engine.AgentRuntimeContext;
import com.yangqiongai.agent.harness.engine.EngineContext;
import com.yangqiongai.agent.harness.paradigms.support.ParadigmOptions;
import com.yangqiongai.agent.harness.model.ModelCaller;
import com.yangqiongai.agent.harness.model.ModelResponseParser;
import com.yangqiongai.agent.harness.tool.ToolExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 计划-执行范式引擎测试
 * @author yangqiong
 */
class PlanExecuteEngineTest {

    /**
     * 测试计划：markdown围栏包裹，一个工具步加一个推理步
     */
    private static final String PLAN_JSON = "```json\n["
            + "{\"type\":\"tool\",\"tool\":\"toolA\",\"args\":{\"query\":\"alpha\"},\"desc\":\"查询alpha\"},"
            + "{\"type\":\"reason\",\"desc\":\"基于查询结果推理中间结论\"}"
            + "]\n```";

    /**
     * 测试计划：三个工具步，配合maxSteps=2验证截断
     */
    private static final String THREE_STEP_PLAN = "["
            + "{\"type\":\"tool\",\"tool\":\"toolA\",\"args\":{\"query\":\"one\"},\"desc\":\"第一步\"},"
            + "{\"type\":\"tool\",\"tool\":\"toolB\",\"args\":{\"query\":\"two\"},\"desc\":\"第二步\"},"
            + "{\"type\":\"tool\",\"tool\":\"toolC\",\"args\":{\"query\":\"three\"},\"desc\":\"第三步\"}"
            + "]";

    /**
     * 模型调用器桩
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
     * 构建模型与上下文桩
     */
    @BeforeEach
    void setUp() {
        modelCaller = mock(ModelCaller.class);
        toolExecutor = mock(ToolExecutor.class);
        context = mock(EngineContext.class);
        when(context.getModelCaller()).thenReturn(modelCaller);
        when(context.getToolExecutor()).thenReturn(toolExecutor);
        when(context.getResponseParser()).thenReturn(new ModelResponseParser());
        when(context.getAllToolSchemas(any())).thenReturn(List.of(
                Map.of("name", "toolA", "type", "object"),
                Map.of("name", "toolB", "type", "object"),
                Map.of("name", "toolC", "type", "object")));
        when(context.getAgentName()).thenReturn("test-agent");
        when(context.getRuntimeContext()).thenReturn(AgentRuntimeContext.empty());
        when(modelCaller.modelName()).thenReturn("test-model");
    }

    /**
     * 构建纯文本模型响应
     * @param text
     * @return
     */
    private AgentChatResponse textResponse(String text) {
        return new AgentChatResponse(List.of(AgentTextBlock.builder().text(text).build()), null);
    }

    /**
     * 桩工具执行器：按工具名返回固定结果文本的工具消息
     */
    private void stubToolResults() {
        when(toolExecutor.executeTools(any(), any(), any())).thenAnswer(invocation -> {
            List<AgentToolUseBlock> calls = invocation.getArgument(0);
            List<AgentMessage> results = new ArrayList<>();
            for (AgentToolUseBlock call : calls) {
                results.add(MessageFactory.createToolMessage(AgentToolResultBlock.of(call.getToolUseId(),
                        List.of(AgentTextBlock.builder().text("result-of-" + call.getToolName()).build()))));
            }
            return Mono.just(results);
        });
    }

    /**
     * 计划含一个工具步与一个推理步时按序执行，事件序列完整并以AgentResultEvent终止
     */
    @Test
    void shouldExecuteToolAndReasonStepsWithFullEventSequence() {
        when(modelCaller.stream(any(), any()))
                .thenReturn(Flux.just(textResponse(PLAN_JSON)))
                .thenReturn(Flux.just(textResponse("中间推理结论")))
                .thenReturn(Flux.just(textResponse("最终答案")));
        stubToolResults();

        List<AgentEvent> events = new PlanExecuteEngine().run(
                List.of(MessageFactory.createUserMessage("计划执行任务")), context)
                .collectList().block();

        assertThat(events).isNotNull();
        List<AgentEventType> types = events.stream()
                .map(AgentEvent::getType)
                .toList();
        // 模型调用与工具调用事件对均存在
        assertThat(types).contains(AgentEventType.MODEL_CALL_START, AgentEventType.MODEL_CALL_END,
                AgentEventType.TOOL_CALL_START, AgentEventType.TOOL_CALL_END);
        // 规划、推理步与汇总共三次模型调用
        assertThat(types.stream().filter(type -> type == AgentEventType.MODEL_CALL_START).count()).isEqualTo(3);
        assertThat(types.stream().filter(type -> type == AgentEventType.MODEL_CALL_END).count()).isEqualTo(3);
        // 一个工具步各一对工具调用事件
        assertThat(types.stream().filter(type -> type == AgentEventType.TOOL_CALL_START).count()).isEqualTo(1);
        assertThat(types.stream().filter(type -> type == AgentEventType.TOOL_CALL_END).count()).isEqualTo(1);
        // 生命周期：首事件AGENT_START，末事件AGENT_END，结果事件携带最终答案
        assertThat(types.get(0)).isEqualTo(AgentEventType.AGENT_START);
        AgentEvent last = events.get(events.size() - 1);
        assertThat(last.getType()).isEqualTo(AgentEventType.AGENT_END);
        AgentEvent resultEvent = events.get(events.size() - 2);
        assertThat(resultEvent).isInstanceOf(AgentResultEvent.class);
        assertThat(((AgentResultEvent) resultEvent).getResult().getTextContent()).isEqualTo("最终答案");
        verify(modelCaller, times(3)).stream(any(), any());
        verify(toolExecutor, times(1)).executeTools(any(), any(), any());
    }

    /**
     * 计划解析失败时降级为单步直接执行：执行模型返回的工具调用后汇总，仍以AgentResultEvent终止
     */
    @Test
    void shouldFallbackToSingleStepWhenPlanParseFails() {
        AgentToolUseBlock toolUse = new AgentToolUseBlock("toolA", "call-1", Map.of("query", "alpha"));
        AgentChatResponse fallbackResponse = new AgentChatResponse(List.of(toolUse), null);
        when(modelCaller.stream(any(), any()))
                .thenReturn(Flux.just(textResponse("这不是一份有效的计划")))
                .thenReturn(Flux.just(fallbackResponse))
                .thenReturn(Flux.just(textResponse("降级最终答案")));
        stubToolResults();

        List<AgentEvent> events = new PlanExecuteEngine().run(
                List.of(MessageFactory.createUserMessage("无法规划的问题")), context)
                .collectList().block();

        assertThat(events).isNotNull();
        AgentEvent last = events.get(events.size() - 1);
        assertThat(last.getType()).isEqualTo(AgentEventType.AGENT_END);
        AgentEvent resultEvent = events.get(events.size() - 2);
        assertThat(resultEvent).isInstanceOf(AgentResultEvent.class);
        assertThat(((AgentResultEvent) resultEvent).getResult().getTextContent()).isEqualTo("降级最终答案");
        // 降级路径走批量工具执行接口且仅执行一次
        verify(toolExecutor, times(1)).executeTools(any(), any(), any());
        // 规划、单步直接执行与汇总共三次模型调用
        verify(modelCaller, times(3)).stream(any(), any());
    }

    /**
     * 计划步数超出maxSteps时截断执行剩余步骤并终止收尾
     */
    @Test
    void shouldTruncatePlanAndFinishWhenStepsExceedMaxSteps() {
        when(modelCaller.stream(any(), any()))
                .thenReturn(Flux.just(textResponse(THREE_STEP_PLAN)))
                .thenReturn(Flux.just(textResponse("截断后最终答案")));
        stubToolResults();

        List<AgentEvent> events = new PlanExecuteEngine(new ParadigmOptions().maxSteps(2)).run(
                List.of(MessageFactory.createUserMessage("超步数任务")), context)
                .collectList().block();

        assertThat(events).isNotNull();
        List<AgentEventType> types = events.stream()
                .map(AgentEvent::getType)
                .toList();
        // 仅执行前两个工具步
        assertThat(types.stream().filter(type -> type == AgentEventType.TOOL_CALL_START).count()).isEqualTo(2);
        assertThat(types.stream().filter(type -> type == AgentEventType.TOOL_CALL_END).count()).isEqualTo(2);
        // 事件流以AgentResultEvent后接AGENT_END收尾且携带最终答案
        AgentEvent last = events.get(events.size() - 1);
        assertThat(last.getType()).isEqualTo(AgentEventType.AGENT_END);
        AgentEvent resultEvent = events.get(events.size() - 2);
        assertThat(resultEvent).isInstanceOf(AgentResultEvent.class);
        assertThat(((AgentResultEvent) resultEvent).getResult().getTextContent()).isEqualTo("截断后最终答案");
        verify(toolExecutor, times(2)).executeTools(any(), any(), any());
        // 规划与汇总共两次模型调用
        verify(modelCaller, times(2)).stream(any(), any());
    }

    /**
     * 生命周期契约：事件流以AGENT_START开头，AgentResultEvent之后以AGENT_END收尾且无ERROR
     */
    @Test
    void shouldWrapResultWithLifecycleEvents() {
        when(modelCaller.stream(any(), any()))
                .thenReturn(Flux.just(textResponse(PLAN_JSON)))
                .thenReturn(Flux.just(textResponse("生命周期推理结论")))
                .thenReturn(Flux.just(textResponse("生命周期最终答案")));
        stubToolResults();

        List<AgentEvent> events = new PlanExecuteEngine().run(
                List.of(MessageFactory.createUserMessage("生命周期任务")), context)
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