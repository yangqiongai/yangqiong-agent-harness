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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.yangqiongai.agent.harness.durable.AgentCheckpoint;
import com.yangqiongai.agent.harness.core.event.ClarificationAnswer;
import com.yangqiongai.agent.harness.core.message.MessageFactory;
import com.yangqiongai.agent.harness.memory.CheckpointManager;
import com.yangqiongai.agent.harness.model.ModelCaller;
import com.yangqiongai.agent.harness.model.ModelResponseParser;
import com.yangqiongai.agent.harness.model.StructuredOutputRetryPolicy;
import com.yangqiongai.agent.harness.permission.PermissionEngine;
import com.yangqiongai.agent.harness.tool.ToolExecutor;
import com.yangqiongai.agent.harness.config.CostBudgetPolicy;
import com.yangqiongai.agent.harness.config.TokenBudgetPolicy;
import com.yangqiongai.agent.harness.model.ModelPricingRegistry;
import com.yangqiongai.agent.harness.event.EventBus;
import com.yangqiongai.agent.harness.core.event.AgentEvent;
import com.yangqiongai.agent.harness.core.event.AgentEventType;
import com.yangqiongai.agent.harness.core.event.AgentResultEvent;
import com.yangqiongai.agent.harness.core.event.ConfirmResult;
import com.yangqiongai.agent.harness.core.event.RequireUserConfirmEvent;
import com.yangqiongai.agent.harness.core.message.AgentContentBlock;
import com.yangqiongai.agent.harness.core.message.AgentMessage;
import com.yangqiongai.agent.harness.core.message.AgentMessageRole;
import com.yangqiongai.agent.harness.core.message.AgentTextBlock;
import com.yangqiongai.agent.harness.core.message.AgentToolResultBlock;
import com.yangqiongai.agent.harness.core.message.AgentToolUseBlock;
import com.yangqiongai.agent.harness.core.model.AgentChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * ReAct执行引擎
 * <p>
 * 基于纯响应式递归实现ReAct循环（推理→行动→观察→再推理）。
 * 事件直接作为Flux元素返回，取消信号自动传播，无需EventEmitter旁路。
 * 生命周期管线、组件双源解析、审批triage、澄清暂停与恢复、上下文截断、
 * 持久执行集成、结构化输出校验重试等引擎级能力复用父类{@link AbstractAgentLoop}；
 * 恢复语义采用对话快照续接（区别于父类台账重放），避免重放多耗模型调用。
 * </p>
 * @author yangqiong
 */
public class ReActEngine extends AbstractAgentLoop {

    /**
     * 日志器
     */
    private static final Logger log = LoggerFactory.getLogger(ReActEngine.class);

    /**
     * 属性键：暂停人工确认时的对话快照
     */
    static final String ATTR_PENDING_CONVERSATION = "harness.pendingConversation";

    /**
     * 属性键：暂停人工确认时的迭代轮次
     */
    static final String ATTR_PENDING_ITERATION = "harness.pendingIteration";

    public ReActEngine(ModelCaller modelCaller, ToolExecutor toolExecutor, MiddlewareChain middlewareChain,
                       ModelResponseParser responseParser, PermissionEngine permissionEngine) {
        this(modelCaller, toolExecutor, middlewareChain, responseParser, permissionEngine, null);
    }

    public ReActEngine(ModelCaller modelCaller, ToolExecutor toolExecutor, MiddlewareChain middlewareChain,
                       ModelResponseParser responseParser, PermissionEngine permissionEngine,
                       CheckpointManager checkpointManager) {
        setEngineModelCaller(modelCaller);
        setEngineToolExecutor(toolExecutor);
        setEngineMiddlewareChain(middlewareChain);
        setEngineResponseParser(responseParser);
        setEnginePermissionEngine(permissionEngine);
        setEngineCheckpointManager(checkpointManager);
    }

