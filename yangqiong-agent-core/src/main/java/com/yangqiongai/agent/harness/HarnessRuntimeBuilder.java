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
package com.yangqiongai.agent.harness;

import com.yangqiongai.agent.harness.core.AgentRuntime;
import com.yangqiongai.agent.harness.core.AgentRuntimeFactory;
import com.yangqiongai.agent.harness.core.HarnessAgentRuntimeBuilder;
import com.yangqiongai.agent.harness.config.AgentApprovalMode;
import com.yangqiongai.agent.harness.config.AgentCompactionConfig;
import com.yangqiongai.agent.harness.config.AgentMemoryConfig;
import com.yangqiongai.agent.harness.config.AgentPermissionContextState;
import com.yangqiongai.agent.harness.config.AgentPermissionMode;
import com.yangqiongai.agent.harness.config.AgentPermissionRule;
import com.yangqiongai.agent.harness.config.AgentResponseFormat;
import com.yangqiongai.agent.harness.config.AgentToolChoice;
import com.yangqiongai.agent.harness.config.AgentToolResultEvictionConfig;
import com.yangqiongai.agent.harness.config.CostBudgetPolicy;
import com.yangqiongai.agent.harness.config.ContextCachingConfig;
import com.yangqiongai.agent.harness.config.TokenBudgetPolicy;
import com.yangqiongai.agent.harness.config.ToolLoadingMode;
import com.yangqiongai.agent.harness.core.middleware.AgentMiddleware;
import com.yangqiongai.agent.harness.core.memory.AgentLongTermMemory;
import com.yangqiongai.agent.harness.core.memory.SessionMemory;
import com.yangqiongai.agent.harness.core.model.AgentGenerateOptions;
import com.yangqiongai.agent.harness.core.model.AgentModel;
import com.yangqiongai.agent.harness.core.model.AgentModelFactory;
import com.yangqiongai.agent.harness.model.embedding.EmbeddingModel;
import com.yangqiongai.agent.harness.memory.ForgettingPolicy;
import com.yangqiongai.agent.harness.memory.VectorLongTermMemory;
import com.yangqiongai.agent.harness.subagent.orchestration.SubagentDeclaration;
import com.yangqiongai.agent.harness.core.skill.AgentSkillBox;
import com.yangqiongai.agent.harness.core.tool.AgentToolkit;
import com.yangqiongai.agent.harness.core.tool.AgentTool;
import com.yangqiongai.agent.harness.engine.AgentLoop;
import com.yangqiongai.agent.harness.engine.EngineConfig;
import com.yangqiongai.agent.harness.engine.MiddlewareChain;
import com.yangqiongai.agent.harness.engine.ReActEngine;
import com.yangqiongai.agent.harness.engine.ApprovalCoordinator;
import com.yangqiongai.agent.harness.engine.DurableExecutionTracker;
import com.yangqiongai.agent.harness.durable.AgentRunStore;
import com.yangqiongai.agent.harness.durable.ApprovalStore;
import com.yangqiongai.agent.harness.durable.CheckpointStore;
import com.yangqiongai.agent.harness.durable.DistributedStores;
import com.yangqiongai.agent.harness.durable.RunLockStore;
import com.yangqiongai.agent.harness.memory.CheckpointManager;
import com.yangqiongai.agent.harness.memory.LongTermMemoryAdapter;
import com.yangqiongai.agent.harness.middleware.CompactionMiddleware;
import com.yangqiongai.agent.harness.guardrail.GuardrailRuleRegistry;
import com.yangqiongai.agent.harness.guardrail.ContentModerationPolicy;
import com.yangqiongai.agent.harness.middleware.InputGuardrailMiddleware;
import com.yangqiongai.agent.harness.middleware.ToolGuardrailMiddleware;
import com.yangqiongai.agent.harness.middleware.MemoryRetrievalMiddleware;
import com.yangqiongai.agent.harness.middleware.SummaryCompactionMiddleware;
import com.yangqiongai.agent.harness.middleware.ToolResultEvictionMiddleware;
import com.yangqiongai.agent.harness.middleware.TraceMiddleware;
import com.yangqiongai.agent.harness.middleware.ContentModerationMiddleware;
import com.yangqiongai.agent.harness.model.ModelCaller;
import com.yangqiongai.agent.harness.model.ModelPricing;
import com.yangqiongai.agent.harness.model.ModelPricingRegistry;
import com.yangqiongai.agent.harness.model.ModelResponseParser;
import com.yangqiongai.agent.harness.model.ModelRetryConfig;
import com.yangqiongai.agent.harness.model.RetryableModelCaller;
import com.yangqiongai.agent.harness.model.SemanticCachingModelCaller;
import com.yangqiongai.agent.harness.model.StructuredOutputRetryPolicy;
import com.yangqiongai.agent.harness.ratelimit.RateLimiter;
import com.yangqiongai.agent.harness.ratelimit.RateLimitedModel;
import com.yangqiongai.agent.harness.subagent.orchestration.AutoOrchestrateTool;
import com.yangqiongai.agent.harness.subagent.orchestration.AskUserTool;
import com.yangqiongai.agent.harness.subagent.orchestration.DefaultSubagentSpecGenerator;
import com.yangqiongai.agent.harness.subagent.orchestration.HandoffContextFilter;
import com.yangqiongai.agent.harness.subagent.orchestration.HandoffRegistry;
import com.yangqiongai.agent.harness.subagent.orchestration.HandoffTarget;
import com.yangqiongai.agent.harness.subagent.orchestration.HandoffTool;
import com.yangqiongai.agent.harness.subagent.orchestration.OrchestrationStrategy;
import com.yangqiongai.agent.harness.subagent.orchestration.OrchestrationStrategyHandler;
import com.yangqiongai.agent.harness.subagent.orchestration.SubagentSpecGenerator;
import com.yangqiongai.agent.harness.permission.AiToolApprovalGate;
import com.yangqiongai.agent.harness.permission.PermissionEngine;
import com.yangqiongai.agent.harness.permission.ToolPolicyGate;
import com.yangqiongai.agent.harness.planmode.PlanModeMiddleware;
import com.yangqiongai.agent.harness.rag.RagRetrievalMiddleware;
import com.yangqiongai.agent.harness.rag.Retriever;
import com.yangqiongai.agent.harness.rag.RetrieverRegistry;
import com.yangqiongai.agent.harness.web.WebFetcher;
import com.yangqiongai.agent.harness.web.WebFetchTool;
import com.yangqiongai.agent.harness.web.WebSearchProvider;
import com.yangqiongai.agent.harness.web.WebSearchTool;
import com.yangqiongai.agent.harness.rag.RetrieverTool;
import com.yangqiongai.agent.harness.rag.DocumentParser;
import com.yangqiongai.agent.harness.skill.LoadSkillTool;
import com.yangqiongai.agent.harness.skill.ReadSkillResourceTool;
import com.yangqiongai.agent.harness.skill.SkillManager;
import com.yangqiongai.agent.harness.skill.SkillPromptInjector;
import com.yangqiongai.agent.harness.subagent.RuntimeCapabilityAccessor;
import com.yangqiongai.agent.harness.subagent.SubagentCapabilityPolicy;
import com.yangqiongai.agent.harness.subagent.SubagentContext;
import com.yangqiongai.agent.harness.subagent.SubagentSpawner;
import com.yangqiongai.agent.harness.subagent.SubagentsMiddleware;
import com.yangqiongai.agent.harness.mcp.McpServerConfig;
import com.yangqiongai.agent.harness.mcp.McpToolkit;
import com.yangqiongai.agent.harness.spi.DiscoveredExtensions;
import com.yangqiongai.agent.harness.spi.HarnessDiscovery;
import com.yangqiongai.agent.harness.tool.HarnessToolkit;
import com.yangqiongai.agent.harness.tool.InMemoryToolExecutionStore;
import com.yangqiongai.agent.harness.tool.KeywordToolFilter;
import com.yangqiongai.agent.harness.tool.LoadToolTool;
import com.yangqiongai.agent.harness.tool.ToolCatalogPromptInjector;
import com.yangqiongai.agent.harness.tool.ToolExecutionStore;
import com.yangqiongai.agent.harness.tool.ToolExecutor;
import com.yangqiongai.agent.harness.tool.ToolLoadingState;
import com.yangqiongai.agent.harness.tool.FileToolkit;
import com.yangqiongai.agent.harness.core.trace.TraceEmitter;
import com.yangqiongai.agent.harness.event.EventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Agent运行时构建器
 * @author yangqiong
 */
public class HarnessRuntimeBuilder implements RuntimeCapabilityAccessor {

    private static final Logger log = LoggerFactory.getLogger(HarnessRuntimeBuilder.class);

    /**
     * MCP预热与build并发执行的互斥锁
     */
    private final Object mcpLock = new Object();

    /**
     * Agent名称
     */
    private String name;

    /**
     * Agent模型
     */
    private AgentModel model;

    /**
     * 系统提示词
     */
    private String systemPrompt;

    /**
     * 最大迭代次数
     */
    private int maxIters = 10;

    /**
     * 工具箱
     */
    private AgentToolkit toolkit;

    /**
     * MCP服务端配置列表
     */
    private List<McpServerConfig> mcpServerConfigs = new ArrayList<>();

    /**
     * MCP工具箱（build时创建，供外部关闭客户端）
     */
    private McpToolkit mcpToolkit;

    /**
     * 自定义Agent执行循环（可空，非空时替换默认ReActEngine）
     */
    private AgentLoop agentLoop;

    /**
     * 显式注册的运行时定制器
     */
    private final List<com.yangqiongai.agent.harness.spi.RuntimeCustomizer> customizers = new ArrayList<>();

    /**
     * 中间件列表
     */
    private List<AgentMiddleware> middlewares = new ArrayList<>();

    /**
     * JDK SPI插件自动发现开关，默认关闭
     */
    private boolean autoDiscover;

