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
package com.yangqiong.agent.harness.engine;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Supplier;

import com.yangqiong.agent.harness.config.AgentPermissionDecision;
import com.yangqiong.agent.harness.config.AgentResponseFormat;
import com.yangqiong.agent.harness.config.CostBudgetPolicy;
import com.yangqiong.agent.harness.config.TokenBudgetPolicy;
import com.yangqiong.agent.harness.core.HarnessAgentRuntimeBuilder;
import com.yangqiong.agent.harness.core.event.AgentEvent;
import com.yangqiong.agent.harness.core.event.AgentEventType;
import com.yangqiong.agent.harness.core.event.ClarificationAnswer;
import com.yangqiong.agent.harness.core.event.ConfirmResult;
import com.yangqiong.agent.harness.core.event.ModelCallInfo;
import com.yangqiong.agent.harness.core.event.ModelCallResult;
import com.yangqiong.agent.harness.core.event.RequireUserClarificationEvent;
import com.yangqiong.agent.harness.core.event.RequireUserConfirmEvent;
import com.yangqiong.agent.harness.core.message.AgentChatUsage;
import com.yangqiong.agent.harness.core.message.AgentContentBlock;
import com.yangqiong.agent.harness.core.message.AgentMessage;
import com.yangqiong.agent.harness.core.message.AgentMessageRole;
import com.yangqiong.agent.harness.core.message.AgentTextBlock;
import com.yangqiong.agent.harness.core.message.AgentToolResultBlock;
import com.yangqiong.agent.harness.core.message.AgentToolUseBlock;
import com.yangqiong.agent.harness.core.message.MessageFactory;
import com.yangqiong.agent.harness.core.model.AgentChatResponse;
import com.yangqiong.agent.harness.core.tool.AgentTool;
import com.yangqiong.agent.harness.durable.AgentCheckpoint;
import com.yangqiong.agent.harness.event.EventBus;
import com.yangqiong.agent.harness.memory.CheckpointManager;
import com.yangqiong.agent.harness.model.ModelCaller;
import com.yangqiong.agent.harness.model.ModelPricing;
import com.yangqiong.agent.harness.model.ModelPricingRegistry;
import com.yangqiong.agent.harness.model.ModelResponseParser;
import com.yangqiong.agent.harness.model.StructuredOutputRetryPolicy;
import com.yangqiong.agent.harness.model.StructuredOutputValidator;
import com.yangqiong.agent.harness.permission.PermissionEngine;
import com.yangqiong.agent.harness.tool.ToolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 引擎公共父类
 * <p>
 * 封装与具体推理范式无关的引擎级管线：系统提示词中间件处理、onAgent洋葱包裹、
 * 错误兜底、AGENT_START/AGENT_END生命周期事件、总超时与事件总线广播，
 * 并提供模型调用事件包裹、预算检查等共享助手，供ReActEngine与各类范式引擎复用。
 * 子类只需实现{@link #doRun}提供各自的循环主体，
 * 审批暂停与恢复（resume）已在本类统一实现：工具执行前经审批三角triage，
 * 需人工确认时暂停，恢复时将决策记入台账并以原始输入重放完整循环。
 * 澄清恢复与检查点恢复按需在子类覆写。
 * </p>
 * @author yangqiong
 */
public abstract class AbstractAgentLoop implements AgentLoop {

    /**
     * 属性键：Token预算告警已发射标记
     */
    static final String ATTR_TOKEN_BUDGET_WARNED = "harness.tokenBudgetWarned";

    /**
     * 属性键：累计成本（美元，double）
     */
    static final String ATTR_COST_USED = "harness.costUsedUsd";

    /**
     * 属性键：成本预算告警已发射标记
     */
    static final String ATTR_COST_BUDGET_WARNED = "harness.costBudgetWarned";

    /**
     * 属性键：原始输入快照，供审批恢复重放
     */
    static final String ATTR_ORIGINAL_INPUTS = "harness.paradigm.originalInputs";

    /**
     * 属性键：人工审批台账（工具调用ID或工具名 → 是否批准）
     */
    static final String ATTR_APPROVAL_LEDGER = "harness.paradigm.approvalLedger";

    /**
     * 属性键：暂停人工确认时的待确认工具调用清单
     */
    static final String ATTR_PENDING_TOOL_CALLS = "harness.paradigm.pendingToolCalls";

    /**
     * 属性键：用户澄清答案台账（问题文本 → 答案文本）
     */
    static final String ATTR_CLARIFICATION_LEDGER = "harness.paradigm.clarificationLedger";

    /**
     * 属性键：待澄清的工具结果（ask_user 专用）
     */
    static final String ATTR_PENDING_CLARIFICATION = "harness.pendingClarification";

    /**
     * 属性键：暂停澄清时的对话快照
     */
    static final String ATTR_PENDING_CLARIFICATION_CONVERSATION = "harness.pendingClarificationConversation";

    /**
     * 属性键：暂停澄清时的迭代轮次
     */
    static final String ATTR_PENDING_CLARIFICATION_ITERATION = "harness.pendingClarificationIteration";

    /**
     * 属性键：连续澄清次数
     */
    static final String ATTR_CLARIFICATION_COUNT = "harness.clarificationCount";

    /**
     * 最大连续澄清次数，超过后报错中止，防止模型无限提问
     */
    static final int MAX_CLARIFICATIONS = 3;

    /**
     * 属性键：结构化输出重试次数
     */
    public static final String ATTR_STRUCTURED_RETRY_COUNT = "harness.structuredRetryCount";

    /**
     * 日志器
     */
    private static final Logger log = LoggerFactory.getLogger(AbstractAgentLoop.class);

    /**
     * 空中间件链，引擎上下文未暴露中间件链时的兜底
     */
    private static final MiddlewareChain EMPTY_MIDDLEWARE_CHAIN = new MiddlewareChain(List.of());

    /**
     * 引擎自有模型调用器回退实例，上下文未暴露时使用
     */
    protected volatile ModelCaller engineModelCaller;

    /**
     * 引擎自有工具执行器回退实例，上下文未暴露时使用
     */
    protected volatile ToolExecutor engineToolExecutor;

    /**
     * 引擎自有中间件链回退实例，上下文未暴露时使用
     */
    protected volatile MiddlewareChain engineMiddlewareChain;

    /**
     * 引擎自有模型响应解析器回退实例，上下文未暴露时使用
     */
    protected volatile ModelResponseParser engineResponseParser;

    /**
     * 引擎自有权限引擎回退实例，上下文未暴露时使用
     */
    protected volatile PermissionEngine enginePermissionEngine;

    /**
     * 引擎自有检查点管理器回退实例（可选，非null时每轮迭代后保存快照）
     */
    protected volatile CheckpointManager engineCheckpointManager;

    /**
     * 引擎自有事件监听器注册中心回退实例（可选，非null时所有Agent事件自动广播给监听器）
     */
    protected volatile EventBus engineEventBus;

    /**
     * 引擎自有审批协调器回退实例（可选，非null时统一审批分拣与toolCallId精确匹配）
     */
    protected volatile ApprovalCoordinator engineApprovalCoordinator;

    /**
     * 引擎自有持久执行状态跟踪器回退实例（可选，非null时按断点落完整检查点与运行状态）
     */
    protected volatile DurableExecutionTracker engineDurableTracker;

    /**
     * 引擎自有Token预算策略回退实例（可选，非null时基于真实usage累计做预算告警与硬控）
     */
    protected volatile TokenBudgetPolicy engineTokenBudgetPolicy;

    /**
     * 引擎自有成本预算策略回退实例（可选，非null时基于真实usage×定价累计做成本告警与硬控）
     */
    protected volatile CostBudgetPolicy engineCostBudgetPolicy;

    /**
     * 引擎自有模型定价注册表回退实例（可选，为空时按默认价0计价）
     */
    protected volatile ModelPricingRegistry engineModelPricingRegistry;

    /**
     * 引擎自有模型编码回退配置（可选，用于成本计价的定价查询，为空时回退默认价）
     */
    protected volatile String engineModelCode;

    /**
     * 引擎自有结构化输出重试策略（可选，为空时使用默认策略，最多重试2次）
     */
    protected volatile StructuredOutputRetryPolicy engineStructuredOutputRetryPolicy;

    /**
     * 运行Agent主循环模板，封装生命周期管线
     * <p>
     * 管线顺序：系统提示词中间件处理 → onAgent洋葱模型包裹执行循环 → 错误兜底 →
     * AGENT_END收尾 → 子类管道装饰钩子 → AGENT_START前置 → 总超时 → 事件总线广播。
     * </p>
     * @param inputs
     * @param context
     * @return
     */
    @Override
    public final Flux<AgentEvent> run(List<AgentMessage> inputs, EngineContext context) {
        // 快照原始输入，供审批暂停后的恢复重放
        if (context.getRuntimeContext() != null) {
            context.getRuntimeContext().put(ATTR_ORIGINAL_INPUTS, inputs);
        }
        List<AgentMessage> messages = new ArrayList<>(inputs);
        String systemPrompt = context.getSystemPrompt();
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            systemPrompt = middlewareChain(context).applyOnSystemPrompt(systemPrompt, context.getRuntimeContext());
            messages.add(0, MessageFactory.createSystemMessage(systemPrompt));
        }
        // 通过 onAgent 洋葱模型包裹整个执行循环
        Flux<AgentEvent> pipeline = middlewareChain(context)
            .applyOnAgent(context.getRuntimeContext(), messages, msgInput -> doRun(msgInput, context))
            .onErrorResume(error -> {
                applyOnError(context, error, "agent");
                return Flux.just(AgentEvent.of(AgentEventType.ERROR, error), AgentEvent.completed());
            })
            .concatWith(Flux.defer(() -> Flux.just(AgentEvent.of(AgentEventType.AGENT_END, context.getAgentName()))));
        return finishPipeline(pipeline, context);
    }

    /**
     * 恢复路径管线收口，与run()共享生命周期装饰
     * <p>
     * 供覆写resume/resumeWithClarification的子引擎包装恢复流，确保恢复路径同样
     * 经过暂停截断、总超时、持久执行跟踪（终态迁移与锁释放）、AGENT_START与事件总线广播。
     * </p>
     * @param pipeline 引擎内部事件流（如续接的循环流）
     * @param context
     * @return
     */
    protected Flux<AgentEvent> finishPipeline(Flux<AgentEvent> pipeline, EngineContext context) {
        // 暂停截断：确认或澄清事件发射后立即终止本轮运行（含AGENT_END），等待恢复
        pipeline = pipeline.takeUntil(event -> event.getType() == AgentEventType.REQUIRE_USER_CONFIRM
                || event.getType() == AgentEventType.REQUIRE_USER_CLARIFICATION);
        // 应用总超时：先于跟踪装饰应用，使超时注入的ERROR事件同样能被跟踪器收口为终态
        pipeline = applyTotalTimeout(pipeline, context);
        // 子类管道装饰钩子（如持久执行状态跟踪）
        pipeline = decoratePipeline(pipeline, context);
        // 发射AGENT_START事件
        pipeline = Flux.concat(
                Flux.just(AgentEvent.of(AgentEventType.AGENT_START, context.getAgentName())),
                pipeline);
        // 事件监听器广播：所有Agent事件同步旁路给已注册监听器
        EventBus bus = eventBus(context);
        if (bus != null) {
            pipeline = pipeline.doOnNext(bus::publish);
        }
        return pipeline;
    }

    /**
     * 应用总超时：硬墙钟模式到点必断，空闲模式仅在无事件发射超过超时时长时触发
     * @param pipeline
     * @param context
     * @return
     */
    private Flux<AgentEvent> applyTotalTimeout(Flux<AgentEvent> pipeline, EngineContext context) {
        Duration totalTimeout = context.getTimeout();
        if (totalTimeout == null || totalTimeout.isZero() || totalTimeout.isNegative()) {
            return pipeline;
        }
        if (context.getTimeoutMode() == HarnessAgentRuntimeBuilder.TimeoutMode.WALL_CLOCK) {
            return applyWallClockTimeout(pipeline, totalTimeout);
        }
        return pipeline.timeout(totalTimeout)
            .onErrorResume(java.util.concurrent.TimeoutException.class, e ->
                Flux.just(AgentEvent.of(AgentEventType.ERROR, e), AgentEvent.completed()));
    }

    /**
     * 执行循环主体，由子类实现各自的推理-执行范式
     * @param inputs
     * @param context
     * @return
     */
    protected abstract Flux<AgentEvent> doRun(List<AgentMessage> inputs, EngineContext context);

    /**
     * 管道装饰钩子，默认附加持久执行状态跟踪
     * <p>
     * 在AGENT_START前置与总超时之前对事件流做整体装饰：
     * 持久执行跟踪器非空且启用时先初始化runId并将运行记录迁移到RUNNING，
     * 再按runId把每个事件旁路给跟踪器，由终态事件驱动运行记录状态机收口
     * （AGENT_RESULT→SUCCEEDED，ERROR→FAILED，INTERRUPTED→CANCELLED）。
     * 子类可覆写以附加更多旁路处理。
     * </p>
     * @param pipeline
     * @param context
     * @return
     */
    protected Flux<AgentEvent> decoratePipeline(Flux<AgentEvent> pipeline, EngineContext context) {
        DurableExecutionTracker tracker = durableTracker(context);
        String runId = null;
        if (tracker != null && tracker.isEnabled()) {
            runId = tracker.ensureRunning(context.getRuntimeContext(), context.getAgentName());
        }
        if (tracker != null && tracker.isEnabled() && runId != null) {
            final String trackedRunId = runId;
            pipeline = pipeline.doOnNext(event -> tracker.onEvent(event, trackedRunId));
        }
        return pipeline;
    }

    /**
     * 应用硬墙钟总超时：到点无论是否有输出都截断并发射ERROR事件
     * <p>
     * 以竞速流实现：引擎事件流与延时超时事件并行，首个COMPLETED或暂停事件后即取消另一侧。
     * 引擎自然完成时取消定时器；到点未完成则发射ERROR+COMPLETED并截断引擎流；
     * 确认/澄清暂停后立即终止合并流（取消定时器），墙钟到点不再补发超时事件，
     * 避免把等待人工的运行误标为失败或让订阅者在暂停后挂起到墙钟耗尽。
     * </p>
     * @param pipeline
     * @param totalTimeout
     * @return
     */
    private Flux<AgentEvent> applyWallClockTimeout(Flux<AgentEvent> pipeline, Duration totalTimeout) {
        // 暂停感知：确认/澄清暂停后墙钟到点不再补发超时事件，避免把等待人工的运行误标为失败
        AtomicBoolean paused = new AtomicBoolean(false);
        Flux<AgentEvent> watched = pipeline.doOnNext(event -> {
            if (event.getType() == AgentEventType.REQUIRE_USER_CONFIRM
                    || event.getType() == AgentEventType.REQUIRE_USER_CLARIFICATION) {
                paused.set(true);
            }
        });
        Flux<AgentEvent> deadline = Mono.delay(totalTimeout)
                .filter(t -> !paused.get())
                .flatMapMany(t -> Flux.just(
                        AgentEvent.of(AgentEventType.ERROR,
                                new java.util.concurrent.TimeoutException("引擎总超时")),
                        AgentEvent.completed()));
        return Flux.merge(watched, deadline)
                .takeUntil(e -> e.getType() == AgentEventType.COMPLETED
                        || e.getType() == AgentEventType.REQUIRE_USER_CONFIRM
                        || e.getType() == AgentEventType.REQUIRE_USER_CLARIFICATION);
    }

    /**
     * 模型调用事件包裹助手
     * <p>
     * 通过 onReasoning 洋葱模型包裹推理阶段，发射MODEL_CALL_START后驱动模型流式调用，
     * 分片转增量事件，全部分片到达后合并响应并累计用量与成本，随后依次发射
     * 预算硬控/告警事件与合并后响应的后续处理事件，最终以MODEL_CALL_END收口。
     * </p>
     * @param context
     * @param messages
     * @param toolSchemas
     * @param iteration
     * @param onResponse 合并响应的后续处理函数，产出MODEL_CALL_END之后的事件
     * @return
     */
    protected Flux<AgentEvent> withModelCallEvents(EngineContext context, List<AgentMessage> messages,
                                                   List<Map<String, Object>> toolSchemas, int iteration,
                                                   Function<AgentChatResponse, Flux<AgentEvent>> onResponse) {
        ModelCaller caller = modelCaller(context);
        ModelResponseParser parser = responseParser(context);
        return middlewareChain(context)
            .applyOnReasoning(context.getRuntimeContext(), new ArrayList<>(messages),
                reasoningInput -> {
                    // 累积流式分片，concatMap 串行执行保证安全
                    List<AgentChatResponse> accumulated = new ArrayList<>();
                    return Flux.just(AgentEvent.of(AgentEventType.MODEL_CALL_START,
                            new ModelCallInfo(context.getAgentName(), caller.modelName(), iteration)))
                            .concatWith(caller.stream(reasoningInput, toolSchemas)
                            .concatMap(response -> {
                                accumulated.add(response);
                                List<AgentEvent> deltas = parser.parseDeltasToEvents(response, null);
                                return Flux.fromIterable(deltas);
                            })
                            // 全部分片到达后合并并处理
                            .concatWith(Flux.defer(() -> {
                                AgentChatResponse merged = parser.mergeResponses(accumulated);
                                // 累计本轮模型调用Token用量与成本
                                if (merged.getChatUsage() != null) {
                                    context.accumulateUsage(merged.getChatUsage());
                                    onModelUsage(context, merged.getChatUsage());
                                }
                                // Token预算硬控：超限且ABORT时中止Agent
                                Flux<AgentEvent> abortFlux = checkTokenBudgetAbort(context);
                                if (abortFlux != null) {
                                    return Flux.just(AgentEvent.of(AgentEventType.MODEL_CALL_END,
                                    new ModelCallResult(context.getAgentName(), caller.modelName(),
                                            iteration, merged.getChatUsage())))
                                            .concatWith(abortFlux);
                                }
                                // 成本预算硬控：超限且ABORT时中止Agent
                                Flux<AgentEvent> costAbortFlux = checkCostBudgetAbort(context);
                                if (costAbortFlux != null) {
                                    return Flux.just(AgentEvent.of(AgentEventType.MODEL_CALL_END,
                                    new ModelCallResult(context.getAgentName(), caller.modelName(),
                                            iteration, merged.getChatUsage())))
                                            .concatWith(costAbortFlux);
                                }
                                // Token预算告警事件（仅首次发射，前置于后续处理）
                                Flux<AgentEvent> warnFlux = buildTokenBudgetWarnFlux(context);
                                // 成本预算告警事件（仅首次发射，前置于后续处理）
                                Flux<AgentEvent> costWarnFlux = buildCostBudgetWarnFlux(context);
                                Flux<AgentEvent> afterHandle = onResponse.apply(merged);
                                return Flux.just(AgentEvent.of(AgentEventType.MODEL_CALL_END,
                                    new ModelCallResult(context.getAgentName(), caller.modelName(),
                                            iteration, merged.getChatUsage())))
                                        .concatWith(warnFlux)
                                        .concatWith(costWarnFlux)
                                        .concatWith(afterHandle);
                            })));
                })
                .onErrorResume(error -> {
                    applyOnError(context, error, "reasoning");
                    return Flux.just(AgentEvent.of(AgentEventType.ERROR, error), AgentEvent.completed());
                });
    }

    /**
     * 模型用量累计钩子，默认按usage×定价累计成本到运行时上下文
     * <p>
     * 定价注册表未配置或未命中模型编码时按默认价0计价，
     * 子类可覆写以追加自有派生用量统计。
     * </p>
     * @param context
     * @param usage
     */
    protected void onModelUsage(EngineContext context, AgentChatUsage usage) {
        accumulateCost(context, usage);
    }

    /**
     * 工具执行事件包裹助手
     * <p>
     * 通过 onActing 洋葱模型包裹执行阶段：先发射TOOL_CALL_START事件，
     * 再驱动工具执行器批量执行，工具结果到达后先做连续失败熔断检查
     * （超限发射ERROR+COMPLETED中止Agent），否则发射TOOL_CALL_END事件
     * 并拼接工具结果后续处理流；执行异常统一经错误钩子通知后以ERROR+COMPLETED收口。
     * </p>
     * @param context
     * @param toolCalls 本次模型响应产出的工具调用列表
     * @param onResults 工具结果后续处理函数，产出TOOL_CALL_END之后的事件
     * @return
     */
    protected Flux<AgentEvent> withToolExecution(EngineContext context, List<AgentToolUseBlock> toolCalls,
                                                 Function<List<AgentMessage>, Flux<AgentEvent>> onResults) {
        ToolExecutor executor = toolExecutor(context);
        if (executor == null) {
            RuntimeException error = new IllegalStateException("工具执行器未暴露，无法执行工具调用");
            return Flux.just(AgentEvent.of(AgentEventType.ERROR, error), AgentEvent.completed());
        }
        // 审批三角与台账重放：一次性完成triage，需要人工确认时暂停等待resume
        boolean approvalEnabled = approvalEnabled(context);
        final List<AgentToolUseBlock> allowed;
        final List<AgentMessage> deniedResults;
        if (approvalEnabled) {
            ApprovalCoordinator.TriageResult triage = triageToolCalls(context, toolCalls, null);
            // 台账重放：此前已决策的调用直接应用，不再重复请示
            Map<String, Boolean> ledger = approvalLedger(context);
            List<AgentToolUseBlock> replayAsk = new ArrayList<>();
            for (AgentToolUseBlock call : triage.getAskCalls()) {
                Boolean decision = ledger.containsKey(call.getToolUseId())
                        ? ledger.get(call.getToolUseId()) : ledger.get(call.getToolName());
                if (decision == null) {
                    replayAsk.add(call);
                } else if (decision) {
                    triage.getAllowedCalls().add(call);
                } else {
                    triage.getDeniedMessages().add(MessageFactory.createToolMessage(
                            AgentToolResultBlock.error(call.getToolUseId(), "人工拒绝: " + call.getToolName())));
                }
            }
            if (!replayAsk.isEmpty()) {
                // 记录待确认清单，发射需要人工确认事件，暂停循环等待resume
                context.getRuntimeContext().put(ATTR_PENDING_TOOL_CALLS, replayAsk);
                // 持久执行：等待审批断点落运行状态与审批记录
                DurableExecutionTracker tracker = durableTracker(context);
                if (tracker != null && tracker.isEnabled()) {
                    String runId = tracker.ensureRunning(context.getRuntimeContext(), context.getAgentName());
                    tracker.markWaitingApproval(context.getRuntimeContext(), runId, replayAsk, List.of(), 0);
                }
                return Flux.just(new RequireUserConfirmEvent(replayAsk));
            }
            allowed = triage.getAllowedCalls();
            deniedResults = triage.getDeniedMessages();
            if (allowed.isEmpty()) {
                // 全部被拒绝：以拒绝结果收口交还onResults，由范式引擎决定下一步
                return Flux.just(AgentEvent.of(AgentEventType.TOOL_CALL_START, toolCalls),
                                AgentEvent.of(AgentEventType.TOOL_CALL_END, deniedResults))
                        .concatWith(onResults.apply(deniedResults));
            }
        } else {
            allowed = toolCalls;
            deniedResults = new ArrayList<>();
        }
        // ask_user澄清台账短路：恢复重放时命中台账答案的提问直接合成结果，不再执行
        List<AgentMessage> answeredResults = new ArrayList<>();
        List<AgentToolUseBlock> executable = new ArrayList<>(allowed);
        executable.removeIf(call -> {
            String answer = clarificationAnswerForCall(context, call);
            if (answer == null) {
                return false;
            }
            answeredResults.add(MessageFactory.createToolMessage(AgentToolResultBlock.of(
                    call.getToolUseId(), List.of(AgentTextBlock.builder().text(answer).build()))));
            return true;
        });
        if (executable.isEmpty() && !answeredResults.isEmpty()) {
            // 全部提问已被回答：拒绝结果与合成结果按原始次序合并收口交还onResults，避免丢失拒绝占位
            List<AgentMessage> combined = mergeByOriginalOrder(toolCalls, deniedResults, answeredResults);
            return Flux.just(AgentEvent.of(AgentEventType.TOOL_CALL_START, toolCalls),
                            AgentEvent.of(AgentEventType.TOOL_CALL_END, combined))
                    .concatWith(onResults.apply(combined));
        }
        // 合成结果并入占位结果，与执行结果一起按原始调用次序合并，保证次序对齐
        deniedResults.addAll(answeredResults);
        return Flux.just(AgentEvent.of(AgentEventType.TOOL_CALL_START, toolCalls))
                .concatWith(middlewareChain(context).applyOnActing(context.getRuntimeContext(), executable,
                    calls -> executor.executeTools(calls, context.getRuntimeContext(), context)
                            .flatMapMany(toolResults -> {
                                // 持久执行：记录已完成工具调用，供恢复时幂等去重副作用
                                DurableExecutionTracker tracker = durableTracker(context);
                                if (tracker != null && tracker.isEnabled()) {
                                    tracker.recordCompletedToolCalls(context.getRuntimeContext(), toolResults);
                                }
                                // 按原始调用次序合并拒绝结果与执行结果，保证onResults消费端次序对齐
                                List<AgentMessage> merged =
                                        mergeByOriginalOrder(toolCalls, deniedResults, toolResults);
                                // ask_user澄清请求检测：带澄清标记的结果触发暂停，等待用户注入答案
                                List<AgentToolResultBlock> clarificationResults =
                                        extractClarificationResults(toolResults);
                                if (!clarificationResults.isEmpty()) {
                                    return clarificationPauseFlux(clarificationResults, merged, context);
                                }
                                // 连续工具失败检查：成功则重置，失败则累加，超过阈值中止Agent
                                if (isConsecutiveFailureExceeded(toolResults, context)) {
                                    return Flux.just(AgentEvent.of(AgentEventType.ERROR,
                                            new RuntimeException("连续工具失败超过阈值: "
                                                    + context.getMaxConsecutiveToolFailures())),
                                            AgentEvent.completed());
                                }
                                return Flux.just(AgentEvent.of(AgentEventType.TOOL_CALL_END, merged))
                                        .concatWith(onResults.apply(merged));
                            })
                ))
                .onErrorResume(error -> {
                    applyOnError(context, error, "acting");
                    return Flux.just(AgentEvent.of(AgentEventType.ERROR, error), AgentEvent.completed());
                });
    }

    /**
     * 按权限引擎执行兼容triage：ASK进入待确认，DENY生成拒绝消息，其余放行
     * @param permissionEngine
     * @param context
     * @param toolCalls
     * @return
     */
    private ApprovalCoordinator.TriageResult triageByPermissionEngine(PermissionEngine permissionEngine,
                                                                      EngineContext context,
                                                                      List<AgentToolUseBlock> toolCalls) {
        ApprovalCoordinator.TriageResult triage = new ApprovalCoordinator.TriageResult();
        for (AgentToolUseBlock call : toolCalls) {
            AgentTool tool = context != null ? context.findTool(call.getToolName()) : null;
            AgentPermissionDecision decision = permissionEngine.evaluate(call.getToolName(), tool);
            if (decision == AgentPermissionDecision.ASK) {
                triage.getAskCalls().add(call);
            } else if (decision == AgentPermissionDecision.DENY) {
                triage.getDeniedMessages().add(MessageFactory.createToolMessage(
                        AgentToolResultBlock.error(call.getToolUseId(),
                                "权限拒绝: " + call.getToolName())));
            } else {
                triage.getAllowedCalls().add(call);
            }
        }
        return triage;
    }

    /**
     * 按原始调用次序合并拒绝结果与执行结果，保持onResults消费端次序对齐
     * <p>
     * 执行结果与拒绝结果均按toolUseId索引，未命中的调用以错误结果占位。
     * </p>
     * @param toolCalls
     * @param deniedResults
     * @param toolResults
     * @return
     */
    private List<AgentMessage> mergeByOriginalOrder(List<AgentToolUseBlock> toolCalls,
                                                    List<AgentMessage> deniedResults,
                                                    List<AgentMessage> toolResults) {
        if (deniedResults.isEmpty()) {
            return toolResults;
        }
        Map<String, AgentMessage> executed = new HashMap<>();
        for (AgentMessage result : toolResults) {
            AgentToolResultBlock block = findResultBlock(result);
            if (block != null) {
                executed.put(block.getToolUseId(), result);
            }
        }
        Map<String, AgentMessage> denied = new HashMap<>();
        for (AgentMessage result : deniedResults) {
            AgentToolResultBlock block = findResultBlock(result);
            if (block != null) {
                denied.put(block.getToolUseId(), result);
            }
        }
        List<AgentMessage> merged = new ArrayList<>();
        for (AgentToolUseBlock call : toolCalls) {
            AgentMessage result = executed.get(call.getToolUseId());
            if (result == null) {
                result = denied.get(call.getToolUseId());
            }
            if (result == null) {
                result = MessageFactory.createToolMessage(
                        AgentToolResultBlock.error(call.getToolUseId(), "人工拒绝: " + call.getToolName()));
            }
            merged.add(result);
        }
        return merged;
    }

    /**
     * 从工具结果消息中提取工具结果块
     * @param result
     * @return
     */
    private AgentToolResultBlock findResultBlock(AgentMessage result) {
        for (com.yangqiong.agent.harness.core.message.AgentContentBlock block : result.getContent()) {
            if (block instanceof AgentToolResultBlock resultBlock) {
                return resultBlock;
            }
        }
        return null;
    }

    /**
     * 获取审批台账，不存在时初始化
     * @param context
     * @return
     */
    @SuppressWarnings("unchecked")
    private Map<String, Boolean> approvalLedger(EngineContext context) {
        Map<String, Boolean> ledger =
                (Map<String, Boolean>) context.getRuntimeContext().get(ATTR_APPROVAL_LEDGER);
        if (ledger == null) {
            ledger = new HashMap<>();
            context.getRuntimeContext().put(ATTR_APPROVAL_LEDGER, ledger);
        }
        return ledger;
    }

    /**
     * 恢复执行：审批决策记入台账后以原始输入重放完整范式循环
     * <p>
     * 重放期间triage按台账应用既有决策不再重复请示，已批准调用照常执行，
     * 已拒绝调用以错误结果占位，直至范式循环产出最终结果。
     * </p>
     * @param confirmResults
     * @param context
     * @return
     */
    @Override
    public Flux<AgentEvent> resume(List<ConfirmResult> confirmResults, EngineContext context) {
        return Flux.defer(() -> {
            if (confirmResults == null || confirmResults.isEmpty()) {
                RuntimeException error = new IllegalArgumentException("恢复执行需要携带人工确认结果");
                return Flux.just(AgentEvent.of(AgentEventType.ERROR, error), AgentEvent.completed());
            }
            Map<String, Boolean> ledger = approvalLedger(context);
            for (ConfirmResult confirmResult : confirmResults) {
                Boolean approved = confirmResult.isApproved();
                if (confirmResult.getToolCallId() != null) {
                    ledger.put(confirmResult.getToolCallId(), approved);
                }
                if (confirmResult.getToolName() != null) {
                    ledger.put(confirmResult.getToolName(), approved);
                }
            }
            // 持久执行：恢复运行自WAITING_APPROVAL迁回RUNNING，并将审批决策落审批记录
            DurableExecutionTracker tracker = durableTracker(context);
            if (tracker != null && tracker.isEnabled()) {
                tracker.ensureRunning(context.getRuntimeContext(), context.getAgentName());
                for (ConfirmResult confirmResult : confirmResults) {
                    if (confirmResult.getToolCallId() != null) {
                        tracker.resolveApproval(context.getRuntimeContext(), confirmResult.getToolCallId(),
                                confirmResult.isApproved(), confirmResult.getReason());
                    }
                }
            }
            @SuppressWarnings("unchecked")
            List<AgentMessage> inputs =
                    (List<AgentMessage>) context.getRuntimeContext().get(ATTR_ORIGINAL_INPUTS);
            if (inputs == null || inputs.isEmpty()) {
                RuntimeException error = new IllegalStateException("无原始输入快照，无法恢复执行");
                return Flux.just(AgentEvent.of(AgentEventType.ERROR, error), AgentEvent.completed());
            }
            // 台账已就绪，重放完整范式循环
            return run(inputs, context);
        });
    }

    /**
     * 应用单轮迭代超时：无事件发射超过超时时长时中断并发射ERROR+COMPLETED
     * <p>
     * 超时未配置或为零负值时原样返回事件流；超时错误先经错误钩子通知
     * 中间件链（阶段标记iteration-timeout），再以ERROR+COMPLETED收口。
     * 本助手不自动嵌入模型调用事件包裹，由范式引擎按需显式调用避免双重包裹。
     * </p>
     * @param context
     * @param body 单轮迭代事件流
     * @return
     */
    protected Flux<AgentEvent> withIterationTimeout(EngineContext context, Flux<AgentEvent> body) {
        Duration iterTimeout = context.getIterationTimeout();
        if (iterTimeout == null || iterTimeout.isZero() || iterTimeout.isNegative()) {
            return body;
        }
        return body.timeout(iterTimeout)
            .onErrorResume(java.util.concurrent.TimeoutException.class, e -> {
                applyOnError(context, e, "iteration-timeout");
                return Flux.just(AgentEvent.of(AgentEventType.ERROR, e), AgentEvent.completed());
            });
    }

    /**
     * 统计本轮工具结果并判断连续失败是否超过阈值
     * <p>
     * 任一成功则重置计数；全失败则按失败数累加。超过maxConsecutiveToolFailures返回true。
     * </p>
     * @param toolResults
     * @param context
     * @return
     */
    protected boolean isConsecutiveFailureExceeded(List<AgentMessage> toolResults, EngineContext context) {
        if (context == null || context.getMaxConsecutiveToolFailures() <= 0) {
            return false;
        }
        int failureCount = 0;
        boolean anySuccess = false;
        for (AgentMessage msg : toolResults) {
            if (msg == null || msg.getContent() == null) {
                continue;
            }
            for (Object block : msg.getContent()) {
                if (block instanceof AgentToolResultBlock resultBlock) {
                    if (resultBlock.isError()) {
                        failureCount++;
                    } else {
                        anySuccess = true;
                    }
                }
            }
        }
        if (anySuccess) {
            context.resetConsecutiveFailures();
        } else if (failureCount > 0) {
            context.addConsecutiveFailures(failureCount);
        }
        return context.getConsecutiveFailures() > context.getMaxConsecutiveToolFailures();
    }

    /**
     * 累计本轮模型调用成本（usage×定价）到运行时上下文
     * @param context
     * @param usage
     */
    protected void accumulateCost(EngineContext context, AgentChatUsage usage) {
        if (context.getRuntimeContext() == null || usage == null) {
            return;
        }
        ModelPricing pricing = resolvePricing(context, modelCode(context));
        double increment = (usage.getPromptTokens() / 1000.0) * pricing.promptUsdPer1k()
                + (usage.getCompletionTokens() / 1000.0) * pricing.completionUsdPer1k();
        Double current = (Double) context.getRuntimeContext().get(ATTR_COST_USED);
        context.getRuntimeContext().put(ATTR_COST_USED, (current != null ? current : 0.0) + increment);
    }

    /**
     * 解析模型定价，未配置注册表或未命中时回退默认价（0）
     * @param context
     * @param modelCode
     * @return
     */
    protected ModelPricing resolvePricing(EngineContext context, String modelCode) {
        ModelPricingRegistry registry = modelPricingRegistry(context);
        if (registry == null) {
            return new ModelPricing(modelCode, 0.0, 0.0);
        }
        return registry.priceOf(modelCode);
    }

    /**
     * Token预算硬控检查：超硬上限且动作为ABORT时返回中止事件流
     * @param context
     * @return 超限ABORT时返回中止事件流，否则返回null
     */
    protected Flux<AgentEvent> checkTokenBudgetAbort(EngineContext context) {
        TokenBudgetPolicy policy = tokenBudgetPolicy(context);
        if (policy == null) {
            return null;
        }
        long used = accumulatedTotalTokens(context);
        if (policy.isHardExceeded(used)
                && policy.getExceedAction() == TokenBudgetPolicy.ExceedAction.ABORT) {
            String message = "Token预算超限中止: used=" + used
                    + ", hardLimit=" + policy.getHardLimitTokens();
            return Flux.just(
                    AgentEvent.of(AgentEventType.TOKEN_BUDGET_EXCEEDED, message),
                    AgentEvent.of(AgentEventType.ERROR, new RuntimeException(message)),
                    AgentEvent.completed());
        }
        return null;
    }

    /**
     * 构建Token预算告警事件流：超阈值时首次发射告警事件（WARN动作或超硬上限未ABORT）
     * @param context
     * @return
     */
    protected Flux<AgentEvent> buildTokenBudgetWarnFlux(EngineContext context) {
        TokenBudgetPolicy policy = tokenBudgetPolicy(context);
        if (policy == null || context.getRuntimeContext() == null) {
            return Flux.empty();
        }
        String warnedFlag = (String) context.getRuntimeContext().get(ATTR_TOKEN_BUDGET_WARNED);
        if (warnedFlag != null) {
            return Flux.empty();
        }
        long used = accumulatedTotalTokens(context);
        boolean hardWarn = policy.isHardExceeded(used)
                && policy.getExceedAction() == TokenBudgetPolicy.ExceedAction.WARN;
        if (!hardWarn && !policy.isWarnExceeded(used)) {
            return Flux.empty();
        }
        context.getRuntimeContext().put(ATTR_TOKEN_BUDGET_WARNED, "1");
        String message = hardWarn
                ? "Token预算超硬上限: used=" + used + ", hardLimit=" + policy.getHardLimitTokens()
                : "Token预算告警: used=" + used + ", warnThreshold=" + policy.getWarnThresholdTokens();
        AgentEventType type = hardWarn ? AgentEventType.TOKEN_BUDGET_EXCEEDED : AgentEventType.TOKEN_BUDGET_WARN;
        return Flux.just(AgentEvent.of(type, message));
    }

    /**
     * 读取累计用量总Token数
     * @param context
     * @return
     */
    protected long accumulatedTotalTokens(EngineContext context) {
        return context.getAccumulatedUsage() != null ? context.getAccumulatedUsage().getTotalTokens() : 0L;
    }

    /**
     * 读取当前累计成本（美元）
     * @param context
     * @return
     */
    protected double currentCostUsd(EngineContext context) {
        if (context.getRuntimeContext() == null) {
            return 0.0;
        }
        Object value = context.getRuntimeContext().get(ATTR_COST_USED);
        return value instanceof Double d ? d : 0.0;
    }

    /**
     * 成本预算硬控检查：超硬上限且动作为ABORT时返回中止事件流
     * @param context
     * @return 超限ABORT时返回中止事件流，否则返回null
     */
    protected Flux<AgentEvent> checkCostBudgetAbort(EngineContext context) {
        CostBudgetPolicy policy = costBudgetPolicy(context);
        if (policy == null) {
            return null;
        }
        double used = currentCostUsd(context);
        if (policy.isHardExceeded(used)
                && policy.getExceedAction() == CostBudgetPolicy.ExceedAction.ABORT) {
            String message = "成本预算超限中止: used=$" + used
                    + ", hardLimit=$" + policy.getHardLimitUsd();
            return Flux.just(
                    AgentEvent.of(AgentEventType.COST_BUDGET_EXCEEDED, message),
                    AgentEvent.of(AgentEventType.ERROR, new RuntimeException(message)),
                    AgentEvent.completed());
        }
        return null;
    }

    /**
     * 构建成本预算告警事件流：超阈值时首次发射告警事件（WARN动作或超硬上限未ABORT）
     * @param context
     * @return
     */
    protected Flux<AgentEvent> buildCostBudgetWarnFlux(EngineContext context) {
        CostBudgetPolicy policy = costBudgetPolicy(context);
        if (policy == null || context.getRuntimeContext() == null) {
            return Flux.empty();
        }
        String warnedFlag = (String) context.getRuntimeContext().get(ATTR_COST_BUDGET_WARNED);
        if (warnedFlag != null) {
            return Flux.empty();
        }
        double used = currentCostUsd(context);
        boolean hardWarn = policy.isHardExceeded(used)
                && policy.getExceedAction() == CostBudgetPolicy.ExceedAction.WARN;
        if (!hardWarn && !policy.isWarnExceeded(used)) {
            return Flux.empty();
        }
        context.getRuntimeContext().put(ATTR_COST_BUDGET_WARNED, "1");
        String message = hardWarn
                ? "成本预算超硬上限: used=$" + used + ", hardLimit=$" + policy.getHardLimitUsd()
                : "成本预算告警: used=$" + used + ", warnThreshold=$" + policy.getWarnUsd();
        AgentEventType type = hardWarn ? AgentEventType.COST_BUDGET_EXCEEDED : AgentEventType.COST_BUDGET_WARN;
        return Flux.just(AgentEvent.of(type, message));
    }

    /**
     * 错误同步钩子便捷方法，依次通知中间件链每个中间件
     * @param context
     * @param error
     * @param phase
     */
    protected void applyOnError(EngineContext context, Throwable error, String phase) {
        middlewareChain(context).applyOnError(error, context.getRuntimeContext(), phase);
    }

    /**
     * 判断审批是否启用：审批协调器非空按其开关判定，否则回退权限引擎非空判定
     * @param context
     * @return
     */
    protected boolean approvalEnabled(EngineContext context) {
        ApprovalCoordinator coordinator = approvalCoordinator(context);
        return coordinator != null ? coordinator.isEnabled() : permissionEngine(context) != null;
    }

    /**
     * 审批三角triage：审批协调器非空走统一分拣，否则按权限引擎三路分拣
     * @param context
     * @param toolCalls
     * @param runId 持久执行运行ID，可为null
     * @return
     */
    protected ApprovalCoordinator.TriageResult triageToolCalls(EngineContext context,
                                                               List<AgentToolUseBlock> toolCalls, String runId) {
        ApprovalCoordinator coordinator = approvalCoordinator(context);
        if (coordinator != null) {
            return coordinator.triage(toolCalls, context.getRuntimeContext(), runId);
        }
        return triageByPermissionEngine(permissionEngine(context), context, toolCalls);
    }

    /**
     * 每轮迭代前中断检查，被中断时返回INTERRUPTED事件流，否则返回null
     * @param context
     * @return
     */
    protected Flux<AgentEvent> checkInterrupted(EngineContext context) {
        if (context.getRuntimeContext() != null
                && context.getRuntimeContext().getInterruptControl() != null
                && context.getRuntimeContext().getInterruptControl().isInterrupted()) {
            return Flux.just(
                    AgentEvent.of(AgentEventType.INTERRUPTED, "Agent被中断"),
                    AgentEvent.completed());
        }
        return null;
    }

    /**
     * 每轮迭代后保存检查点（非首轮迭代时）
     * @param context
     * @param messages
     * @param iteration
     */
    protected void saveIterationCheckpoint(EngineContext context, List<AgentMessage> messages, int iteration) {
        CheckpointManager manager = checkpointManager(context);
        if (manager == null || iteration <= 0 || context.getRuntimeContext() == null) {
            return;
        }
        String sessionId = context.getRuntimeContext().getSessionId();
        if (sessionId != null) {
            manager.save(sessionId, messages, iteration);
        }
    }

    /**
     * 装载最近的持久执行检查点并恢复运行状态到上下文
     * <p>
     * 命中检查点时恢复runId、已完成工具ID并迁回RUNNING状态，由子类以检查点内容续接各自循环。
     * </p>
     * @param context
     * @return 命中时返回检查点，未命中或未启用持久化时返回empty
     */
    protected Optional<AgentCheckpoint> loadLatestDurableCheckpoint(EngineContext context) {
        DurableExecutionTracker tracker = durableTracker(context);
        if (tracker == null || !tracker.isEnabled() || context.getRuntimeContext() == null) {
            return Optional.empty();
        }
        Optional<AgentCheckpoint> latest = tracker.latestCheckpoint(
                context.getRuntimeContext().getScopeId(), context.getRuntimeContext().getSessionId());
        if (!latest.isPresent()) {
            return Optional.empty();
        }
        AgentCheckpoint checkpoint = latest.get();
        if (checkpoint.getRunId() != null) {
            context.getRuntimeContext().put(DurableExecutionTracker.ATTR_RUN_ID, checkpoint.getRunId());
        }
        if (!checkpoint.getCompletedToolUseIds().isEmpty()) {
            Set<String> restored = java.util.concurrent.ConcurrentHashMap.newKeySet();
            restored.addAll(checkpoint.getCompletedToolUseIds());
            context.getRuntimeContext().put(DurableExecutionTracker.ATTR_COMPLETED_TOOL_IDS, restored);
        }
        tracker.ensureRunning(context.getRuntimeContext(), context.getAgentName());
        return latest;
    }

    /**
     * 从工具结果消息中提取澄清请求块（ask_user专用标记）
     * @param toolResults
     * @return
     */
    protected List<AgentToolResultBlock> extractClarificationResults(List<AgentMessage> toolResults) {
        List<AgentToolResultBlock> results = new ArrayList<>();
        if (toolResults == null) {
            return results;
        }
        for (AgentMessage msg : toolResults) {
            if (msg == null || msg.getContent() == null) {
                continue;
            }
            for (AgentContentBlock block : msg.getContent()) {
                if (block instanceof AgentToolResultBlock resultBlock && resultBlock.isClarificationRequest()) {
                    results.add(resultBlock);
                }
            }
        }
        return results;
    }

    /**
     * 处理澄清请求暂停，ReAct快照续接语义专用
     * <p>
     * 在通用澄清暂停（熔断检查、待澄清快照、事件发射）基础上，
     * 额外保存对话快照与迭代轮次，供ReAct快照续接恢复。
     * </p>
     * @param clarificationResults
     * @param toolResults
     * @param messages
     * @param context
     * @param iteration
     * @return
     */
    protected Flux<AgentEvent> handleClarificationRequest(List<AgentToolResultBlock> clarificationResults,
                                                          List<AgentMessage> toolResults, List<AgentMessage> messages,
                                                          EngineContext context, int iteration) {
        // 对话快照与迭代轮次存入上下文，供快照续接恢复
        context.getRuntimeContext().put(ATTR_PENDING_CLARIFICATION_CONVERSATION, new ArrayList<>(messages));
        context.getRuntimeContext().put(ATTR_PENDING_CLARIFICATION_ITERATION, iteration);
        return clarificationPauseFlux(clarificationResults, toolResults, context);
    }

    /**
     * 通用澄清暂停：熔断检查、保存待澄清快照并发射REQUIRE_USER_CLARIFICATION事件
     * <p>
     * 连续澄清次数超过MAX_CLARIFICATIONS时报错中止，防止模型无限提问；
     * 事件发射后由run模板截断本轮，范式引擎以台账重放恢复，ReAct以快照续接恢复。
     * </p>
     * @param clarificationResults
     * @param toolResults
     * @param context
     * @return
     */
    protected Flux<AgentEvent> clarificationPauseFlux(List<AgentToolResultBlock> clarificationResults,
                                                      List<AgentMessage> toolResults, EngineContext context) {
        Integer count = (Integer) context.getRuntimeContext().get(ATTR_CLARIFICATION_COUNT);
        int current = count != null ? count : 0;
        if (current >= MAX_CLARIFICATIONS) {
            return Flux.just(AgentEvent.of(AgentEventType.ERROR,
                    new IllegalStateException("连续澄清次数超过上限: " + MAX_CLARIFICATIONS)),
                    AgentEvent.completed());
        }
        context.getRuntimeContext().put(ATTR_CLARIFICATION_COUNT, current + 1);
        // 保存暂停快照，供 resumeWithClarification 恢复
        context.getRuntimeContext().put(ATTR_PENDING_CLARIFICATION, clarificationResults);
        // 发射需要用户澄清事件，暂停循环等待答案
        List<AgentEvent> events = new ArrayList<>();
        for (AgentToolResultBlock result : clarificationResults) {
            events.add(new RequireUserClarificationEvent(result.getTextContent(), result.getToolUseId()));
        }
        return Flux.just(AgentEvent.of(AgentEventType.TOOL_CALL_END, toolResults))
                .concatWith(Flux.fromIterable(events));
    }

    /**
     * 恢复因用户澄清暂停的Agent执行（台账重放模板）
     * <p>
     * 将用户答案记入澄清台账（按问题文本作键），随后以原始输入重放完整循环；
     * 重放时withToolExecution对命中台账的ask_user调用短路合成答案结果，不再真实执行。
     * ReActEngine覆写本方法为对话快照续接语义。
     * </p>
     * @param answers
     * @param context
     * @return
     */
    public Flux<AgentEvent> resumeWithClarification(List<ClarificationAnswer> answers, EngineContext context) {
        if (answers == null || answers.isEmpty()) {
            return Flux.just(AgentEvent.of(AgentEventType.ERROR,
                    new IllegalStateException("无澄清答案，无法恢复执行")), AgentEvent.completed());
        }
        // 持久执行：恢复运行自WAITING_CLARIFICATION迁回RUNNING
        DurableExecutionTracker tracker = durableTracker(context);
        if (tracker != null && tracker.isEnabled()) {
            tracker.ensureRunning(context.getRuntimeContext(), context.getAgentName());
        }
        // 从上下文中恢复待澄清的请求
        @SuppressWarnings("unchecked")
        List<AgentToolResultBlock> pendingClarifications = (List<AgentToolResultBlock>) context.getRuntimeContext()
                .get(ATTR_PENDING_CLARIFICATION);
        if (pendingClarifications == null || pendingClarifications.isEmpty()) {
            return Flux.just(AgentEvent.of(AgentEventType.ERROR,
                    new IllegalStateException("无待澄清的请求，无法恢复执行")), AgentEvent.completed());
        }
        // 答案记入澄清台账：按问题文本作键，重放时相同问题命中短路
        Map<String, String> ledger = clarificationLedger(context);
        for (AgentToolResultBlock pending : pendingClarifications) {
            String answer = findClarificationAnswer(answers, pending.getToolUseId());
            ledger.put(pending.getTextContent(), answer);
        }
        // 以原始输入重放完整循环，ask_user调用命中台账后短路合成结果；
        // 走run模板获得完整生命周期（AGENT_START/AGENT_END、持久跟踪、事件广播、总超时）
        @SuppressWarnings("unchecked")
        List<AgentMessage> originalInputs = (List<AgentMessage>) context.getRuntimeContext()
                .get(ATTR_ORIGINAL_INPUTS);
        List<AgentMessage> replayInputs = originalInputs != null ? originalInputs : context.getConversation();
        return run(replayInputs, context);
    }

    /**
     * 获取或创建澄清答案台账
     * @param context
     * @return
     */
    @SuppressWarnings("unchecked")
    private Map<String, String> clarificationLedger(EngineContext context) {
        Map<String, String> ledger = (Map<String, String>) context.getRuntimeContext()
                .get(ATTR_CLARIFICATION_LEDGER);
        if (ledger == null) {
            ledger = new HashMap<>();
            context.getRuntimeContext().put(ATTR_CLARIFICATION_LEDGER, ledger);
        }
        return ledger;
    }

    /**
     * 匹配ask_user调用的台账答案，未命中返回null
     * <p>
     * 按调用参数中的question文本匹配台账；范式引擎的toolUseId按步骤确定性生成会跨轮复用，
     * 不能作为匹配键。未命中时放行真实执行（新问题会被再次暂停，由熔断上限兜底）。
     * </p>
     * @param context
     * @param call
     * @return
     */
    protected String clarificationAnswerForCall(EngineContext context, AgentToolUseBlock call) {
        if (!"ask_user".equals(call.getToolName()) || context.getRuntimeContext() == null) {
            return null;
        }
        @SuppressWarnings("unchecked")
        Map<String, String> ledger = (Map<String, String>) context.getRuntimeContext()
                .get(ATTR_CLARIFICATION_LEDGER);
        if (ledger == null || ledger.isEmpty()) {
            return null;
        }
        // 问题文本匹配
        Object question = call.getInput() != null ? call.getInput().get("question") : null;
        return question != null ? ledger.get(question.toString()) : null;
    }

    /**
     * 按toolCallId匹配澄清答案，未匹配时回退空串
     * @param answers
     * @param toolCallId
     * @return
     */
    protected String findClarificationAnswer(List<ClarificationAnswer> answers, String toolCallId) {
        for (ClarificationAnswer answer : answers) {
            if (answer != null && Objects.equals(answer.toolCallId(), toolCallId)) {
                return answer.answer() != null ? answer.answer() : "";
            }
        }
        return "";
    }

    /**
     * 当上下文Token估算超过上限时，按截断策略缩减历史消息
     * @param messages
     * @param context
     */
    protected void truncateContextIfNeeded(List<AgentMessage> messages, EngineContext context) {
        int maxContextTokens = context.getMaxContextTokens();
        if (maxContextTokens <= 0 || messages.isEmpty()) {
            return;
        }
        HarnessAgentRuntimeBuilder.TruncationStrategy strategy = context.getHistoryTruncationStrategy();
        if (strategy == null) {
            strategy = HarnessAgentRuntimeBuilder.TruncationStrategy.TAIL;
        }

        if (estimateTokens(messages) <= maxContextTokens) {
            return;
        }

        // system消息始终保留，截断从非system消息中剔除
        int startIndex = 0;
        if (messages.get(0).getRole() == AgentMessageRole.SYSTEM) {
            startIndex = 1;
        }

        while (estimateTokens(messages) > maxContextTokens && messages.size() > startIndex + 1) {
            if (strategy == HarnessAgentRuntimeBuilder.TruncationStrategy.HEAD) {
                // 保留最早的历史，删除尾部最新消息
                messages.remove(messages.size() - 1);
            } else {
                // TAIL与SUMMARY均保留最新，删除头部最旧消息；SUMMARY的摘要压缩由CompactionMiddleware负责
                messages.remove(startIndex);
            }
        }
        // 清理因截断产生的孤立工具结果消息：其对应的assistant工具调用已被删除，
        // 保留孤立的tool消息会导致OpenAI API因缺少配对的tool_call_id而报错
        while (messages.size() > startIndex
                && messages.get(startIndex).getRole() == AgentMessageRole.TOOL) {
            messages.remove(startIndex);
        }
        // 修复截断破坏的tool_calls/tool结果配对：截断可能停在assistant(tool_calls)
        // 与其部分tool结果之间（如HEAD删掉第二个tool结果后达标），只兜底末条消息
        // 无法覆盖该中间态，需全量扫描，凡assistant的任一tool_call缺失结果即整组移除
        repairToolCallPairing(messages, startIndex);
    }

    /**
     * 扫描并修复tool_calls与tool结果的配对完整性
     * <p>
     * assistant声明的任一tool_call在其紧随的tool结果段中缺失对应结果时，
     * 移除该assistant及其残留的全部tool结果消息，避免违反OpenAI协议的配对要求。
     * </p>
     * @param messages
     * @param startIndex
     */
    private void repairToolCallPairing(List<AgentMessage> messages, int startIndex) {
        for (int i = startIndex; i < messages.size(); ) {
            AgentMessage msg = messages.get(i);
            if (msg.getRole() != AgentMessageRole.ASSISTANT || !hasToolCalls(msg)) {
                i++;
                continue;
            }
            Set<String> useIds = new HashSet<>();
            for (AgentContentBlock block : msg.getContent()) {
                if (block instanceof AgentToolUseBlock use && use.getToolUseId() != null) {
                    useIds.add(use.getToolUseId());
                }
            }
            int j = i + 1;
            Set<String> resultIds = new HashSet<>();
            while (j < messages.size() && messages.get(j).getRole() == AgentMessageRole.TOOL) {
                AgentMessage toolMsg = messages.get(j);
                if (toolMsg.getContent() != null) {
                    for (AgentContentBlock block : toolMsg.getContent()) {
                        if (block instanceof AgentToolResultBlock result && result.getToolUseId() != null) {
                            resultIds.add(result.getToolUseId());
                        }
                    }
                }
                j++;
            }
            if (resultIds.containsAll(useIds)) {
                i = j;
            } else {
                // 配对不完整：移除该assistant与其残留的tool结果段后原地重新检查
                messages.subList(i, j).clear();
            }
        }
    }

    /**
     * 判断消息是否包含工具调用块
     * @param message
     * @return
     */
    protected boolean hasToolCalls(AgentMessage message) {
        if (message == null || message.getContent() == null) {
            return false;
        }
        return message.getContent().stream().anyMatch(b -> b instanceof AgentToolUseBlock);
    }

    /**
     * 估算消息列表的Token数量，区分中英文以提升精度
     * <p>
     * 中文按1.5字符≈1 token，英文与ASCII符号按4字符≈1 token，
     * 每个内容块额外计入8 token开销。无需引入外部tokenizer，保持轻量。
     * </p>
     * @param messages
     * @return
     */
    protected int estimateTokens(List<AgentMessage> messages) {
        int cjkChars = 0;
        int nonCjkChars = 0;
        int contentBlocks = 0;
        for (AgentMessage msg : messages) {
            if (msg == null) {
                continue;
            }
            String text = msg.getTextContent();
            if (text != null) {
                for (int i = 0; i < text.length(); i++) {
                    char c = text.charAt(i);
                    if (c >= '\u4e00' && c <= '\u9fff') {
                        cjkChars++;
                    } else {
                        nonCjkChars++;
                    }
                }
            }
            if (msg.getContent() != null) {
                contentBlocks += msg.getContent().size();
            }
        }
        return (int) (cjkChars / 1.5) + nonCjkChars / 4 + contentBlocks * 8;
    }

    /**
     * 从消息列表中提取最近一条用户查询文本
     * @param messages
     * @return
     */
    protected String extractUserQuery(List<AgentMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        // 从后往前找最近一条用户消息
        for (int i = messages.size() - 1; i >= 0; i--) {
            AgentMessage msg = messages.get(i);
            if (msg.getRole() == AgentMessageRole.USER && msg.getContent() != null) {
                StringBuilder sb = new StringBuilder();
                for (AgentContentBlock block : msg.getContent()) {
                    String text = extractText(block);
                    if (text != null) {
                        sb.append(text).append(" ");
                    }
                }
                return sb.toString().trim();
            }
        }
        return "";
    }

    /**
     * 从内容块中提取文本
     * @param block
     * @return
     */
    private String extractText(AgentContentBlock block) {
        if (block == null) {
            return null;
        }
        // 尝试获取文本内容
        if (block instanceof AgentTextBlock) {
            return ((AgentTextBlock) block).getText();
        }
        // 尝试通过反射获取 text 属性（兼容不同实现）
        try {
            java.lang.reflect.Method getText = block.getClass().getMethod("getText");
            Object result = getText.invoke(block);
            return result != null ? result.toString() : null;
        } catch (Exception e) {
            return block.toString();
        }
    }

    /**
     * 结构化输出校验：配置了json_schema时校验消息文本，返回错误描述，校验通过返回null
     * @param context
     * @param message
     * @return
     */
    protected String structuredOutputValidationError(EngineContext context, AgentMessage message) {
        if (context.getGenerateOptions() == null) {
            return null;
        }
        AgentResponseFormat responseFormat = context.getGenerateOptions().getResponseFormat();
        if (responseFormat == null) {
            return null;
        }
        return StructuredOutputValidator.validate(message.getTextContent(), responseFormat);
    }

    /**
     * 处理结构化输出校验失败，注入纠正提示并由retryLoop重试推理
     * @param validationError 校验错误描述
     * @param messages 对话历史
     * @param context 引擎上下文
     * @param iteration 当前迭代轮次
     * @param retryLoop 重试续接循环，由子类提供各自的循环主体
     * @return
     */
    protected Flux<AgentEvent> handleStructuredOutputFailure(String validationError, List<AgentMessage> messages,
                                                             EngineContext context, int iteration,
                                                             Supplier<Flux<AgentEvent>> retryLoop) {
        StructuredOutputRetryPolicy policy = engineStructuredOutputRetryPolicy != null
                ? engineStructuredOutputRetryPolicy : StructuredOutputRetryPolicy.defaultPolicy();
        Integer retryCount = (Integer) context.getRuntimeContext().get(ATTR_STRUCTURED_RETRY_COUNT);
        int currentRetry = retryCount != null ? retryCount : 0;
        if (currentRetry >= policy.getMaxRetries()) {
            // 重试次数耗尽，输出结构化校验失败ERROR事件
            return Flux.just(AgentEvent.of(AgentEventType.ERROR,
                    new StructuredOutputException("结构化输出校验失败，重试次数已耗尽: " + validationError)),
                    AgentEvent.completed());
        }
        context.getRuntimeContext().put(ATTR_STRUCTURED_RETRY_COUNT, currentRetry + 1);
        // 注入纠正提示，引导模型修正输出格式
        AgentMessage correctionMsg = MessageFactory.createUserMessage(policy.formatError(validationError));
        messages.add(correctionMsg);
        return retryLoop.get();
    }

    /**
     * 结构化输出校验失败异常（重试耗尽后抛出）
     */
    public static class StructuredOutputException extends RuntimeException {

        public StructuredOutputException(String message) {
            super(message);
        }
    }

    /**
     * 解析中间件链，优先取引擎上下文暴露的组件，未暴露时回退引擎自有实例
     * @param context
     * @return
     */
    protected MiddlewareChain middlewareChain(EngineContext context) {
        MiddlewareChain chain = context.getMiddlewareChain();
        if (chain != null) {
            return chain;
        }
        return engineMiddlewareChain != null ? engineMiddlewareChain : EMPTY_MIDDLEWARE_CHAIN;
    }

    /**
     * 解析事件监听器注册中心，优先取引擎上下文暴露的组件，未暴露时回退引擎自有实例
     * @param context
     * @return
     */
    protected EventBus eventBus(EngineContext context) {
        EventBus bus = context.getEventBus();
        return bus != null ? bus : engineEventBus;
    }

    /**
     * 解析模型调用器，优先取引擎上下文暴露的组件，未暴露时回退引擎自有实例
     * @param context
     * @return
     */
    protected ModelCaller modelCaller(EngineContext context) {
        ModelCaller caller = context.getModelCaller();
        return caller != null ? caller : engineModelCaller;
    }

    /**
     * 解析工具执行器，优先取引擎上下文暴露的组件，未暴露时回退引擎自有实例
     * @param context
     * @return
     */
    protected ToolExecutor toolExecutor(EngineContext context) {
        ToolExecutor executor = context.getToolExecutor();
        return executor != null ? executor : engineToolExecutor;
    }

    /**
     * 解析模型响应解析器，优先取引擎上下文暴露的组件，未暴露时回退引擎自有实例
     * @param context
     * @return
     */
    protected ModelResponseParser responseParser(EngineContext context) {
        ModelResponseParser parser = context.getResponseParser();
        return parser != null ? parser : engineResponseParser;
    }

    /**
     * 解析权限引擎，优先取引擎上下文暴露的组件，未暴露时回退引擎自有实例
     * @param context
     * @return
     */
    protected PermissionEngine permissionEngine(EngineContext context) {
        PermissionEngine engine = context.getPermissionEngine();
        return engine != null ? engine : enginePermissionEngine;
    }

    /**
     * 解析审批协调器，优先取引擎上下文暴露的组件，未暴露时回退引擎自有实例
     * @param context
     * @return
     */
    protected ApprovalCoordinator approvalCoordinator(EngineContext context) {
        ApprovalCoordinator coordinator = context.getApprovalCoordinator();
        return coordinator != null ? coordinator : engineApprovalCoordinator;
    }

    /**
     * 解析检查点管理器，取引擎自有实例
     * @param context
     * @return
     */
    protected CheckpointManager checkpointManager(EngineContext context) {
        return engineCheckpointManager;
    }

    /**
     * 解析持久执行状态跟踪器，优先取引擎上下文暴露的组件，未暴露时回退引擎自有实例
     * @param context
     * @return
     */
    protected DurableExecutionTracker durableTracker(EngineContext context) {
        DurableExecutionTracker tracker = context.getDurableTracker();
        return tracker != null ? tracker : engineDurableTracker;
    }

    /**
     * 解析Token预算策略，优先取引擎上下文暴露的组件，未暴露时回退引擎自有实例
     * @param context
     * @return
     */
    protected TokenBudgetPolicy tokenBudgetPolicy(EngineContext context) {
        TokenBudgetPolicy policy = context.getTokenBudgetPolicy();
        return policy != null ? policy : engineTokenBudgetPolicy;
    }

    /**
     * 解析成本预算策略，优先取引擎上下文暴露的组件，未暴露时回退引擎自有实例
     * @param context
     * @return
     */
    protected CostBudgetPolicy costBudgetPolicy(EngineContext context) {
        CostBudgetPolicy policy = context.getCostBudgetPolicy();
        return policy != null ? policy : engineCostBudgetPolicy;
    }

    /**
     * 解析模型定价注册表，优先取引擎上下文暴露的组件，未暴露时回退引擎自有实例
     * @param context
     * @return
     */
    protected ModelPricingRegistry modelPricingRegistry(EngineContext context) {
        ModelPricingRegistry registry = context.getModelPricingRegistry();
        return registry != null ? registry : engineModelPricingRegistry;
    }

    /**
     * 解析模型编码，优先取引擎上下文暴露的配置，未暴露时回退引擎自有配置
     * @param context
     * @return
     */
    protected String modelCode(EngineContext context) {
        String code = context.getModelCode();
        return code != null ? code : engineModelCode;
    }

    /**
     * 注入引擎自有模型调用器回退实例
     * @param engineModelCaller
     */
    protected void setEngineModelCaller(ModelCaller engineModelCaller) {
        this.engineModelCaller = engineModelCaller;
    }

    /**
     * 注入引擎自有工具执行器回退实例
     * @param engineToolExecutor
     */
    protected void setEngineToolExecutor(ToolExecutor engineToolExecutor) {
        this.engineToolExecutor = engineToolExecutor;
    }

    /**
     * 注入引擎自有中间件链回退实例
     * @param engineMiddlewareChain
     */
    protected void setEngineMiddlewareChain(MiddlewareChain engineMiddlewareChain) {
        this.engineMiddlewareChain = engineMiddlewareChain;
    }

    /**
     * 注入引擎自有模型响应解析器回退实例
     * @param engineResponseParser
     */
    protected void setEngineResponseParser(ModelResponseParser engineResponseParser) {
        this.engineResponseParser = engineResponseParser;
    }

    /**
     * 注入引擎自有权限引擎回退实例
     * @param enginePermissionEngine
     */
    protected void setEnginePermissionEngine(PermissionEngine enginePermissionEngine) {
        this.enginePermissionEngine = enginePermissionEngine;
    }

    /**
     * 注入引擎自有检查点管理器回退实例
     * @param engineCheckpointManager
     */
    protected void setEngineCheckpointManager(CheckpointManager engineCheckpointManager) {
        this.engineCheckpointManager = engineCheckpointManager;
    }

    /**
     * 注入引擎自有事件监听器注册中心回退实例
     * @param engineEventBus
     */
    protected void setEngineEventBus(EventBus engineEventBus) {
        this.engineEventBus = engineEventBus;
    }

    /**
     * 注入引擎自有审批协调器回退实例
     * @param engineApprovalCoordinator
     */
    protected void setEngineApprovalCoordinator(ApprovalCoordinator engineApprovalCoordinator) {
        this.engineApprovalCoordinator = engineApprovalCoordinator;
    }

    /**
     * 注入引擎自有持久执行状态跟踪器回退实例
     * @param engineDurableTracker
     */
    protected void setEngineDurableTracker(DurableExecutionTracker engineDurableTracker) {
        this.engineDurableTracker = engineDurableTracker;
    }

    /**
     * 注入引擎自有Token预算策略回退实例
     * @param engineTokenBudgetPolicy
     */
    protected void setEngineTokenBudgetPolicy(TokenBudgetPolicy engineTokenBudgetPolicy) {
        this.engineTokenBudgetPolicy = engineTokenBudgetPolicy;
    }

    /**
     * 注入引擎自有成本预算策略回退实例
     * @param engineCostBudgetPolicy
     */
    protected void setEngineCostBudgetPolicy(CostBudgetPolicy engineCostBudgetPolicy) {
        this.engineCostBudgetPolicy = engineCostBudgetPolicy;
    }

    /**
     * 注入引擎自有模型定价注册表回退实例
     * @param engineModelPricingRegistry
     */
    protected void setEngineModelPricingRegistry(ModelPricingRegistry engineModelPricingRegistry) {
        this.engineModelPricingRegistry = engineModelPricingRegistry;
    }

    /**
     * 注入引擎自有模型编码回退配置
     * @param engineModelCode
     */
    protected void setEngineModelCode(String engineModelCode) {
        this.engineModelCode = engineModelCode;
    }

    /**
     * 注入引擎自有结构化输出重试策略
     * @param engineStructuredOutputRetryPolicy
     */
    protected void setEngineStructuredOutputRetryPolicy(
            StructuredOutputRetryPolicy engineStructuredOutputRetryPolicy) {
        this.engineStructuredOutputRetryPolicy = engineStructuredOutputRetryPolicy;
    }
}