    /**
     * 注入审批协调器
     * @param approvalCoordinator
     * @return
     */
    public ReActEngine approvalCoordinator(ApprovalCoordinator approvalCoordinator) {
        setEngineApprovalCoordinator(approvalCoordinator);
        return this;
    }

    /**
     * 注入持久执行状态跟踪器
     * @param durableTracker
     * @return
     */
    public ReActEngine durableTracker(DurableExecutionTracker durableTracker) {
        setEngineDurableTracker(durableTracker);
        return this;
    }

    /**
     * 注入Token预算策略
     * @param tokenBudgetPolicy
     * @return
     */
    public ReActEngine tokenBudgetPolicy(TokenBudgetPolicy tokenBudgetPolicy) {
        setEngineTokenBudgetPolicy(tokenBudgetPolicy);
        return this;
    }

    /**
     * 注入成本预算策略
     * @param costBudgetPolicy
     * @return
     */
    public ReActEngine costBudgetPolicy(CostBudgetPolicy costBudgetPolicy) {
        setEngineCostBudgetPolicy(costBudgetPolicy);
        return this;
    }

    /**
     * 注入模型定价注册表
     * @param modelPricingRegistry
     * @return
     */
    public ReActEngine modelPricingRegistry(ModelPricingRegistry modelPricingRegistry) {
        setEngineModelPricingRegistry(modelPricingRegistry);
        return this;
    }

    /**
     * 设置模型编码，用于成本计价的定价查询
     * @param modelCode
     * @return
     */
    public ReActEngine modelCode(String modelCode) {
        setEngineModelCode(modelCode);
        return this;
    }

    /**
     * 注入结构化输出重试策略
     * @param structuredOutputRetryPolicy
     * @return
     */
    public ReActEngine structuredOutputRetryPolicy(StructuredOutputRetryPolicy structuredOutputRetryPolicy) {
        setEngineStructuredOutputRetryPolicy(structuredOutputRetryPolicy);
        return this;
    }

    /**
     * 注入事件监听器注册中心，运行时所有Agent事件自动广播给监听器
     * @param eventBus
     * @return
     */
    public ReActEngine eventBus(EventBus eventBus) {
        setEngineEventBus(eventBus);
        return this;
    }

    /**
     * 同步调用Agent，消费完整事件流并返回最终消息
     * <p>委托父类默认实现：全量消费直到流完成，避免提前取消导致尾段事件丢失。</p>
     * @param inputs
     * @param context
     * @return
     */
    @Override
    public Mono<AgentMessage> call(List<AgentMessage> inputs, EngineContext context) {
        return super.call(inputs, context);
    }

    /**
     * 流式调用Agent，通过事件流输出增量事件与最终结果
     * @param inputs
     * @param context
     * @return
     */
    public Flux<AgentEvent> stream(List<AgentMessage> inputs, EngineContext context) {
        return run(inputs, context);
    }

    /**
     * 执行ReAct循环主体，迭代轮次从0开始
     * @param inputs
     * @param context
     * @return
     */
    @Override
    protected Flux<AgentEvent> doRun(List<AgentMessage> inputs, EngineContext context) {
        return reactLoopFlux(inputs, context, 0);
    }

