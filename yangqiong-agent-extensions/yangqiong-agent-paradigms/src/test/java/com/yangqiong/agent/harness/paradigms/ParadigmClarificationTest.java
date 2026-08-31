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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.yangqiong.agent.harness.core.event.AgentEvent;
import com.yangqiong.agent.harness.core.event.AgentEventType;
import com.yangqiong.agent.harness.core.event.AgentResultEvent;
import com.yangqiong.agent.harness.core.event.ClarificationAnswer;
import com.yangqiong.agent.harness.core.event.RequireUserClarificationEvent;
import com.yangqiong.agent.harness.core.message.AgentMessage;
import com.yangqiong.agent.harness.core.message.AgentTextBlock;
import com.yangqiong.agent.harness.core.message.AgentToolResultBlock;
import com.yangqiong.agent.harness.core.message.MessageFactory;
import com.yangqiong.agent.harness.core.model.AgentChatResponse;
import com.yangqiong.agent.harness.engine.AgentRuntimeContext;
import com.yangqiong.agent.harness.engine.EngineContext;
import com.yangqiong.agent.harness.model.ModelCaller;
import com.yangqiong.agent.harness.model.ModelResponseParser;
import com.yangqiong.agent.harness.tool.ToolExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 范式引擎澄清暂停与恢复测试
 * <p>
 * 以PlanExecuteEngine为载体验证AbstractAgentLoop统一实现的ask_user澄清能力：
 * 工具结果带澄清标记时暂停并发射REQUIRE_USER_CLARIFICATION事件，
 * 恢复时答案记入台账并以原始输入重放，命中台账的提问被短路合成答案结果，
 * 不再真实执行；台账未命中的新问题会再次暂停。
 * </p>
 * @author yangqiong
 */
class ParadigmClarificationTest {

    /**
     * 测试计划：单个ask_user工具步，询问部署环境
     */
    private static final String PLAN_ASK_ENV = "["
            + "{\"type\":\"tool\",\"tool\":\"ask_user\",\"args\":{\"question\":\"部署环境是哪个？\"},\"desc\":\"询问部署环境\"}"
            + "]";

