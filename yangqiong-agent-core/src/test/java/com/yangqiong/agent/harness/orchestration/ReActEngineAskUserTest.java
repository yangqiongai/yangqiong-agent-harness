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
package com.yangqiong.agent.harness.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import com.yangqiong.agent.harness.engine.AgentRuntimeContext;
import com.yangqiong.agent.harness.engine.EngineContext;
import com.yangqiong.agent.harness.engine.MiddlewareChain;
import com.yangqiong.agent.harness.engine.ReActEngine;
import com.yangqiong.agent.harness.core.message.MessageFactory;
import com.yangqiong.agent.harness.model.ModelCaller;
import com.yangqiong.agent.harness.model.ModelResponseParser;
import com.yangqiong.agent.harness.tool.ToolExecutor;
import com.yangqiong.agent.harness.core.event.AgentEvent;
import com.yangqiong.agent.harness.core.event.AgentEventType;
import com.yangqiong.agent.harness.core.event.AgentResultEvent;
import com.yangqiong.agent.harness.core.event.ClarificationAnswer;
import com.yangqiong.agent.harness.core.event.RequireUserClarificationEvent;
import com.yangqiong.agent.harness.core.message.AgentMessage;
import com.yangqiong.agent.harness.core.message.AgentMessageRole;
import com.yangqiong.agent.harness.core.message.AgentTextBlock;
import com.yangqiong.agent.harness.core.message.AgentToolResultBlock;
import com.yangqiong.agent.harness.core.message.AgentToolUseBlock;
import com.yangqiong.agent.harness.core.model.AgentChatResponse;
import org.junit.jupiter.api.Test;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * ReAct引擎澄清续接测试
 * @author yangqiong
 */
class ReActEngineAskUserTest {

    /**
     * 构建返回ask_user工具调用响应的模型，首轮返回澄清调用、后续按给定响应返回
     * @param askUseId
     * @param next
     * @return
     */
    private ModelCaller askThen(String askUseId, AgentChatResponse next) {
        ModelCaller modelCaller = mock(ModelCaller.class);
        AgentToolUseBlock askUse = new AgentToolUseBlock("ask_user", askUseId,
                Map.of("question", "请告诉我项目预算上限？"));
        AgentChatResponse askResponse = new AgentChatResponse(List.of(askUse), null);
        when(modelCaller.stream(any(), any()))
                .thenReturn(Flux.just(askResponse))
                .thenReturn(Flux.just(next));
        return modelCaller;
    }

    /**
     * 构建返回澄清标记块的工具执行器
     * @param askUseId
     * @return
     */
    private ToolExecutor clarificationExecutor(String askUseId) {
        ToolExecutor toolExecutor = mock(ToolExecutor.class);
        AgentToolResultBlock clarification = AgentToolResultBlock
                .clarification("请告诉我项目预算上限？")
                .withToolUseId(askUseId);
        AgentMessage toolMsg = MessageFactory.createToolMessage(clarification);
        when(toolExecutor.executeTools(any(), any(), any()))
                .thenReturn(Mono.just(List.of(toolMsg)));
        return toolExecutor;
    }

    @Test
    void modelAsksUserThenResumeContinues() {
        AgentTextBlock finalText = AgentTextBlock.builder().text("根据您提供的预算上限完成评估").build();
        ModelCaller modelCaller = askThen("id1", new AgentChatResponse(List.of(finalText), null));
        ToolExecutor toolExecutor = clarificationExecutor("id1");

        MiddlewareChain chain = new MiddlewareChain(List.of());
        ModelResponseParser parser = new ModelResponseParser();
        ReActEngine engine = new ReActEngine(modelCaller, toolExecutor, chain, parser, null);
        EngineContext ctx = new EngineContext(null, null, AgentRuntimeContext.empty(), null, 10, null);

        // 首轮：模型调用ask_user，引擎暂停并发出澄清事件
        List<AgentEvent> events = engine.stream(
                List.of(MessageFactory.createUserMessage("帮我做一个预算评估")), ctx)
                .collectList().block();

        RequireUserClarificationEvent clarification = events.stream()
                .filter(e -> e.getType() == AgentEventType.REQUIRE_USER_CLARIFICATION)
                .map(e -> (RequireUserClarificationEvent) e)
                .findFirst().orElse(null);
        assertThat(clarification).isNotNull();
        assertThat(clarification.getQuestion()).isEqualTo("请告诉我项目预算上限？");
        assertThat(clarification.getToolCallId()).isEqualTo("id1");

        // 续接：注入用户答案后引擎继续并产出最终结果
        List<AgentEvent> resumeEvents = engine.resumeWithClarification(
                List.of(new ClarificationAnswer("id1", "预算上限100万")), ctx)
                .collectList().block();

        AgentResultEvent result = resumeEvents.stream()
                .filter(e -> e.getType() == AgentEventType.AGENT_RESULT)
                .map(e -> (AgentResultEvent) e)
                .findFirst().orElse(null);
        assertThat(result).isNotNull();
        assertThat(result.getResult().getTextContent()).isEqualTo("根据您提供的预算上限完成评估");
    }