    /**
     * 恢复因人工确认暂停的Agent执行
     * <p>
     * 根据确认结果决定：批准的工具继续执行，拒绝的工具生成错误结果后递归进入下一轮推理。
     * </p>
     * @param confirmResults
     * @param context
     * @return
     */
    @Override
    public Flux<AgentEvent> resume(List<ConfirmResult> confirmResults, EngineContext context) {
        if (confirmResults == null || confirmResults.isEmpty()) {
            return Flux.just(AgentEvent.of(AgentEventType.ERROR,
                    new IllegalStateException("无确认结果，无法恢复执行")), AgentEvent.completed());
        }
        // 持久执行：恢复运行自WAITING_APPROVAL迁回RUNNING
        DurableExecutionTracker tracker = durableTracker(context);
        if (tracker != null && tracker.isEnabled()) {
            tracker.ensureRunning(context.getRuntimeContext(), context.getAgentName());
        }
        // 分离批准和拒绝的工具
        List<AgentToolUseBlock> approvedCalls = new ArrayList<>();
        List<AgentMessage> deniedMessages = new ArrayList<>();
        // 从上下文中恢复待确认的工具调用
        @SuppressWarnings("unchecked")
        List<AgentToolUseBlock> pendingCalls = (List<AgentToolUseBlock>) context.getRuntimeContext()
                .get(ATTR_PENDING_TOOL_CALLS);
        if (pendingCalls == null || pendingCalls.isEmpty()) {
            return Flux.just(AgentEvent.of(AgentEventType.ERROR,
                    new IllegalStateException("无待确认的工具调用，无法恢复执行")), AgentEvent.completed());
        }
        ApprovalCoordinator coordinator = approvalCoordinator(context);
        for (AgentToolUseBlock call : pendingCalls) {
            // toolCallId精确匹配优先，未携带toolCallId的审批结果回退按工具名匹配
            ConfirmResult result = coordinator != null
                    ? coordinator.matchConfirm(call, confirmResults)
                    : matchConfirmByName(call, confirmResults);
            if (tracker != null && tracker.isEnabled()) {
                tracker.resolveApproval(context.getRuntimeContext(), call.getToolUseId(),
                        result != null && result.isApproved(),
                        result != null ? result.getReason() : null);
            }
            if (result != null && result.isApproved()) {
                approvedCalls.add(call);
            } else {
                String reason = result != null ? result.getReason() : "未提供审批结果，默认拒绝";
                deniedMessages.add(MessageFactory.createToolMessage(
                        AgentToolResultBlock.error(call.getToolUseId(),
                                "人工拒绝: " + call.getToolName() + " - " + reason)));
            }
        }
        // 先发射拒绝的错误结果，再执行批准的工具
        // 从上下文恢复暂停时的对话快照与迭代轮次，避免丢失上下文与突破maxIters限制
        @SuppressWarnings("unchecked")
        List<AgentMessage> snapshot = (List<AgentMessage>) context.getRuntimeContext()
                .get(ATTR_PENDING_CONVERSATION);
        List<AgentMessage> conversation = snapshot != null
                ? new ArrayList<>(snapshot)
                : context.getConversation();
        conversation.addAll(deniedMessages);
        // 恢复暂停时的迭代轮次，避免重置为0导致突破maxIters限制
        Integer pendingIter = (Integer) context.getRuntimeContext().get(ATTR_PENDING_ITERATION);
        int resumeIter = pendingIter != null ? pendingIter + 1 : 1;
        if (approvedCalls.isEmpty()) {
            // 全部拒绝，递归进入下一轮推理
            return finishPipeline(reactLoopFlux(conversation, context, resumeIter), context);
        }
        return finishPipeline(middlewareChain(context).applyOnActing(context.getRuntimeContext(), approvedCalls,
                calls -> toolExecutor(context).executeTools(calls, context.getRuntimeContext(), context)
                        .flatMapMany(toolResults -> {
                            conversation.addAll(toolResults);
                            return reactLoopFlux(conversation, context, resumeIter);
                        })
            )
            .onErrorResume(error -> {
                applyOnError(context, error, "acting");
                return Flux.just(AgentEvent.of(AgentEventType.ERROR, error), AgentEvent.completed());
            }), context);
    }

