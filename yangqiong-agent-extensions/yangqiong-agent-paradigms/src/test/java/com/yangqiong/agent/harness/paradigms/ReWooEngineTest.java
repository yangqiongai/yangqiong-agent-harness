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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import com.yangqiong.agent.harness.core.event.AgentEvent;
import com.yangqiong.agent.harness.core.event.AgentEventType;
import com.yangqiong.agent.harness.core.event.AgentResultEvent;
import com.yangqiong.agent.harness.core.message.AgentChatUsage;
import com.yangqiong.agent.harness.core.message.AgentMessage;
import com.yangqiong.agent.harness.core.message.AgentTextBlock;
import com.yangqiong.agent.harness.core.message.AgentToolResultBlock;
import com.yangqiong.agent.harness.core.message.AgentToolUseBlock;
import com.yangqiong.agent.harness.core.message.MessageFactory;
import com.yangqiong.agent.harness.core.middleware.AgentMiddleware;
import com.yangqiong.agent.harness.core.model.AgentChatResponse;
import com.yangqiong.agent.harness.engine.AgentRuntimeContext;
import com.yangqiong.agent.harness.engine.EngineContext;
import com.yangqiong.agent.harness.engine.MiddlewareChain;
import com.yangqiong.agent.harness.model.ModelCaller;
import com.yangqiong.agent.harness.model.ModelPricing;
import com.yangqiong.agent.harness.model.ModelPricingRegistry;
import com.yangqiong.agent.harness.model.ModelResponseParser;
import com.yangqiong.agent.harness.tool.ToolExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * ReWoo范式引擎测试
 * @author yangqiong
 */
class ReWooEngineTest {

    /**
     * 测试计划：三个无依赖工具步加一个引用#E1的依赖步
     */
    private static final String PLAN_JSON = "["
            + "{\"tool\":\"toolA\",\"args\":{\"query\":\"alpha\"},\"desc\":\"查询alpha\"},"
            + "{\"tool\":\"toolB\",\"args\":{\"query\":\"beta\"},\"desc\":\"查询beta\"},"
            + "{\"tool\":\"toolC\",\"args\":{\"query\":\"gamma\"},\"desc\":\"查询gamma\"},"
            + "{\"tool\":\"toolD\",\"args\":{\"input\":\"#E1\"},\"desc\":\"消费第一步结果\"}"
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
     * 当前并发执行中的工具数
     */
    private final AtomicInteger activeCount = new AtomicInteger();

    /**
     * 并发执行峰值
     */
    private final AtomicInteger peakActive = new AtomicInteger();

    /**
     * 工具执行顺序日志
     */
    private final List<String> executionLog = Collections.synchronizedList(new ArrayList<>());

    /**
     * 各工具步执行时间窗，键为工具名，值首元素为开始毫秒时间戳、次元素为结束毫秒时间戳
     */
    private final Map<String, long[]> executionWindows = new ConcurrentHashMap<>();

