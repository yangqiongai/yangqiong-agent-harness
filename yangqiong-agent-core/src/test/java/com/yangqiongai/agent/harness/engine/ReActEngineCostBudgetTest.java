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
package com.yangqiongai.agent.harness.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import com.yangqiongai.agent.harness.config.CostBudgetPolicy;
import com.yangqiongai.agent.harness.core.event.AgentEvent;
import com.yangqiongai.agent.harness.core.event.AgentEventType;
import com.yangqiongai.agent.harness.core.event.AgentResultEvent;
import com.yangqiongai.agent.harness.core.message.AgentChatUsage;
import com.yangqiongai.agent.harness.core.message.AgentMessage;
import com.yangqiongai.agent.harness.core.message.AgentTextBlock;
import com.yangqiongai.agent.harness.core.model.AgentChatResponse;
import com.yangqiongai.agent.harness.core.message.MessageFactory;
import com.yangqiongai.agent.harness.model.ModelCaller;
import com.yangqiongai.agent.harness.model.ModelPricing;
import com.yangqiongai.agent.harness.model.ModelPricingRegistry;
import com.yangqiongai.agent.harness.model.ModelResponseParser;
import com.yangqiongai.agent.harness.tool.ToolExecutor;

/**
 * ReAct引擎成本预算测试
 * @author yangqiong
 */
class ReActEngineCostBudgetTest {

    @Test
    void shouldEmitCostBudgetWarnWhenWarnExceeded() {
        ModelCaller modelCaller = mock(ModelCaller.class);
        ToolExecutor toolExecutor = mock(ToolExecutor.class);
        AgentTextBlock textBlock = AgentTextBlock.builder().text("final answer").build();
        AgentChatResponse response = new AgentChatResponse(List.of(textBlock),
                new AgentChatUsage(1000, 500, 1500));
        when(modelCaller.stream(any(), any())).thenReturn(Flux.just(response));

        MiddlewareChain chain = new MiddlewareChain(List.of());
        ModelResponseParser parser = new ModelResponseParser();
        ReActEngine engine = new ReActEngine(modelCaller, toolExecutor, chain, parser, null)
                .costBudgetPolicy(CostBudgetPolicy.warnOnly(0.001))
                .modelPricingRegistry(new ModelPricingRegistry() {{
                    register(new ModelPricing("gpt-4o", 2.5, 10.0));
                }})
                .modelCode("gpt-4o");

        EngineContext ctx = new EngineContext(null, null, AgentRuntimeContext.builder().build(),
                null, 10, null);

        AgentMessage input = MessageFactory.createUserMessage("hi");
        StepVerifier.create(engine.stream(List.of(input), ctx)
                .filter(ReActEngineCostBudgetTest::isRelevantEvent))
                .expectNextMatches(e -> e.getType() == AgentEventType.COST_BUDGET_WARN)
                .expectNextMatches(e -> e.getType() == AgentEventType.AGENT_RESULT)
                .expectNextMatches(e -> e.getType() == AgentEventType.COMPLETED)
                .verifyComplete();
    }

    @Test
    void shouldEmitCostBudgetExceededAndAbortWhenHardLimit() {
        ModelCaller modelCaller = mock(ModelCaller.class);
        ToolExecutor toolExecutor = mock(ToolExecutor.class);
        AgentTextBlock textBlock = AgentTextBlock.builder().text("final answer").build();
        AgentChatResponse response = new AgentChatResponse(List.of(textBlock),
                new AgentChatUsage(1000, 500, 1500));
        when(modelCaller.stream(any(), any())).thenReturn(Flux.just(response));

        MiddlewareChain chain = new MiddlewareChain(List.of());
        ModelResponseParser parser = new ModelResponseParser();
        // 硬限0.001美元，单次调用成本约(1*2.5 + 0.5*10)=7.5美元，远超硬限，走ABORT
        ReActEngine engine = new ReActEngine(modelCaller, toolExecutor, chain, parser, null)
                .costBudgetPolicy(CostBudgetPolicy.hardLimit(0.0005, 0.001))
                .modelPricingRegistry(new ModelPricingRegistry() {{
                    register(new ModelPricing("gpt-4o", 2.5, 10.0));
                }})
                .modelCode("gpt-4o");

        EngineContext ctx = new EngineContext(null, null, AgentRuntimeContext.builder().build(),
                null, 10, null);

        AgentMessage input = MessageFactory.createUserMessage("hi");
        // 硬限超限+ABORT应在MODEL_CALL_END后立即发射COST_BUDGET_EXCEEDED+ERROR+COMPLETED
        List<AgentEvent> events = engine.stream(List.of(input), ctx)
                .collectList().block();
        assertThat(events).isNotNull();
        // 应包含COST_BUDGET_EXCEEDED事件
        assertThat(events).anyMatch(e -> e.getType() == AgentEventType.COST_BUDGET_EXCEEDED);
        // 应包含ERROR事件
        assertThat(events).anyMatch(e -> e.getType() == AgentEventType.ERROR);
        // 不应有AGENT_RESULT（被中止）
        assertThat(events).noneMatch(e -> e.getType() == AgentEventType.AGENT_RESULT);
    }

