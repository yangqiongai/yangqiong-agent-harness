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

import com.yangqiong.agent.harness.core.HarnessAgentRuntimeBuilder.TimeoutMode;
import com.yangqiong.agent.harness.config.CostBudgetPolicy;
import com.yangqiong.agent.harness.config.TokenBudgetPolicy;
import com.yangqiong.agent.harness.event.EventBus;
import com.yangqiong.agent.harness.model.ModelCaller;
import com.yangqiong.agent.harness.model.ModelPricingRegistry;
import com.yangqiong.agent.harness.model.ModelResponseParser;
import com.yangqiong.agent.harness.permission.PermissionEngine;
import com.yangqiong.agent.harness.tool.HarnessToolkit;
import com.yangqiong.agent.harness.tool.ToolExecutor;
import com.yangqiong.agent.harness.tool.ToolFilter;
import com.yangqiong.agent.harness.core.HarnessAgentRuntimeBuilder;
import com.yangqiong.agent.harness.core.model.AgentGenerateOptions;

/**
 * 引擎配置
 * @author yangqiong
 */
public class EngineConfig {

    /**
     * Agent名称
     */
    private final String agentName;

    /**
     * 系统提示词
     */
    private final String systemPrompt;

    /**
     * 最大迭代次数
     */
    private final int maxIters;

    /**
     * 工具箱
     */
    private final HarnessToolkit toolkit;

    /**
     * 模型生成选项
     */
    private final AgentGenerateOptions generateOptions;

    /**
     * 最大并行工具调用数
     */
    private final int maxConcurrentToolCalls;

    /**
     * 单次Agent执行总超时
     */
    private final Duration timeout;

    /**
     * 单次Agent执行总超时模式，默认硬墙钟到点必断
     */
    private TimeoutMode timeoutMode = TimeoutMode.WALL_CLOCK;

    /**
     * 单轮推理与工具执行超时
     */
    private final Duration iterationTimeout;

    /**
     * 工具失败处理策略
     */
    private final HarnessAgentRuntimeBuilder.ToolFailureStrategy toolFailureStrategy;

    /**
     * 单个工具最大重试次数
     */
    private final int maxToolRetries;

    /**
     * 上下文窗口Token上限
     */
    private final int maxContextTokens;

    /**
     * 历史截断策略
     */
    private final HarnessAgentRuntimeBuilder.TruncationStrategy historyTruncationStrategy;

    /**
     * 单次工具调用超时
     */
    private final Duration toolCallTimeout;

    /**
     * 工具结果最大字符数
     */
    private final int maxToolResultChars;

    /**
     * 最大连续工具失败次数
     */
    private final int maxConsecutiveToolFailures;

    /**
     * 工具筛选器（可选，为空时不筛选）
     */
    private ToolFilter toolFilter;

    /**
     * 模型调用器（可选，构建时注入，供自定义执行循环直接驱动模型）
     */
    private ModelCaller modelCaller;

    /**
     * 工具执行器（可选，构建时注入，供自定义执行循环直接执行工具）
     */
    private ToolExecutor toolExecutor;

    /**
     * 模型响应解析器（可选，构建时注入，供自定义执行循环解析模型响应）
     */
    private ModelResponseParser responseParser;

    /**
     * 中间件链（可选，构建时注入，供执行引擎复用中间件管线）
     */
    private MiddlewareChain middlewareChain;

    /**
     * 审批协调器（可选，构建时注入，为空时执行循环不做人工确认triage）
     */
    private ApprovalCoordinator approvalCoordinator;

    /**
     * 权限引擎（可选，构建时注入，审批协调器缺省时的兼容判定路径）
     */
    private PermissionEngine permissionEngine;

    /**
     * Token预算策略（可选，构建时注入，为空时Token预算告警与硬控不生效）
     */
    private TokenBudgetPolicy tokenBudgetPolicy;

    /**
     * 成本预算策略（可选，构建时注入，为空时成本预算告警与硬控不生效）
     */
    private CostBudgetPolicy costBudgetPolicy;

