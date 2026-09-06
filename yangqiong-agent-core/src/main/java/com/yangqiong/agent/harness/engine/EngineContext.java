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
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import com.yangqiong.agent.harness.config.CostBudgetPolicy;
import com.yangqiong.agent.harness.config.TokenBudgetPolicy;
import com.yangqiong.agent.harness.event.EventBus;
import com.yangqiong.agent.harness.model.ModelCaller;
import com.yangqiong.agent.harness.model.ModelPricingRegistry;
import com.yangqiong.agent.harness.model.ModelResponseParser;
import com.yangqiong.agent.harness.permission.PermissionEngine;
import com.yangqiong.agent.harness.planmode.PlanModeMiddleware;
import com.yangqiong.agent.harness.tool.HarnessToolkit;
import com.yangqiong.agent.harness.tool.ToolExecutor;
import com.yangqiong.agent.harness.tool.ToolFilter;
import com.yangqiong.agent.harness.tool.ToolLoadingState;
import com.yangqiong.agent.harness.tool.ToolSchemaBuilder;
import com.yangqiong.agent.harness.core.HarnessAgentRuntimeBuilder;
import com.yangqiong.agent.harness.core.message.AgentChatUsage;
import com.yangqiong.agent.harness.core.message.AgentMessage;
import com.yangqiong.agent.harness.core.model.AgentGenerateOptions;
import com.yangqiong.agent.harness.core.tool.AgentTool;

/**
 * 引擎上下文
 * @author yangqiong
 */
public class EngineContext {

    /**
     * Agent名称
     */
    private final String agentName;

    /**
     * 系统提示词
     */
    private final String systemPrompt;

    /**
     * 运行时上下文
     */
    private final AgentRuntimeContext runtimeContext;

    /**
     * 静态工具箱
     */
    private final HarnessToolkit staticToolkit;

    /**
     * 动态工具列表
     */
    private final List<AgentTool> dynamicTools;

    /**
     * 工具筛选器（可选，为空时不筛选）
     */
    private volatile ToolFilter toolFilter;

    /**
     * 对话历史
     */
    private final List<AgentMessage> conversation;

    /**
     * 最大迭代次数
     */
    private final int maxIters;

    /**
     * 模型生成选项
     */
    private final AgentGenerateOptions generateOptions;

    /**
     * 单次执行总超时
     */
    private final Duration timeout;

    /**
     * 单次执行总超时模式，默认硬墙钟到点必断
     */
    private volatile HarnessAgentRuntimeBuilder.TimeoutMode timeoutMode =
            HarnessAgentRuntimeBuilder.TimeoutMode.WALL_CLOCK;

    /**
     * 单轮迭代超时
     */
    private final Duration iterationTimeout;