    @Test
    void shouldIncludeTotalCostInResultEvent() {
        ModelCaller modelCaller = mock(ModelCaller.class);
        ToolExecutor toolExecutor = mock(ToolExecutor.class);
        AgentTextBlock textBlock = AgentTextBlock.builder().text("final answer").build();
        AgentChatResponse response = new AgentChatResponse(List.of(textBlock),
                new AgentChatUsage(1000, 500, 1500));
        when(modelCaller.stream(any(), any())).thenReturn(Flux.just(response));

        MiddlewareChain chain = new MiddlewareChain(List.of());
        ModelResponseParser parser = new ModelResponseParser();
        ReActEngine engine = new ReActEngine(modelCaller, toolExecutor, chain, parser, null)
                .modelPricingRegistry(new ModelPricingRegistry() {{
                    register(new ModelPricing("gpt-4o", 2.5, 10.0));
                }})
                .modelCode("gpt-4o");

        EngineContext ctx = new EngineContext(null, null, AgentRuntimeContext.builder().build(),
                null, 10, null);

        AgentMessage input = MessageFactory.createUserMessage("hi");
        StepVerifier.create(engine.stream(List.of(input), ctx)
                .filter(ReActEngineCostBudgetTest::isRelevantEvent))
                .assertNext(e -> {
                    assertThat(e.getType()).isEqualTo(AgentEventType.AGENT_RESULT);
                    AgentResultEvent result = (AgentResultEvent) e;
                    assertThat(result.getTotalCostUsd()).isNotNull();
                    // 1000/1000*2.5 + 500/1000*10.0 = 2.5 + 5.0 = 7.5
                    assertThat(result.getTotalCostUsd()).isCloseTo(7.5, within(0.001));
                })
                .expectNextMatches(e -> e.getType() == AgentEventType.COMPLETED)
                .verifyComplete();
    }

    @Test
    void shouldNotEmitCostEventsWhenNoPolicy() {
        ModelCaller modelCaller = mock(ModelCaller.class);
        ToolExecutor toolExecutor = mock(ToolExecutor.class);
        AgentTextBlock textBlock = AgentTextBlock.builder().text("final answer").build();
        AgentChatResponse response = new AgentChatResponse(List.of(textBlock),
                new AgentChatUsage(1000, 500, 1500));
        when(modelCaller.stream(any(), any())).thenReturn(Flux.just(response));

        MiddlewareChain chain = new MiddlewareChain(List.of());
        ModelResponseParser parser = new ModelResponseParser();
        // 不设置costBudgetPolicy，不应有成本事件
        ReActEngine engine = new ReActEngine(modelCaller, toolExecutor, chain, parser, null)
                .modelPricingRegistry(new ModelPricingRegistry() {{
                    register(new ModelPricing("gpt-4o", 2.5, 10.0));
                }})
                .modelCode("gpt-4o");

        EngineContext ctx = new EngineContext(null, null, AgentRuntimeContext.builder().build(),
                null, 10, null);

        AgentMessage input = MessageFactory.createUserMessage("hi");
        StepVerifier.create(engine.stream(List.of(input), ctx)
                .filter(ReActEngineCostBudgetTest::isRelevantEvent))
                .expectNextMatches(e -> e.getType() == AgentEventType.AGENT_RESULT)
                .expectNextMatches(e -> e.getType() == AgentEventType.COMPLETED)
                .verifyComplete();
    }

    @Test
    void shouldCostZeroWhenNoPricingConfigured() {
        ModelCaller modelCaller = mock(ModelCaller.class);
        ToolExecutor toolExecutor = mock(ToolExecutor.class);
        AgentTextBlock textBlock = AgentTextBlock.builder().text("final answer").build();
        AgentChatResponse response = new AgentChatResponse(List.of(textBlock),
                new AgentChatUsage(1000, 500, 1500));
        when(modelCaller.stream(any(), any())).thenReturn(Flux.just(response));

        MiddlewareChain chain = new MiddlewareChain(List.of());
        ModelResponseParser parser = new ModelResponseParser();
        // 不设置pricing，成本应为0
        ReActEngine engine = new ReActEngine(modelCaller, toolExecutor, chain, parser, null);

        EngineContext ctx = new EngineContext(null, null, AgentRuntimeContext.builder().build(),
                null, 10, null);

        AgentMessage input = MessageFactory.createUserMessage("hi");
        StepVerifier.create(engine.stream(List.of(input), ctx)
                .filter(ReActEngineCostBudgetTest::isRelevantEvent))
                .assertNext(e -> {
                    assertThat(e.getType()).isEqualTo(AgentEventType.AGENT_RESULT);
                    AgentResultEvent result = (AgentResultEvent) e;
                    assertThat(result.getTotalCostUsd()).isEqualTo(0.0);
                })
                .expectNextMatches(e -> e.getType() == AgentEventType.COMPLETED)
                .verifyComplete();
    }

    /**
     * 过滤流式产生的瞬时事件，聚焦成本与结果事件
     * @param e
     * @return
     */
    private static boolean isRelevantEvent(AgentEvent e) {
        return e.getType() != AgentEventType.AGENT_START
                && e.getType() != AgentEventType.AGENT_END
                && e.getType() != AgentEventType.MODEL_CALL_START
                && e.getType() != AgentEventType.MODEL_CALL_END
                && e.getType() != AgentEventType.TEXT_BLOCK_DELTA
                && e.getType() != AgentEventType.TOOL_CALL_START
                && e.getType() != AgentEventType.TOOL_CALL_END;
    }

    private static org.assertj.core.data.Offset within(double value) {
        return org.assertj.core.data.Offset.offset(value);
    }
}