    /**
     * 事件监听器注册中心（可选，构建时注入，为空时不广播事件）
     */
    private EventBus eventBus;

    /**
     * 模型定价注册表（可选，构建时注入，为空时按默认价0计价）
     */
    private ModelPricingRegistry modelPricingRegistry;

    /**
     * 模型编码（可选，构建时注入，用于成本计价的定价查询，为空时回退默认价）
     */
    private String modelCode;

    /**
     * 持久执行状态跟踪器（可选，构建时注入，为空时运行不落RunRecord）
     */
    private DurableExecutionTracker durableTracker;

    public EngineConfig(String agentName, String systemPrompt, int maxIters, HarnessToolkit toolkit,
                        AgentGenerateOptions generateOptions, int maxConcurrentToolCalls) {
        this(agentName, systemPrompt, maxIters, toolkit, generateOptions, maxConcurrentToolCalls,
                null, null, null, 0, 0, null);
    }

    public EngineConfig(String agentName, String systemPrompt, int maxIters, HarnessToolkit toolkit,
                        AgentGenerateOptions generateOptions, int maxConcurrentToolCalls,
                        Duration timeout, Duration iterationTimeout,
                        HarnessAgentRuntimeBuilder.ToolFailureStrategy toolFailureStrategy,
                        int maxToolRetries,
                        int maxContextTokens,
                        HarnessAgentRuntimeBuilder.TruncationStrategy historyTruncationStrategy) {
        this(agentName, systemPrompt, maxIters, toolkit, generateOptions, maxConcurrentToolCalls,
                timeout, iterationTimeout, toolFailureStrategy, maxToolRetries,
                maxContextTokens, historyTruncationStrategy, null, 0, 0);
    }

    /**
     * 全参数构造器，含工具超时、结果大小限制与连续失败限制
     * @param agentName
     * @param systemPrompt
     * @param maxIters
     * @param toolkit
     * @param generateOptions
     * @param maxConcurrentToolCalls
     * @param timeout
     * @param iterationTimeout
     * @param toolFailureStrategy
     * @param maxToolRetries
     * @param maxContextTokens
     * @param historyTruncationStrategy
     * @param toolCallTimeout
     * @param maxToolResultChars
     * @param maxConsecutiveToolFailures
     */
    public EngineConfig(String agentName, String systemPrompt, int maxIters, HarnessToolkit toolkit,
                        AgentGenerateOptions generateOptions, int maxConcurrentToolCalls,
                        Duration timeout, Duration iterationTimeout,
                        HarnessAgentRuntimeBuilder.ToolFailureStrategy toolFailureStrategy,
                        int maxToolRetries,
                        int maxContextTokens,
                        HarnessAgentRuntimeBuilder.TruncationStrategy historyTruncationStrategy,
                        Duration toolCallTimeout, int maxToolResultChars,
                        int maxConsecutiveToolFailures) {
        this.agentName = agentName;
        this.systemPrompt = systemPrompt;
        this.maxIters = maxIters;
        this.toolkit = toolkit;
        this.generateOptions = generateOptions;
        this.maxConcurrentToolCalls = maxConcurrentToolCalls;
        this.timeout = timeout;
        this.iterationTimeout = iterationTimeout;
        this.toolFailureStrategy = toolFailureStrategy;
        this.maxToolRetries = maxToolRetries;
        this.maxContextTokens = maxContextTokens;
        this.historyTruncationStrategy = historyTruncationStrategy;
        this.toolCallTimeout = toolCallTimeout;
        this.maxToolResultChars = maxToolResultChars;
        this.maxConsecutiveToolFailures = maxConsecutiveToolFailures;
    }