    /**
     * 测试计划：单个ask_user工具步，询问新问题（台账未命中）
     */
    private static final String PLAN_ASK_VERSION = "["
            + "{\"type\":\"tool\",\"tool\":\"ask_user\",\"args\":{\"question\":\"目标版本号是多少？\"},\"desc\":\"询问版本号\"}"
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
     * 构建模型与工具桩（无审批协调器，走纯澄清路径）
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
                Map.of("name", "ask_user", "type", "object")));
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
     * 桩工具执行器：ask_user返回带澄清标记的结果块，其余工具返回固定文本
     */
    private void stubAskUserTool() {
        when(toolExecutor.executeTools(any(), any(), any())).thenAnswer(invocation -> {
            List<com.yangqiong.agent.harness.core.message.AgentToolUseBlock> calls = invocation.getArgument(0);
            List<AgentMessage> results = new ArrayList<>();
            for (com.yangqiong.agent.harness.core.message.AgentToolUseBlock call : calls) {
                if ("ask_user".equals(call.getToolName())) {
                    Object question = call.getInput().get("question");
                    results.add(MessageFactory.createToolMessage(
                            AgentToolResultBlock.clarification(question != null ? question.toString() : "")
                                    .withToolUseId(call.getToolUseId())));
                } else {
                    results.add(MessageFactory.createToolMessage(AgentToolResultBlock.of(call.getToolUseId(),
                            List.of(AgentTextBlock.builder().text("result-of-" + call.getToolName()).build()))));
                }
            }
            return Mono.just(results);
        });
    }

    /**
     * 构建用户输入
     * @return
     */
    private List<AgentMessage> inputs() {
        return List.of(MessageFactory.createUserMessage("请协助完成部署任务"));
    }

    @Test
    void shouldPauseWithClarificationEventWhenAskUserResultObserved() {
        stubAskUserTool();
        when(modelCaller.stream(any(), any())).thenReturn(Flux.just(textResponse(PLAN_ASK_ENV)));

        List<AgentEvent> events = new PlanExecuteEngine().run(inputs(), context).collectList().block();

        assertThat(events).isNotNull();
        RequireUserClarificationEvent clarification = events.stream()
                .filter(e -> e instanceof RequireUserClarificationEvent)
                .map(e -> (RequireUserClarificationEvent) e)
                .findFirst()
                .orElse(null);
        assertThat(clarification).as("应发射需要用户澄清事件并暂停").isNotNull();
        assertThat(clarification.getQuestion()).isEqualTo("部署环境是哪个？");
        assertThat(clarification.getToolCallId()).isEqualTo("plan-E1");
        // 暂停截断后不应有AGENT_END收口
        assertThat(events.stream().noneMatch(e -> e.getType() == AgentEventType.AGENT_END))
                .as("澄清暂停后本轮不应收口").isTrue();
    }

    @Test
    void shouldResumeViaLedgerReplayWithSynthesizedAnswerAndNoToolExecution() {
        stubAskUserTool();
        // 模型调用序列：首轮规划 → 重放规划 → 汇总
        when(modelCaller.stream(any(), any()))
                .thenReturn(Flux.just(textResponse(PLAN_ASK_ENV)))
                .thenReturn(Flux.just(textResponse(PLAN_ASK_ENV)))
                .thenReturn(Flux.just(textResponse("最终汇总答案")));

        PlanExecuteEngine engine = new PlanExecuteEngine();
        engine.run(inputs(), context).collectList().block();
        List<AgentEvent> resumed = engine.resumeWithClarification(
                List.of(new ClarificationAnswer("plan-E1", "生产环境")), context).collectList().block();

        assertThat(resumed).isNotNull();
        // 台账已命中，重放不应再次澄清暂停
        assertThat(resumed.stream().noneMatch(e -> e instanceof RequireUserClarificationEvent))
                .as("台账已有答案，重放不应再次暂停").isTrue();
        // 首轮执行过一次ask_user产生澄清标记，重放时被台账短路不再执行
        verify(toolExecutor, times(1)).executeTools(any(), any(), any());
        // TOOL_CALL_END携带合成的用户答案结果
        String toolResultText = resumed.stream()
                .filter(e -> e.getType() == AgentEventType.TOOL_CALL_END)
                .map(this::extractToolResultText)
                .collect(Collectors.joining());
        assertThat(toolResultText).contains("生产环境");
        // 范式循环正常收口
        AgentResultEvent result = resumed.stream()
                .filter(e -> e instanceof AgentResultEvent)
                .map(e -> (AgentResultEvent) e)
                .findFirst()
                .orElse(null);
        assertThat(result).as("恢复后应产出最终结果").isNotNull();
        assertThat(result.getResult().getTextContent()).isEqualTo("最终汇总答案");
    }

    @Test
    void shouldPauseAgainWhenNewQuestionNotInLedger() {
        stubAskUserTool();
        // 模型调用序列：首轮规划(问环境) → 重放规划(问版本，台账未命中)
        when(modelCaller.stream(any(), any()))
                .thenReturn(Flux.just(textResponse(PLAN_ASK_ENV)))
                .thenReturn(Flux.just(textResponse(PLAN_ASK_VERSION)));

        PlanExecuteEngine engine = new PlanExecuteEngine();
        engine.run(inputs(), context).collectList().block();
        List<AgentEvent> resumed = engine.resumeWithClarification(
                List.of(new ClarificationAnswer("plan-E1", "生产环境")), context).collectList().block();

        assertThat(resumed).isNotNull();
        // 台账未命中新问题，应再次澄清暂停且问题为新问题
        RequireUserClarificationEvent second = resumed.stream()
                .filter(e -> e instanceof RequireUserClarificationEvent)
                .map(e -> (RequireUserClarificationEvent) e)
                .findFirst()
                .orElse(null);
        assertThat(second).as("台账未命中的新问题应再次暂停").isNotNull();
        assertThat(second.getQuestion()).isEqualTo("目标版本号是多少？");
        // 首轮执行环境问题1次，重放时新版本问题台账未命中真实执行1次
        verify(toolExecutor, times(2)).executeTools(any(), any(), any());
    }

    /**
     * 从TOOL_CALL_END事件中提取各工具结果文本
     * @param event
     * @return
     */
    private String extractToolResultText(AgentEvent event) {
        StringBuilder text = new StringBuilder();
        Object payload = event.getPayload();
        if (payload instanceof List<?> results) {
            for (Object item : results) {
                if (item instanceof AgentMessage message) {
                    for (var block : message.getContent()) {
                        if (block instanceof AgentToolResultBlock resultBlock) {
                            text.append(resultBlock.getTextContent()).append('\n');
                        }
                    }
                }
            }
        }
        return text.toString();
    }
}