    /**
     * 已发现的扩展集合（build时懒加载）
     */
    private DiscoveredExtensions autoExtensions;

    /**
     * 工具护栏规则注册中心（enableToolGuardrail时记录，供autoDiscover注册检测器）
     */
    private GuardrailRuleRegistry guardrailRegistry;

    /**
     * 自定义模型调用层（注册顺序即从外到内包裹在默认重试/缓存/限流链之上）
     */
    private List<Function<AgentModel, AgentModel>> modelCallerLayers = new ArrayList<>();

    /**
     * 事件监听器注册中心
     */
    private EventBus eventBus;

    /**
     * 自定义编排策略处理器（按策略名注册）
     */
    private Map<String, OrchestrationStrategyHandler> orchestrationStrategyHandlers = new LinkedHashMap<>();

    /**
     * 模型生成选项
     */
    private AgentGenerateOptions generateOptions;

    /**
     * 响应格式
     */
    private AgentResponseFormat responseFormat;

    /**
     * 结构化输出类型
     */
    private Class<?> structuredOutputType;

    /**
     * 权限上下文状态
     */
    private AgentPermissionContextState permissionState;

    /**
     * 记忆配置
     */
    private AgentMemoryConfig memoryConfig;

    /**
     * 压缩配置
     */
    private AgentCompactionConfig compactionConfig;

    /**
     * 工具结果驱逐配置
     */
    private AgentToolResultEvictionConfig evictionConfig;

    /**
     * 工具选择策略
     */
    private AgentToolChoice toolChoice;

    /**
     * 技能箱
     */
    private AgentSkillBox skillBox;

    /**
     * 工具下发模式，默认FULL全量下发
     */
    private ToolLoadingMode toolLoadingMode = ToolLoadingMode.FULL;

    /**
     * 渐进模式下常驻工具名单（始终下发完整schema）
     */
    private Set<String> alwaysOnTools;

    /**
     * 单次Agent执行总超时
     */
    private Duration timeout;

    /**
     * 单次Agent执行总超时模式，默认硬墙钟到点必断
     */
    private TimeoutMode timeoutMode = TimeoutMode.WALL_CLOCK;

    /**
     * 单轮推理与工具执行超时
     */
    private Duration iterationTimeout;

    /**
     * 最大并行工具调用数
     */
    private int maxConcurrentToolCalls = 1;

    /**
     * 工具白名单
     */
    private Set<String> allowedTools;

    /**
     * 工具黑名单
     */
    private Set<String> deniedTools;

    /**
     * 需要人工审批的工具集合
     */
    private Set<String> requireApproval;

    /**
     * 统一审批模式，默认CUSTOM沿用既有细粒度配置
     */
    private AgentApprovalMode approvalMode = AgentApprovalMode.CUSTOM;

    /**
     * AI自动审批（AUTO模式）的审批模型
     */
    private AgentModel approvalJudgeModel;

    /**
     * AI审批判定失败时是否回退人工确认，默认回退
     */
    private boolean aiApprovalFallbackAsk = true;

    /**
     * AI审批附加策略描述，拼入判定提示词
     */
    private String aiApprovalGuidance;

    /**
     * 工具失败处理策略
     */
    private HarnessAgentRuntimeBuilder.ToolFailureStrategy toolFailureStrategy;

    /**
     * 单个工具最大重试次数
     */
    private int maxToolRetries;

    /**
     * 上下文窗口Token上限
     */
    private int maxContextTokens;

    /**
     * 历史截断策略
     */
    private HarnessAgentRuntimeBuilder.TruncationStrategy historyTruncationStrategy;

    /**
     * 单次工具调用超时
     */
    private Duration toolCallTimeout = Duration.ofSeconds(60);

    /**
     * 工具结果最大字符数
     */
    private int maxToolResultChars = 10000;

    /**
     * 最大连续工具失败次数
     */
    private int maxConsecutiveToolFailures = 3;

    /**
     * 模型重试配置
     */
    private ModelRetryConfig modelRetryConfig;

    /**
     * 子代理声明列表
     */
    private List<SubagentDeclaration> subagentDeclarations;

    /**
     * 是否启用计划模式
     */
    private boolean planModeEnabled;

    /**
     * 是否启用长期记忆工具
     */
    private boolean memoryToolsEnabled = true;

    /**
     * 是否启用记忆自动持久化钩子
     */
    private boolean memoryHooksEnabled = true;

    /**
     * L3长期记忆存储
     */
    private AgentLongTermMemory longTermMemory;

    /**
     * L2会话级短期记忆
     */
    private SessionMemory sessionMemory;

    /**
     * 是否启用基于LLM摘要的上下文压缩（与截断式压缩互斥）
     */
    private boolean summaryCompactionEnabled;

    /**
     * 是否启用L3→L1检索注入，仅在配置长期记忆时生效
     */
    private boolean memoryRetrievalEnabled = true;

    /**
     * L3→L1检索注入条目上限
     */
    private int memoryRetrievalTopK = 3;

    /**
     * 是否启用多子代理（默认关闭；传入非空静态声明时自动启用）
     */
    private boolean subagentsEnabled;

    /**
     * 是否启用动态子代理
     */
    private boolean dynamicSubagentsEnabled = true;

    /**
     * 自动编排全局开关（从配置读取，默认true）
     */
    private boolean autoOrchestrationEnabled = true;

    /**
     * 最大嵌套深度
     */
    private int subagentMaxDepth = 3;

    /**
     * 是否允许子代理递归编排（默认true）
     */
    private boolean allowRecursiveOrchestration = true;

    /**
     * 子代理声明生成器（可选，用于LLM动态任务分解）
     */
    private SubagentSpecGenerator specGenerator;

    /**
     * LLM动态生成声明的最大子代理数
     */
    private int maxSubagents = 5;

    /**
     * 声明生成使用的模型编码（可空）
     */
    private String generationModelCode;

    /**
     * 编排策略默认值（可空，LLM未指定strategy时使用）
     */
    private OrchestrationStrategy orchestrationStrategy;

    /**
     * 编排轮次（可空，LLM未指定rounds时使用，也可由LLM按任务自行决定）
     */
    private Integer orchestrationRounds;

    /**
     * 编排轮次上限（未配置时默认8，约束LLM指定或默认轮次，避免无限迭代）
     */
    private int maxOrchestrationRounds = 8;

    /**
     * 编排轮次上限是否被显式配置（影响提示词是否提示轮次约束）
     */
    private boolean maxOrchestrationRoundsSet;

    /**
     * 运行时工厂（用于子代理生成，可选）
     */
    private AgentRuntimeFactory runtimeFactory;

    /**
     * 模型工厂（用于子代理按modelCode获取模型，可选）
     */
    private AgentModelFactory modelFactory;

    /**
     * 检查点管理器（可选，启用后每轮迭代保存快照支持崩溃恢复）
     */
    private CheckpointManager checkpointManager;

    /**
     * 统一权限策略门（可选，未提供时按权限引擎与名单自动构建）
     */
    private ToolPolicyGate toolPolicyGate;

    /**
     * 工具执行记录存储（可选，启用后跨节点按幂等键去重复用首次结果）
     */
    private ToolExecutionStore toolExecutionStore;

    /**
     * 运行锁存储（可选，分布式运行时防止多节点并发续跑同一运行）
     */
    private RunLockStore runLockStore;

    /**
     * 本节点唯一标识（可选，运行锁持有者标识，多节点部署需配置）
     */
    private String nodeId;

    /**
     * 运行记录存储（可选，持久执行三SPI之一）
     */
    private AgentRunStore agentRunStore;

    /**
     * 完整检查点存储（可选，持久执行三SPI之二）
     */
    private CheckpointStore checkpointStore;

    /**
     * 审批存储（可选，持久执行三SPI之三）
     */
    private ApprovalStore approvalStore;

    /**
     * Token预算策略（可选，启用后基于真实usage累计做预算告警与硬控）
     */
    private TokenBudgetPolicy tokenBudgetPolicy;

    /**
     * 成本预算策略（可选，启用后基于真实usage×定价累计做成本告警与硬控）
     */
    private CostBudgetPolicy costBudgetPolicy;

    /**
     * 模型定价注册表（可选，为空时按默认价0计价）
     */
    private ModelPricingRegistry modelPricingRegistry;

    /**
     * Handoff目标注册表（可选，注册后handoff工具可用）
     */
    private HandoffRegistry handoffRegistry;

    /**
     * 检索器注册表（可选，注册后rag_search工具可用）
     */
    private RetrieverRegistry retrieverRegistry;

    /**
     * Web检索提供方（可选，注册后web_search工具可用）
     */
    private WebSearchProvider webSearchProvider;

    /**
     * 网页抓取提供方（可选，注册后web_fetch工具可用）
     */
    private WebFetcher webFetcher;

    /**
     * 是否强制启用Web工具（true时即使未配置提供方也注册，调用时返回未配置提示）
     */
    private boolean webEnabled;

    /**
     * 是否启用向用户提问工具（ask_user，模型中途缺少关键信息时主动澄清）
     */
    private boolean askUserEnabled;

    /**
     * 是否启用RAG自动检索注入中间件（默认关闭）
     */
    private boolean ragRetrievalEnabled;

    /**
     * RAG检索条数
     */
    private int ragTopK = 3;

    /**
     * RAG注入模板前缀
     */
    private String ragInjectionTemplate;

    /**
     * RAG默认检索源名称
     */
    private String ragSource = "default";

    /**
     * 嵌入模型（可选，设置后默认L3切换为向量记忆）
     */
    private EmbeddingModel embeddingModel;

    /**
     * 记忆遗忘策略（可选，配合向量记忆启用遗忘清理）
     */
    private ForgettingPolicy memoryForgettingPolicy;

    /**
     * 是否启用语义缓存（依赖嵌入模型，命中相似度阈值直接返回缓存响应）
     */
    private boolean semanticCachingEnabled;