    @Test
    void resumeInjectsAnswerIntoConversation() {
        AtomicReference<List<AgentMessage>> captured = new AtomicReference<>();
        AgentTextBlock finalText = AgentTextBlock.builder().text("完成").build();
        ModelCaller modelCaller = mock(ModelCaller.class);
        AgentToolUseBlock askUse = new AgentToolUseBlock("ask_user", "id2",
                Map.of("question", "您的部门是？"));
        AgentChatResponse askResponse = new AgentChatResponse(List.of(askUse), null);
        when(modelCaller.stream(any(), any()))
                .thenReturn(Flux.just(askResponse))
                .thenAnswer(invocation -> {
                    captured.set(invocation.getArgument(0));
                    return Flux.just(new AgentChatResponse(List.of(finalText), null));
                });
        ToolExecutor toolExecutor = clarificationExecutor("id2");

        MiddlewareChain chain = new MiddlewareChain(List.of());
        ModelResponseParser parser = new ModelResponseParser();
        ReActEngine engine = new ReActEngine(modelCaller, toolExecutor, chain, parser, null);
        EngineContext ctx = new EngineContext(null, null, AgentRuntimeContext.empty(), null, 10, null);

        engine.stream(List.of(MessageFactory.createUserMessage("开始")), ctx).collectList().block();
        engine.resumeWithClarification(
                List.of(new ClarificationAnswer("id2", "财务部")), ctx).collectList().block();

        List<AgentMessage> resumedMessages = captured.get();
        assertThat(resumedMessages).isNotNull();
        // 澄清工具结果消息已被用户答案消息替换
        AgentMessage answerMsg = resumedMessages.stream()
                .filter(m -> m.getRole() == AgentMessageRole.USER)
                .reduce((first, second) -> second)
                .orElse(null);
        assertThat(answerMsg).isNotNull();
        assertThat(answerMsg.getTextContent()).isEqualTo("财务部");
        assertThat(resumedMessages).noneMatch(m -> m.getRole() == AgentMessageRole.TOOL
                && m.getTextContent().contains("预算"));
        // 续接后不应残留携带tool_calls的assistant消息，避免"tool_calls后无对应tool消息"的协议错误
        assertThat(resumedMessages).noneMatch(m -> m.getRole() == AgentMessageRole.ASSISTANT
                && m.getContent().stream().anyMatch(AgentToolUseBlock.class::isInstance));
    }

    @Test
    void streamingToolCallIdRestoredAndResumeClean() {
        // 模拟真实流式分片：工具调用按index派生稳定id，真实id保存在保留键中，由解析器合并时恢复
        AtomicReference<List<AgentMessage>> captured = new AtomicReference<>();
        AgentTextBlock finalText = AgentTextBlock.builder().text("流式续接完成").build();
        ModelCaller modelCaller = mock(ModelCaller.class);
        when(modelCaller.stream(any(), any()))
                .thenReturn(Flux.just(
                        new AgentChatResponse(List.of(streamToolChunk("call_stream_0", "call_real_1",
                                "{\"question\":\"请告诉我")), null),
                        new AgentChatResponse(List.of(streamToolChunk("call_stream_0", null,
                                "预算上限？\"}")), null)))
                .thenAnswer(invocation -> {
                    captured.set(invocation.getArgument(0));
                    return Flux.just(new AgentChatResponse(List.of(finalText), null));
                });
        ToolExecutor toolExecutor = clarificationExecutor("call_real_1");

        MiddlewareChain chain = new MiddlewareChain(List.of());
        ModelResponseParser parser = new ModelResponseParser();
        ReActEngine engine = new ReActEngine(modelCaller, toolExecutor, chain, parser, null);
        EngineContext ctx = new EngineContext(null, null, AgentRuntimeContext.empty(), null, 10, null);

        List<AgentEvent> events = engine.stream(
                List.of(MessageFactory.createUserMessage("帮我做一个预算评估")), ctx).collectList().block();
        RequireUserClarificationEvent clarification = events.stream()
                .filter(e -> e.getType() == AgentEventType.REQUIRE_USER_CLARIFICATION)
                .map(e -> (RequireUserClarificationEvent) e)
                .findFirst().orElse(null);
        // 合并后真实id被恢复，澄清事件携带真实id
        assertThat(clarification).isNotNull();
        assertThat(clarification.getToolCallId()).isEqualTo("call_real_1");

        engine.resumeWithClarification(
                List.of(new ClarificationAnswer("call_real_1", "预算上限100万")), ctx).collectList().block();

        List<AgentMessage> resumedMessages = captured.get();
        assertThat(resumedMessages).isNotNull();
        // 续接后不应残留任何携带tool_calls的assistant消息
        assertThat(resumedMessages).noneMatch(m -> m.getRole() == AgentMessageRole.ASSISTANT
                && m.getContent().stream().anyMatch(AgentToolUseBlock.class::isInstance));
    }