    /**
     * 最大并行工具调用数
     */
    private final int maxConcurrentToolCalls;

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
     * 连续工具失败计数器
     */
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);

    /**
     * 当前迭代次数（原子操作保证线程安全）
     */
    private final AtomicInteger currentIter = new AtomicInteger(0);

    /**
     * 累计Token用量
     */
    private volatile AgentChatUsage accumulatedUsage;

    /**
     * 模型调用器（可空，供自定义执行循环直接驱动模型）
     */
    private final ModelCaller modelCaller;

    /**
     * 工具执行器（可空，供自定义执行循环直接执行工具）
     */
    private final ToolExecutor toolExecutor;

    /**
     * 模型响应解析器（可空，供自定义执行循环解析模型响应）
     */
    private final ModelResponseParser responseParser;

    /**
     * 中间件链（可空，供执行引擎复用中间件管线）
     */
    private volatile MiddlewareChain middlewareChain;

    /**
     * 审批协调器（可空，为空时执行循环不做人工确认triage）
     */
    private volatile ApprovalCoordinator approvalCoordinator;

    /**
     * 权限引擎（可空，审批协调器缺省时的兼容判定路径）
     */
    private volatile PermissionEngine permissionEngine;

    /**
     * Token预算策略（可空，为空时Token预算告警与硬控不生效）
     */
    private volatile TokenBudgetPolicy tokenBudgetPolicy;

    /**
     * 成本预算策略（可空，为空时成本预算告警与硬控不生效）
     */
    private volatile CostBudgetPolicy costBudgetPolicy;

    /**
     * 事件监听器注册中心（可空，为空时不广播事件）
     */
    private volatile EventBus eventBus;

    /**
     * 模型定价注册表（可空，为空时按默认价0计价）
     */
    private volatile ModelPricingRegistry modelPricingRegistry;

    /**
     * 模型编码（可空，用于成本计价的定价查询，为空时回退默认价）
     */
    private volatile String modelCode;

    /**
     * 持久执行状态跟踪器（可空，为空时运行不落RunRecord）
     */
    private volatile DurableExecutionTracker durableTracker;

    /**
     * 工具渐进加载状态（可空，为空时全量下发schema；非空且progressive时按常驻/已转正裁剪）
     */
    private volatile ToolLoadingState toolLoadingState;

    public EngineContext(String agentName, String systemPrompt, AgentRuntimeContext runtimeContext,
                         HarnessToolkit toolkit, int maxIters, AgentGenerateOptions generateOptions) {
        this(agentName, systemPrompt, runtimeContext, toolkit, maxIters, generateOptions,
                null, null, 1, null, 0, 0, null);
    }

    public EngineContext(String agentName, String systemPrompt, AgentRuntimeContext runtimeContext,
                         HarnessToolkit toolkit, int maxIters, AgentGenerateOptions generateOptions,
                         Duration timeout, Duration iterationTimeout, int maxConcurrentToolCalls,
                         HarnessAgentRuntimeBuilder.ToolFailureStrategy toolFailureStrategy,
                         int maxToolRetries,
                         int maxContextTokens,
                         HarnessAgentRuntimeBuilder.TruncationStrategy historyTruncationStrategy) {
        this(agentName, systemPrompt, runtimeContext, toolkit, maxIters, generateOptions,
                timeout, iterationTimeout, maxConcurrentToolCalls, toolFailureStrategy, maxToolRetries,
                maxContextTokens, historyTruncationStrategy, null, 0, 0);
    }

    /**
     * 全参数构造器，含工具超时、结果大小限制与连续失败限制
     * @param agentName
     * @param systemPrompt
     * @param runtimeContext
     * @param toolkit
     * @param maxIters
     * @param generateOptions
     * @param timeout
     * @param iterationTimeout
     * @param maxConcurrentToolCalls
     * @param toolFailureStrategy
     * @param maxToolRetries
     * @param maxContextTokens
     * @param historyTruncationStrategy
     * @param toolCallTimeout 单次工具调用超时，null或零表示不限制
     * @param maxToolResultChars 工具结果最大字符数，0表示不限制
     * @param maxConsecutiveToolFailures 最大连续工具失败次数，0表示不限制
     */
    public EngineContext(String agentName, String systemPrompt, AgentRuntimeContext runtimeContext,
                         HarnessToolkit toolkit, int maxIters, AgentGenerateOptions generateOptions,
                         Duration timeout, Duration iterationTimeout, int maxConcurrentToolCalls,
                         HarnessAgentRuntimeBuilder.ToolFailureStrategy toolFailureStrategy,
                         int maxToolRetries,
                         int maxContextTokens,
                         HarnessAgentRuntimeBuilder.TruncationStrategy historyTruncationStrategy,
                         Duration toolCallTimeout, int maxToolResultChars,
                         int maxConsecutiveToolFailures) {
        this(agentName, systemPrompt, runtimeContext, toolkit, maxIters, generateOptions,
                timeout, iterationTimeout, maxConcurrentToolCalls, toolFailureStrategy, maxToolRetries,
                maxContextTokens, historyTruncationStrategy, toolCallTimeout, maxToolResultChars,
                maxConsecutiveToolFailures, null, null, null);
    }

    /**
     * 全参数构造器，额外携带模型调用器、工具执行器与模型响应解析器，供自定义执行循环直接使用
     * @param agentName
     * @param systemPrompt
     * @param runtimeContext
     * @param toolkit
     * @param maxIters
     * @param generateOptions
     * @param timeout
     * @param iterationTimeout
     * @param maxConcurrentToolCalls
     * @param toolFailureStrategy
     * @param maxToolRetries
     * @param maxContextTokens
     * @param historyTruncationStrategy
     * @param toolCallTimeout 单次工具调用超时，null或零表示不限制
     * @param maxToolResultChars 工具结果最大字符数，0表示不限制
     * @param maxConsecutiveToolFailures 最大连续工具失败次数，0表示不限制
     * @param modelCaller 模型调用器，null表示未暴露
     * @param toolExecutor 工具执行器，null表示未暴露
     * @param responseParser 模型响应解析器，null表示未暴露
     */
    public EngineContext(String agentName, String systemPrompt, AgentRuntimeContext runtimeContext,
                         HarnessToolkit toolkit, int maxIters, AgentGenerateOptions generateOptions,
                         Duration timeout, Duration iterationTimeout, int maxConcurrentToolCalls,
                         HarnessAgentRuntimeBuilder.ToolFailureStrategy toolFailureStrategy,
                         int maxToolRetries,
                         int maxContextTokens,
                         HarnessAgentRuntimeBuilder.TruncationStrategy historyTruncationStrategy,
                         Duration toolCallTimeout, int maxToolResultChars,
                         int maxConsecutiveToolFailures,
                         ModelCaller modelCaller, ToolExecutor toolExecutor,
                         ModelResponseParser responseParser) {
        this.agentName = agentName;
        this.systemPrompt = systemPrompt;
        this.runtimeContext = runtimeContext;
        this.staticToolkit = toolkit;
        this.maxIters = maxIters;
        this.generateOptions = generateOptions;
        this.timeout = timeout;
        this.iterationTimeout = iterationTimeout;
        this.maxConcurrentToolCalls = maxConcurrentToolCalls;
        this.toolFailureStrategy = toolFailureStrategy;
        this.maxToolRetries = maxToolRetries;
        this.maxContextTokens = maxContextTokens;
        this.historyTruncationStrategy = historyTruncationStrategy;
        this.toolCallTimeout = toolCallTimeout;
        this.maxToolResultChars = maxToolResultChars;
        this.maxConsecutiveToolFailures = maxConsecutiveToolFailures;
        this.modelCaller = modelCaller;
        this.toolExecutor = toolExecutor;
        this.responseParser = responseParser;
        this.dynamicTools = new CopyOnWriteArrayList<>();
        this.conversation = new CopyOnWriteArrayList<>();
    }

    /**
     * 注册动态工具
     * @param tools
     */
    public void registerDynamicTools(List<AgentTool> tools) {
        if (tools != null) {
            dynamicTools.addAll(tools);
        }
    }

    /**
     * 合并静态与动态工具的Schema列表
     * @return
     */
    public List<Map<String, Object>> getAllToolSchemas() {
        return getAllToolSchemas(null);
    }

    /**
     * 合并静态与动态工具的Schema列表，并根据用户查询筛选
     * @param userQuery 用户当前查询，为空时不筛选
     * @return
     */
    public List<Map<String, Object>> getAllToolSchemas(String userQuery) {
        List<Map<String, Object>> schemas = new ArrayList<>();
        if (staticToolkit != null) {
            if (toolLoadingState == null || !toolLoadingState.isProgressive()) {
                schemas.addAll(staticToolkit.getToolSchemas());
            } else {
                // 渐进模式：静态工具箱仅保留常驻、已转正与元工具的schema
                for (Map.Entry<String, Map<String, Object>> e : staticToolkit.getToolSchemasMap().entrySet()) {
                    if (toolLoadingState.isSchemaVisible(e.getKey())) {
                        schemas.add(e.getValue());
                    }
                }
            }
        }
        schemas.addAll(ToolSchemaBuilder.buildAll(dynamicTools));
        List<AgentTool> planTools = getPlanModeTools();
        schemas.addAll(ToolSchemaBuilder.buildAll(planTools));
        // 工具筛选
        if (toolFilter != null && userQuery != null && !userQuery.isBlank()) {
            schemas = toolFilter.filter(schemas, userQuery);
        }
        return schemas;
    }

    /**
     * 按需启用工具（PROGRESSIVE模式，转正后下一轮schema生效）
     * @param toolName 工具名
     * @return 转正的工具，未找到时返回null
     */
    public AgentTool activateTool(String toolName) {
        if (toolName == null || staticToolkit == null) {
            return null;
        }
        AgentTool tool = staticToolkit.find(toolName);
        if (tool == null) {
            return null;
        }
        if (toolLoadingState != null) {
            toolLoadingState.activate(toolName);
        }
        return tool;
    }

    /**
     * 注入工具渐进加载状态
     * @param toolLoadingState
     */
    public void setToolLoadingState(ToolLoadingState toolLoadingState) {
        this.toolLoadingState = toolLoadingState;
    }

    /**
     * 获取工具渐进加载状态
     * @return
     */
    public ToolLoadingState getToolLoadingState() {
        return toolLoadingState;
    }

    /**
     * 设置工具筛选器
     * @param toolFilter
     */
    public void setToolFilter(ToolFilter toolFilter) {
        this.toolFilter = toolFilter;
    }

    /**
     * 按名称查找工具，先查静态工具箱再查动态工具与计划模式工具
     * @param name
     * @return
     */
    public AgentTool findTool(String name) {
        if (staticToolkit != null) {
            AgentTool tool = staticToolkit.find(name);
            if (tool != null) {
                return tool;
            }
        }
        for (AgentTool tool : dynamicTools) {
            if (name != null && name.equals(tool.getName())) {
                return tool;
            }
        }
        for (AgentTool tool : getPlanModeTools()) {
            if (name != null && name.equals(tool.getName())) {
                return tool;
            }
        }
        return null;
    }

    /**
     * 从运行时上下文获取计划模式工具列表
     * @return
     */
    @SuppressWarnings("unchecked")
    private List<AgentTool> getPlanModeTools() {
        if (runtimeContext == null) {
            return List.of();
        }
        Object obj = runtimeContext.get(PlanModeMiddleware.ATTR_PLAN_MODE_TOOLS);
        if (obj instanceof List) {
            try {
                return (List<AgentTool>) obj;
            } catch (ClassCastException e) {
                return List.of();
            }
        }
        return List.of();
    }

    public AgentRuntimeContext getRuntimeContext() {
        return runtimeContext;
    }

    public HarnessToolkit getToolkit() {
        return staticToolkit;
    }

    /**
     * 获取模型调用器，未暴露时为null
     * @return
     */
    public ModelCaller getModelCaller() {
        return modelCaller;
    }

    /**
     * 获取工具执行器，未暴露时为null
     * @return
     */
    public ToolExecutor getToolExecutor() {
        return toolExecutor;
    }

    /**
     * 获取模型响应解析器，未暴露时为null
     * @return
     */
    public ModelResponseParser getResponseParser() {
        return responseParser;
    }

    /**
     * 获取中间件链，未暴露时为null
     * @return
     */
    public MiddlewareChain getMiddlewareChain() {
        return middlewareChain;
    }

    /**
     * 注入中间件链
     * @param middlewareChain
     */
    public void setMiddlewareChain(MiddlewareChain middlewareChain) {
        this.middlewareChain = middlewareChain;
    }

    /**
     * 获取审批协调器，未暴露时为null
     * @return
     */
    public ApprovalCoordinator getApprovalCoordinator() {
        return approvalCoordinator;
    }

    /**
     * 注入审批协调器
     * @param approvalCoordinator
     */
    public void setApprovalCoordinator(ApprovalCoordinator approvalCoordinator) {
        this.approvalCoordinator = approvalCoordinator;
    }

    /**
     * 获取权限引擎，未暴露时为null
     * @return
     */
    public PermissionEngine getPermissionEngine() {
        return permissionEngine;
    }

    /**
     * 注入权限引擎
     * @param permissionEngine
     */
    public void setPermissionEngine(PermissionEngine permissionEngine) {
        this.permissionEngine = permissionEngine;
    }

    /**
     * 获取Token预算策略，未暴露时为null
     * @return
     */
    public TokenBudgetPolicy getTokenBudgetPolicy() {
        return tokenBudgetPolicy;
    }

    /**
     * 注入Token预算策略
     * @param tokenBudgetPolicy
     */
    public void setTokenBudgetPolicy(TokenBudgetPolicy tokenBudgetPolicy) {
        this.tokenBudgetPolicy = tokenBudgetPolicy;
    }

    /**
     * 获取成本预算策略，未暴露时为null
     * @return
     */
    public CostBudgetPolicy getCostBudgetPolicy() {
        return costBudgetPolicy;
    }

    /**
     * 注入成本预算策略
     * @param costBudgetPolicy
     */
    public void setCostBudgetPolicy(CostBudgetPolicy costBudgetPolicy) {
        this.costBudgetPolicy = costBudgetPolicy;
    }

    /**
     * 获取事件监听器注册中心，未暴露时为null
     * @return
     */
    public EventBus getEventBus() {
        return eventBus;
    }

    /**
     * 注入事件监听器注册中心
     * @param eventBus
     */
    public void setEventBus(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    /**
     * 获取模型定价注册表，未暴露时为null
     * @return
     */
    public ModelPricingRegistry getModelPricingRegistry() {
        return modelPricingRegistry;
    }

    /**
     * 注入模型定价注册表
     * @param modelPricingRegistry
     */
    public void setModelPricingRegistry(ModelPricingRegistry modelPricingRegistry) {
        this.modelPricingRegistry = modelPricingRegistry;
    }

    /**
     * 获取模型编码，未暴露时为null
     * @return
     */
    public String getModelCode() {
        return modelCode;
    }

    /**
     * 注入模型编码
     * @param modelCode
     */
    public void setModelCode(String modelCode) {
        this.modelCode = modelCode;
    }

    /**
     * 获取持久执行状态跟踪器，未暴露时为null
     * @return
     */
    public DurableExecutionTracker getDurableTracker() {
        return durableTracker;
    }

    /**
     * 注入持久执行状态跟踪器
     * @param durableTracker
     */
    public void setDurableTracker(DurableExecutionTracker durableTracker) {
        this.durableTracker = durableTracker;
    }

    public String getAgentName() {
        return agentName;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public AgentGenerateOptions getGenerateOptions() {
        return generateOptions;
    }

    public int getCurrentIter() {
        return currentIter.get();
    }

    public int getMaxIters() {
        return maxIters;
    }

    public Duration getTimeout() {
        return timeout;
    }

    /**
     * 获取总超时模式
     * @return
     */
    public HarnessAgentRuntimeBuilder.TimeoutMode getTimeoutMode() {
        return timeoutMode;
    }

    /**
     * 设置总超时模式
     * @param timeoutMode
     */
    public void setTimeoutMode(HarnessAgentRuntimeBuilder.TimeoutMode timeoutMode) {
        if (timeoutMode != null) {
            this.timeoutMode = timeoutMode;
        }
    }

    public Duration getIterationTimeout() {
        return iterationTimeout;
    }

    public int getMaxConcurrentToolCalls() {
        return maxConcurrentToolCalls;
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
     * 获取当前连续工具失败次数
     * @return
     */
    public int getConsecutiveFailures() {
        return consecutiveFailures.get();
    }

    /**
     * 累加连续工具失败次数并返回新值
     * @param delta
     * @return
     */
    public int addConsecutiveFailures(int delta) {
        return consecutiveFailures.addAndGet(delta);
    }

    /**
     * 重置连续工具失败计数
     */
    public void resetConsecutiveFailures() {
        consecutiveFailures.set(0);
    }

    /**
     * 获取累计Token用量
     * @return
     */
    public AgentChatUsage getAccumulatedUsage() {
        return accumulatedUsage;
    }

    /**
     * 累加Token用量
     * @param usage
     */
    public void accumulateUsage(AgentChatUsage usage) {
        this.accumulatedUsage = AgentChatUsage.merge(this.accumulatedUsage, usage);
    }

    /**
     * 递增当前迭代次数并返回新值
     * @return
     */
    public int incrementIter() {
        return currentIter.incrementAndGet();
    }

    /**
     * 同步当前迭代轮次（由ReActEngine在进入每轮迭代时调用）
     * @param iteration
     */
    public void setCurrentIter(int iteration) {
        currentIter.set(iteration);
    }

    /**
     * 判断是否达到最大迭代次数
     * @return
     */
    public boolean reachedMaxIters() {
        return currentIter.get() >= maxIters;
    }

    /**
     * 添加消息到对话历史
     * @param message
     */
    public void addMessage(AgentMessage message) {
        if (message != null) {
            conversation.add(message);
        }
    }

    /**
     * 返回对话历史的副本
     * @return
     */
    public List<AgentMessage> getConversation() {
        return new ArrayList<>(conversation);
    }

    /**
     * 返回消息历史的副本，兼容旧调用方
     * @return
     */
    public List<AgentMessage> getMessages() {
        return new ArrayList<>(conversation);
    }
}