    /**
     * 语义缓存命中相似度阈值
     */
    private double semanticCacheThreshold = SemanticCachingModelCaller.DEFAULT_THRESHOLD;

    /**
     * 语义缓存最大条目数
     */
    private int semanticCacheMaxEntries = SemanticCachingModelCaller.MAX_ENTRIES;

    /**
     * 上下文缓存配置（可选，仅Anthropic协议支持）
     */
    private ContextCachingConfig contextCachingConfig;

    /**
     * 成本计价的模型编码（可选，须与modelPricing注册的modelCode对应，缺省按默认价0计价）
     */
    private String modelCode;

    /**
     * 模型速率限制器（可选，设置后在模型调用前按令牌桶限流）
     */
    private RateLimiter modelRateLimiter;

    /**
     * 结构化输出重试策略（可选，设置后校验失败自动注入纠正提示重试）
     */
    private StructuredOutputRetryPolicy structuredOutputRetryPolicy;

    /**
     * 追踪导出器（可选，设置后TraceMiddleware加入链，span生命周期回调导出）
     */
    private TraceEmitter traceEmitter;

    /**
     * 内容审查策略（可选，设置后ContentModerationMiddleware加入链，未设置默认放行）
     */
    private ContentModerationPolicy contentModerationPolicy;

    /**
     * 文件工具箱（可选，设置后file_read/file_list工具注册，沙箱内读取）
     */
    private FileToolkit fileToolkit;

    /**
     * 设置结构化输出重试策略
     * @param maxRetries
     * @param errorTemplate
     * @return
     */
    public HarnessRuntimeBuilder structuredOutputRetry(int maxRetries, String errorTemplate) {
        this.structuredOutputRetryPolicy = new StructuredOutputRetryPolicy(maxRetries, errorTemplate);
        return this;
    }

    /**
     * 设置追踪导出器
     * @param traceEmitter
     * @return
     */
    public HarnessRuntimeBuilder traceEmitter(TraceEmitter traceEmitter) {
        this.traceEmitter = traceEmitter;
        return this;
    }

    /**
     * 设置内容审查策略，加入内容审查中间件（命中BLOCK中止、MASK脱敏）
     * @param contentModerationPolicy
     * @return
     */
    public HarnessRuntimeBuilder contentModeration(ContentModerationPolicy contentModerationPolicy) {
        this.contentModerationPolicy = contentModerationPolicy;
        return this;
    }

    /**
     * 启用Agent文件工具（file_read/file_list），限定在沙箱根目录内读取
     * @param sandboxRoot 沙箱根目录，为空时使用默认工作区目录
     * @param parser 文档解析器（可注入平台Tika实现，为空时使用纯文本解析）
     * @return
     */
    public HarnessRuntimeBuilder enableFileToolkit(String sandboxRoot, DocumentParser parser) {
        this.fileToolkit = new FileToolkit(sandboxRoot, parser);
        return this;
    }

    /**
     * 启用Agent文件工具（file_read/file_list），使用默认沙箱根目录
     * @return
     */
    public HarnessRuntimeBuilder enableFileToolkit() {
        return enableFileToolkit(null, null);
    }

    /**
     * 设置检查点管理器
     * @param checkpointManager
     * @return
     */
    public HarnessRuntimeBuilder checkpointManager(CheckpointManager checkpointManager) {
        this.checkpointManager = checkpointManager;
        return this;
    }

    /**
     * 设置统一权限策略门（不设置时按权限引擎与白/黑名单自动构建）
     * @param toolPolicyGate
     * @return
     */
    public HarnessRuntimeBuilder toolPolicyGate(ToolPolicyGate toolPolicyGate) {
        this.toolPolicyGate = toolPolicyGate;
        return this;
    }

    /**
     * 设置工具执行记录存储（跨节点幂等去重）
     * @param toolExecutionStore
     * @return
     */
    public HarnessRuntimeBuilder toolExecutionStore(ToolExecutionStore toolExecutionStore) {
        this.toolExecutionStore = toolExecutionStore;
        return this;
    }

    /**
     * 设置运行锁存储（分布式运行归属控制）
     * @param runLockStore
     * @return
     */
    public HarnessRuntimeBuilder runLockStore(RunLockStore runLockStore) {
        this.runLockStore = runLockStore;
        return this;
    }

    /**
     * 设置本节点唯一标识（运行锁持有者标识，多节点部署需配置）
     * @param nodeId
     * @return
     */
    public HarnessRuntimeBuilder nodeId(String nodeId) {
        this.nodeId = nodeId;
        return this;
    }

    /**
     * 设置运行记录存储（持久执行）
     * @param agentRunStore
     * @return
     */
    public HarnessRuntimeBuilder agentRunStore(AgentRunStore agentRunStore) {
        this.agentRunStore = agentRunStore;
        return this;
    }

    /**
     * 设置完整检查点存储（持久执行）
     * @param checkpointStore
     * @return
     */
    public HarnessRuntimeBuilder checkpointStore(CheckpointStore checkpointStore) {
        this.checkpointStore = checkpointStore;
        return this;
    }

    /**
     * 设置审批存储（持久执行）
     * @param approvalStore
     * @return
     */
    public HarnessRuntimeBuilder approvalStore(ApprovalStore approvalStore) {
        this.approvalStore = approvalStore;
        return this;
    }

    /**
     * 注册运行时定制器，build装配前按order依次回调
     * @param customizer
     * @return
     */
    public HarnessRuntimeBuilder addCustomizer(com.yangqiongai.agent.harness.spi.RuntimeCustomizer customizer) {
        if (customizer != null) {
            this.customizers.add(customizer);
        }
        return this;
    }

    /**
     * 注入自定义Agent执行循环，替换默认ReActEngine
     * <p>
     * 用于切换推理范式（如plan-and-execute、reflexion），中间件、工具、审批等装配保持不变。
     * </p>
     * @param loop 自定义执行循环实现
     * @return
     */
    public HarnessRuntimeBuilder agentLoop(AgentLoop loop) {
        this.agentLoop = loop;
        return this;
    }

    /**
     * 一次性注入共享存储实现，整合运行记录、检查点、审批、记忆、运行锁等全部存储
     * <p>
     * 未配置时可由调用方传入内存/分布式实现，避免逐项 setter 拼接；
     * 传入实现中为空的项不覆盖已显式设置的单项，保证单项 setter 优先。
     * </p>
     * @param stores 共享存储实现
     * @return
     */
    public HarnessRuntimeBuilder stores(DistributedStores stores) {
        if (stores == null) {
            return this;
        }
        if (stores.runStore() != null) {
            this.agentRunStore = stores.runStore();
        }
        if (stores.checkpointStore() != null) {
            this.checkpointStore = stores.checkpointStore();
        }
        if (stores.approvalStore() != null) {
            this.approvalStore = stores.approvalStore();
        }
        if (stores.sessionMemory() != null) {
            this.sessionMemory = stores.sessionMemory();
        }
        if (stores.longTermMemory() != null) {
            this.longTermMemory = stores.longTermMemory();
        }
        if (stores.toolExecutionStore() != null) {
            this.toolExecutionStore = stores.toolExecutionStore();
        }
        if (stores.runLockStore() != null) {
            this.runLockStore = stores.runLockStore();
        }
        return this;
    }

    /**
     * 设置Token预算策略
     * @param tokenBudgetPolicy
     * @return
     */
    public HarnessRuntimeBuilder tokenBudgetPolicy(TokenBudgetPolicy tokenBudgetPolicy) {
        this.tokenBudgetPolicy = tokenBudgetPolicy;
        return this;
    }

    /**
     * 设置成本预算策略
     * @param costBudgetPolicy
     * @return
     */
    public HarnessRuntimeBuilder costBudgetPolicy(CostBudgetPolicy costBudgetPolicy) {
        this.costBudgetPolicy = costBudgetPolicy;
        return this;
    }

    /**
     * 注册模型定价，累计成本时按定价换算usage为美元
     * @param pricings
     * @return
     */
    public HarnessRuntimeBuilder modelPricing(ModelPricing... pricings) {
        if (pricings == null || pricings.length == 0) {
            return this;
        }
        if (modelPricingRegistry == null) {
            modelPricingRegistry = new ModelPricingRegistry();
        }
        for (ModelPricing pricing : pricings) {
            modelPricingRegistry.register(pricing);
        }
        return this;
    }

    /**
     * 注册Handoff目标，启用后主代理可通过handoff工具将控制权移交给目标代理
     * @param name
     * @param runtime
     * @return
     */
    public HarnessRuntimeBuilder handoffTarget(String name, AgentRuntime runtime) {
        return handoffTarget(name, runtime, null);
    }

    /**
     * 注册Handoff目标并指定上下文过滤器
     * @param name
     * @param runtime
     * @param filter
     * @return
     */
    public HarnessRuntimeBuilder handoffTarget(String name, AgentRuntime runtime, HandoffContextFilter filter) {
        if (runtime == null) {
            return this;
        }
        if (handoffRegistry == null) {
            handoffRegistry = new HandoffRegistry();
        }
        handoffRegistry.register(name, new HandoffTarget(runtime, null, filter));
        return this;
    }

    /**
     * 注册命名检索器，作为rag_search工具的检索源
     * @param name
     * @param retriever
     * @return
     */
    public HarnessRuntimeBuilder retriever(String name, Retriever retriever) {
        if (retriever == null || name == null || name.isBlank()) {
            return this;
        }
        if (retrieverRegistry == null) {
            retrieverRegistry = new RetrieverRegistry();
        }
        retrieverRegistry.register(name, retriever);
        return this;
    }

    /**
     * 设置Web检索提供方，注册后web_search工具可用
     * @param provider
     * @return
     */
    public HarnessRuntimeBuilder webSearchProvider(WebSearchProvider provider) {
        this.webSearchProvider = provider;
        return this;
    }

