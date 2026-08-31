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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import com.yangqiong.agent.harness.core.event.AgentEvent;
import com.yangqiong.agent.harness.core.event.AgentEventType;
import com.yangqiong.agent.harness.core.event.AgentResultEvent;
import com.yangqiong.agent.harness.core.message.AgentMessage;
import com.yangqiong.agent.harness.core.message.AgentMessageRole;
import com.yangqiong.agent.harness.core.message.AgentTextBlock;
import com.yangqiong.agent.harness.core.message.MessageFactory;
import com.yangqiong.agent.harness.core.model.AgentChatResponse;
import com.yangqiong.agent.harness.engine.AbstractAgentLoop;
import com.yangqiong.agent.harness.engine.EngineContext;
import com.yangqiong.agent.harness.model.ModelCaller;
import com.yangqiong.agent.harness.model.ModelResponseParser;
import com.yangqiong.agent.harness.tool.ToolExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

/**
 * 范式路由引擎测试
 * @author yangqiong
 */
class RouterEngineTest {

    /**
     * 模型调用桩
     */
    private ModelCaller modelCaller;

    /**
     * 引擎上下文桩
     */
    private EngineContext context;

    /**
     * 按路由次序记录的选中范式
     */
    private List<RouterEngine.ParadigmType> routedTo;

    /**
     * 按调用次序记录的模型输入消息
     */
    private List<List<AgentMessage>> recordedMessages;

    @BeforeEach
    void setUp() {
        modelCaller = mock(ModelCaller.class);
        context = mock(EngineContext.class);
        routedTo = new ArrayList<>();
        recordedMessages = new ArrayList<>();
        when(context.getModelCaller()).thenReturn(modelCaller);
        when(context.getResponseParser()).thenReturn(new ModelResponseParser());
        when(context.getAgentName()).thenReturn("router-agent");
    }

    @Test
    void shouldRouteToSelectedParadigmAndEmitDecision() {
        when(context.getToolExecutor()).thenReturn(mock(ToolExecutor.class));
        stubClassification("{\"paradigm\": \"rewoo\", \"reason\": \"工具步可并行\"}");

        List<AgentEvent> events = run(null);

        assertThat(routedTo).containsExactly(RouterEngine.ParadigmType.REWOO);
        RouterEngine.RoutingDecision decision = extractDecision(events);
        assertThat(decision).isNotNull();
        assertThat(decision.getParadigm()).isEqualTo(RouterEngine.ParadigmType.REWOO);
        assertThat(decision.getReason()).isEqualTo("工具步可并行");
        assertThat(events.get(events.size() - 1).getType()).isEqualTo(AgentEventType.AGENT_END);
        assertThat(events.stream().anyMatch(e -> e.getType() == AgentEventType.AGENT_RESULT)).isTrue();
    }

    @Test
    void shouldFallBackToReactWhenClassificationUnparseable() {
        when(context.getToolExecutor()).thenReturn(mock(ToolExecutor.class));
        stubClassification("抱歉，我无法判断该使用哪个范式。");

        run(null);

        assertThat(routedTo).containsExactly(RouterEngine.ParadigmType.REACT);
    }

    @Test
    void shouldExcludeToolParadigmsFromCandidatesWithoutTools() {
        when(context.getToolExecutor()).thenReturn(null);
        stubClassification("{\"paradigm\": \"self_ask\", \"reason\": \"复合问题需拆解\"}");

        List<AgentEvent> events = run(null);

        assertThat(routedTo).containsExactly(RouterEngine.ParadigmType.SELF_ASK);
        String routingPrompt = recordedMessages.get(0).get(0).getTextContent();
        assertThat(routingPrompt).contains("self_ask");
        assertThat(routingPrompt).doesNotContain("rewoo").doesNotContain("plan_execute").doesNotContain("react");
        assertThat(extractDecision(events).getParadigm()).isEqualTo(RouterEngine.ParadigmType.SELF_ASK);
    }

    @Test
    void shouldFallBackToFirstCandidateWhenNoToolsAndUnparseable() {
        when(context.getToolExecutor()).thenReturn(null);
        stubClassification("路由失败，无法输出JSON。");

        run(null);

        assertThat(routedTo).containsExactly(RouterEngine.ParadigmType.SELF_ASK);
    }

    @Test
    void shouldSkipRoutingWhenParadigmForced() {
        when(context.getToolExecutor()).thenReturn(mock(ToolExecutor.class));

        List<AgentEvent> events = run(RouterEngine.ParadigmType.REWOO);

        assertThat(routedTo).containsExactly(RouterEngine.ParadigmType.REWOO);
        assertThat(events.stream().anyMatch(e -> e.getType() == AgentEventType.MODEL_CALL_START)).isFalse();
        assertThat(events.stream().anyMatch(e -> e.getType() == AgentEventType.ENGINE_ROUTED)).isFalse();
        verify(modelCaller, never()).stream(any(), any());
    }

    @Test
    void shouldMatchEarliestKeywordWhenTextMentionsMultipleParadigms() {
        when(context.getToolExecutor()).thenReturn(mock(ToolExecutor.class));
        stubClassification("比较过plan_execute与rewoo后，推荐rewoo，因为步骤可并行。");

        run(null);

        assertThat(routedTo).containsExactly(RouterEngine.ParadigmType.PLAN_EXECUTE);
    }

    private List<AgentEvent> run(RouterEngine.ParadigmType forced) {
        return router(forced).run(
                List.of(MessageFactory.createUserMessage("请完成计算任务")),
                context
        ).collectList().block();
    }

    private RouterEngine router(RouterEngine.ParadigmType forced) {
        Function<RouterEngine.ParadigmType, AbstractAgentLoop> resolver = type -> {
            routedTo.add(type);
            return stubEngine();
        };
        return new RouterEngine(resolver, forced);
    }

    private AbstractAgentLoop stubEngine() {
        return new AbstractAgentLoop() {
            @Override
            protected Flux<AgentEvent> doRun(List<AgentMessage> inputs, EngineContext ctx) {
                return Flux.just(new AgentResultEvent(
                        AgentMessage.builder().role(AgentMessageRole.ASSISTANT).build(), null));
            }
        };
    }

    private void stubClassification(String text) {
        when(modelCaller.stream(any(), any())).thenAnswer(invocation -> {
            List<AgentMessage> messages = invocation.getArgument(0);
            recordedMessages.add(new ArrayList<>(messages));
            return Flux.just(textResponse(text));
        });
    }

    private AgentChatResponse textResponse(String text) {
        return new AgentChatResponse(List.of(AgentTextBlock.builder().text(text).build()), null);
    }

    private RouterEngine.RoutingDecision extractDecision(List<AgentEvent> events) {
        return events.stream()
                .filter(e -> e.getType() == AgentEventType.ENGINE_ROUTED)
                .map(e -> (RouterEngine.RoutingDecision) e.getPayload())
                .findFirst()
                .orElse(null);
    }
}