    /**
     * 恢复因用户澄清暂停的Agent执行（对话快照续接语义）
     * <p>
     * 覆写父类台账重放模板：恢复暂停时的对话快照与迭代轮次，把对应 ask_user
     * 工具结果消息替换为用户答案消息并修复协议孤立项后，直接续接ReAct循环，
     * 避免台账重放多耗模型调用。
     * </p>
     * @param answers
     * @param context
     * @return
     */
    @Override
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
        // 恢复暂停时的对话快照与迭代轮次
        @SuppressWarnings("unchecked")
        List<AgentMessage> snapshot = (List<AgentMessage>) context.getRuntimeContext()
                .get(ATTR_PENDING_CLARIFICATION_CONVERSATION);
        List<AgentMessage> conversation = snapshot != null
                ? new ArrayList<>(snapshot)
                : context.getConversation();
        Integer pendingIter = (Integer) context.getRuntimeContext()
                .get(ATTR_PENDING_CLARIFICATION_ITERATION);
        int resumeIter = pendingIter != null ? pendingIter + 1 : 1;
        // 按toolCallId将澄清工具结果消息替换为用户答案消息，并消除assistant的孤立tool_calls
        for (AgentToolResultBlock pending : pendingClarifications) {
            String answer = findClarificationAnswer(answers, pending.getToolUseId());
            boolean replaced = replaceClarificationToolMessage(conversation, pending.getToolUseId(), answer);
            // 将携带ask_user调用的assistant消息转为纯文本，避免"tool_calls后无对应tool消息"的协议错误
            clarifyAssistantToolCallMessage(conversation, pending.getToolUseId(), pending.getTextContent());
            if (!replaced) {
                conversation.add(MessageFactory.createUserMessage(answer));
            }
        }
        // 防御性清理：移除所有孤立的tool_calls，确保不会违反OpenAI协议
        removeOrphanedToolCalls(conversation);
        // 调试辅助：输出续接对话摘要，便于排查"tool_calls后无对应tool消息"的协议错误
        if (log.isInfoEnabled()) {
            StringBuilder summary = new StringBuilder();
            for (AgentMessage m : conversation) {
                String role = m.getRole() != null ? m.getRole().name() : "?";
                boolean toolCalls = m.getContent() != null
                        && m.getContent().stream().anyMatch(AgentToolUseBlock.class::isInstance);
                summary.append(role).append(toolCalls ? "(tool_calls)" : "").append("; ");
            }
            log.info("澄清续接对话[{}条]: {}", conversation.size(), summary);
        }
        // 以修复后的对话快照续接ReAct循环（经finishPipeline接入持久跟踪与生命周期装饰）
        return finishPipeline(reactLoopFlux(conversation, context, resumeIter), context);
    }

    /**
     * 将携带指定toolCallId工具调用的assistant消息转为纯文本消息（丢弃工具调用块），
     * 避免续接时"assistant的tool_calls后无对应tool消息"违反OpenAI协议
     * @param conversation
     * @param toolCallId
     * @param fallbackText
     */
    private void clarifyAssistantToolCallMessage(List<AgentMessage> conversation, String toolCallId,
                                                 String fallbackText) {
        if (conversation == null || toolCallId == null) {
            return;
        }
        for (int i = 0; i < conversation.size(); i++) {
            AgentMessage msg = conversation.get(i);
            if (msg.getRole() != AgentMessageRole.ASSISTANT || msg.getContent() == null) {
                continue;
            }
            // 检查是否包含目标工具调用
            boolean hasMatch = msg.getContent().stream()
                    .anyMatch(b -> b instanceof AgentToolUseBlock use
                            && Objects.equals(use.getToolUseId(), toolCallId));
            if (!hasMatch) {
                continue;
            }
            // 保留文本块并丢弃所有工具调用块（同一个assistant消息中可能有多个工具调用，都要清理）
            List<AgentContentBlock> kept = msg.getContent().stream()
                    .filter(b -> !(b instanceof AgentToolUseBlock))
                    .collect(java.util.stream.Collectors.toList());
            if (kept.isEmpty()) {
                kept.add(AgentTextBlock.builder().text(fallbackText != null ? fallbackText : "").build());
            }
            conversation.set(i, MessageFactory.createAssistantMessage(kept, msg.getChatUsage()));
            // 不提前 return，遍历完整个对话列表，防止多个assistant消息中有匹配项
        }
    }

    /**
     * 将指定toolCallId的澄清工具结果消息替换为用户答案消息，返回是否完成替换
     * @param conversation
     * @param toolCallId
     * @param answerText
     * @return
     */
    private boolean replaceClarificationToolMessage(List<AgentMessage> conversation, String toolCallId,
                                                    String answerText) {
        if (conversation == null || toolCallId == null) {
            return false;
        }
        for (int i = 0; i < conversation.size(); i++) {
            AgentMessage msg = conversation.get(i);
            if (msg.getRole() != AgentMessageRole.TOOL || msg.getContent() == null) {
                continue;
            }
            for (AgentContentBlock block : msg.getContent()) {
                if (block instanceof AgentToolResultBlock resultBlock
                        && resultBlock.isClarificationRequest()
                        && Objects.equals(resultBlock.getToolUseId(), toolCallId)) {
                    conversation.set(i, MessageFactory.createUserMessage(answerText));
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 移除对话中孤立的tool_calls与孤儿的tool结果消息
     * <p>
     * OpenAI协议要求assistant消息的每个tool_call_id都必须被紧随其后的tool消息覆盖。
     * 澄清续接时ask_user的工具结果被替换为用户答案，其对应assistant消息的工具调用会变成孤立，
     * 若ID匹配失败或多工具调用场景未清理干净，将违反协议导致400错误，此处统一兜底清理。
     * </p>
     * @param conversation
     */
    private void removeOrphanedToolCalls(List<AgentMessage> conversation) {
        if (conversation == null || conversation.isEmpty()) {
            return;
        }
        // 清理assistant消息中未被tool结果覆盖的工具调用
        for (int i = 0; i < conversation.size(); i++) {
            AgentMessage msg = conversation.get(i);
            if (msg.getRole() != AgentMessageRole.ASSISTANT || msg.getContent() == null) {
                continue;
            }
            List<AgentToolUseBlock> calls = msg.getContent().stream()
                    .filter(AgentToolUseBlock.class::isInstance)
                    .map(AgentToolUseBlock.class::cast)
                    .collect(java.util.stream.Collectors.toList());
            if (calls.isEmpty()) {
                continue;
            }
            // 收集紧随其后的tool结果ID（中间不允许夹带非tool消息）
            Set<String> coveredIds = new HashSet<>();
            int j = i + 1;
            while (j < conversation.size()
                    && conversation.get(j).getRole() == AgentMessageRole.TOOL
                    && conversation.get(j).getContent() != null) {
                for (AgentContentBlock block : conversation.get(j).getContent()) {
                    if (block instanceof AgentToolResultBlock resultBlock
                            && resultBlock.getToolUseId() != null) {
                        coveredIds.add(resultBlock.getToolUseId());
                    }
                }
                j++;
            }
            boolean allCovered = calls.stream()
                    .allMatch(c -> c.getToolUseId() != null && coveredIds.contains(c.getToolUseId()));
            if (allCovered) {
                continue;
            }
            // 存在未被覆盖的tool_call_id（或紧随其后的不是tool消息），工具调用整体转为纯文本
            List<AgentContentBlock> kept = msg.getContent().stream()
                    .filter(b -> !(b instanceof AgentToolUseBlock))
                    .collect(java.util.stream.Collectors.toList());
            if (kept.isEmpty()) {
                kept.add(AgentTextBlock.builder().text("").build());
            }
            conversation.set(i, MessageFactory.createAssistantMessage(kept, msg.getChatUsage()));
        }
        // 清理因上一步产生的孤儿tool消息（其tool_call_id无对应的assistant工具调用）
        for (int i = 0; i < conversation.size(); i++) {
            AgentMessage msg = conversation.get(i);
            if (msg.getRole() != AgentMessageRole.TOOL || msg.getContent() == null) {
                continue;
            }
            boolean hasPrecedingAssistantCall = false;
            for (int k = i - 1; k >= 0; k--) {
                AgentMessage prev = conversation.get(k);
                if (prev.getRole() == AgentMessageRole.ASSISTANT) {
                    hasPrecedingAssistantCall = prev.getContent() != null
                            && prev.getContent().stream().anyMatch(AgentToolUseBlock.class::isInstance);
                    break;
                }
            }
            if (!hasPrecedingAssistantCall) {
                conversation.remove(i);
                i--;
            }
        }
    }

    /**
     * 按工具名匹配审批结果（审批协调器未注入时的兼容路径）
     * @param call
     * @param confirmResults
     * @return
     */
    private ConfirmResult matchConfirmByName(AgentToolUseBlock call, List<ConfirmResult> confirmResults) {
        return confirmResults.stream()
                .filter(r -> call.getToolName().equals(r.getToolName()))
                .findFirst()
                .orElse(null);
    }

    /**
     * 纯响应式ReAct递归循环
     * <p>
     * 通过父类withModelCallEvents包裹推理阶段（onReasoning洋葱模型、MODEL_CALL事件与预算检查），
     * 合并响应后决定返回结果或进入执行阶段。
     * </p>
     * @param messages
     * @param context
     * @param iteration
     * @return
     */
    private Flux<AgentEvent> reactLoopFlux(List<AgentMessage> messages, EngineContext context, int iteration) {
        // 同步迭代状态到EngineContext，保证外部观察者能获取真实迭代次数
        context.setCurrentIter(iteration);
        // 每轮迭代前检查中断
        Flux<AgentEvent> interrupted = checkInterrupted(context);
        if (interrupted != null) {
            return interrupted;
        }
        // 保存检查点（非首轮迭代时）
        saveIterationCheckpoint(context, messages, iteration);
        // 上下文窗口超限时按截断策略缩减历史
        truncateContextIfNeeded(messages, context);

        String userQuery = extractUserQuery(messages);
        List<Map<String, Object>> toolSchemas = context.getAllToolSchemas(userQuery);

        Flux<AgentEvent> iterationFlux = withModelCallEvents(context, messages, toolSchemas, iteration,
                merged -> handleModelResponse(merged, messages, context, iteration));
        // 应用单轮迭代超时
        return withIterationTimeout(context, iterationFlux);
    }

    /**
     * 处理模型响应，决定返回最终结果或进入工具执行阶段
     * @param response
     * @param messages
     * @param context
     * @param iteration
     * @return
     */
    private Flux<AgentEvent> handleModelResponse(AgentChatResponse response, List<AgentMessage> messages,
                                                  EngineContext context, int iteration) {
        AgentMessage assistantMessage = responseParser(context).parseToMessage(response);
        // onMessage 同步钩子
        assistantMessage = middlewareChain(context).applyOnMessage(assistantMessage, context.getRuntimeContext());
        messages.add(assistantMessage);

        List<AgentToolUseBlock> toolCalls = responseParser(context).extractToolCalls(response);

        if (toolCalls.isEmpty() || iteration >= context.getMaxIters()) {
            // 结构化输出校验：配置了json_schema且无工具调用时，校验输出格式
            if (toolCalls.isEmpty()) {
                String validationError = structuredOutputValidationError(context, assistantMessage);
                if (validationError != null) {
                    return handleStructuredOutputFailure(validationError, messages, context, iteration,
                            () -> reactLoopFlux(messages, context, iteration + 1));
                }
            }
            return Flux.just(new AgentResultEvent(assistantMessage, context.getAccumulatedUsage(),
                    currentCostUsd(context)), AgentEvent.completed());
        }

        // 检查是否需要人工确认
        if (approvalEnabled(context)) {
            DurableExecutionTracker tracker = durableTracker(context);
            String runId = tracker != null && tracker.isEnabled()
                    ? tracker.ensureRunning(context.getRuntimeContext(), context.getAgentName())
                    : null;
            ApprovalCoordinator.TriageResult triage = triageToolCalls(context, toolCalls, runId);
            messages.addAll(triage.getDeniedMessages());
            if (!triage.getAskCalls().isEmpty()) {
                // 将待确认的工具调用、当前对话快照与迭代轮次存入上下文，供 resume 恢复
                context.getRuntimeContext().put(ATTR_PENDING_TOOL_CALLS, triage.getAskCalls());
                context.getRuntimeContext().put(ATTR_PENDING_CONVERSATION, new ArrayList<>(messages));
                context.getRuntimeContext().put(ATTR_PENDING_ITERATION, iteration);
                // 持久执行：等待审批断点落运行状态、审批记录与完整检查点
                if (tracker != null && tracker.isEnabled()) {
                    tracker.markWaitingApproval(context.getRuntimeContext(), runId,
                            triage.getAskCalls(), messages, iteration);
                }
                // 发射需要人工确认事件，暂停循环等待 resume
                return Flux.just(new RequireUserConfirmEvent(triage.getAskCalls()));
            }
            // 仅保留允许的工具调用
            toolCalls = triage.getAllowedCalls();
            if (toolCalls.isEmpty()) {
                // 所有工具被拒绝，递归进入下一轮推理
                return reactLoopFlux(messages, context, iteration + 1);
            }
        }

        // 持久执行：工具执行前断点落检查点（恢复时配合幂等台账去重副作用）
        DurableExecutionTracker tracker = durableTracker(context);
        if (tracker != null && tracker.isEnabled()) {
            String runId = tracker.ensureRunning(context.getRuntimeContext(), context.getAgentName());
            tracker.saveCheckpoint(context.getRuntimeContext(), runId, iteration,
                    messages, toolCalls, null);
        }

        // 通过 onActing 洋葱模型包裹执行阶段，递归进入下一轮推理
        return Flux.just(AgentEvent.of(AgentEventType.TOOL_CALL_START, toolCalls))
                .concatWith(middlewareChain(context).applyOnActing(context.getRuntimeContext(), toolCalls,
                    calls -> toolExecutor(context).executeTools(calls, context.getRuntimeContext(), context)
                            .flatMapMany(toolResults -> {
                                messages.addAll(toolResults);
                                // 澄清请求检测：ask_user工具结果带专用标记，暂停循环等待用户注入答案
                                List<AgentToolResultBlock> clarificationResults =
                                        extractClarificationResults(toolResults);
                                if (!clarificationResults.isEmpty()) {
                                    return handleClarificationRequest(clarificationResults, toolResults,
                                            messages, context, iteration);
                                }
                                // 持久执行：记录已完成工具调用并落工具后断点检查点
                                if (tracker != null && tracker.isEnabled()) {
                                    tracker.recordCompletedToolCalls(context.getRuntimeContext(), toolResults);
                                    String runId = (String) context.getRuntimeContext()
                                            .get(DurableExecutionTracker.ATTR_RUN_ID);
                                    tracker.saveCheckpoint(context.getRuntimeContext(), runId,
                                            iteration, messages, List.of(), null);
                                }
                                // 连续工具失败检查：成功则重置，失败则累加，超过阈值中止Agent
                                if (isConsecutiveFailureExceeded(toolResults, context)) {
                                    return Flux.just(AgentEvent.of(AgentEventType.ERROR,
                                            new RuntimeException("连续工具失败超过阈值: "
                                                    + context.getMaxConsecutiveToolFailures())),
                                            AgentEvent.completed());
                                }
                                return Flux.just(AgentEvent.of(AgentEventType.TOOL_CALL_END, toolResults))
                                        .concatWith(reactLoopFlux(messages, context, iteration + 1));
                            })
                ))
                .onErrorResume(error -> {
                    applyOnError(context, error, "acting");
                    return Flux.just(AgentEvent.of(AgentEventType.ERROR, error), AgentEvent.completed());
                });
    }

    /**
     * 从最近检查点恢复执行
     * <p>
     * 优先走持久执行检查点存储（完整状态：消息+迭代+已完成工具ID，runId跨重启不变），
     * 未配置持久化时回退内存CheckpointManager（仅消息+迭代）。
     * </p>
     * @param context
     * @return
     */
    @Override
    public Flux<AgentEvent> restoreFromCheckpoint(EngineContext context) {
        if (context.getRuntimeContext() == null) {
            return Flux.just(AgentEvent.of(AgentEventType.ERROR,
                    new IllegalStateException("运行上下文缺失，无法恢复")), AgentEvent.completed());
        }
        // 持久执行检查点优先：恢复runId、已完成工具ID与完整消息快照
        var latest = loadLatestDurableCheckpoint(context);
        if (latest.isPresent()) {
            AgentCheckpoint checkpoint = latest.get();
            List<AgentMessage> messages = repairDanglingToolCalls(
                    new ArrayList<>(checkpoint.getMessages()));
            return finishPipeline(reactLoopFlux(messages, context, checkpoint.getIteration()), context);
        }
        CheckpointManager manager = checkpointManager(context);
        if (manager == null) {
            return Flux.just(AgentEvent.of(AgentEventType.ERROR,
                    new IllegalStateException("检查点管理器未配置，无法恢复")), AgentEvent.completed());
        }
        String sessionId = context.getRuntimeContext().getSessionId();
        if (sessionId == null || !manager.hasCheckpoint(sessionId)) {
            return Flux.just(AgentEvent.of(AgentEventType.ERROR,
                    new IllegalStateException("无检查点可恢复: sessionId=" + sessionId)), AgentEvent.completed());
        }
        List<AgentMessage> restoredMessages = repairDanglingToolCalls(
                new ArrayList<>(manager.restore(sessionId)));
        int restoredIteration = manager.restoreIteration(sessionId);
        return finishPipeline(reactLoopFlux(restoredMessages, context, restoredIteration), context);
    }

    /**
     * 修复恢复对话中的悬空tool_calls：为缺失tool结果的工具调用补齐中断占位结果
     * <p>
     * 审批等待或工具执行中途落盘的检查点，其消息末尾可能存在assistant(tool_calls)
     * 无对应tool消息的形态，直接续跑会违反OpenAI协议返回400；占位结果让模型感知
     * 执行被中断并自行决定是否重试。
     * </p>
     * @param messages 恢复出的消息列表
     * @return
     */
    private List<AgentMessage> repairDanglingToolCalls(List<AgentMessage> messages) {
        Set<String> answeredIds = new HashSet<>();
        for (AgentMessage msg : messages) {
            if (msg.getContent() == null) {
                continue;
            }
            for (AgentContentBlock block : msg.getContent()) {
                if (block instanceof AgentToolResultBlock result && result.getToolUseId() != null) {
                    answeredIds.add(result.getToolUseId());
                }
            }
        }
        List<AgentMessage> repaired = new ArrayList<>(messages.size());
        for (AgentMessage msg : messages) {
            repaired.add(msg);
            if (msg.getRole() != AgentMessageRole.ASSISTANT || msg.getContent() == null) {
                continue;
            }
            List<AgentToolUseBlock> dangling = msg.getContent().stream()
                    .filter(AgentToolUseBlock.class::isInstance)
                    .map(AgentToolUseBlock.class::cast)
                    .filter(use -> use.getToolUseId() != null && !answeredIds.contains(use.getToolUseId()))
                    .toList();
            for (AgentToolUseBlock use : dangling) {
                repaired.add(MessageFactory.createToolMessage(AgentToolResultBlock.error(use.getToolUseId(),
                        "工具执行因运行中断未完成，如需该结果请重新调用")));
            }
        }
        return repaired;
    }
}