    /**
     * 设置网页抓取提供方，注册后web_fetch工具可用
     * @param fetcher
     * @return
     */
    public HarnessRuntimeBuilder webFetcher(WebFetcher fetcher) {
        this.webFetcher = fetcher;
        return this;
    }

    /**
     * 设置Web工具强制启用开关（即使未配置提供方也注册，调用时返回未配置提示）
     * @param enabled
     * @return
     */
    public HarnessRuntimeBuilder webEnabled(boolean enabled) {
        this.webEnabled = enabled;
        return this;
    }

    /**
     * 设置向用户提问工具启用开关（ask_user），模型中途缺少关键信息时可主动澄清
     * @param enabled
     * @return
     */
    public HarnessRuntimeBuilder askUserEnabled(boolean enabled) {
        this.askUserEnabled = enabled;
        return this;
    }

    /**
     * 启用RAG自动检索注入中间件（仅最后一条为USER时注入围栏检索结果）
     * @param topK
     * @param injectionTemplate
     * @return
     */
    public HarnessRuntimeBuilder enableRagRetrieval(int topK, String injectionTemplate) {
        this.ragRetrievalEnabled = true;
        this.ragTopK = topK > 0 ? topK : 3;
        this.ragInjectionTemplate = injectionTemplate;
        return this;
    }

    /**
     * 禁用RAG自动检索注入中间件
     * @return
     */
    public HarnessRuntimeBuilder disableRagRetrieval() {
        this.ragRetrievalEnabled = false;
        return this;
    }

    /**
     * 设置嵌入模型，未显式注入longTermMemory时默认L3切换为向量记忆
     * @param embeddingModel
     * @return
     */
    public HarnessRuntimeBuilder embeddingModel(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
        return this;
    }

    /**
     * 设置记忆遗忘策略，配合向量记忆在存储时清理过期条目
     * @param forgettingPolicy
     * @return
     */
    public HarnessRuntimeBuilder memoryForgettingPolicy(ForgettingPolicy forgettingPolicy) {
        this.memoryForgettingPolicy = forgettingPolicy;
        return this;
    }

    /**
     * 启用语义缓存，查询嵌入与缓存向量余弦相似度达到阈值的直接返回缓存响应
     * @param threshold
     * @param maxEntries
     * @return
     */
    public HarnessRuntimeBuilder enableSemanticCaching(double threshold, int maxEntries) {
        this.semanticCachingEnabled = true;
        this.semanticCacheThreshold = threshold;
        this.semanticCacheMaxEntries = maxEntries > 0 ? maxEntries : SemanticCachingModelCaller.MAX_ENTRIES;
        return this;
    }

    /**
     * 禁用语义缓存
     * @return
     */
    public HarnessRuntimeBuilder disableSemanticCaching() {
        this.semanticCachingEnabled = false;
        return this;
    }

    /**
     * 启用上下文缓存（仅Anthropic协议支持，对system提示与工具定义追加cache_control）
     * @param enabled
     * @param cacheSystemPrompt
     * @param cacheTools
     * @return
     */
    public HarnessRuntimeBuilder contextCaching(boolean enabled, boolean cacheSystemPrompt, boolean cacheTools) {
        this.contextCachingConfig = new ContextCachingConfig(enabled, cacheSystemPrompt, cacheTools);
        return this;
    }

    /**
     * 设置成本计价的模型编码，须与modelPricing注册的modelCode对应
     * @param modelCode
     * @return
     */
    public HarnessRuntimeBuilder modelCode(String modelCode) {
        this.modelCode = modelCode;
        return this;
    }

    /**
     * 设置模型速率限制器，启用后模型调用前按令牌桶限流（RPM级）
     * @param limiter
     * @return
     */
    public HarnessRuntimeBuilder modelRateLimiter(RateLimiter limiter) {
        this.modelRateLimiter = limiter;
        return this;
    }

    /**
     * 设置运行时工厂
     * @param runtimeFactory
     * @return
     */
    public HarnessRuntimeBuilder runtimeFactory(AgentRuntimeFactory runtimeFactory) {
        this.runtimeFactory = runtimeFactory;
        return this;
    }

    /**
     * 设置模型工厂
     * @param modelFactory
     * @return
     */
    public HarnessRuntimeBuilder modelFactory(AgentModelFactory modelFactory) {
        this.modelFactory = modelFactory;
        return this;
    }

    @Override
    public HarnessRuntimeBuilder name(String name) {
        this.name = name;
        return this;
    }

    @Override
    public HarnessRuntimeBuilder model(AgentModel model) {
        this.model = model;
        return this;
    }