    /**
     * 构造流式工具调用分片（真实id与参数前缀只出现在首个分片）
     * @param stableId
     * @param realId
     * @param argsFragment
     * @return
     */
    private static AgentToolUseBlock streamToolChunk(String stableId, String realId, String argsFragment) {
        Map<String, Object> input = new java.util.HashMap<>();
        if (realId != null) {
            input.put(ModelResponseParser.TOOL_CALL_ID_KEY, realId);
        }
        input.put(ModelResponseParser.RAW_ARGS_KEY, argsFragment);
        return new AgentToolUseBlock("ask_user", stableId, input);
    }

    @Test
    void consecutiveClarificationOverLimitAborts() {
        AgentToolUseBlock askUse = new AgentToolUseBlock("ask_user", "id3",
                Map.of("question", "还需确认什么？"));
        AgentChatResponse askResponse = new AgentChatResponse(List.of(askUse), null);
        ModelCaller modelCaller = mock(ModelCaller.class);
        when(modelCaller.stream(any(), any())).thenReturn(Flux.just(askResponse));
        ToolExecutor toolExecutor = clarificationExecutor("id3");

        MiddlewareChain chain = new MiddlewareChain(List.of());
        ModelResponseParser parser = new ModelResponseParser();
        ReActEngine engine = new ReActEngine(modelCaller, toolExecutor, chain, parser, null);
        EngineContext ctx = new EngineContext(null, null, AgentRuntimeContext.empty(), null, 10, null);

        // 前3次澄清正常暂停
        List<AgentEvent> first = engine.stream(
                List.of(MessageFactory.createUserMessage("开始")), ctx).collectList().block();
        assertThat(first).anyMatch(e -> e.getType() == AgentEventType.REQUIRE_USER_CLARIFICATION);
        for (int i = 0; i < 2; i++) {
            List<AgentEvent> resumed = engine.resumeWithClarification(
                    List.of(new ClarificationAnswer("id3", "答案" + i)), ctx).collectList().block();
            assertThat(resumed).anyMatch(e -> e.getType() == AgentEventType.REQUIRE_USER_CLARIFICATION);
        }

        // 第4次澄清超限，报错中止
        List<AgentEvent> overLimit = engine.resumeWithClarification(
                List.of(new ClarificationAnswer("id3", "答案3")), ctx).collectList().block();
        assertThat(overLimit).anyMatch(e -> e.getType() == AgentEventType.ERROR);
        assertThat(overLimit).noneMatch(e -> e.getType() == AgentEventType.REQUIRE_USER_CLARIFICATION);
    }

    @Test
    void resumeWithoutPendingReturnsError() {
        AgentTextBlock text = AgentTextBlock.builder().text("ok").build();
        ModelCaller modelCaller = mock(ModelCaller.class);
        when(modelCaller.stream(any(), any())).thenReturn(Flux.just(new AgentChatResponse(List.of(text), null)));
        ToolExecutor toolExecutor = mock(ToolExecutor.class);
        MiddlewareChain chain = new MiddlewareChain(List.of());
        ModelResponseParser parser = new ModelResponseParser();
        ReActEngine engine = new ReActEngine(modelCaller, toolExecutor, chain, parser, null);
        EngineContext ctx = new EngineContext(null, null, AgentRuntimeContext.empty(), null, 10, null);

        List<AgentEvent> events = engine.resumeWithClarification(
                List.of(new ClarificationAnswer("x", "答案")), ctx).collectList().block();
        assertThat(events).anyMatch(e -> e.getType() == AgentEventType.ERROR);
    }
}