    /**
     * 构建模型与上下文桩，并发上限固定为3
     */
    @BeforeEach
    void setUp() {
        modelCaller = mock(ModelCaller.class);
        toolExecutor = mock(ToolExecutor.class);
        context = mock(EngineContext.class);
        when(context.getModelCaller()).thenReturn(modelCaller);
        when(context.getToolExecutor()).thenReturn(toolExecutor);
        when(context.getResponseParser()).thenReturn(new ModelResponseParser());
        when(context.getMaxConcurrentToolCalls()).thenReturn(3);
        when(context.getAllToolSchemas(any())).thenReturn(List.of(
                Map.of("name", "toolA", "type", "object"),
                Map.of("name", "toolB", "type", "object"),
                Map.of("name", "toolC", "type", "object"),
                Map.of("name", "toolD", "type", "object")));
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
     * 桩工具执行器：在弹性线程池模拟耗时工具，记录并发计数与执行顺序
     * <p>
     * 引擎迁移后每个工具步经withToolExecution走批量执行接口，桩按调用列表逐个返回耗时结果。
     * </p>
     * @param delayMillis
     */
    private void stubToolExecutorWithDelay(long delayMillis) {
        when(toolExecutor.executeTools(any(), any(), any())).thenAnswer(invocation -> {
            List<AgentToolUseBlock> calls = invocation.getArgument(0);
            return Flux.fromIterable(calls)
                    .flatMap(call -> Mono.fromCallable(() -> blockingToolResult(call, delayMillis))
                            .subscribeOn(Schedulers.boundedElastic()))
                    .collectList();
        });
    }

    /**
     * 阻塞执行工具并记录并发峰值与执行顺序
     * @param toolUse
     * @param delayMillis
     * @return
     */
    private AgentMessage blockingToolResult(
            AgentToolUseBlock toolUse, long delayMillis) {
        int current = activeCount.incrementAndGet();
        peakActive.accumulateAndGet(current, Math::max);
        long startMillis = System.currentTimeMillis();
        long[] window = new long[2];
        window[0] = startMillis;
        executionWindows.put(toolUse.getToolName(), window);
        executionLog.add(toolUse.getToolName() + ":start");
        try {
            Thread.sleep(delayMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            activeCount.decrementAndGet();
        }
        window[1] = System.currentTimeMillis();
        executionLog.add(toolUse.getToolName() + ":end");
        AgentToolResultBlock result = AgentToolResultBlock.of(toolUse.getToolUseId(),
                List.of(AgentTextBlock.builder().text("result-of-" + toolUse.getToolName()).build()));
        return MessageFactory.createToolMessage(result);
    }

    /**
     * 三个无依赖工具步各耗时300毫秒仍并发重叠执行：执行时间窗存在交集，
     * 总耗时明显小于串行执行三步所需的3倍单步耗时，且并发度不超过上下文限制的3
     */
    @Test
    void shouldRunIndependentStepsInParallel() {
        when(modelCaller.stream(any(), any()))
                .thenReturn(Flux.just(textResponse(PLAN_JSON)))
                .thenReturn(Flux.just(textResponse("最终答案")));
        stubToolExecutorWithDelay(300);

        long runStartMillis = System.currentTimeMillis();
        List<AgentEvent> events = new ReWooEngine().run(
                List.of(MessageFactory.createUserMessage("并行查询")), context)
                .collectList().block();
        long elapsedMillis = System.currentTimeMillis() - runStartMillis;

        assertThat(events).isNotNull();
        assertThat(events.get(events.size() - 1).getType()).isEqualTo(AgentEventType.AGENT_END);
        assertThat(events.get(events.size() - 2)).isInstanceOf(AgentResultEvent.class);
        // 取三个无依赖步中最晚的开始时间与最早的结束时间，加余量后仍重叠说明发生了并发执行
        long maxStart = Long.MIN_VALUE;
        long minEnd = Long.MAX_VALUE;
        for (String toolName : List.of("toolA", "toolB", "toolC")) {
            long[] window = executionWindows.get(toolName);
            assertThat(window).isNotNull();
            maxStart = Math.max(maxStart, window[0]);
            minEnd = Math.min(minEnd, window[1]);
        }
        assertThat(maxStart).isLessThan(minEnd + 100);
        // 总耗时明显小于串行执行三个300毫秒步骤所需的900毫秒
        assertThat(elapsedMillis).isLessThan(900);
        // 并发峰值不超过上下文配置的最大并发工具调用数
        assertThat(peakActive.get()).isLessThanOrEqualTo(3);
        // 三个无依赖步加一个依赖步全部执行
        verify(toolExecutor, times(4)).executeTools(any(), any(), any());
    }

    /**
     * 依赖步骤在#E1引用步骤完成后执行，且入参中的#E1已被替换为工具结果文本
     */
    @Test
    void shouldExecuteDependentStepAfterReferenceResolved() {
        when(modelCaller.stream(any(), any()))
                .thenReturn(Flux.just(textResponse(PLAN_JSON)))
                .thenReturn(Flux.just(textResponse("最终答案")));
        stubToolExecutorWithDelay(100);

        List<AgentEvent> events = new ReWooEngine().run(
                List.of(MessageFactory.createUserMessage("依赖链查询")), context)
                .collectList().block();

        assertThat(events).isNotNull();
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AgentToolUseBlock>> captor = ArgumentCaptor.forClass(List.class);
        verify(toolExecutor, times(4)).executeTools(captor.capture(), any(), any());
        AgentToolUseBlock dependentCall = captor.getAllValues().stream()
                .flatMap(List::stream)
                .filter(call -> "toolD".equals(call.getToolName()))
                .findFirst()
                .orElseThrow();
        // 依赖步参数中的#E1已被替换为第一步的工具结果文本
        assertThat(dependentCall.getInput().get("input")).isEqualTo("result-of-toolA");
        // 依赖步在第一步完成之后才开始执行
        assertThat(executionLog.indexOf("toolD:start")).isGreaterThan(executionLog.indexOf("toolA:end"));
    }

    /**
     * 规划解析失败时降级为单步直接执行：一次模型调用加工具执行加统一汇总
     */
    @Test
    void shouldFallbackToSingleStepWhenPlanParseFails() {
        AgentToolUseBlock toolUse = new AgentToolUseBlock("toolA", "call-1", Map.of("query", "alpha"));
        AgentChatResponse fallbackResponse = new AgentChatResponse(List.of(toolUse), null);
        when(modelCaller.stream(any(), any()))
                .thenReturn(Flux.just(textResponse("这不是一份有效的计划")))
                .thenReturn(Flux.just(fallbackResponse))
                .thenReturn(Flux.just(textResponse("降级最终答案")));
        when(toolExecutor.executeTools(any(), any(), any())).thenReturn(Mono.just(List.of(
                MessageFactory.createToolMessage(AgentToolResultBlock.of("call-1",
                        List.of(AgentTextBlock.builder().text("result-of-toolA").build()))))));

        List<AgentEvent> events = new ReWooEngine().run(
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
        // 规划、单步直接执行与统一汇总共三次模型调用
        verify(modelCaller, times(3)).stream(any(), any());
    }

    /**
     * 事件序列包含模型调用与工具调用事件对，AGENT_START开头且AgentResultEvent之后以AGENT_END收尾
     */
    @Test
    void shouldEmitExpectedEventSequence() {
        when(modelCaller.stream(any(), any()))
                .thenReturn(Flux.just(textResponse(PLAN_JSON)))
                .thenReturn(Flux.just(textResponse("最终答案")));
        stubToolExecutorWithDelay(50);

        List<AgentEvent> events = new ReWooEngine().run(
                List.of(MessageFactory.createUserMessage("事件序列检查")), context)
                .collectList().block();

        assertThat(events).isNotNull();
        List<AgentEventType> types = events.stream()
                .map(AgentEvent::getType)
                .toList();
        // 模型调用与工具调用事件对均存在
        assertThat(types).contains(AgentEventType.MODEL_CALL_START, AgentEventType.MODEL_CALL_END,
                AgentEventType.TOOL_CALL_START, AgentEventType.TOOL_CALL_END);
        // 规划与汇总两次模型调用
        assertThat(types.stream().filter(type -> type == AgentEventType.MODEL_CALL_START).count()).isEqualTo(2);
        assertThat(types.stream().filter(type -> type == AgentEventType.MODEL_CALL_END).count()).isEqualTo(2);
        // 每个工具步各一对工具调用事件
        assertThat(types.stream().filter(type -> type == AgentEventType.TOOL_CALL_START).count()).isEqualTo(4);
        assertThat(types.stream().filter(type -> type == AgentEventType.TOOL_CALL_END).count()).isEqualTo(4);
        // 生命周期：首事件AGENT_START，末事件AGENT_END，结果事件携带最终答案
        assertThat(types.get(0)).isEqualTo(AgentEventType.AGENT_START);
        AgentEvent last = events.get(events.size() - 1);
        assertThat(last.getType()).isEqualTo(AgentEventType.AGENT_END);
        AgentEvent resultEvent = events.get(events.size() - 2);
        assertThat(resultEvent).isInstanceOf(AgentResultEvent.class);
        assertThat(((AgentResultEvent) resultEvent).getResult().getTextContent()).isEqualTo("最终答案");
    }

    /**
     * 生命周期契约：事件流以AGENT_START开头，AgentResultEvent之后以AGENT_END收尾且无ERROR
     */
    @Test
    void shouldWrapResultWithLifecycleEvents() {
        when(modelCaller.stream(any(), any()))
                .thenReturn(Flux.just(textResponse("没有可解析的计划")))
                .thenReturn(Flux.just(textResponse("生命周期最终答案")));

        List<AgentEvent> events = new ReWooEngine().run(
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

    /**
     * 构建包含错误块的批量工具结果桩，每个调用返回一条错误工具消息
     */
    private void stubErrorToolResults() {
        when(toolExecutor.executeTools(any(), any(), any())).thenAnswer(invocation -> {
            List<AgentToolUseBlock> calls = invocation.getArgument(0);
            List<AgentMessage> results = new ArrayList<>();
            for (AgentToolUseBlock call : calls) {
                results.add(MessageFactory.createToolMessage(
                        AgentToolResultBlock.error(call.getToolUseId(), "boom")));
            }
            return Mono.just(results);
        });
    }

    /**
     * 查找事件类型列表中第n次出现指定类型的位置，n从1起算
     * @param types
     * @param type
     * @param n
     * @return
     */
    private int indexOfNth(List<AgentEventType> types, AgentEventType type, int n) {
        int seen = 0;
        for (int i = 0; i < types.size(); i++) {
            if (types.get(i) == type) {
                seen++;
                if (seen == n) {
                    return i;
                }
            }
        }
        return -1;
    }

    /**
     * 工具中间件接入：每个工具步的执行都经onActing中间件洋葱包裹，且工具事件对保持完整
     */
    @Test
    void shouldInvokeActingMiddlewareOnToolExecution() {
        AtomicInteger actingInvocations = new AtomicInteger();
        List<AgentToolUseBlock> capturedCalls = Collections.synchronizedList(new ArrayList<>());
        AgentMiddleware middleware = new AgentMiddleware() {
            @Override
            public Flux<AgentEvent> onActing(AgentRuntimeContext ctx, List<AgentToolUseBlock> toolCalls,
                                             Function<List<AgentToolUseBlock>, Flux<AgentEvent>> next) {
                actingInvocations.incrementAndGet();
                capturedCalls.addAll(toolCalls);
                return next.apply(toolCalls);
            }
        };
        when(context.getMiddlewareChain()).thenReturn(new MiddlewareChain(List.of(middleware)));
        when(modelCaller.stream(any(), any()))
                .thenReturn(Flux.just(textResponse(PLAN_JSON)))
                .thenReturn(Flux.just(textResponse("中间件最终答案")));
        stubToolExecutorWithDelay(10);

        List<AgentEvent> events = new ReWooEngine().run(
                List.of(MessageFactory.createUserMessage("中间件场景")), context)
                .collectList().block();

        assertThat(events).isNotNull();
        // 每个工具步独立经过一次onActing中间件，四个调用全部捕获
        assertThat(actingInvocations.get()).isEqualTo(4);
        assertThat(capturedCalls).hasSize(4);
        // 工具事件对仍完整
        List<AgentEventType> types = events.stream()
                .map(AgentEvent::getType)
                .toList();
        assertThat(types.stream().filter(type -> type == AgentEventType.TOOL_CALL_START).count()).isEqualTo(4);
        assertThat(types.stream().filter(type -> type == AgentEventType.TOOL_CALL_END).count()).isEqualTo(4);
        assertThat(((AgentResultEvent) events.get(events.size() - 2)).getResult().getTextContent())
                .isEqualTo("中间件最终答案");
    }

    /**
     * 连续失败熔断：工具结果全为错误块且达到连续失败阈值时，第二次工具调用后发射ERROR中止且不再有TOOL_CALL_END
     */
    @Test
    void shouldAbortWhenConsecutiveToolFailuresExceeded() {
        String twoStepPlan = "["
                + "{\"tool\":\"toolA\",\"args\":{\"query\":\"alpha\"},\"desc\":\"第一步\"},"
                + "{\"tool\":\"toolB\",\"args\":{\"query\":\"beta\"},\"desc\":\"第二步\"}"
                + "]";
        when(context.getMaxConcurrentToolCalls()).thenReturn(1);
        when(context.getMaxConsecutiveToolFailures()).thenReturn(1);
        AtomicInteger failures = new AtomicInteger();
        when(context.addConsecutiveFailures(anyInt()))
                .thenAnswer(invocation -> failures.addAndGet(invocation.getArgument(0, Integer.class)));
        when(context.getConsecutiveFailures()).thenAnswer(invocation -> failures.get());
        when(modelCaller.stream(any(), any()))
                .thenReturn(Flux.just(textResponse(twoStepPlan)))
                .thenReturn(Flux.just(textResponse("汇总答案")));
        stubErrorToolResults();

        List<AgentEvent> events = new ReWooEngine().run(
                List.of(MessageFactory.createUserMessage("连续失败场景")), context)
                .collectList().block();

        assertThat(events).isNotNull();
        List<AgentEventType> types = events.stream()
                .map(AgentEvent::getType)
                .toList();
        // 两次工具调用，仅第一次发射TOOL_CALL_END
        assertThat(types.stream().filter(type -> type == AgentEventType.TOOL_CALL_START).count()).isEqualTo(2);
        assertThat(types.stream().filter(type -> type == AgentEventType.TOOL_CALL_END).count()).isEqualTo(1);
        // 第二次工具调用未发射TOOL_CALL_END，直接以连续失败ERROR收口
        int secondStart = indexOfNth(types, AgentEventType.TOOL_CALL_START, 2);
        assertThat(secondStart).isGreaterThan(0);
        assertThat(types.get(secondStart + 1)).isEqualTo(AgentEventType.ERROR);
        assertThat(((RuntimeException) events.get(secondStart + 1).getPayload()).getMessage())
                .contains("连续工具失败超过阈值");
        // 熔断ERROR之后不再有第二次TOOL_CALL_END
        assertThat(types.subList(secondStart + 1, types.size()))
                .doesNotContain(AgentEventType.TOOL_CALL_END);
    }

    /**
     * 迭代超时：模型响应慢于单轮迭代超时时长时发射携带TimeoutException的ERROR事件
     */
    @Test
    void shouldEmitTimeoutErrorWhenModelCallExceedsIterationTimeout() {
        when(context.getIterationTimeout()).thenReturn(Duration.ofMillis(50));
        AgentChatResponse slowResponse = textResponse("慢响应");
        when(modelCaller.stream(any(), any())).thenAnswer(invocation ->
                Flux.just(slowResponse).delaySubscription(Duration.ofMillis(500)));

        List<AgentEvent> events = new ReWooEngine().run(
                List.of(MessageFactory.createUserMessage("迭代超时场景")), context)
                .collectList().block();

        assertThat(events).isNotNull();
        // 模型调用空闲超限触发超时ERROR，且规划阶段被中断不再产出结果事件
        assertThat(events).anyMatch(event -> event.getType() == AgentEventType.ERROR
                && event.getPayload() instanceof java.util.concurrent.TimeoutException);
        assertThat(events).noneMatch(event -> event instanceof AgentResultEvent);
        assertThat(events.get(events.size() - 1).getType()).isEqualTo(AgentEventType.AGENT_END);
    }

    /**
     * 成本累计：带usage的模型响应按定价注册表计价并累计到运行时上下文的harness.costUsedUsd
     */
    @Test
    void shouldAccumulateCostIntoRuntimeContext() {
        AgentRuntimeContext runtimeContext = AgentRuntimeContext.empty();
        when(context.getRuntimeContext()).thenReturn(runtimeContext);
        ModelPricingRegistry registry = new ModelPricingRegistry();
        registry.register(new ModelPricing("test-model", 2.5, 10.0));
        when(context.getModelPricingRegistry()).thenReturn(registry);
        when(context.getModelCode()).thenReturn("test-model");
        AgentChatResponse usageResponse = new AgentChatResponse(
                List.of(AgentTextBlock.builder().text("成本场景最终答案").build()),
                new AgentChatUsage(1000, 500, 1500));
        when(modelCaller.stream(any(), any()))
                .thenReturn(Flux.just(usageResponse))
                .thenReturn(Flux.just(usageResponse));
        stubToolExecutorWithDelay(10);

        List<AgentEvent> events = new ReWooEngine().run(
                List.of(MessageFactory.createUserMessage("成本累计场景")), context)
                .collectList().block();

        assertThat(events).isNotNull();
        // 规划与统一推理各调用一次模型，每次成本(1000/1000)*2.5+(500/1000)*10=7.5美元，累计15美元
        Object costUsed = runtimeContext.get("harness.costUsedUsd");
        assertThat(costUsed).isInstanceOf(Double.class);
        assertThat((Double) costUsed).isEqualTo(15.0);
        assertThat(((AgentResultEvent) events.get(events.size() - 2)).getResult().getTextContent())
                .isEqualTo("成本场景最终答案");
    }
}