    public String getAgentName() {
        return agentName;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public int getMaxIters() {
        return maxIters;
    }

    public HarnessToolkit getToolkit() {
        return toolkit;
    }

    public AgentGenerateOptions getGenerateOptions() {
        return generateOptions;
    }

    public int getMaxConcurrentToolCalls() {
        return maxConcurrentToolCalls;
    }

    public Duration getTimeout() {
        return timeout;
    }

    /**
     * 获取总超时模式
     * @return
     */
    public TimeoutMode getTimeoutMode() {
        return timeoutMode;
    }

    /**
     * 设置总超时模式
     * @param timeoutMode
     */
    public void setTimeoutMode(TimeoutMode timeoutMode) {
        if (timeoutMode != null) {
            this.timeoutMode = timeoutMode;
        }
    }

    public Duration getIterationTimeout() {
        return iterationTimeout;
    }

    public HarnessAgentRuntimeBuilder.ToolFailureStrategy getToolFailureStrategy() {
        return toolFailureStrategy;
    }

    public int getMaxToolRetries() {
        return maxToolRetries;
    }

    /**
     * 获取上下文窗口Token上限
     * @return
     */
    public int getMaxContextTokens() {
        return maxContextTokens;
    }

    /**
     * 获取历史截断策略
     * @return
     */
    public HarnessAgentRuntimeBuilder.TruncationStrategy getHistoryTruncationStrategy() {
        return historyTruncationStrategy;
    }

    /**
     * 获取单次工具调用超时
     * @return
     */
    public Duration getToolCallTimeout() {
        return toolCallTimeout;
    }

    /**
     * 获取工具结果最大字符数
     * @return
     */
    public int getMaxToolResultChars() {
        return maxToolResultChars;
    }

    /**
     * 获取最大连续工具失败次数
     * @return
     */
    public int getMaxConsecutiveToolFailures() {
        return maxConsecutiveToolFailures;
    }

    /**
     * 设置工具筛选器
     * @param toolFilter
     */
    public void setToolFilter(ToolFilter toolFilter) {
        this.toolFilter = toolFilter;
    }

    /**
     * 获取模型调用器
     * @return
     */
    public ModelCaller getModelCaller() {
        return modelCaller;
    }

    /**
     * 注入模型调用器
     * @param modelCaller
     * @return
     */
    public EngineConfig modelCaller(ModelCaller modelCaller) {
        this.modelCaller = modelCaller;
        return this;
    }

    /**
     * 获取工具执行器
     * @return
     */
    public ToolExecutor getToolExecutor() {
        return toolExecutor;
    }

    /**
     * 注入工具执行器
     * @param toolExecutor
     * @return
     */
    public EngineConfig toolExecutor(ToolExecutor toolExecutor) {
        this.toolExecutor = toolExecutor;
        return this;
    }

    /**
     * 获取模型响应解析器
     * @return
     */
    public ModelResponseParser getResponseParser() {
        return responseParser;
    }

    /**
     * 注入模型响应解析器
     * @param responseParser
     * @return
     */
    public EngineConfig responseParser(ModelResponseParser responseParser) {
        this.responseParser = responseParser;
        return this;
    }

    /**
     * 获取中间件链
     * @return
     */
    public MiddlewareChain getMiddlewareChain() {
        return middlewareChain;
    }

    /**
     * 注入中间件链
     * @param middlewareChain
     * @return
     */
    public EngineConfig middlewareChain(MiddlewareChain middlewareChain) {
        this.middlewareChain = middlewareChain;
        return this;
    }

    /**
     * 注入审批协调器
     * @param approvalCoordinator
     * @return
     */
    public EngineConfig approvalCoordinator(ApprovalCoordinator approvalCoordinator) {
        this.approvalCoordinator = approvalCoordinator;
        return this;
    }

    /**
     * 获取审批协调器
     * @return
     */
    public ApprovalCoordinator getApprovalCoordinator() {
        return approvalCoordinator;
    }

    /**
     * 注入权限引擎
     * @param permissionEngine
     * @return
     */
    public EngineConfig permissionEngine(PermissionEngine permissionEngine) {
        this.permissionEngine = permissionEngine;
        return this;
    }

    /**
     * 获取权限引擎
     * @return
     */
    public PermissionEngine getPermissionEngine() {
        return permissionEngine;
    }

    /**
     * 获取Token预算策略
     * @return
     */
    public TokenBudgetPolicy getTokenBudgetPolicy() {
        return tokenBudgetPolicy;
    }

    /**
     * 注入Token预算策略
     * @param tokenBudgetPolicy
     * @return
     */
    public EngineConfig tokenBudgetPolicy(TokenBudgetPolicy tokenBudgetPolicy) {
        this.tokenBudgetPolicy = tokenBudgetPolicy;
        return this;
    }

    /**
     * 获取成本预算策略
     * @return
     */
    public CostBudgetPolicy getCostBudgetPolicy() {
        return costBudgetPolicy;
    }

    /**
     * 注入成本预算策略
     * @param costBudgetPolicy
     * @return
     */
    public EngineConfig costBudgetPolicy(CostBudgetPolicy costBudgetPolicy) {
        this.costBudgetPolicy = costBudgetPolicy;
        return this;
    }

    /**
     * 获取事件监听器注册中心
     * @return
     */
    public EventBus getEventBus() {
        return eventBus;
    }

    /**
     * 注入事件监听器注册中心
     * @param eventBus
     * @return
     */
    public EngineConfig eventBus(EventBus eventBus) {
        this.eventBus = eventBus;
        return this;
    }

    /**
     * 获取模型定价注册表
     * @return
     */
    public ModelPricingRegistry getModelPricingRegistry() {
        return modelPricingRegistry;
    }

    /**
     * 注入模型定价注册表
     * @param modelPricingRegistry
     * @return
     */
    public EngineConfig modelPricingRegistry(ModelPricingRegistry modelPricingRegistry) {
        this.modelPricingRegistry = modelPricingRegistry;
        return this;
    }

    /**
     * 获取模型编码
     * @return
     */
    public String getModelCode() {
        return modelCode;
    }

    /**
     * 注入模型编码
     * @param modelCode
     * @return
     */
    public EngineConfig modelCode(String modelCode) {
        this.modelCode = modelCode;
        return this;
    }

    /**
     * 获取持久执行状态跟踪器
     * @return
     */
    public DurableExecutionTracker getDurableTracker() {
        return durableTracker;
    }

    /**
     * 注入持久执行状态跟踪器
     * @param durableTracker
     * @return
     */
    public EngineConfig durableTracker(DurableExecutionTracker durableTracker) {
        this.durableTracker = durableTracker;
        return this;
    }

    /**
     * 基于运行时上下文构建引擎上下文，注入Agent名称、系统提示词与生成选项
     * @param runtimeContext
     * @return
     */
    public EngineContext toEngineContext(AgentRuntimeContext runtimeContext) {
        EngineContext ctx = new EngineContext(agentName, systemPrompt, runtimeContext, toolkit, maxIters, generateOptions,
                timeout, iterationTimeout, maxConcurrentToolCalls, toolFailureStrategy, maxToolRetries,
                maxContextTokens, historyTruncationStrategy, toolCallTimeout, maxToolResultChars,
                maxConsecutiveToolFailures, modelCaller, toolExecutor, responseParser);
        ctx.setTimeoutMode(timeoutMode);
        if (toolFilter != null) {
            ctx.setToolFilter(toolFilter);
        }
        ctx.setMiddlewareChain(middlewareChain);
        ctx.setApprovalCoordinator(approvalCoordinator);
        ctx.setPermissionEngine(permissionEngine);
        ctx.setTokenBudgetPolicy(tokenBudgetPolicy);
        ctx.setCostBudgetPolicy(costBudgetPolicy);
        ctx.setEventBus(eventBus);
        ctx.setModelPricingRegistry(modelPricingRegistry);
        ctx.setModelCode(modelCode);
        ctx.setDurableTracker(durableTracker);
        return ctx;
    }
}
