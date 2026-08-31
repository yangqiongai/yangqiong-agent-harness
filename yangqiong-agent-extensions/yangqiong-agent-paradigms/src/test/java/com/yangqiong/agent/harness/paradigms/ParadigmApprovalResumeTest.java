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
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.yangqiong.agent.harness.paradigms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.yangqiong.agent.harness.core.event.AgentEvent;
import com.yangqiong.agent.harness.core.event.AgentEventType;
import com.yangqiong.agent.harness.core.event.AgentResultEvent;
import com.yangqiong.agent.harness.core.event.ConfirmResult;
import com.yangqiong.agent.harness.core.event.RequireUserConfirmEvent;
import com.yangqiong.agent.harness.core.message.AgentMessage;
import com.yangqiong.agent.harness.core.message.AgentTextBlock;
import com.yangqiong.agent.harness.core.message.AgentToolResultBlock;
import com.yangqiong.agent.harness.core.message.AgentToolUseBlock;
import com.yangqiong.agent.harness.core.message.MessageFactory;
import com.yangqiong.agent.harness.core.model.AgentChatResponse;
import com.yangqiong.agent.harness.engine.AgentRuntimeContext;
import com.yangqiong.agent.harness.engine.ApprovalCoordinator;
import com.yangqiong.agent.harness.engine.EngineContext;
import com.yangqiong.agent.harness.model.ModelCaller;
import com.yangqiong.agent.harness.model.ModelResponseParser;
import com.yangqiong.agent.harness.tool.ToolExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 范式引擎审批暂停与恢复测试
 * <p>
 * 以PlanExecuteEngine为载体验证AbstractAgentLoop统一实现的审批能力：
 * 工具步触发人工确认时暂停，恢复时决策记入台账并以原始输入重放，
 * 已批准调用照常执行，已拒绝调用以错误结果占位且不再重复请示。
 * </p>
 * @author yangqiong
 */
class ParadigmApprovalResumeTest {

    /**
     * 测试计划：两个工具步，第一步触发暂停，第二步由恢复台账自动拒绝
     */
    private static final String PLAN_JSON = "["
            + "{\"type\":\"tool\",\"tool\":\"toolA\",\"args\":{\"query\":\"one\"},\"desc\":\"第一步\"},"
            + "{\"type\":\"tool\",\"tool\":\"toolB\",\"args\":{\"query\":\"two\"},\"desc\":\"第二步\"}"
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
     * 审批协调器桩
     */
    private ApprovalCoordinator coordinator;

    /**
     * 引擎上下文桩
     */
    private EngineContext context;

    /**
     * 构建模型、工具与审批桩
     */
    @BeforeEach
    void setUp() {
        modelCaller = mock(ModelCaller.class);
        toolExecutor = mock(ToolExecutor.class);
        coordinator = mock(ApprovalCoordinator.class);
        context = mock(EngineContext.class);
        when(context.getModelCaller()).thenReturn(modelCaller);
        when(context.getToolExecutor()).thenReturn(toolExecutor);
        when(context.getResponseParser()).thenReturn(new ModelResponseParser());
        when(context.getAllToolSchemas(any())).thenReturn(List.of(
                Map.of("name", "toolA", "type", "object"),
                Map.of("name", "toolB", "type", "object")));
        when(context.getAgentName()).thenReturn("test-agent");
        when(context.getRuntimeContext()).thenReturn(AgentRuntimeContext.empty());
        when(modelCaller.modelName()).thenReturn("test-model");
        when(context.getApprovalCoordinator()).thenReturn(coordinator);
        when(coordinator.isEnabled()).thenReturn(true);
        // 全部工具调用均需要人工确认
        when(coordinator.triage(any(), any(), any())).thenAnswer(invocation -> {
            ApprovalCoordinator.TriageResult triage = new ApprovalCoordinator.TriageResult();
            List<AgentToolUseBlock> calls = invocation.getArgument(0);
            triage.getAskCalls().addAll(calls);
            return triage;
        });
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
     * 构建用户输入
     * @return
     */
    private List<AgentMessage> inputs() {
        return List.of(MessageFactory.createUserMessage("请依次完成两个工具步"));
    }

    @Test
    void shouldPauseWithPendingCallsWhenApprovalRequired() {
        when(modelCaller.stream(any(), any()))
                .thenReturn(Flux.just(textResponse(PLAN_JSON)));
        List<AgentEvent> events = new PlanExecuteEngine().run(inputs(), context).collectList().block();

        assertThat(events).isNotNull();
        RequireUserConfirmEvent confirm = events.stream()
                .filter(e -> e instanceof RequireUserConfirmEvent)
                .map(e -> (RequireUserConfirmEvent) e)
                .findFirst()
                .orElse(null);
        assertThat(confirm).as("应发射需要人工确认事件并暂停").isNotNull();
        assertThat(confirm.getPendingToolCalls()).hasSize(1);
        assertThat(confirm.getPendingToolCalls().get(0).getToolName()).isEqualTo("toolA");
        assertThat(events.stream().noneMatch(e -> e.getType() == AgentEventType.TOOL_CALL_START))
                .as("暂停前不应执行任何工具")
                .isTrue();
        verify(toolExecutor, never()).executeTools(any(), any(), any());
    }

    @Test
    void shouldResumeViaLedgerReplayWithApprovedExecutedAndDeniedPlaceholder() {
        stubToolResults();
        // 模型调用序列：首轮规划 → 重放规划 → 汇总
        when(modelCaller.stream(any(), any()))
                .thenReturn(Flux.just(textResponse(PLAN_JSON)))
                .thenReturn(Flux.just(textResponse(PLAN_JSON)))
                .thenReturn(Flux.just(textResponse("最终汇总答案")));

        PlanExecuteEngine engine = new PlanExecuteEngine();
        engine.run(inputs(), context).collectList().block();
        // 批准第一步，拒绝第二步，恢复后重放
        List<AgentEvent> resumed = engine.resume(
                List.of(ConfirmResult.approveCall("plan-E1", "toolA"),
                        ConfirmResult.denyCall("plan-E2", "toolB", "不需要")),
                context).collectList().block();

        assertThat(resumed).isNotNull();
        // 重放期间不再出现人工确认暂停
        assertThat(resumed.stream().noneMatch(e -> e instanceof RequireUserConfirmEvent))
                .as("台账已有决策，重放不应再次暂停")
                .isTrue();
        // 仅批准的第一步真实执行
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AgentToolUseBlock>> captor =
                ArgumentCaptor.forClass((Class<List<AgentToolUseBlock>>) (Class<?>) List.class);
        verify(toolExecutor).executeTools(captor.capture(), any(), any());
        assertThat(captor.getValue()).as("仅批准的toolA应被执行")
                .hasSize(1)
                .extracting(AgentToolUseBlock::getToolName)
                .containsExactly("toolA");
        // 工具结果事件按原始次序对齐：第一步执行结果 + 第二步拒绝占位
        String allToolResultText = resumed.stream()
                .filter(e -> e.getType() == AgentEventType.TOOL_CALL_END)
                .map(this::extractToolResultText)
                .collect(Collectors.joining());
        assertThat(allToolResultText).contains("result-of-toolA").contains("人工拒绝");
        // 范式循环正常收口
        AgentResultEvent result = resumed.stream()
                .filter(e -> e instanceof AgentResultEvent)
                .map(e -> (AgentResultEvent) e)
                .findFirst()
                .orElse(null);
        assertThat(result).as("恢复后应产出最终结果").isNotNull();
        assertThat(result.getResult().getTextContent()).isEqualTo("最终汇总答案");
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