    @Override
    public HarnessRuntimeBuilder systemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
        return this;
    }

    @Override
    public HarnessRuntimeBuilder maxIters(int maxIters) {
        this.maxIters = maxIters;
        return this;
    }

    @Override
    public HarnessRuntimeBuilder toolkit(AgentToolkit toolkit) {
        this.toolkit = toolkit;
        return this;
    }

    /**
     * 注册MCP服务端，build时同步完成握手并将工具注册进工具箱（工具名默认带服务端前缀）
     * @param config
     * @return
     */
    public HarnessRuntimeBuilder mcpServer(McpServerConfig config) {
        if (config != null) {
            this.mcpServerConfigs.add(config);
        }
        return this;
    }

    /**
     * 批量注册MCP服务端
     * @param configs
     * @return
     */
    public HarnessRuntimeBuilder mcpServers(List<McpServerConfig> configs) {
        if (configs != null) {
            this.mcpServerConfigs.addAll(configs);
        }
        return this;
    }

    /**
     * 获取build时创建的MCP工具箱，用于运行时关闭后释放MCP连接
     * @return
     */
    public McpToolkit getMcpToolkit() {
        return mcpToolkit;
    }

    /**
     * 预热MCP：启动期提前完成各服务端握手与工具发现
     * <p>
     * 同步阻塞执行，适用于应用启动钩子；已预热或无需MCP时幂等返回。
     * 日志会逐个打印每个MCP服务端是否接入可用，便于启动期提前发现配置或网络问题。
     * </p>
     * @return
     */
    public HarnessRuntimeBuilder preloadMcp() {
        ensureMcpInitialized();
        return this;
    }

    /**
     * 异步预热MCP：不阻塞启动线程
     * <p>
     * 返回的{@link Mono}在订阅时才真正执行，与{@link #preloadMcp()}共用幂等保护，
     * build时会复用预热完成的结果，避免重复握手。
     * </p>
     * @return
     */
    public Mono<Void> preloadMcpAsync() {
        return Mono.fromRunnable(this::ensureMcpInitialized);
    }

    private void ensureMcpInitialized() {
        if (mcpServerConfigs.isEmpty()) {
            return;
        }
        synchronized (mcpLock) {
            if (mcpToolkit != null && mcpToolkit.isInitialized()) {
                return;
            }
            if (mcpToolkit == null) {
                mcpToolkit = new McpToolkit(mcpServerConfigs);
            }
            log.info("开始预热MCP，共{}个服务端", mcpServerConfigs.size());
            mcpToolkit.initializeAll().block();
            log.info("MCP预热完成，可用服务端: {}", mcpToolkit.getServerNames());
        }
    }

    @Override
    public HarnessRuntimeBuilder middleware(AgentMiddleware middleware) {
        if (middleware != null) {
            this.middlewares.add(middleware);
        }
        return this;
    }

    /**
     * 启用JDK SPI插件自动发现
     * <p>
     * build时通过ServiceLoader扫描classpath，自动注册{@code MiddlewareProvider}提供的
     * 中间件、{@code AgentToolProvider}提供的工具；未显式配置模型/存储时采用
     * {@code DefaultModelProvider}/{@code StoreProvider}提供的默认实现；{@code GuardrailDetectorProvider}
     * 提供的检测器注册进已启用的护栏规则注册中心。
     * </p>
     * @return
     */
    public HarnessRuntimeBuilder autoDiscover() {
        this.autoDiscover = true;
        return this;
    }

    /**
     * 注册自定义模型调用层
     * <p>
     * 以{@code AgentModel}装饰器方式插入模型调用链，注册顺序即从外到内包裹在默认
     * 重试→语义缓存→限流链之上，满足自定义熔断、审计调用层、影子流量复制等需求。
     * </p>
     * @param layer 模型装饰器，入参为当前链上模型，返回装饰后的模型
     * @return
     */
    public HarnessRuntimeBuilder addModelCallerLayer(Function<AgentModel, AgentModel> layer) {
        if (layer != null) {
            this.modelCallerLayers.add(layer);
        }
        return this;
    }

    /**
     * 设置事件监听器注册中心，运行时的全部Agent事件自动广播给已注册监听器
     * @param eventBus
     * @return
     */
    public HarnessRuntimeBuilder eventBus(EventBus eventBus) {
        this.eventBus = eventBus;
        return this;
    }

    /**
     * 注册事件监听器（懒创建EventBus，运行时自动广播）
     * @param listener
     * @return
     */
    public HarnessRuntimeBuilder addEventListener(
            com.yangqiongai.agent.harness.event.AgentEventListener listener) {
        if (listener != null) {
            if (eventBus == null) {
                eventBus = new EventBus();
            }
            eventBus.register(listener);
        }
        return this;
    }

    /**
     * 注册自定义编排策略处理器，供auto_orchestrate按策略名调用
     * @param name 策略名（与工具入参strategy字符串比对，匹配即触发该处理器）
     * @param handler
     * @return
     */
    public HarnessRuntimeBuilder orchestrationStrategyHandler(String name,
            OrchestrationStrategyHandler handler) {
        if (name != null && handler != null) {
            this.orchestrationStrategyHandlers.put(name.toUpperCase(java.util.Locale.ROOT), handler);
        }
        return this;
    }

    @Override
    public HarnessRuntimeBuilder generateOptions(AgentGenerateOptions options) {
        this.generateOptions = options;
        return this;
    }

    @Override
    public HarnessRuntimeBuilder skillBox(AgentSkillBox skillBox) {
        this.skillBox = skillBox;
        return this;
    }

    /**
     * 设置工具下发模式
     * @param mode FULL全量下发（默认）；PROGRESSIVE渐进加载（常驻工具+目录注入+按需启用）
     * @return
     */
    public HarnessRuntimeBuilder toolLoadingMode(ToolLoadingMode mode) {
        if (mode != null) {
            this.toolLoadingMode = mode;
        }
        return this;
    }

    /**
     * 设置渐进模式下常驻工具名单（始终下发完整schema）
     * @param toolNames 工具名集合，空集表示全部工具进延迟池
     * @return
     */
    public HarnessRuntimeBuilder alwaysOnTools(Set<String> toolNames) {
        this.alwaysOnTools = toolNames;
        return this;
    }

    /**
     * 获取工具下发模式
     * @return
     */
    public ToolLoadingMode getToolLoadingMode() {
        return toolLoadingMode;
    }

    /**
     * 获取渐进模式常驻工具名单
     * @return
     */
    public Set<String> getAlwaysOnTools() {
        return alwaysOnTools;
    }

    @Override
    public HarnessRuntimeBuilder responseFormat(AgentResponseFormat format) {
        this.responseFormat = format;
        return this;
    }

    @Override
    public HarnessRuntimeBuilder structuredOutputType(Class<?> type) {
        this.structuredOutputType = type;
        return this;
    }

    @Override
    public HarnessRuntimeBuilder permission(AgentPermissionMode mode, AgentPermissionRule rule) {
        return this;
    }

    @Override
    public HarnessRuntimeBuilder permissionContextState(AgentPermissionContextState state) {
        this.permissionState = state;
        return this;
    }

    @Override
    public HarnessRuntimeBuilder memoryConfig(AgentMemoryConfig config) {
        this.memoryConfig = config;
        return this;
    }

    @Override
    public HarnessRuntimeBuilder compactionConfig(AgentCompactionConfig config) {
        this.compactionConfig = config;
        return this;
    }

    @Override
    public HarnessRuntimeBuilder toolResultEvictionConfig(AgentToolResultEvictionConfig config) {
        this.evictionConfig = config;
        return this;
    }

    @Override
    public HarnessRuntimeBuilder toolChoice(AgentToolChoice choice) {
        this.toolChoice = choice;
        return this;
    }

    /**
     * 启用多子代理
     * <p>
     * 显式开启子代理能力：已传静态声明时使用指定声明；
     * 未传静态声明时启用LLM动态生成模式（经SubagentSpecGenerator按任务分解）。
     * </p>
     * @return
     */
    public HarnessRuntimeBuilder enableSubagents() {
        this.subagentsEnabled = true;
        return this;
    }

    /**
     * 禁用多子代理
     * @return
     */
    public HarnessRuntimeBuilder disableSubagents() {
        this.subagentsEnabled = false;
        return this;
    }

    /**
     * 设置动态子代理启用开关（模式4 auto_orchestrate），默认启用
     * @param enabled
     * @return
     */
    @Override
    public HarnessRuntimeBuilder dynamicSubagentsEnabled(boolean enabled) {
        this.dynamicSubagentsEnabled = enabled;
        return this;
    }

    /**
     * 设置长期记忆工具启用开关，默认启用
     * @param enabled
     * @return
     */
    @Override
    public HarnessRuntimeBuilder memoryToolsEnabled(boolean enabled) {
        this.memoryToolsEnabled = enabled;
        return this;
    }

    /**
     * 设置记忆自动持久化钩子启用开关，默认启用
     * @param enabled
     * @return
     */
    @Override
    public HarnessRuntimeBuilder memoryHooksEnabled(boolean enabled) {
        this.memoryHooksEnabled = enabled;
        return this;
    }

    /**
     * 注入L3长期记忆存储
     * @param longTermMemory
     * @return
     */
    public HarnessRuntimeBuilder longTermMemory(AgentLongTermMemory longTermMemory) {
        this.longTermMemory = longTermMemory;
        return this;
    }

    /**
     * 注入L2会话级短期记忆
     * @param sessionMemory
     * @return
     */
    public HarnessRuntimeBuilder sessionMemory(SessionMemory sessionMemory) {
        this.sessionMemory = sessionMemory;
        return this;
    }

    /**
     * 设置基于LLM摘要的上下文压缩开关（与截断式压缩互斥）
     * @param enabled
     * @return
     */
    @Override
    public HarnessRuntimeBuilder summaryCompactionEnabled(boolean enabled) {
        this.summaryCompactionEnabled = enabled;
        return this;
    }

    /**
     * 设置L3→L1检索注入开关
     * @param enabled
     * @return
     */
    @Override
    public HarnessRuntimeBuilder memoryRetrievalEnabled(boolean enabled) {
        this.memoryRetrievalEnabled = enabled;
        return this;
    }

    /**
     * 设置L3→L1检索注入条目上限
     * @param topK
     * @return
     */
    public HarnessRuntimeBuilder memoryRetrievalTopK(int topK) {
        this.memoryRetrievalTopK = topK;
        return this;
    }

    @Override
    public HarnessRuntimeBuilder timeout(Duration timeout) {
        this.timeout = timeout;
        return this;
    }

    @Override
    public HarnessRuntimeBuilder timeoutMode(TimeoutMode mode) {
        if (mode != null) {
            this.timeoutMode = mode;
        }
        return this;
    }

    @Override
    public HarnessRuntimeBuilder iterationTimeout(Duration timeout) {
        this.iterationTimeout = timeout;
        return this;
    }

    @Override
    public HarnessRuntimeBuilder maxConcurrentToolCalls(int max) {
        this.maxConcurrentToolCalls = max;
        return this;
    }

    @Override
    public HarnessRuntimeBuilder allowedTools(Set<String> toolNames) {
        this.allowedTools = toolNames;
        return this;
    }

    @Override
    public HarnessRuntimeBuilder deniedTools(Set<String> toolNames) {
        this.deniedTools = toolNames;
        return this;
    }

    @Override
    public HarnessRuntimeBuilder requireApproval(Set<String> toolNames) {
        this.requireApproval = toolNames;
        return this;
    }

    /**
     * 设置统一审批模式
     * <p>
     * 四类模式的统一配置入口：MANUAL人工审批（破坏性工具暂停等待人工确认）、
     * AUTO由AI审批模型依据工具调用入参自动判定、FULL_ACCESS完全访问跳过全部审批、
     * CUSTOM沿用既有权限模式、规则、名单与策略门细粒度配置（默认）。
     * </p>
     * @param approvalMode 审批模式，null时按CUSTOM处理
     * @return
     */
    @Override
    public HarnessRuntimeBuilder approvalMode(AgentApprovalMode approvalMode) {
        this.approvalMode = approvalMode != null ? approvalMode : AgentApprovalMode.CUSTOM;
        return this;
    }

    /**
     * 设置AI自动审批（AUTO模式）的审批模型
     * @param judgeModel 审批模型，AUTO模式必填，未设置时构建报错
     * @return
     */
    @Override
    public HarnessRuntimeBuilder approvalJudgeModel(AgentModel judgeModel) {
        this.approvalJudgeModel = judgeModel;
        return this;
    }

    /**
     * 设置AI审批判定失败时的回退行为
     * @param fallbackAsk true回退人工确认（默认），false直接拒绝
     * @return
     */
    @Override
    public HarnessRuntimeBuilder aiApprovalFallbackAsk(boolean fallbackAsk) {
        this.aiApprovalFallbackAsk = fallbackAsk;
        return this;
    }

    /**
     * 设置AI审批附加策略描述，拼入判定提示词约束审批模型
     * @param guidance 策略描述，可空
     * @return
     */
    @Override
    public HarnessRuntimeBuilder aiApprovalGuidance(String guidance) {
        this.aiApprovalGuidance = guidance;
        return this;
    }

    @Override
    public HarnessRuntimeBuilder toolFailureStrategy(HarnessAgentRuntimeBuilder.ToolFailureStrategy strategy) {
        this.toolFailureStrategy = strategy;
        return this;
    }

    @Override
    public HarnessRuntimeBuilder maxToolRetries(int maxRetries) {
        this.maxToolRetries = maxRetries;
        return this;
    }

    @Override
    public HarnessRuntimeBuilder maxContextTokens(int maxTokens) {
        this.maxContextTokens = maxTokens;
        return this;
    }

    @Override
    public HarnessRuntimeBuilder historyTruncationStrategy(HarnessAgentRuntimeBuilder.TruncationStrategy strategy) {
        this.historyTruncationStrategy = strategy;
        return this;
    }

    @Override
    public HarnessRuntimeBuilder toolCallTimeout(Duration timeout) {
        this.toolCallTimeout = timeout;
        return this;
    }

    @Override
    public HarnessRuntimeBuilder maxToolResultChars(int maxChars) {
        this.maxToolResultChars = maxChars;
        return this;
    }

    @Override
    public HarnessRuntimeBuilder maxConsecutiveToolFailures(int maxFailures) {
        this.maxConsecutiveToolFailures = maxFailures;
        return this;
    }

    /**
     * 设置模型重试配置，启用后对模型调用增加带指数退避的重试
     * @param config
     * @return
     */
    public HarnessRuntimeBuilder modelRetryConfig(ModelRetryConfig config) {
        this.modelRetryConfig = config;
        return this;
    }

    /**
     * 设置子代理声明列表
     * @param declarations
     * @return
     */
    public HarnessRuntimeBuilder subagentDeclarations(List<SubagentDeclaration> declarations) {
        this.subagentDeclarations = declarations;
        return this;
    }

    /**
     * 指定编排策略默认值，LLM调用auto_orchestrate工具时若未指定strategy则使用该值，
     * 并自动转换为系统提示词指令
     * @param strategy
     * @return
     */
    public HarnessRuntimeBuilder orchestrationStrategy(OrchestrationStrategy strategy) {
        this.orchestrationStrategy = strategy;
        return this;
    }

    /**
     * 指定编排轮次，LLM调用auto_orchestrate工具时若未指定rounds则使用该值，
     * 不指定时可由LLM根据任务自行决定，两者均受maxOrchestrationRounds上限约束
     * @param rounds
     * @return
     */
    public HarnessRuntimeBuilder orchestrationRounds(Integer rounds) {
        this.orchestrationRounds = rounds;
        return this;
    }

    /**
     * 指定编排轮次上限，约束LLM自行决定或指定的轮次，避免无限迭代
     * @param maxRounds
     * @return
     */
    public HarnessRuntimeBuilder maxOrchestrationRounds(int maxRounds) {
        this.maxOrchestrationRounds = maxRounds;
        this.maxOrchestrationRoundsSet = true;
        return this;
    }

    @Override
    public AgentToolkit getToolkit() {
        return toolkit;
    }

    @Override
    public AgentSkillBox getSkillBox() {
        return skillBox;
    }

    @Override
    public List<AgentMiddleware> getMiddlewares() {
        return middlewares;
    }

    @Override
    public AgentMemoryConfig getMemoryConfig() {
        return memoryConfig;
    }

    @Override
    public AgentCompactionConfig getCompactionConfig() {
        return compactionConfig;
    }

    @Override
    public AgentToolResultEvictionConfig getEvictionConfig() {
        return evictionConfig;
    }

    @Override
    public AgentPermissionContextState getPermissionState() {
        return permissionState;
    }

    @Override
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

    @Override
    public Duration getIterationTimeout() {
        return iterationTimeout;
    }

    @Override
    public int getMaxConcurrentToolCalls() {
        return maxConcurrentToolCalls;
    }

    @Override
    public Set<String> getAllowedTools() {
        return allowedTools;
    }

    @Override
    public Set<String> getDeniedTools() {
        return deniedTools;
    }

    @Override
    public Set<String> getRequireApproval() {
        return requireApproval;
    }

    @Override
    public HarnessAgentRuntimeBuilder.ToolFailureStrategy getToolFailureStrategy() {
        return toolFailureStrategy;
    }

    @Override
    public int getMaxToolRetries() {
        return maxToolRetries;
    }

    @Override
    public int getMaxContextTokens() {
        return maxContextTokens;
    }

    @Override
    public HarnessAgentRuntimeBuilder.TruncationStrategy getHistoryTruncationStrategy() {
        return historyTruncationStrategy;
    }

    @Override
    public AgentGenerateOptions getGenerateOptions() {
        return generateOptions;
    }

    @Override
    public AgentToolChoice getToolChoice() {
        return toolChoice;
    }

    public List<SubagentDeclaration> getSubagentDeclarations() {
        return subagentDeclarations;
    }

    public boolean isPlanModeEnabled() {
        return planModeEnabled;
    }

    public boolean isMemoryToolsEnabled() {
        return memoryToolsEnabled;
    }

    public AgentRuntimeFactory getRuntimeFactory() {
        return runtimeFactory;
    }

    public AgentModelFactory getModelFactory() {
        return modelFactory;
    }

    public CheckpointManager getCheckpointManager() {
        return checkpointManager;
    }

    public AgentModel getModel() {
        return model;
    }

    public String getName() {
        return name;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public int getMaxIters() {
        return maxIters;
    }

    public AgentResponseFormat getResponseFormat() {
        return responseFormat;
    }

    public Class<?> getStructuredOutputType() {
        return structuredOutputType;
    }

    public boolean isAutoOrchestrationEnabled() {
        return autoOrchestrationEnabled;
    }

    /**
     * 设置自动编排全局开关
     * @param enabled
     * @return
     */
    public HarnessRuntimeBuilder autoOrchestrationEnabled(boolean enabled) {
        this.autoOrchestrationEnabled = enabled;
        return this;
    }

    public int getSubagentMaxDepth() {
        return subagentMaxDepth;
    }

    public void setSubagentMaxDepth(int subagentMaxDepth) {
        this.subagentMaxDepth = subagentMaxDepth;
    }

    public boolean isAllowRecursiveOrchestration() {
        return allowRecursiveOrchestration;
    }

    public void setAllowRecursiveOrchestration(boolean allowRecursiveOrchestration) {
        this.allowRecursiveOrchestration = allowRecursiveOrchestration;
    }

    public SubagentSpecGenerator getSpecGenerator() {
        return specGenerator;
    }

    public void setSpecGenerator(SubagentSpecGenerator specGenerator) {
        this.specGenerator = specGenerator;
    }

    public int getMaxSubagents() {
        return maxSubagents;
    }

    public void setMaxSubagents(int maxSubagents) {
        this.maxSubagents = maxSubagents;
    }

    public String getGenerationModelCode() {
        return generationModelCode;
    }

    public void setGenerationModelCode(String generationModelCode) {
        this.generationModelCode = generationModelCode;
    }

    /**
     * 设置计划模式启用开关
     * @param enabled
     * @return
     */
    public HarnessRuntimeBuilder planModeEnabled(boolean enabled) {
        this.planModeEnabled = enabled;
        return this;
    }

    /**
     * 启用输入护栏，前置拦截恶意输入（prompt注入关键词、敏感词），命中即中止
     * @return
     */
    public HarnessRuntimeBuilder enableInputGuardrail() {
        this.middlewares.add(new InputGuardrailMiddleware.Builder()
                .addDefaultInjectionPatterns()
                .action(InputGuardrailMiddleware.GuardrailAction.BLOCK)
                .build());
        return this;
    }

    /**
     * 启用输入护栏并指定自定义配置
     * @param middleware
     * @return
     */
    public HarnessRuntimeBuilder enableInputGuardrail(InputGuardrailMiddleware middleware) {
        if (middleware != null) {
            this.middlewares.add(middleware);
        }
        return this;
    }

    /**
     * 启用全链路工具护栏：工具参数与工具结果统一入护栏规则链，结果加fencing围栏
     * <p>
     * 使用默认规则注册中心（中英文注入检测 + 敏感信息脱敏）。
     * </p>
     * @return
     */
    public HarnessRuntimeBuilder enableToolGuardrail() {
        return enableToolGuardrail(new GuardrailRuleRegistry());
    }

    /**
     * 启用全链路工具护栏并指定规则注册中心（可配置规则库与注入检测器SPI）
     * @param registry
     * @return
     */
    public HarnessRuntimeBuilder enableToolGuardrail(GuardrailRuleRegistry registry) {
        if (registry != null) {
            this.guardrailRegistry = registry;
            this.middlewares.add(new ToolGuardrailMiddleware(registry));
        }
        return this;
    }

    /**
     * 构建Agent运行时
     * @return
     */
    @Override
    public AgentRuntime build() {
        // 运行时定制器：显式注册与JDK SPI发现的实现统一按order回调，扩展模块借此注入配置而无需修改builder
        List<com.yangqiongai.agent.harness.spi.RuntimeCustomizer> effectiveCustomizers =
                new ArrayList<>(customizers);
        java.util.ServiceLoader.load(com.yangqiongai.agent.harness.spi.RuntimeCustomizer.class)
                .forEach(effectiveCustomizers::add);
        effectiveCustomizers.stream()
                .sorted(java.util.Comparator.comparingInt(
                        com.yangqiongai.agent.harness.spi.RuntimeCustomizer::order))
                .forEach(customizer -> customizer.customize(this));

        // JDK SPI插件自动发现：扫描classpath注册扩展，未显式配置模型/存储时采用发现的默认实现
        if (autoDiscover && autoExtensions == null) {
            autoExtensions = HarnessDiscovery.discover();
            if (model == null && !autoExtensions.models().isEmpty()) {
                model = autoExtensions.models().get(0);
            }
            applyStoresIfUnconfigured(autoExtensions.stores());
            if (guardrailRegistry != null) {
                for (com.yangqiongai.agent.harness.guardrail.InjectionDetector detector
                        : autoExtensions.detectors()) {
                    guardrailRegistry.addDetector(detector);
                }
            }
        }
        if (model == null) {
            throw new IllegalStateException("AgentModel未配置");
        }

        HarnessToolkit harnessToolkit = new HarnessToolkit(toolkit);

        // SPI自动发现的工具接入工具箱
        if (autoExtensions != null) {
            for (com.yangqiongai.agent.harness.core.tool.AgentTool tool : autoExtensions.tools()) {
                harnessToolkit.addTool(tool);
            }
        }

        // MCP工具接入：已预热则复用，否则同步完成握手与工具发现，失败服务端跳过不影响构建
        if (!mcpServerConfigs.isEmpty()) {
            synchronized (mcpLock) {
                if (mcpToolkit == null) {
                    mcpToolkit = new McpToolkit(mcpServerConfigs);
                }
                if (!mcpToolkit.isInitialized()) {
                    mcpToolkit.initializeAll().block();
                }
                mcpToolkit.registerAll(harnessToolkit);
            }
        }

        // Handoff工具接入：注册了目标后，主代理可通过handoff工具转移控制权
        if (handoffRegistry != null && !handoffRegistry.names().isEmpty()) {
            harnessToolkit.addTool(new HandoffTool(handoffRegistry));
        }

        // RAG检索工具接入：注册了检索器后rag_search工具可用
        if (retrieverRegistry != null && retrieverRegistry.size() > 0) {
            harnessToolkit.addTool(new RetrieverTool(retrieverRegistry));
        }

        // Web检索/抓取工具接入：配置提供方或强制启用后注册
        if (webEnabled || webSearchProvider != null) {
            harnessToolkit.addTool(new WebSearchTool(webSearchProvider));
        }
        if (webEnabled || webFetcher != null) {
            harnessToolkit.addTool(new WebFetchTool(webFetcher));
        }

        // 向用户提问工具接入：显式启用后ask_user工具可用
        if (askUserEnabled) {
            harnessToolkit.addTool(new AskUserTool());
        }

        // Agent文件工具接入：显式启用后在沙箱内提供file_read/file_list
        if (fileToolkit != null) {
            harnessToolkit.addTool(fileToolkit.readTool());
            harnessToolkit.addTool(fileToolkit.listTool());
        }

        List<AgentMiddleware> allMiddlewares = new ArrayList<>(middlewares);

        // SPI自动发现的中间件接入链底
        if (autoExtensions != null) {
            allMiddlewares.addAll(autoExtensions.middlewares());
        }

        // 可观测性：配置了TraceEmitter时加入TraceMiddleware，span自动导出
        if (traceEmitter != null) {
            allMiddlewares.add(new TraceMiddleware(traceEmitter));
        }

        // 内容审查：配置了审查策略时加入ContentModerationMiddleware（BLOCK中止/MASK脱敏）
        if (contentModerationPolicy != null) {
            allMiddlewares.add(new ContentModerationMiddleware(contentModerationPolicy));
        }

        // 模型重试装饰 + ModelCaller创建（提前到中间件装配前，供摘要压缩使用）
        AgentModel effectiveModel = model;
        if (modelRetryConfig != null && modelRetryConfig.isEnabled()) {
            effectiveModel = new RetryableModelCaller(model, modelRetryConfig);
        }
        // 语义缓存装饰：查询嵌入与缓存向量余弦命中阈值直接返回缓存响应（外层重试→语义缓存）
        if (semanticCachingEnabled && embeddingModel != null) {
            effectiveModel = new SemanticCachingModelCaller(effectiveModel, embeddingModel,
                    semanticCacheThreshold, semanticCacheMaxEntries);
        }
        // 模型速率限制装饰：令牌桶限流RPM，防止突发打爆上游配额（外层重试→语义缓存→限流→内层降级）
        if (modelRateLimiter != null) {
            effectiveModel = new RateLimitedModel(effectiveModel, modelRateLimiter);
        }
        // 用户自定义模型调用层装饰：注册顺序即从外到内包裹在默认重试/缓存/限流链之上
        for (Function<AgentModel, AgentModel> layer : modelCallerLayers) {
            effectiveModel = layer.apply(effectiveModel);
        }
        ModelCaller modelCaller = new ModelCaller(effectiveModel);
        AgentGenerateOptions finalOptions = mergeResponseFormat(generateOptions, responseFormat);
        finalOptions = mergeToolChoice(finalOptions, toolChoice);
        // 上下文缓存开启时置cacheControlEnabled，供Anthropic协议适配器生成cache_control
        if (contextCachingConfig != null && contextCachingConfig.isEnabled()) {
            finalOptions = AgentGenerateOptions.mergeOptions(
                    AgentGenerateOptions.builder().cacheControlEnabled(true).build(), finalOptions);
        }
        modelCaller.setGenerateOptions(finalOptions);

        // L3长期记忆子系统装配：工具注册 + 自动持久化钩子 + L3→L1检索注入
        // 未显式注入longTermMemory但配置了嵌入模型时，默认L3切换为向量记忆
        AgentLongTermMemory effectiveLongTermMemory = longTermMemory;
        if (effectiveLongTermMemory == null && embeddingModel != null) {
            effectiveLongTermMemory = new VectorLongTermMemory(embeddingModel, memoryForgettingPolicy);
        }
        if (effectiveLongTermMemory != null) {
            LongTermMemoryAdapter ltmAdapter = new LongTermMemoryAdapter(
                    effectiveLongTermMemory, !memoryToolsEnabled, !memoryHooksEnabled);
            for (AgentTool tool : ltmAdapter.getMemoryTools()) {
                harnessToolkit.addTool(tool);
            }
            if (memoryHooksEnabled) {
                allMiddlewares.add(ltmAdapter);
            }
            // 检索注入先于摘要压缩加入链，保证"先注入记忆→再压缩"的执行顺序
            if (memoryRetrievalEnabled) {
                allMiddlewares.add(new MemoryRetrievalMiddleware(effectiveLongTermMemory, memoryRetrievalTopK));
            }
        }

        // RAG自动检索注入中间件（可选，仅注册了检索器且显式启用时生效）
        if (ragRetrievalEnabled && retrieverRegistry != null && retrieverRegistry.size() > 0) {
            allMiddlewares.add(new RagRetrievalMiddleware(
                    retrieverRegistry, ragSource, ragTopK, ragInjectionTemplate, true));
        }

        // 上下文压缩：摘要式（LLM压缩）与截断式互斥
        if (summaryCompactionEnabled) {
            AgentCompactionConfig effectiveCompaction = compactionConfig != null ? compactionConfig
                    : (memoryConfig != null ? memoryConfig.getCompactionConfig() : null);
            allMiddlewares.add(new SummaryCompactionMiddleware(effectiveCompaction, modelCaller, sessionMemory));
        } else if (compactionConfig != null) {
            allMiddlewares.add(new CompactionMiddleware(compactionConfig));
        } else if (memoryConfig != null && memoryConfig.getCompactionConfig() != null) {
            allMiddlewares.add(new CompactionMiddleware(memoryConfig.getCompactionConfig()));
        }
        if (evictionConfig != null) {
            allMiddlewares.add(new ToolResultEvictionMiddleware(evictionConfig));
        } else if (memoryConfig != null && memoryConfig.getToolResultEvictionConfig() != null) {
            allMiddlewares.add(new ToolResultEvictionMiddleware(memoryConfig.getToolResultEvictionConfig()));
        }
        if (planModeEnabled) {
            allMiddlewares.add(new PlanModeMiddleware());
        }
        // 智能启用：显式enableSubagents()或传入非空静态声明均视为启用；
        // 已传静态声明则使用指定声明，未传声明则走LLM动态生成
        boolean hasStaticDeclarations = subagentDeclarations != null && !subagentDeclarations.isEmpty();
        if (subagentsEnabled || hasStaticDeclarations) {
            SubagentContext capabilityContext = SubagentContext.capture(this);
            SubagentCapabilityPolicy capabilityPolicy = new SubagentCapabilityPolicy();
            SubagentSpawner spawner = new SubagentSpawner(
                    runtimeFactory, modelFactory, capabilityContext, capabilityPolicy);
            // 动态编排：auto.enabled全局开关 && dynamicSubagentsEnabled per-builder覆盖
            boolean dynamicEnabled = autoOrchestrationEnabled && dynamicSubagentsEnabled;
            // 未注入SubagentSpecGenerator时使用引擎自包含默认实现，支持独立框架开箱即用；
            // 已传静态声明时不交给LLM重新生成（AutoOrchestrateTool按声明是否为空决定）
            SubagentSpecGenerator effectiveSpecGenerator = specGenerator != null
                    ? specGenerator
                    : new DefaultSubagentSpecGenerator(modelCaller,
                            generationModelCode != null ? generationModelCode : modelCode);
            List<SubagentDeclaration> toolDeclarations = hasStaticDeclarations
                    ? subagentDeclarations : Collections.emptyList();
            AutoOrchestrateTool autoTool = dynamicEnabled
                    ? new AutoOrchestrateTool(spawner, toolDeclarations,
                            subagentMaxDepth, allowRecursiveOrchestration,
                            effectiveSpecGenerator, maxSubagents, generationModelCode,
                            orchestrationStrategy, orchestrationRounds, maxOrchestrationRounds)
                    : null;
            // 自定义编排策略处理器注册进auto_orchestrate引擎
            if (autoTool != null && !orchestrationStrategyHandlers.isEmpty()) {
                orchestrationStrategyHandlers.forEach(autoTool::registerOrchestrationHandler);
            }
            allMiddlewares.add(new SubagentsMiddleware(subagentDeclarations, spawner, harnessToolkit, autoTool));
        }

        if (skillBox != null) {
            SkillManager skillManager = new SkillManager(skillBox);
            if (!skillManager.isEmpty()) {
                allMiddlewares.add(new SkillPromptInjector(skillManager));
                harnessToolkit.addTool(new LoadSkillTool(skillManager));
                harnessToolkit.addTool(new ReadSkillResourceTool(skillManager));
            }
        }

        // 工具渐进加载：目录注入 + load_tool元工具，仅PROGRESSIVE模式装配
        ToolLoadingState progressiveState = null;
        if (toolLoadingMode == ToolLoadingMode.PROGRESSIVE && harnessToolkit != null) {
            progressiveState = new ToolLoadingState(true, alwaysOnTools);
            Set<String> catalogExcludes = new HashSet<>(progressiveState.getAlwaysOnTools());
            if (deniedTools != null) {
                catalogExcludes.addAll(deniedTools);
            }
            catalogExcludes.addAll(ToolLoadingState.META_TOOL_NAMES);
            allMiddlewares.add(new ToolCatalogPromptInjector(harnessToolkit, catalogExcludes));
            harnessToolkit.addTool(new LoadToolTool(harnessToolkit, progressiveState));
            log.info("工具渐进加载已启用: 常驻工具{}个, 延迟池{}个", progressiveState.getAlwaysOnTools().size(),
                    harnessToolkit.getTools().size() - progressiveState.getAlwaysOnTools().size());
        }

        MiddlewareChain middlewareChain = new MiddlewareChain(allMiddlewares);

        // 统一审批模式适配：按四种模式调整权限引擎与策略门装配
        ApprovalWiring approvalWiring = wireApprovalMode();

        PermissionEngine permissionEngine = approvalWiring.permissionEngine();

        // 统一权限策略门：未显式提供时按权限引擎与白/黑名单自动构建，收敛判定唯一出口
        ToolPolicyGate effectivePolicyGate = approvalWiring.policyGate();

        ToolExecutor toolExecutor = new ToolExecutor(harnessToolkit, middlewareChain, permissionEngine,
                allowedTools, deniedTools)
                .policyGate(effectivePolicyGate)
                .ledger(toolExecutionStore != null ? toolExecutionStore : new InMemoryToolExecutionStore());

        ModelResponseParser responseParser = new ModelResponseParser();
        ReActEngine reactEngine = new ReActEngine(modelCaller, toolExecutor, middlewareChain, responseParser,
                permissionEngine, checkpointManager)
                .approvalCoordinator(new ApprovalCoordinator(effectivePolicyGate, permissionEngine))
                .tokenBudgetPolicy(tokenBudgetPolicy)
                .costBudgetPolicy(costBudgetPolicy)
                .modelPricingRegistry(modelPricingRegistry)
                .modelCode(modelCode)
                .structuredOutputRetryPolicy(structuredOutputRetryPolicy);
        // 持久执行三SPI任一存在即启用状态跟踪器；运行锁随节点ID一并注入
        DurableExecutionTracker durableTracker = null;
        if (agentRunStore != null || checkpointStore != null || approvalStore != null) {
            durableTracker = new DurableExecutionTracker(agentRunStore, checkpointStore, approvalStore,
                    runLockStore, nodeId);
            reactEngine.durableTracker(durableTracker);
        }
        // 事件监听器注册中心：配置后运行时所有Agent事件自动广播给监听器
        if (eventBus != null) {
            reactEngine.eventBus(eventBus);
        }
        // 自定义执行循环替换默认ReActEngine，共享事件总线与持久执行装配
        AgentLoop engine = agentLoop != null ? agentLoop : reactEngine;

        String finalSystemPrompt = buildFinalSystemPrompt();

        EngineConfig config = new EngineConfig(name, finalSystemPrompt, maxIters, harnessToolkit,
                finalOptions, maxConcurrentToolCalls, timeout, iterationTimeout,
                toolFailureStrategy, maxToolRetries,
                maxContextTokens, historyTruncationStrategy,
                toolCallTimeout, maxToolResultChars, maxConsecutiveToolFailures);
        config.setTimeoutMode(timeoutMode);
        if (progressiveState != null) {
            config.toolLoadingState(progressiveState);
        }

        // 默认装配工具筛选器，减少无关工具Schema注入
        config.setToolFilter(new KeywordToolFilter());

        // 引擎服务暴露：与ReActEngine共享同一套模型调用、工具执行与响应解析组件，供自定义执行循环直接使用
        config.modelCaller(modelCaller)
                .toolExecutor(toolExecutor)
                .responseParser(responseParser);

        // 引擎组件暴露：与ReActEngine共享同一套中间件链、预算策略与事件总线，供自定义执行循环复用引擎级管线
        config.middlewareChain(middlewareChain)
                .tokenBudgetPolicy(tokenBudgetPolicy)
                .costBudgetPolicy(costBudgetPolicy)
                .eventBus(eventBus)
                .modelPricingRegistry(modelPricingRegistry)
                .modelCode(modelCode)
                .durableTracker(durableTracker)
                .approvalCoordinator(new ApprovalCoordinator(effectivePolicyGate, permissionEngine))
                .permissionEngine(permissionEngine);

        return new HarnessAgentRuntime(engine, config, mcpToolkit);
    }

    /**
     * 未配置任何子存储时，采用SPI发现的默认聚合存储
     * @param candidates
     */
    private void applyStoresIfUnconfigured(List<DistributedStores> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return;
        }
        boolean configured = agentRunStore != null || checkpointStore != null || approvalStore != null
                || sessionMemory != null || longTermMemory != null || toolExecutionStore != null
                || runLockStore != null;
        if (!configured) {
            stores(candidates.get(0));
        }
    }

    /**
     * 按统一审批模式装配权限引擎与策略门
     * <p>
     * 四种模式适配：MANUAL未显式配置权限状态时默认ASK语义（破坏性工具暂停等人工确认）；
     * AUTO装配AI自动审批策略门（静态名单短路后由审批模型判定，判定失败回退人工）；
     * FULL_ACCESS跳过全部权限引擎与审批集合；CUSTOM沿用既有细粒度配置不做调整。
     * 显式注入的toolPolicyGate在任一模式下均保持最高优先级。
     * </p>
     * @return
     */
    private ApprovalWiring wireApprovalMode() {
        if (approvalMode == AgentApprovalMode.FULL_ACCESS) {
            return new ApprovalWiring(null, toolPolicyGate != null
                    ? toolPolicyGate : null);
        }
        if (approvalMode == AgentApprovalMode.AUTO) {
            if (approvalJudgeModel == null) {
                throw new IllegalStateException("AUTO审批模式必须通过 approvalJudgeModel 设置审批模型");
            }
            // AI审批门：静态名单短路 + requireApproval强制人工 + 审批模型判定其余调用
            ToolPolicyGate aiGate = toolPolicyGate != null ? toolPolicyGate
                    : new AiToolApprovalGate(approvalJudgeModel, allowedTools, deniedTools,
                            requireApproval, aiApprovalFallbackAsk, aiApprovalGuidance);
            return new ApprovalWiring(null, aiGate);
        }
        if (approvalMode == AgentApprovalMode.MANUAL && permissionState == null) {
            PermissionEngine engine = new PermissionEngine(
                    AgentPermissionContextState.builder().mode(AgentPermissionMode.ASK).build(),
                    requireApproval);
            return new ApprovalWiring(engine, toolPolicyGate != null ? toolPolicyGate
                    : new ToolPolicyGate(engine, allowedTools, deniedTools, false, null));
        }
        // CUSTOM：沿用既有细粒度装配
        PermissionEngine engine = (permissionState != null || (requireApproval != null && !requireApproval.isEmpty()))
                ? new PermissionEngine(permissionState, requireApproval) : null;
        ToolPolicyGate gate = toolPolicyGate != null ? toolPolicyGate
                : new ToolPolicyGate(engine, allowedTools, deniedTools, false, null);
        return new ApprovalWiring(engine, gate);
    }

    /**
     * 审批装配产物
     * @author yangqiong
     */
    private record ApprovalWiring(PermissionEngine permissionEngine, ToolPolicyGate policyGate) {
    }

    /**
     * 将编排参数还原为系统提示词指令后与原提示词拼接
     * <p>
     * strategy/rounds/maxRounds以参数方式指定，内部转换为对LLM调用
     * auto_orchestrate工具的明确指引，调用方无需在提示词中手工书写。
     * </p>
     * @return
     */
    private String buildFinalSystemPrompt() {
        String orchestrationHint = buildOrchestrationPromptFragment();
        if (orchestrationHint.isEmpty()) {
            return systemPrompt;
        }
        return (systemPrompt != null ? systemPrompt : "") + "\n\n" + orchestrationHint;
    }

    /**
     * 由编排参数生成提示词指令片段
     * <p>
     * 未配置任何编排参数时返回空串；rounds未指定时提示LLM按任务自行决定，
     * 无论是否指定均受maxOrchestrationRounds约束。
     * </p>
     * @return
     */
    private String buildOrchestrationPromptFragment() {
        boolean strategySet = orchestrationStrategy != null;
        boolean roundsSet = orchestrationRounds != null;
        boolean maxSet = maxOrchestrationRoundsSet && maxOrchestrationRounds > 0;
        if (!strategySet && !roundsSet && !maxSet) {
            return "";
        }
        List<String> clauses = new ArrayList<>();
        if (strategySet) {
            if (orchestrationStrategy == OrchestrationStrategy.ADAPTIVE) {
                clauses.add("strategy可由你根据任务特征自行选择（SEQUENTIAL/PARALLEL/DEBATE/REFLECTION/GROUP_CHAT）");
            } else {
                clauses.add("strategy必须使用" + orchestrationStrategy.name());
            }
        }
        if (roundsSet) {
            clauses.add("rounds使用" + orchestrationRounds);
        } else {
            clauses.add("rounds可由你根据任务复杂度自行决定");
        }
        if (maxSet) {
            clauses.add("rounds不得超过" + maxOrchestrationRounds);
        }
        return "【自动编排参数】调用auto_orchestrate工具时：" + String.join("；", clauses) + "。";
    }

    /**
     * 将响应格式合并到生成选项中
     * @param options
     * @param format
     * @return
     */
    private AgentGenerateOptions mergeResponseFormat(AgentGenerateOptions options, AgentResponseFormat format) {
        if (format == null) {
            return options;
        }
        if (options == null) {
            return AgentGenerateOptions.builder().responseFormat(format).build();
        }
        if (options.getResponseFormat() != null) {
            return options;
        }
        return AgentGenerateOptions.builder()
                .temperature(options.getTemperature())
                .maxTokens(options.getMaxTokens())
                .topP(options.getTopP())
                .reasoningEffort(options.getReasoningEffort())
                .thinkingBudget(options.getThinkingBudget())
                .stream(options.isStream() ? Boolean.TRUE : null)
                .responseFormat(format)
                .toolChoice(options.getToolChoice())
                .build();
    }

    /**
     * 将工具选择策略合并到生成选项中
     * @param options
     * @param choice
     * @return
     */
    private AgentGenerateOptions mergeToolChoice(AgentGenerateOptions options, AgentToolChoice choice) {
        if (choice == null) {
            return options;
        }
        if (options == null) {
            return AgentGenerateOptions.builder().toolChoice(choice).build();
        }
        if (options.getToolChoice() != null) {
            return options;
        }
        return AgentGenerateOptions.builder()
                .temperature(options.getTemperature())
                .maxTokens(options.getMaxTokens())
                .topP(options.getTopP())
                .reasoningEffort(options.getReasoningEffort())
                .thinkingBudget(options.getThinkingBudget())
                .stream(options.isStream() ? Boolean.TRUE : null)
                .responseFormat(options.getResponseFormat())
                .toolChoice(choice)
                .build();
    }
}
